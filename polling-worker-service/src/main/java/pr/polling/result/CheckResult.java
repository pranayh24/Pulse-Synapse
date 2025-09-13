package pr.polling.result;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class CheckResult {
    private String targetId;
    private Instant timestamp;
    private boolean isUp;
    private Integer statusCode;
    private Long latencyMs;
    private String errorMessage;
}
