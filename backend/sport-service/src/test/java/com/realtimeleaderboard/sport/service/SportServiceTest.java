package com.realtimeleaderboard.sport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SportServiceTest {

    @Mock
    private SportRepository sportRepository;

    @Mock
    private CompetitionRepository competitionRepository;

    private SportService sportService;

    @BeforeEach
    void setUp() {
        sportService = new SportService(sportRepository, competitionRepository);
    }

    private Sport sport(SportCode code, String name) {
        return sport(code, name, true);
    }

    private Sport sport(SportCode code, String name, boolean active) {
        Sport sport = new Sport(code, name, name + " description");
        sport.setActive(active);
        return sport;
    }

    @Test
    void getAllReturnsAllSports() {
        when(sportRepository.findAll()).thenReturn(List.of(
                sport(SportCode.FOOTBALL, "Football", true),
                sport(SportCode.CRICKET, "Cricket", true),
                sport(SportCode.F1, "Formula 1", false)));

        List<SportResponse> result = sportService.getAll();

        assertThat(result).hasSize(3);
        assertThat(result).extracting(SportResponse::code)
                .containsExactly("FOOTBALL", "CRICKET", "F1");
        assertThat(result.get(2).active()).isFalse();
    }

    @Test
    void getByIdReturnsSport() {
        Sport existing = sport(SportCode.FOOTBALL, "Football");
        when(sportRepository.findById(1L)).thenReturn(Optional.of(existing));

        SportResponse response = sportService.getById(1L);

        assertThat(response.code()).isEqualTo("FOOTBALL");
        assertThat(response.name()).isEqualTo("Football");
    }

    @Test
    void getByIdThrowsWhenMissing() {
        when(sportRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sportService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void getByCodeReturnsSport() {
        when(sportRepository.findByCode(SportCode.F1)).thenReturn(Optional.of(sport(SportCode.F1, "Formula 1", true)));

        SportResponse response = sportService.getByCode(SportCode.F1);

        assertThat(response.code()).isEqualTo("F1");
    }

    @Test
    void getByCodeThrowsWhenMissing() {
        when(sportRepository.findByCode(SportCode.CRICKET)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sportService.getByCode(SportCode.CRICKET))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("CRICKET");
    }

    @Test
    void createPersistsNewSport() {
        when(sportRepository.existsByCode(SportCode.FOOTBALL)).thenReturn(false);
        when(sportRepository.save(any(Sport.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Sport.class));

        SportResponse response = sportService.create(new CreateSportRequest(SportCode.FOOTBALL, "Football", "d"));

        assertThat(response.code()).isEqualTo("FOOTBALL");
        ArgumentCaptor<Sport> captor = ArgumentCaptor.forClass(Sport.class);
        verify(sportRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Football");
        assertThat(captor.getValue().isActive()).isTrue();
    }

    @Test
    void createRejectsDuplicateCode() {
        when(sportRepository.existsByCode(SportCode.CRICKET)).thenReturn(true);

        assertThatThrownBy(() -> sportService.create(new CreateSportRequest(SportCode.CRICKET, "Cricket", null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("CRICKET");

        verify(sportRepository, never()).save(any());
    }

    @Test
    void updateChangesNameAndDescription() {
        Sport existing = sport(SportCode.F1, "Old name");
        when(sportRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(sportRepository.save(any(Sport.class))).thenAnswer(invocation -> invocation.getArgument(0, Sport.class));

        SportResponse response = sportService.update(3L, new UpdateSportRequest("Formula 1", "new description"));

        assertThat(response.name()).isEqualTo("Formula 1");
        assertThat(response.description()).isEqualTo("new description");
        assertThat(existing.getCode()).isEqualTo(SportCode.F1);
    }

    @Test
    void updateStatusTogglesActive() {
        Sport existing = sport(SportCode.FOOTBALL, "Football", true);
        when(sportRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(sportRepository.save(any(Sport.class))).thenAnswer(invocation -> invocation.getArgument(0, Sport.class));

        SportResponse response = sportService.updateStatus(1L, new StatusUpdateRequest(false));

        assertThat(response.active()).isFalse();
        assertThat(existing.isActive()).isFalse();
    }

    @Test
    void deleteRemovesSportWithoutCompetitions() {
        Sport existing = sport(SportCode.CRICKET, "Cricket", true);
        when(sportRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(competitionRepository.existsBySportId(2L)).thenReturn(false);

        sportService.delete(2L);

        verify(sportRepository).delete(existing);
    }

    @Test
    void deleteIsBlockedWhenCompetitionsExist() {
        Sport existing = sport(SportCode.FOOTBALL, "Football", true);
        when(sportRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(competitionRepository.existsBySportId(1L)).thenReturn(true);

        assertThatThrownBy(() -> sportService.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("deactivate");

        verify(sportRepository, never()).delete(any());
    }
}
