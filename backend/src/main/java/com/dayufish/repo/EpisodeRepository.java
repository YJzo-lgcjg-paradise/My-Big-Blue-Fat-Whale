package com.dayufish.repo;

import com.dayufish.domain.Episode;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {

    List<Episode> findBySpaceIdOrderByStartedAtDesc(Long spaceId);

    List<Episode> findBySpaceIdOrderByLastAtDesc(Long spaceId, Pageable pageable);

    List<Episode> findByStatusOrderByIdAsc(String status, Pageable pageable);

    List<Episode> findBySpaceIdAndStatusOrderByIdAsc(Long spaceId, String status, Pageable pageable);

    long countBySpaceId(Long spaceId);

    long countByStatus(String status);

    long countBySpaceIdAndStatus(Long spaceId, String status);

    long countBySpaceIdAndDiary(Long spaceId, String diary);
}
