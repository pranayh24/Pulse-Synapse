package pr.pulsesynapse.service;

import com.google.protobuf.Empty;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import pr.pulsesynapse.entity.User;
import pr.pulsesynapse.proto.*;
import pr.pulsesynapse.repository.UserRepository;
import pr.pulsesynapse.utils.JwtUtil;

@GrpcService
@RequiredArgsConstructor
public class AuthService extends AuthServiceGrpc.AuthServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

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

    @Override
    public void loginUser(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
        log.info("Received login request for username: {}", request.getUsername());

        User user = userRepository.findByUsername(request.getUsername()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            log.warn("Login failed for username: {}", request.getUsername());
            responseObserver.onError(Status.UNAUTHENTICATED
                    .withDescription("Invalid username or password")
                    .asRuntimeException());
            return;
        }

        String token = jwtUtil.generateToken(user);
        log.info("Generated token for username: {}", user.getUsername());

        LoginResponse response = LoginResponse.newBuilder()
                .setAccessToken(token)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void getProfile(Empty request, StreamObserver<UserProfile> responseObserver) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            responseObserver.onError(Status.UNAUTHENTICATED.withDescription("Not authenticated").asRuntimeException());
            return;
        }
        String username = authentication.getName();

        userRepository.findByUsername(username).ifPresentOrElse(user -> {
            UserProfile profile = UserProfile.newBuilder()
                    .setId(user.getId().toString())
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail())
                    .build();
            responseObserver.onNext(profile);
            responseObserver.onCompleted();
        }, () -> {
            responseObserver.onError(Status.NOT_FOUND.withDescription("User profile not found").asRuntimeException());
        });
    }
}
