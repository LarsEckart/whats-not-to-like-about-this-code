package kata;

import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

@Repository
public class DeliveryRepository {

    private static final Logger log = getLogger(DeliveryRepository.class);

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public DeliveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(String email, float la, float lo) {
        String sql =
                """
                        INSERT INTO
                            Delivery (email, date_of_delivery, latitude, longitude)
                        VALUES
                            ('%s',CURRENT_TIMESTAMP,%s,%s)
                        """.formatted(email, la, lo);
        jdbcTemplate.update(sql);
    }

    public List<Delivery> findTodaysDeliveries() {
        String sql = "SELECT * FROM Delivery WHERE DATE(date_of_delivery) = CURRENT_DATE";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            long id = rs.getLong("id");
            String email = rs.getString("email");
            float latitude = rs.getFloat("latitude");
            float longitude = rs.getFloat("longitude");
            Timestamp estimatedDeliveryDate = rs.getTimestamp("date_of_delivery");

            Delivery delivery = new Delivery(id, email, longitude, latitude, estimatedDeliveryDate.toLocalDateTime(), false, false);
            log.info(delivery.toString());
            return delivery;
        });
    }

    public void save(Delivery delivery) {
        String sql = "UPDATE Delivery SET date_of_delivery = ?, arrived = ?, onTime = ? where id = ?";
        jdbcTemplate.update(sql, delivery.getTimeOfDelivery(), delivery.isArrived(), delivery.isOnTime(), delivery.getId());
    }
}
