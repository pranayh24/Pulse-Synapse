package pr.polling.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import pr.polling.config.RabbitMQConfig;
import pr.polling.job.CheckJob;
import pr.polling.result.CheckResult;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMQListener {

    private final WebClient webClient;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConfig.INCOMING_QUEUE_NAME)
    public void handleJob(CheckJob job) {
        log.info("Received job for target ID: {}. URL: {}", job.getTargetId(), job.getUrl());

        long startTime = System.currentTimeMillis();

        webClient.get()
                .uri(job.getUrl())
                .exchangeToMono(response -> {
                    long latency = System.currentTimeMillis() - startTime;
                    CheckResult result = CheckResult.builder()
                            .targetId(job.getTargetId())
                            .timestamp(Instant.now())
                            .isUp(response.statusCode().is2xxSuccessful())
                            .statusCode(response.statusCode().value())
                            .latencyMs(latency)
                            .build();
                    return Mono.just(result);
                })
                .onErrorResume(error -> {
                    // network errors or dns failures
                    long latency = System.currentTimeMillis() - startTime;
                    CheckResult result = CheckResult.builder()
                            .targetId(job.getTargetId())
                            .timestamp(Instant.now())
                            .isUp(false)
                            .latencyMs(latency)
                            .errorMessage(error.getMessage())
                            .build();
                    return Mono.just(result);
                })
                .doOnSuccess(this :: publishResult)
                .subscribe();
    }

    private void publishResult(CheckResult result) {
        log.info("Publishing result for target ID: {}. Status: {}", result.getTargetId(), result.isUp() ? "UP" : "DOWN");
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.RESULTS_EXCHANGE_NAME,
                RabbitMQConfig.RESULTS_ROUTING_KEY,
                result);
    }
}
