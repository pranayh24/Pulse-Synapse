package pr.targetmanagementservice.service;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.service.GrpcService;
import pr.pulsesynapse.proto.*;
import pr.targetmanagementservice.entity.Target;
import pr.targetmanagementservice.repository.TargetRepository;
import pr.targetmanagementservice.security.JwtAuthInterceptor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class TargetService extends TargetServiceGrpc.TargetServiceImplBase {

    private final TargetRepository targetRepository;

    @Override
    public void addTarget(AddTargetRequest request, StreamObserver<TargetResponse> responseObserver ){
        String userId = JwtAuthInterceptor.getCurrentUserId();
        String username = JwtAuthInterceptor.getCurrentUsername();

        log.info("Received AddTargetRequest from userId: {} (username: {})", userId, username);

        Target target = Target.builder()
                .userId(UUID.fromString(userId))
                .name(request.getName())
                .url(request.getUrl())
                .checkIntervalSeconds(request.getCheckIntervalSeconds())
                .build();

        Target savedTarget = targetRepository.save(target);

        TargetResponse response = TargetResponse.newBuilder()
                .setId(savedTarget.getId().toString())
                .setName(savedTarget.getName())
                .setUrl(savedTarget.getUrl())
                .setCheckIntervalSeconds(savedTarget.getCheckIntervalSeconds())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();

    }

    @Override
    public void listMyTargets(Empty request, StreamObserver<TargetListResponse> responseObserver) {
        String userId = JwtAuthInterceptor.getCurrentUserId();
        String username = JwtAuthInterceptor.getCurrentUsername();

        log.info("Received ListMyTargets request from userId: {} (username: {})", userId, username);

        List<Target> targetList = targetRepository.findAllByUserId(UUID.fromString(userId));

        List<TargetResponse> responseList = targetList.stream()
                .map(target -> TargetResponse.newBuilder()
                        .setId(target.getId().toString())
                        .setName(target.getName())
                        .setUrl(target.getUrl())
                        .setCheckIntervalSeconds(target.getCheckIntervalSeconds())
                        .build())
                .toList();

        TargetListResponse targetListResponse = TargetListResponse.newBuilder()
                .addAllTargets(responseList)
                .build();

        responseObserver.onNext(targetListResponse);
        responseObserver.onCompleted();
    }

    @Override
    public void deleteTarget(DeleteTargetRequest request, StreamObserver<Empty> responseObserver){
        String userId = JwtAuthInterceptor.getCurrentUserId();
        String username = JwtAuthInterceptor.getCurrentUsername();

        log.info("Received deleteTarget request from userId: {} (username: {}) and targetId", userId, username, request.getId());

        targetRepository.deleteByIdAndUserId(UUID.fromString(request.getId()), UUID.fromString(userId));

        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void getAllTargets(Empty request, StreamObserver<TargetListResponse> responseObserver){
        List<Target> allTargets = targetRepository.findAll();

        List<TargetResponse> responseList = allTargets.stream()
                .map(target -> TargetResponse.newBuilder()
                        .setId(target.getId().toString())
                        .setName(target.getName())
                        .setUrl(target.getUrl())
                        .setCheckIntervalSeconds(target.getCheckIntervalSeconds())
                        .build())
                .toList();

        TargetListResponse response = TargetListResponse.newBuilder()
                .addAllTargets(responseList)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
