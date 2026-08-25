package com.realtimeleaderboard.sport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.realtimeleaderboard.sport.dto.request.CreateCompetitionRequest;
import com.realtimeleaderboard.sport.dto.request.StatusUpdateRequest;
import com.realtimeleaderboard.sport.dto.request.UpdateCompetitionRequest;
import com.realtimeleaderboard.sport.dto.response.CompetitionResponse;
import com.realtimeleaderboard.sport.entity.Competition;
import com.realtimeleaderboard.sport.entity.Sport;
import com.realtimeleaderboard.sport.entity.SportCode;
import com.realtimeleaderboard.sport.exception.DuplicateResourceException;
import com.realtimeleaderboard.sport.exception.ResourceNotFoundException;
import com.realtimeleaderboard.sport.repository.CompetitionRepository;
import com.realtimeleaderboard.sport.repository.SportRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompetitionServiceTest {

    @Mock
    private CompetitionRepository competitionRepository;

    @Mock
    private SportRepository sportRepository;

    private CompetitionService competitionService;

    @BeforeEach
    void setUp() {
        competitionService = new CompetitionService(competitionRepository, sportRepository);
    }

    private Sport football() {
        return new Sport(SportCode.FOOTBALL, "Football", "desc");
    }

    private Competition competition(long id, String name, String code, Sport sport) {
        Competition c = new Competition();
        c.setName(name);
        c.setCode(code);
        c.setSport(sport);
        c.setDescription("d");
        c.setStartDate(LocalDate.of(2026, 8, 1));
        c.setEndDate(LocalDate.of(2027, 5, 31));
        return c;
    }

    @Test
    void getAllReturnsCompetitionsWithSport() {
        when(competitionRepository.findAllWithSport())
                .thenReturn(List.of(competition(1L, "Premier League", "PREMIER_LEAGUE", football())));

        List<CompetitionResponse> result = competitionService.getAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).sportCode()).isEqualTo("FOOTBALL");
        assertThat(result.get(0).code()).isEqualTo("PREMIER_LEAGUE");
    }

    @Test
    void getByIdReturnsCompetition() {
        when(competitionRepository.findWithSportById(1L))
                .thenReturn(Optional.of(competition(1L, "IPL", "IPL", new Sport(SportCode.CRICKET, "Cricket", null))));

        CompetitionResponse response = competitionService.getById(1L);

        assertThat(response.name()).isEqualTo("IPL");
        assertThat(response.sportCode()).isEqualTo("CRICKET");
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(competitionRepository.findWithSportById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.getById(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getBySportIdThrowsWhenSportMissing() {
        when(sportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> competitionService.getBySportId(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getBySportIdReturnsCompetitions() {
        when(sportRepository.findById(1L)).thenReturn(Optional.of(football()));
        when(competitionRepository.findAllWithSportBySportId(1L))
                .thenReturn(List.of(competition(1L, "Premier League", "PREMIER_LEAGUE", football())));

        List<CompetitionResponse> result = competitionService.getBySportId(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).name()).isEqualTo("Premier League");
    }

    @Test
    void createPersistsCompetitionForSport() {
        when(sportRepository.findById(1L)).thenReturn(Optional.of(football()));
        when(competitionRepository.existsByCodeIgnoreCase("PREMIER_LEAGUE")).thenReturn(false);
        when(competitionRepository.save(any(Competition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Competition.class));

        CreateCompetitionRequest request = new CreateCompetitionRequest(
                "Premier League", "PREMIER_LEAGUE", "English top flight",
                LocalDate.of(2026, 8, 15), LocalDate.of(2027, 5, 24));

        CompetitionResponse response = competitionService.create(1L, request);

        assertThat(response.name()).isEqualTo("Premier League");
        ArgumentCaptor<Competition> captor = ArgumentCaptor.forClass(Competition.class);
        verify(competitionRepository).save(captor.capture());
        assertThat(captor.getValue().getSport().getCode()).isEqualTo(SportCode.FOOTBALL);
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void createThrowsWhenSportMissing() {
        when(sportRepository.findById(77L)).thenReturn(Optional.empty());

        CreateCompetitionRequest request = new CreateCompetitionRequest(
                "Ghost League", "GHOST_LEAGUE", null, null, null);

        assertThatThrownBy(() -> competitionService.create(77L, request))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(competitionRepository, never()).save(any());
    }

    @Test
    void createRejectsDuplicateCode() {
        when(sportRepository.findById(1L)).thenReturn(Optional.of(football()));
        when(competitionRepository.existsByCodeIgnoreCase("premier_league")).thenReturn(true);

        CreateCompetitionRequest request = new CreateCompetitionRequest(
                "Premier League", "premier_league", null, null, null);

        assertThatThrownBy(() -> competitionService.create(1L, request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("premier_league");

        verify(competitionRepository, never()).save(any());
    }

    @Test
    void updateChangesMutableFieldsOnly() {
        Sport sport = football();
        Competition existing = competition(1L, "Old name", "PREMIER_LEAGUE", sport);
        existing.setStartDate(LocalDate.of(2026, 8, 15));
        existing.setEndDate(LocalDate.of(2027, 5, 24));
        when(competitionRepository.findWithSportById(1L)).thenReturn(Optional.of(existing));
        when(competitionRepository.save(any(Competition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Competition.class));

        UpdateCompetitionRequest request = new UpdateCompetitionRequest(
                "Premier League", "updated", LocalDate.of(2026, 8, 20), LocalDate.of(2027, 5, 30));

        CompetitionResponse response = competitionService.update(1L, request);

        assertThat(response.name()).isEqualTo("Premier League");
        assertThat(response.startDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(existing.getCode()).isEqualTo("PREMIER_LEAGUE");
    }

    @Test
    void updateStatusTogglesActive() {
        Competition existing = competition(1L, "IPL", "IPL", new Sport(SportCode.CRICKET, "Cricket", null));
        when(competitionRepository.findWithSportById(1L)).thenReturn(Optional.of(existing));
        when(competitionRepository.save(any(Competition.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Competition.class));

        CompetitionResponse response = competitionService.updateStatus(1L, new StatusUpdateRequest(false));

        assertThat(response.active()).isFalse();
    }

    @Test
    void deleteRemovesCompetition() {
        Competition existing = competition(1L, "IPL", "IPL", new Sport(SportCode.CRICKET, "Cricket", null));
        when(competitionRepository.findWithSportById(1L)).thenReturn(Optional.of(existing));

        competitionService.delete(1L);

        verify(competitionRepository).delete(existing);
    }
}
