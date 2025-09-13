package pr.polling.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String INCOMING_QUEUE_NAME = "check_jobs_queue";

    public static final String RESULTS_EXCHANGE_NAME = "results_exchange";
    public static final String RESULTS_QUEUE_NAME = "check_results_queue";
    public static final String RESULTS_ROUTING_KEY = "results.check";

    @Bean
    Queue resultsQueue() {
        return new Queue(RESULTS_QUEUE_NAME, true);
    }

    @Bean
    TopicExchange resultsExchange() {
        return new TopicExchange(RESULTS_EXCHANGE_NAME);
    }

    @Bean
    Binding resultsBinding(Queue resultsQueue, TopicExchange resultsExchange) {
        return BindingBuilder.bind(resultsQueue).to(resultsExchange).with(RESULTS_ROUTING_KEY);
    }
}
