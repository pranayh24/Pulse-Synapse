package pr.targetmanagementservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
import pr.targetmanagementservice.entity.Target;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TargetRepository extends JpaRepository<Target, UUID> {

    List<Target> findAllByUserId(UUID userId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Target t WHERE t.id = :id AND t.userId = :userId")
    void deleteByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    List<Target> findAllByNextCheckTimeBefore(Instant currentTime);
}
