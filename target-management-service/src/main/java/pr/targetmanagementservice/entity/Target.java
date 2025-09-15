package pr.targetmanagementservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Target {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String url;

    @Column(name = "check_interval_seconds", nullable = false)
    private Integer checkIntervalSeconds;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "next_check_time", nullable = false)
    private Instant nextCheckTime;
}
