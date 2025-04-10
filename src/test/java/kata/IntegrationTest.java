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
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13.3")
            .withDatabaseName("delivery")
            .withUsername("admin")
            .withPassword("admin");

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private NoOpEmailGateway emailGateway;

    @Autowired
    private DeliveryRepository deliveryRepository;

    @Autowired
    private MapService mapService;
    public Random random = new Random(42L);

    @Test
    void it_works() {
        registerNewDelivery(httpHeaders(), "test@example.com");

        // verify that we save the delivery
        Delivery savedDelivery = deliveryRepository.findTodaysDeliveries().stream().filter(d -> d.getId() == 1).findFirst().orElseThrow();
        // needs fix!
        //  assertThat(savedDelivery.getLongitude()).isEqualTo(26.727897f);

        registerNewDelivery(httpHeaders(), "test2@example.com");
        registerNewDelivery(httpHeaders(), "test3@example.com");
        registerNewDelivery(httpHeaders(), "test4@example.com");
        registerNewDelivery(httpHeaders(), "test5@example.com");

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
        assertThat(lastEmail.message()).contains("Your delivery to ", " is next, estimated time of arrival is in 5150 minutes. Be ready!");

        // Verify the feedback email is sent
        assertThat(emailGateway.getSentEmails().get(0).recipient()).isEqualTo("test@example.com");
        assertThat(emailGateway.getSentEmails().get(0).subject()).isEqualTo("Your feedback is important to us");
        assertThat(emailGateway.getSentEmails().get(0).message()).contains("Regarding your delivery today at ", "How likely would you be to recommend this delivery service to a friend?");

        // verify state of Delivery
        Delivery delivery = deliveryRepository.findTodaysDeliveries().stream().filter(d -> d.getId() == 1).findFirst().orElseThrow();
        assertThat(delivery.isArrived()).isTrue();
        assertThat(delivery.isOnTime()).isTrue();

        // verify avg speed in MapService
        assertThat(mapService.averageSpeed).isEqualTo(50.0);

        // slower delivery
        String deliveryJson2 = """
                {
                  "id": 2,
                  "timeOfDelivery": "%s",
                  "latitude": 58.377066,
                  "longitude": 26.727897
                }""".formatted(LocalDateTime.now().plusMinutes(11));
        HttpEntity<String> deliveryRequest2 = new HttpEntity<>(deliveryJson2, httpHeaders());
        restTemplate.exchange("/delivery/update", HttpMethod.POST, deliveryRequest2, String.class);

        Delivery delivery2 = deliveryRepository.findTodaysDeliveries().stream().filter(d -> d.getId() == 2).findFirst().orElseThrow();
        assertThat(delivery2.isArrived()).isTrue();
        assertThat(delivery2.isOnTime()).isFalse();

        // another late delivery
        String deliveryJson3 = """
                {
                  "id": 3,
                  "timeOfDelivery": "%s",
                  "latitude": 58.377088,
                  "longitude": 26.727897
                }""".formatted(LocalDateTime.now().plusMinutes(12));
        HttpEntity<String> deliveryRequest3 = new HttpEntity<>(deliveryJson3, httpHeaders());
        restTemplate.exchange("/delivery/update", HttpMethod.POST, deliveryRequest3, String.class);


        assertThat(mapService.averageSpeed).isEqualTo(0.5346282571868953);
    }

    private static @NotNull HttpHeaders httpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void registerNewDelivery(HttpHeaders headers, String mail) {
        int i = random.nextInt(19);
        String newDeliveryJson = """
                {
                  "email": "%s",
                  "latitude": 58.3770%d,
                  "longitude": 26.727897
                }""".formatted(mail, i);

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
