package paypulse;

import static io.gatling.javaapi.core.CoreDsl.StringBody;
import static io.gatling.javaapi.core.CoreDsl.constantUsersPerSec;
import static io.gatling.javaapi.core.CoreDsl.global;
import static io.gatling.javaapi.core.CoreDsl.scenario;
import static io.gatling.javaapi.http.HttpDsl.http;
import static io.gatling.javaapi.http.HttpDsl.status;

import io.gatling.javaapi.core.ScenarioBuilder;
import io.gatling.javaapi.core.Simulation;
import io.gatling.javaapi.http.HttpProtocolBuilder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Payment burst load test against auth-gateway. */
public class PaymentBurstSimulation extends Simulation {

  private static final Pattern ACCESS_TOKEN =
      Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"");

  private final String gatewayUrl = envOrDefault("PAYPULSE_GATEWAY_URL", "http://localhost:8090");
  private final String username = envOrDefault("PAYPULSE_LOAD_USER", "admin");
  private final String password = envOrDefault("PAYPULSE_LOAD_PASSWORD", "admin");
  private final int targetRps = Integer.parseInt(envOrDefault("GATLING_TARGET_RPS", "100"));
  private final int durationMinutes = Integer.parseInt(envOrDefault("GATLING_DURATION_MINUTES", "5"));

  /** Single login for the whole simulation (avoids auth RPS storm). */
  private final String sharedToken = login();

  private final HttpProtocolBuilder httpProtocol =
      http.baseUrl(gatewayUrl)
          .acceptHeader("application/json")
          .contentTypeHeader("application/json")
          .authorizationHeader("Bearer " + sharedToken);

  private final ScenarioBuilder scenarioChain =
      scenario("PaymentBurst")
          .exec(
              session -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                String accountId = "acc-load-" + random.nextInt(100_000);
                double amount = (random.nextInt(490) + 10) + random.nextInt(100) / 100.0;
                String idempotencyKey = UUID.randomUUID().toString();
                return session
                    .set("accountId", accountId)
                    .set("amount", amount)
                    .set("idempotencyKey", idempotencyKey);
              })
          .exec(
              http("create_payment")
                  .post("/api/v1/payments")
                  .header("Idempotency-Key", session -> session.getString("idempotencyKey"))
                  .body(
                      StringBody(
                          session ->
                              "{\"accountId\":\""
                                  + session.getString("accountId")
                                  + "\",\"amount\":"
                                  + session.getDouble("amount")
                                  + ",\"currency\":\"USD\",\"merchantId\":\"load-merchant\"}"))
                  .check(status().in(200, 201, 202, 409)));

  {
    setUp(
            scenarioChain.injectOpen(
                constantUsersPerSec(targetRps)
                    .during(Duration.ofMinutes(durationMinutes))))
        .protocols(httpProtocol)
        .assertions(global().successfulRequests().percent().gt(95.0));
  }

  private String login() {
    try {
      HttpClient client = HttpClient.newHttpClient();
      String body = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
      HttpRequest request =
          HttpRequest.newBuilder()
              .uri(URI.create(gatewayUrl + "/auth/login"))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
              .build();
      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "login failed: " + response.statusCode() + " " + response.body());
      }
      Matcher matcher = ACCESS_TOKEN.matcher(response.body());
      if (!matcher.find()) {
        throw new IllegalStateException(
            "accessToken missing in login response: " + response.body());
      }
      return matcher.group(1);
    } catch (Exception e) {
      throw new IllegalStateException("login failed", e);
    }
  }

  private static String envOrDefault(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
