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

import java.util.Optional;

@GrpcService
@RequiredArgsConstructor
public class AuthService extends AuthServiceGrpc.AuthServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public void registerUser(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver) {
        try {
            log.info("Received registration request for username: {}, email: {}",
                    request.getUsername(), request.getEmail());

            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                log.error("Username is null or empty");
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Username cannot be empty")
                        .asRuntimeException());
                return;
            }

            if (request.getPassword() == null || request.getPassword().length() < 6) {
                log.error("Password is null or too short");
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Password must be at least 6 characters")
                        .asRuntimeException());
                return;
            }

            if (request.getEmail() == null || request.getEmail().trim().isEmpty()) {
                log.error("Email is null or empty");
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Email cannot be empty")
                        .asRuntimeException());
                return;
            }

            Optional<User> existingUserByUsername = userRepository.findByUsername(request.getUsername());
            if (existingUserByUsername.isPresent()) {
                log.warn("Username '{}' is already taken", request.getUsername());
                responseObserver.onError(Status.ALREADY_EXISTS
                        .withDescription("Username is already in use")
                        .asRuntimeException());
                return;
            }

            Optional<User> existingUserByEmail = userRepository.findByEmail(request.getEmail());
            if (existingUserByEmail.isPresent()) {
                log.warn("Email '{}' is already taken", request.getEmail());
                responseObserver.onError(Status.ALREADY_EXISTS
                        .withDescription("Email is already in use")
                        .asRuntimeException());
                return;
            }

            String encodedPassword = passwordEncoder.encode(request.getPassword());

            User newUser = User.builder()
                    .username(request.getUsername())
                    .password(encodedPassword)
                    .email(request.getEmail())
                    .build();

            User savedUser = userRepository.save(newUser);
            RegisterResponse response = RegisterResponse.newBuilder()
                    .setUserId(savedUser.getId().toString())
                    .build();

            log.info("Sending successful registration response for user ID: {}", savedUser.getId());
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error during user registration: ", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Registration failed: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void loginUser(LoginRequest request, StreamObserver<LoginResponse> responseObserver) {
        try {
            log.info("Received login request for username: {}", request.getUsername());

            if (request.getUsername() == null || request.getUsername().trim().isEmpty()) {
                log.error("Username is null or empty");
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Username cannot be empty")
                        .asRuntimeException());
                return;
            }

            if (request.getPassword() == null || request.getPassword().trim().isEmpty()) {
                log.error("Password is null or empty");
                responseObserver.onError(Status.INVALID_ARGUMENT
                        .withDescription("Password cannot be empty")
                        .asRuntimeException());
                return;
            }

            Optional<User> userOptional = userRepository.findByUsername(request.getUsername());

            if (userOptional.isEmpty()) {
                log.warn("User not found for username: {}", request.getUsername());
                responseObserver.onError(Status.UNAUTHENTICATED
                        .withDescription("Invalid username or password")
                        .asRuntimeException());
                return;
            }

            User user = userOptional.get();

            boolean passwordMatches = passwordEncoder.matches(request.getPassword(), user.getPassword());

            if (!passwordMatches) {
                log.warn("Password verification failed for username: {}", request.getUsername());
                responseObserver.onError(Status.UNAUTHENTICATED
                        .withDescription("Invalid username or password")
                        .asRuntimeException());
                return;
            }

            String token = jwtUtil.generateToken(user);
            log.info("JWT token generated successfully for user: {}", user.getUsername());

            LoginResponse response = LoginResponse.newBuilder()
                    .setAccessToken(token)
                    .build();

            log.info("Sending successful login response for user: {}", user.getUsername());
            responseObserver.onNext(response);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error during user login: ", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Login failed: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }

    @Override
    public void getProfile(Empty request, StreamObserver<UserProfile> responseObserver) {
        try {

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            log.info("Authentication object: {}", authentication);

            if (authentication == null || !authentication.isAuthenticated()) {
                log.warn("User not authenticated");
                responseObserver.onError(Status.UNAUTHENTICATED
                        .withDescription("Not authenticated")
                        .asRuntimeException());
                return;
            }

            String username = authentication.getName();

            Optional<User> userOptional = userRepository.findByUsername(username);

            if (userOptional.isEmpty()) {
                log.warn("User profile not found for username: {}", username);
                responseObserver.onError(Status.NOT_FOUND
                        .withDescription("User profile not found")
                        .asRuntimeException());
                return;
            }

            User user = userOptional.get();
            log.info("User profile found: ID={}, username={}, email={}",
                    user.getId(), user.getUsername(), user.getEmail());

            UserProfile profile = UserProfile.newBuilder()
                    .setId(user.getId().toString())
                    .setUsername(user.getUsername())
                    .setEmail(user.getEmail())
                    .build();

            log.info("Sending user profile response");
            responseObserver.onNext(profile);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error during get profile: ", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription("Get profile failed: " + e.getMessage())
                    .withCause(e)
                    .asRuntimeException());
        }
    }
}
