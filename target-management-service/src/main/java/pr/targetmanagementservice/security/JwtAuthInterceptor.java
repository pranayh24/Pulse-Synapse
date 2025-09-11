package pr.targetmanagementservice.security;

import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.stereotype.Component;
import pr.targetmanagementservice.utils.JwtUtil;

@Slf4j
@RequiredArgsConstructor
@Component
@GlobalServerInterceptor
public class JwtAuthInterceptor implements ServerInterceptor {
    private final JwtUtil jwtUtil;

    private static final Metadata.Key<String> AUTHORIZATION_KEY = Metadata.Key.of("Authorization", Metadata.ASCII_STRING_MARSHALLER);

    private static final Context.Key<String> USER_ID_KEY = Context.key("userId");
    private static final Context.Key<String> USERNAME_KEY = Context.key("username");

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String methodName = call.getMethodDescriptor().getFullMethodName();
        log.info("Intercepting method "+methodName);

        if (methodName.contains("grpc.reflection") || methodName.contains("grpc.health") || methodName.contains("GetAllTargets")) {
            log.info("Skipping authentication for method {}", methodName);
            return next.startCall(call, headers);
        }

        String authHeader = headers.get(AUTHORIZATION_KEY);
        log.info("Authorization header: {}", authHeader);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authorization token is missing or doesn't start with Bearer");
            call.close(Status.UNAUTHENTICATED.withDescription("JWT Token is missing or invalid"), new Metadata());
            return new ServerCall.Listener<>() {};
        }

        String jwt = authHeader.substring(7);
        try {
            if (!jwtUtil.isTokenValid(jwt)) {
                log.warn("Token is invalid");
                call.close(Status.UNAUTHENTICATED.withDescription("JWT Token is invalid"), new Metadata());
                return new ServerCall.Listener<>() {};
            }

            String username = jwtUtil.extractUsername(jwt);
            String userId = jwtUtil.extractUserId(jwt);
            log.info("Extracted username: {}, userId: {}", username, userId);

            Context context = Context.current()
                    .withValue(USER_ID_KEY, userId)
                    .withValue(USERNAME_KEY, username);

            return Contexts.interceptCall(context, call, headers, next);
        } catch (Exception e) {
            log.error("Cannot authenticate user: {}", e.getMessage());
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid JWT Token"), new Metadata());
            return new ServerCall.Listener<>() {};
        }
    }

    public static String getCurrentUserId() {
        return USER_ID_KEY.get();
    }

    public static String getCurrentUsername() {
        return USERNAME_KEY.get();
    }
}
