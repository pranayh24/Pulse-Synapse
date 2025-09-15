package pr.scheduler.task;

import com.google.protobuf.Empty;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pr.pulsesynapse.proto.TargetListResponse;
import pr.pulsesynapse.proto.TargetResponse;
import pr.pulsesynapse.proto.TargetServiceGrpc;
import pr.scheduler.config.RabbitMQConfig;
import pr.scheduler.job.CheckJob;

@Slf4j
@Component
@RequiredArgsConstructor
public class TargetPollingScheduler {

    private final TargetServiceGrpc.TargetServiceBlockingStub targetServiceStub;
    private final RabbitTemplate rabbitTemplate;

    @Scheduled(fixedRateString = "${polling.schedule.rate.ms}")
    public void scheduleTargetChecks() {
        log.info("Scheduler running: Fetching targets to poll...");
        try {
            TargetListResponse response = targetServiceStub.getDueTargets(Empty.newBuilder().build());
            int targetsCount = response.getTargetsCount();
            log.info("Found {} targets to schedule.",  targetsCount);

            for (TargetResponse target : response.getTargetsList()) {
                CheckJob job = new CheckJob(target.getId(), target.getUrl());

                rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, RabbitMQConfig.ROUTING_KEY, job);
                log.info("Sent job for target ID: {}", target.getId());
            }
        } catch (StatusRuntimeException e) {
            log.error("Error calling target-management-service: {}", e.getStatus());
        } catch (Exception e) {
            log.error("An unexpected error occurred during scheduling.", e);
        }
    }
}
