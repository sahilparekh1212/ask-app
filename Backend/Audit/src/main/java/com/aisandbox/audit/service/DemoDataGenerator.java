package com.aisandbox.audit.service;

import com.aisandbox.audit.model.AuditLog;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Produces randomized-but-realistic audit rows for the demo endpoint, using the same
 * entityType/action vocabulary as the startup {@code DemoDataSeeder} so generated rows blend
 * with the seeded ones (and exercise the dashboard's dropdowns, stats and details search).
 *
 * <p>LOCAL/DEV only, like the seeder — dummy rows have no business in a real audit trail.
 */
@Component
@Profile({"LOCAL", "DEV"})
public class DemoDataGenerator {

	/** One weighted-equal template per row; {@code %d} receives a random 4-digit id. */
	private record Template(String entityType, String action, String detailsPattern) {
	}

	private static final List<Template> TEMPLATES = List.of(
		new Template("User", "LOGIN", "User #%d signed in"),
		new Template("User", "TOKEN_REFRESH", "Access token refreshed for user #%d"),
		new Template("User", "LOGOUT", "User #%d signed out"),
		new Template("Order", "CREATE", "Order #%d created"),
		new Template("Order", "UPDATE", "Order #%d shipped"),
		new Template("Order", "UPDATE", "Order #%d refunded"),
		new Template("Order", "DELETE", "Order #%d cancelled"),
		new Template("Payment", "CREATE", "Payment captured for order #%d"),
		new Template("Payment", "DELETE", "Payment reversed for order #%d"),
		new Template("Inventory", "UPDATE", "Stock decremented for SKU-%d"),
		new Template("Inventory", "UPDATE", "Stock replenished for SKU-%d"),
		new Template("Report", "CREATE", "Sales report #%d generated")
	);

	public List<AuditLog> generate(int count) {
		ThreadLocalRandom rnd = ThreadLocalRandom.current();
		List<AuditLog> rows = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			Template t = TEMPLATES.get(rnd.nextInt(TEMPLATES.size()));
			rows.add(new AuditLog(t.entityType(), t.action(), t.detailsPattern().formatted(rnd.nextInt(1000, 10000))));
		}
		return rows;
	}

}
