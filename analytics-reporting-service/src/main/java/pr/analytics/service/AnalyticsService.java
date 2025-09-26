package pr.analytics.service;

import com.google.protobuf.Timestamp;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.influxdb.query.FluxTable;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.grpc.server.service.GrpcService;
import pr.pulsesynapse.proto.*;

import java.time.Instant;
import java.util.List;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class AnalyticsService extends AnalyticsServiceGrpc.AnalyticsServiceImplBase {

    private final InfluxDBClient influxDBClient;

    @Value("${influxdb.bucket}")
    private String bucket;

    @Value("${influxdb.org}")
    private String organization;

    @Override
    public void getUptime(UptimeRequest request, StreamObserver<UptimeResponse> responseObserver) {
        Instant startTime = Instant.ofEpochSecond(request.getStartTime().getSeconds(), request.getStartTime().getNanos());
        Instant endTime = Instant.ofEpochSecond(request.getEndTime().getSeconds(), request.getEndTime().getNanos());

        // Flux query to calculate uptime
        String fluxQuery = String.format(
                "from(bucket: \"%s\")\n" +
                        "  |> range(start: %s, stop: %s)\n" +
                        "  |> filter(fn: (r) => r._measurement == \"health_check\" and r.targetId == \"%s\" and r._field == \"is_up\")\n" +
                        "  |> toFloat()\n" +
                        "  |> mean()",
                bucket, startTime, endTime, request.getTargetId()
        );

        log.info("Executing Uptime Flux Query: {}", fluxQuery);
        QueryApi queryApi = influxDBClient.getQueryApi();

        try {
            List<FluxTable> tables = queryApi.query(fluxQuery, organization);
            double uptimePercentage = 0.0;

            if (!tables.isEmpty() && !tables.get(0).getRecords().isEmpty()) {
                Double mean = (Double) tables.get(0).getRecords().get(0).getValue();
                if (mean != null) {
                    uptimePercentage = mean * 100.0;
                }
            }

            UptimeResponse response = UptimeResponse.newBuilder()
                    .setTargetId(request.getTargetId())
                    .setUptimePercentage(uptimePercentage)
                    .build();

            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error querying InfluxDB for uptime: {}", e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to query uptime data").asRuntimeException());
        }
    }

    @Override
    public void getLatencyHistory(LatencyHistoryRequest request, StreamObserver<LatencyHistoryResponse> responseObserver) {
        Instant startTime = Instant.ofEpochSecond(request.getStartTime().getSeconds(), request.getStartTime().getNanos());
        Instant endTime = Instant.ofEpochSecond(request.getEndTime().getSeconds(), request.getEndTime().getNanos());

        // Flux query to get historical latency data
        String fluxQuery = String.format(
                "from(bucket: \"%s\")\n" +
                        "  |> range(start: %s, stop: %s)\n" +
                        "  |> filter(fn: (r) => r._measurement == \"health_check\" and r.targetId == \"%s\" and r._field == \"latency_ms\")",
                bucket, startTime, endTime, request.getTargetId()
        );

        log.info("Executing Latency Flux Query: {}", fluxQuery);
        QueryApi queryApi = influxDBClient.getQueryApi();

        try {
            LatencyHistoryResponse.Builder responseBuilder = LatencyHistoryResponse.newBuilder().setTargetId(request.getTargetId());

            queryApi.query(fluxQuery, organization, (cancellable, record) -> {
                Instant timestamp = record.getTime();
                Long latency = (Long) record.getValue();

                if (timestamp != null && latency != null) {
                    responseBuilder.addHistory(LatencyDataPoint.newBuilder()
                            .setTimestamp(Timestamp.newBuilder().setSeconds(timestamp.getEpochSecond()).setNanos(timestamp.getNano()).build())
                            .setLatencyMs(latency)
                            .build());
                }
            });

            responseObserver.onNext(responseBuilder.build());
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error querying InfluxDB for latency history: {}", e.getMessage());
            responseObserver.onError(Status.INTERNAL.withDescription("Failed to query latency data").asRuntimeException());
        }
    }
}