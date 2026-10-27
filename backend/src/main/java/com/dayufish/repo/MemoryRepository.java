package com.dayufish.repo;

import com.dayufish.domain.Memory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemoryRepository extends JpaRepository<Memory, Long> {

    List<Memory> findBySpaceIdOrderByOccurredAtDesc(Long spaceId);

    List<Memory> findBySpaceIdOrderByOccurredAtDesc(Long spaceId, Pageable pageable);

    List<Memory> findBySpaceIdAndStatusNot(Long spaceId, String status);

    List<Memory> findByStatusOrderByIdAsc(String status, Pageable pageable);

    List<Memory> findByIdIn(Collection<Long> ids);

    List<Memory> findByEpisodeIdOrderByOccurredAtAscIdAsc(Long episodeId);

    List<Memory> findBySpaceIdOrderByOccurredAtAscIdAsc(Long spaceId);

    List<Memory> findBySpaceIdAndEpisodeIdIsNullAndStatusOrderByOccurredAtAscIdAsc(Long spaceId, String status, Pageable pageable);

    long countBySpaceIdAndEpisodeIdIsNull(Long spaceId);

    long countBySpaceIdAndEpisodeIdIsNullAndStatus(Long spaceId, String status);

    long countBySpaceId(Long spaceId);

    long countByStatus(String status);

    long countBySpaceIdAndStatus(Long spaceId, String status);

    long countByIdInAndStatus(Collection<Long> ids, String status);

    @Query("""
            select m from Memory m
            where m.spaceId = :spaceId
              and (:q = '' or lower(m.summary) like lower(concat('%', :q, '%'))
                   or lower(m.question) like lower(concat('%', :q, '%'))
                   or lower(m.keywords) like lower(concat('%', :q, '%'))
                   or lower(m.answer) like lower(concat('%', :q, '%')))
            order by m.occurredAt desc
            """)
    List<Memory> search(@Param("spaceId") Long spaceId, @Param("q") String q, Pageable pageable);

    @Query("""
            select count(m) from Memory m
            where m.spaceId = :spaceId
              and (:q = '' or lower(m.summary) like lower(concat('%', :q, '%'))
                   or lower(m.question) like lower(concat('%', :q, '%'))
                   or lower(m.keywords) like lower(concat('%', :q, '%'))
                   or lower(m.answer) like lower(concat('%', :q, '%')))
            """)
    long countSearch(@Param("spaceId") Long spaceId, @Param("q") String q);

    @Query("select min(m.occurredAt) from Memory m where m.spaceId = :spaceId")
    java.time.Instant firstAt(@Param("spaceId") Long spaceId);

    @Query("select max(m.occurredAt) from Memory m where m.spaceId = :spaceId")
    java.time.Instant lastAt(@Param("spaceId") Long spaceId);
}
