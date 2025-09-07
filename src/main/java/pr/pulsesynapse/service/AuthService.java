package pr.pulsesynapse.service;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.security.crypto.password.PasswordEncoder;
import pr.pulsesynapse.entity.User;
import pr.pulsesynapse.proto.AuthServiceGrpc;
import pr.pulsesynapse.proto.RegisterRequest;
import pr.pulsesynapse.proto.RegisterResponse;
import pr.pulsesynapse.repository.UserRepository;

import java.util.UUID;

@GrpcService
@RequiredArgsConstructor
public class AuthService extends AuthServiceGrpc.AuthServiceImplBase {

    private static Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void registerUser(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver) {
        log.info("Received registration request for username: {}", request.getUsername());

        if (userRepository.findByUsername(request.getUsername()) != null) {
            throw new RuntimeException("Username is already in use");
        }

        if (userRepository.findByEmail(request.getEmail()) != null) {
            throw new RuntimeException("Email is already in use");
        }

        User newUser = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .build();

        User savedUser = userRepository.save(newUser);

        log.info("Saved new user with id: {}", savedUser.getId());

        RegisterResponse response = RegisterResponse.newBuilder()
                .setUserId(savedUser.getId().toString())
                .build();

        responseObserver.onNext(response);

        responseObserver.onCompleted();
    }
}
