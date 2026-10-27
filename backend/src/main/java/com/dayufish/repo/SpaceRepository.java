package com.dayufish.repo;

import com.dayufish.domain.Space;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpaceRepository extends JpaRepository<Space, Long> {

    List<Space> findAllByOrderByIdAsc();
}
