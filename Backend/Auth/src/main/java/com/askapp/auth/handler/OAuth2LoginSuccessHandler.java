package com.askapp.auth.handler;

import com.askapp.auth.event.AuditEventPublisher;
import com.askapp.auth.model.Roles;
import com.askapp.auth.model.TokenResponse;
import com.askapp.auth.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Hands the freshly-issued tokens to the SPA via a redirect, instead of writing them as raw JSON
 * (which would otherwise land on the Auth server's own origin after the Google redirect —
 * unusable from the UI, which is a different origin).
 *
 * <p>Tokens travel in the URL <em>fragment</em> ({@code #...}), not the query string: fragments
 * are never sent to the server on the next request, never appear in server access logs or
 * {@code Referer} headers, and the UI route can read them once and strip them from history
 * immediately. (The alternative considered was an httpOnly cookie — rejected for now since it'd
 * mean redesigning how the rest of the API already expects tokens, as an {@code Authorization:
 * Bearer} header the client attaches itself, not a cookie the browser attaches automatically.)
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

	private final TokenService tokenService;
	private final AuditEventPublisher auditEventPublisher;
	private final String frontendUrl;

	public OAuth2LoginSuccessHandler(TokenService tokenService, AuditEventPublisher auditEventPublisher,
			@Value("${app.frontend-url:http://localhost:4200}") String frontendUrl) {
		this.tokenService = tokenService;
		this.auditEventPublisher = auditEventPublisher;
		this.frontendUrl = frontendUrl;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		OAuth2User user = (OAuth2User) authentication.getPrincipal();
		String userId = user.getAttribute("sub");
		String email = user.getAttribute("email");
		String name = user.getAttribute("name");

		// Google carries no notion of our app roles, so every OAuth login is a plain user;
		// ROLE_ADMIN can only be minted via the demo login, for testing admin-gated endpoints.
		TokenResponse tokens = tokenService.generateTokens(userId, email, name, Roles.ROLE_USER);
		auditEventPublisher.publish("User", "LOGIN", userId);
		log.info("Issued tokens for user email={}, redirecting to SPA callback", email);

		response.sendRedirect(callbackUrl(tokens));
	}

	private String callbackUrl(TokenResponse tokens) {
		return frontendUrl + "/login/callback#access_token=" + encode(tokens.accessToken())
			+ "&refresh_token=" + encode(tokens.refreshToken())
			+ "&expires_in=" + tokens.expiresIn()
			+ "&token_type=Bearer";
	}

	private static String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}

}
