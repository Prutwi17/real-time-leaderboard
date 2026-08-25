package com.realtimeleaderboard.sport.service;

import com.realtimeleaderboard.sport.dto.request.CreateCompetitionRequest;
import com.realtimeleaderboard.sport.dto.request.StatusUpdateRequest;
import com.realtimeleaderboard.sport.dto.request.UpdateCompetitionRequest;
import com.realtimeleaderboard.sport.dto.response.CompetitionResponse;
import com.realtimeleaderboard.sport.entity.Competition;
import com.realtimeleaderboard.sport.entity.Sport;
import com.realtimeleaderboard.sport.exception.DuplicateResourceException;
import com.realtimeleaderboard.sport.exception.ResourceNotFoundException;
import com.realtimeleaderboard.sport.repository.CompetitionRepository;
import com.realtimeleaderboard.sport.repository.SportRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompetitionService {

    private final CompetitionRepository competitionRepository;
    private final SportRepository sportRepository;

    public CompetitionService(CompetitionRepository competitionRepository, SportRepository sportRepository) {
        this.competitionRepository = competitionRepository;
        this.sportRepository = sportRepository;
    }

    @Transactional(readOnly = true)
    public List<CompetitionResponse> getAll() {
        return competitionRepository.findAllWithSport().stream()
                .map(CompetitionResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CompetitionResponse getById(Long id) {
        return CompetitionResponse.from(findCompetition(id));
    }

    @Transactional(readOnly = true)
    public List<CompetitionResponse> getBySportId(Long sportId) {
        requireSport(sportId);
        return competitionRepository.findAllWithSportBySportId(sportId).stream()
                .map(CompetitionResponse::from)
                .toList();
    }

    @Transactional
    public CompetitionResponse create(Long sportId, CreateCompetitionRequest request) {
        Sport sport = requireSport(sportId);
        if (competitionRepository.existsByCodeIgnoreCase(request.code())) {
            throw new DuplicateResourceException("Competition code '" + request.code() + "' already exists");
        }
        Competition competition = new Competition();
        competition.setName(request.name());
        competition.setCode(request.code());
        competition.setSport(sport);
        competition.setDescription(request.description());
        competition.setStartDate(request.startDate());
        competition.setEndDate(request.endDate());
        return CompetitionResponse.from(competitionRepository.save(competition));
    }

    @Transactional
    public CompetitionResponse update(Long id, UpdateCompetitionRequest request) {
        Competition competition = findCompetition(id);
        competition.setName(request.name());
        competition.setDescription(request.description());
        competition.setStartDate(request.startDate());
        competition.setEndDate(request.endDate());
        return CompetitionResponse.from(competitionRepository.save(competition));
    }

    @Transactional
    public CompetitionResponse updateStatus(Long id, StatusUpdateRequest request) {
        Competition competition = findCompetition(id);
        competition.setActive(request.active());
        return CompetitionResponse.from(competitionRepository.save(competition));
    }

    @Transactional
    public void delete(Long id) {
        Competition competition = findCompetition(id);
        competitionRepository.delete(competition);
    }

    private Sport requireSport(Long sportId) {
        return sportRepository.findById(sportId)
                .orElseThrow(() -> new ResourceNotFoundException("Sport not found: id " + sportId));
    }

    private Competition findCompetition(Long id) {
        return competitionRepository.findWithSportById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Competition not found: id " + id));
    }
}
