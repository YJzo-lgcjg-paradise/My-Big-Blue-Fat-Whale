package com.dayufish.repo;

import com.dayufish.domain.Treasure;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TreasureRepository extends JpaRepository<Treasure, Long> {

    List<Treasure> findBySpaceIdOrderByIdDesc(Long spaceId);

    List<Treasure> findBySpaceIdOrderByIdDesc(Long spaceId, org.springframework.data.domain.Pageable pageable);

    boolean existsByMemoryId(Long memoryId);

    boolean existsBySpaceIdAndTitle(Long spaceId, String title);

    long countBySpaceId(Long spaceId);
}
