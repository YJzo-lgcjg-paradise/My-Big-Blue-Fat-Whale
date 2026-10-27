package com.dayufish.repo;

import com.dayufish.domain.Attachment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttachmentRepository extends JpaRepository<Attachment, Long> {

    List<Attachment> findByMemoryIdOrderByIdAsc(Long memoryId);

    List<Attachment> findByStatusOrderByIdAsc(String status, Pageable pageable);

    Optional<Attachment> findFirstBySha256AndSpaceId(String sha256, Long spaceId);

    long countByStatus(String status);

    long countBySpaceId(Long spaceId);
}
