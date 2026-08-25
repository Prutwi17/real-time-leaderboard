package com.realtimeleaderboard.sport.repository;

import com.realtimeleaderboard.sport.entity.Competition;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompetitionRepository extends JpaRepository<Competition, Long> {

    @Query("select c from Competition c join fetch c.sport")
    List<Competition> findAllWithSport();

    @Query("select c from Competition c join fetch c.sport where c.id = :id")
    Optional<Competition> findWithSportById(Long id);

    @Query("select c from Competition c join fetch c.sport where c.sport.id = :sportId order by c.name")
    List<Competition> findAllWithSportBySportId(Long sportId);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsBySportId(Long sportId);
}
