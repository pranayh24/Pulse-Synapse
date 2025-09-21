package pr.ingestion.listener;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pr.ingestion.dto.CheckResult;

@Slf4j
@Component
@RequiredArgsConstructor
public class ResultListener {

    private final InfluxDBClient influxDBClient;

    @Value("${influxdb.org}")
    private String organization;

    @Value("${influxdb.bucket}")
    private String bucket;

    @RabbitListener(queues = "check_results_queue")
    public void handleResult(CheckResult result) {
        log.info("Received result for target ID: {}. Status: {}", result.getTargetId(), result.isUp() ? "UP" : "DOWN");

        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();

        Point point = Point.measurement("health_check")
                .addTag("targetId", result.getTargetId())
                .addField("isUp", result.isUp() ? 1 : 0)
                .addField("latency_ms", result.getLatencyMs())
                .addField("status_code", result.getStatusCode() != null ? result.getStatusCode() : 0)
                .time(result.getTimestamp(), WritePrecision.MS);

        try {
            writeApi.writePoint(bucket, organization, point);
            log.info("Successfully wrote data point to InfluxDB for target ID: {}", result.getTargetId());
        } catch (Exception e) {
            log.error("Failed to write to InfluxDB for target ID: {}. Error: {}", result.getTargetId(), e.getMessage());
        }
    }

}
