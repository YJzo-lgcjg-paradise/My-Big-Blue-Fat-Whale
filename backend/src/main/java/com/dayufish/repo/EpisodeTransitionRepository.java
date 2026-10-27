package com.dayufish.repo;

import com.dayufish.domain.EpisodeTransition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EpisodeTransitionRepository extends JpaRepository<EpisodeTransition, Long> {

    List<EpisodeTransition> findBySpaceIdOrderByIdAsc(Long spaceId);

    List<EpisodeTransition> findByFromEpisodeIdOrToEpisodeId(Long fromId, Long toId);

    void deleteByFromEpisodeIdOrToEpisodeId(Long fromId, Long toId);
}
