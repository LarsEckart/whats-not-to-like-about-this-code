package kata;

import kata.DeliveryController.DeliveryEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

@ExtendWith(MockitoExtension.class)
class DeliveryControllerTest {

    @Mock
    private DeliveryRepository deliveryRepository;

    @Mock
    private MapService mapService;

    @Mock
    private SendgridEmailGatewayImpl sendgridEmailGatewayImpl;

    @InjectMocks
    private DeliveryController deliveryController;

    @Test
    void testDeliveryDelivered() {
        DeliveryEvent deliveryEvent = mock(DeliveryEvent.class);
        when(deliveryEvent.id()).thenReturn(2L);
        LocalDateTime now = LocalDateTime.now();
        when(deliveryEvent.timeOfDelivery()).thenReturn(now);
        Delivery delivery = testDelivery(2L, now.minusMinutes(1));
        List<Delivery> deliveries = List.of(
                testDelivery(1L, now.minusMinutes(5)),
                delivery,
                testDelivery(3L, null),
                testDelivery(4L, null));
        when(deliveryRepository.findTodaysDeliveries()).thenReturn(deliveries);
        when(mapService.calculateETA(0.0f, 0.0f, 1.0f, 1.0f)).thenReturn(Duration.ofMinutes(5));
        deliveryController.onDelivery(deliveryEvent);
        verify(deliveryRepository).save(delivery);
        verify(mapService).calculateETA(0.0f, 0.0f, 1.0f, 1.0f);
        verify(sendgridEmailGatewayImpl).send("test@test.test",
                "Your feedback is important to us",
                """
                        Regarding your delivery today at %s.
                        How likely would you be to recommend this delivery service to a friend?

                        Click <a href='http://example.com/feedback'>here</a>""".formatted(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(now.minusMinutes(1))));
        verify(sendgridEmailGatewayImpl).send("test@test.test", "Your delivery will arrive soon", "Your delivery to [1.0,1.0] is next, estimated time of arrival is in 5 minutes. Be ready!");
    }

    private static Delivery testDelivery(long id, LocalDateTime timeOfDelivery) {
        Delivery delivery = mock(Delivery.class, withSettings().strictness(Strictness.LENIENT));
        when(delivery.getId()).thenReturn(id);
        when(delivery.getContactEmail()).thenReturn("test@test.test");
        when(delivery.getLatitude()).thenReturn(1.0f);
        when(delivery.getLongitude()).thenReturn(1.0f);
        when(delivery.getTimeOfDelivery()).thenReturn(timeOfDelivery);
        return delivery;
    }
}
