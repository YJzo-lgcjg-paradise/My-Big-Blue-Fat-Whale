package com.dayufish.repo;

import com.dayufish.domain.TextHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TextHistoryRepository extends JpaRepository<TextHistory, Long> {

    List<TextHistory> findBySpaceIdAndKindOrderByIdDesc(Long spaceId, String kind);

    void deleteBySpaceId(Long spaceId);
}
