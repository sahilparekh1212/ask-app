package com.aisandbox.auth.handler;

import com.aisandbox.auth.event.AuditEventPublisher;
import com.aisandbox.auth.model.TokenResponse;
import com.aisandbox.auth.service.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);

	private final TokenService tokenService;
	private final ObjectMapper objectMapper;
	private final AuditEventPublisher auditEventPublisher;

	public OAuth2LoginSuccessHandler(TokenService tokenService, ObjectMapper objectMapper,
			AuditEventPublisher auditEventPublisher) {
		this.tokenService = tokenService;
		this.objectMapper = objectMapper;
		this.auditEventPublisher = auditEventPublisher;
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		OAuth2User user = (OAuth2User) authentication.getPrincipal();
		String userId = user.getAttribute("sub");
		String email = user.getAttribute("email");
		String name = user.getAttribute("name");

		TokenResponse tokens = tokenService.generateTokens(userId, email, name);
		auditEventPublisher.publish("User", "LOGIN", userId);
		log.info("Issued tokens for user email={}", email);

		response.setContentType("application/json");
		response.setStatus(HttpServletResponse.SC_OK);
		objectMapper.writeValue(response.getWriter(), tokens);
	}

}
