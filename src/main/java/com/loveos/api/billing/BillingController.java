package com.loveos.api.billing;

import com.loveos.api.auth.AuthenticatedUser;
import com.loveos.api.core.ApiResponse;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/account/billing")
public class BillingController {

  @GetMapping
    public ApiResponse<BillingSummary> get(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ApiResponse.of(new BillingSummary(
        "free",
        false,
        List.of(
            new Plan("free", "Free", 0, "INR", "never", List.of(
                "Unlimited messages and voice notes", "Your whole shared story"), true, true),
            new Plan("premium", "Together", 29_900, "INR", "year", List.of(
                "Full-resolution photographs", "Unlimited albums", "Printed keepsakes"),
                    false, false)),
        List.of()));
  }

  public record BillingSummary(
      String currentPlan, boolean providerConfigured, List<Plan> plans, List<Invoice> invoices) {}

  public record Plan(
      String key, String name, long priceMinor, String currency, String period,
      List<String> features, boolean current, boolean available) {}

  public record Invoice(
      String id, String date, long amountMinor, String currency, String status) {}
}
