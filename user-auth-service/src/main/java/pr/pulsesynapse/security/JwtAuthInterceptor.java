package pr.pulsesynapse.security;

import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.grpc.server.GlobalServerInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import pr.pulsesynapse.utils.JwtUtil;

@Slf4j
@RequiredArgsConstructor
@Component
@GlobalServerInterceptor
public class JwtAuthInterceptor implements ServerInterceptor {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    private static final Metadata.Key<String> AUTHORIZATION_KEY =
            Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        String methodName = call.getMethodDescriptor().getFullMethodName();
        log.info("Intercepting method: {}", methodName);

        if (methodName.contains("LoginUser") ||
                methodName.contains("RegisterUser") ||
                methodName.contains("grpc.reflection") ||
                methodName.contains("grpc.health")) {
            log.info("Skipping authentication for method: {}", methodName);
            return next.startCall(call, headers);
        }

        String authHeader = headers.get(AUTHORIZATION_KEY);
        log.info("Authorization header: {}", authHeader);

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Authorization token is missing or doesn't start with Bearer");
            call.close(Status.UNAUTHENTICATED.withDescription("JWT Token is missing or invalid"), new Metadata());
            return new  ServerCall.Listener<>() {};
        }

        String jwt = authHeader.substring(7);
        try {
            String username =  jwtUtil.extractUsername(jwt);
            log.info("Extracted username: {}", username);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);
                if (jwtUtil.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("Authentication set for user: {}", username);
                } else {
                    log.warn("Token is not valid for user: {}", username);
                    call.close(Status.UNAUTHENTICATED.withDescription("JWT Token is invalid"), new Metadata());
                    return new  ServerCall.Listener<>() {};
                }
            }
        } catch (Exception e) {
            log.error("Cannot set user authentication: {}", e.getMessage());
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid JWT Token"), new Metadata());
            return new  ServerCall.Listener<>() {};
        }
        return next.startCall(call, headers);
    }
}
