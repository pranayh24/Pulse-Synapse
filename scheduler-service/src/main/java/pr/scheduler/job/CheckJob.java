package pr.scheduler.job;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CheckJob implements Serializable {
    private String targetId;
    private String url;
}
