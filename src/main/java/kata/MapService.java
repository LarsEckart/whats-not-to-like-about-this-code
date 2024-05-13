package kata;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class MapService {

    // in km/h
    private double averageSpeed = 50.0;

    private final int MINUTES_PER_HOUR = 60;
    private final int SECONDS_PER_HOUR = 3600;
    private final double R = 6373.0;

    public Duration calculateETA(float latitude, float longitude,
                                 float otherLatitude, float otherLongitude) {
        var distance = this.calculateDistance(latitude, longitude,
                otherLatitude, otherLongitude);
        Double v = distance / this.averageSpeed * MINUTES_PER_HOUR;
        return Duration.ofMinutes(v.longValue());
    }

    public void updateAverageSpeed(Duration elapsedTime, float la, float lo, float la2, float lo2) {
        var distance = this.calculateDistance(la, lo, la2, lo2);
        var updatedSpeed = distance / (elapsedTime.getSeconds() / (double) SECONDS_PER_HOUR);
        this.averageSpeed = updatedSpeed;
    }

    private double calculateDistance(float la, float lo, float la2, float lo2) {
        var d1 = la * (Math.PI / 180.0);
        var num1 = lo * (Math.PI / 180.0);
        var d2 = la2 * (Math.PI / 180.0);
        var num2 = lo2 * (Math.PI / 180.0) - num1;
        var d3 = Math.pow(Math.sin((d2 - d1) / 2.0), 2.0) + Math.cos(d1) * Math.cos(d2) * Math.pow(
                Math.sin(num2 / 2.0), 2.0);

        return R * (2.0 * Math.atan2(Math.sqrt(d3), Math.sqrt(1.0 - d3)));
    }
}
