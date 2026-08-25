package com.realtimeleaderboard.sport.service;

import com.realtimeleaderboard.sport.dto.request.CreateSportRequest;
import com.realtimeleaderboard.sport.dto.request.StatusUpdateRequest;
import com.realtimeleaderboard.sport.dto.request.UpdateSportRequest;
import com.realtimeleaderboard.sport.dto.response.SportResponse;
import com.realtimeleaderboard.sport.entity.Sport;
import com.realtimeleaderboard.sport.entity.SportCode;
import com.realtimeleaderboard.sport.exception.ConflictException;
import com.realtimeleaderboard.sport.exception.DuplicateResourceException;
import com.realtimeleaderboard.sport.exception.ResourceNotFoundException;
import com.realtimeleaderboard.sport.repository.CompetitionRepository;
import com.realtimeleaderboard.sport.repository.SportRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SportService {

    private final SportRepository sportRepository;
    private final CompetitionRepository competitionRepository;

    public SportService(SportRepository sportRepository, CompetitionRepository competitionRepository) {
        this.sportRepository = sportRepository;
        this.competitionRepository = competitionRepository;
    }

    @Transactional(readOnly = true)
    public List<SportResponse> getAll() {
        return sportRepository.findAll().stream().map(SportResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public SportResponse getById(Long id) {
        return SportResponse.from(findSport(id));
    }

    @Transactional(readOnly = true)
    public SportResponse getByCode(SportCode code) {
        return sportRepository.findByCode(code)
                .map(SportResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Sport not found: " + code.name()));
    }

    @Transactional
    public SportResponse create(CreateSportRequest request) {
        if (sportRepository.existsByCode(request.code())) {
            throw new DuplicateResourceException("Sport code '" + request.code().name() + "' already exists");
        }
        Sport sport = new Sport(request.code(), request.name(), request.description());
        return SportResponse.from(sportRepository.save(sport));
    }

    @Transactional
    public SportResponse update(Long id, UpdateSportRequest request) {
        Sport sport = findSport(id);
        sport.setName(request.name());
        sport.setDescription(request.description());
        return SportResponse.from(sportRepository.save(sport));
    }

    @Transactional
    public SportResponse updateStatus(Long id, StatusUpdateRequest request) {
        Sport sport = findSport(id);
        sport.setActive(request.active());
        return SportResponse.from(sportRepository.save(sport));
    }

    /**
     * Deletes a sport only when it owns no competitions; otherwise the caller
     * is told to deactivate instead, keeping competition references valid.
     */
    @Transactional
    public void delete(Long id) {
        Sport sport = findSport(id);
        if (competitionRepository.existsBySportId(id)) {
            throw new ConflictException(
                    "Sport '" + sport.getCode().name()
                            + "' still has competitions; deactivate it instead of deleting");
        }
        sportRepository.delete(sport);
    }

    private Sport findSport(Long id) {
        return sportRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Sport not found: id " + id));
    }
}
