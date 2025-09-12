package pr.scheduler.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.grpc.client.GrpcChannelFactory;
import pr.pulsesynapse.proto.TargetServiceGrpc;

@Configuration
public class GrpcClientConfig {

    @Bean
    TargetServiceGrpc.TargetServiceBlockingStub targetServiceBlockingStub(GrpcChannelFactory factory) {
        return TargetServiceGrpc.newBlockingStub(factory.createChannel("target-management-service"));
    }
}
