package kata;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> pgvector = new PostgreSQLContainer<>("postgres:13.3")
            .withDatabaseName("delivery")
            .withUsername("admin")
            .withPassword("admin");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private NoOpEmailGateway emailGateway;

    @Test
    void it_works() {

        registerNewDelivery(httpHeaders(), "test@example.com");
        registerNewDelivery(httpHeaders(), "test2@example.com");

        String deliveryJson = """
                {
                  "id": 1,
                  "timeOfDelivery": "%s",
                  "latitude": 58.377066,
                  "longitude": 26.727897
                }""".formatted(LocalDateTime.now());
        HttpEntity<String> deliveryRequest = new HttpEntity<>(deliveryJson, httpHeaders());
        restTemplate.exchange("/delivery/update", HttpMethod.POST, deliveryRequest, String.class);

        // Verify the email sent
        NoOpEmailGateway.Email lastEmail = emailGateway.getLastEmail();
        assertThat(lastEmail.recipient()).isEqualTo("test2@example.com");
        assertThat(lastEmail.subject()).isEqualTo("Your delivery will arrive soon");
        assertThat(lastEmail.message()).contains("Your delivery to ", " is next, estimated time of arrival is in 0 minutes. Be ready!");

        // Verify the feedback email is sent
        assertThat(emailGateway.getSentEmails().get(0).recipient()).isEqualTo("test@example.com");
        assertThat(emailGateway.getSentEmails().get(0).subject()).isEqualTo("Your feedback is important to us");
        assertThat(emailGateway.getSentEmails().get(0).message()).contains("Regarding your delivery today at ", "How likely would you be to recommend this delivery service to a friend?");
    }

    private static @NotNull HttpHeaders httpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void registerNewDelivery(HttpHeaders headers, String mail) {
        String newDeliveryJson = """
                {
                  "email": "%s",
                  "latitude": 58.377065,
                  "longitude": 26.727897
                }""".formatted(mail);

        HttpEntity<String> newDeliveryRequest = new HttpEntity<>(newDeliveryJson, headers);
        restTemplate.exchange("/delivery/new", HttpMethod.POST, newDeliveryRequest, String.class);
    }

    @Test
    void when_no_delivery_found_with_given_id_then_no_email_sent() {

        String deliveryJson = """
                {
                  "id": 99999999999999,
                  "timeOfDelivery": "%s",
                  "latitude": 58.377066,
                  "longitude": 26.727897
                }""".formatted(LocalDateTime.now());
        HttpEntity<String> deliveryRequest = new HttpEntity<>(deliveryJson, httpHeaders());
        restTemplate.exchange("/delivery/update", HttpMethod.POST, deliveryRequest, String.class);

        // Verify no email is sent
        assertThat(emailGateway.getSentEmails()).isEmpty();
    }

    @TestConfiguration
    static class TestConfig {
        @Bean
        public EmailGateway emailGateway() {
            return new NoOpEmailGateway();
        }
    }
}
