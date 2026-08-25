package com.realtimeleaderboard.sport.repository;

import com.realtimeleaderboard.sport.entity.Sport;
import com.realtimeleaderboard.sport.entity.SportCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SportRepository extends JpaRepository<Sport, Long> {

    Optional<Sport> findByCode(SportCode code);

    boolean existsByCode(SportCode code);
}
