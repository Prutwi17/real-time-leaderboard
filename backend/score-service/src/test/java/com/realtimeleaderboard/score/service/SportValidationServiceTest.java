package com.realtimeleaderboard.score.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.realtimeleaderboard.score.client.SportServiceClient;
import com.realtimeleaderboard.score.client.SportSnapshot;
import com.realtimeleaderboard.score.exception.ConflictException;
import com.realtimeleaderboard.score.exception.ResourceNotFoundException;
import com.realtimeleaderboard.score.exception.ServiceUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SportValidationServiceTest {

    @Mock private SportServiceClient sportServiceClient;
    private SportValidationService sportValidationService;

    @BeforeEach void setUp() { sportValidationService = new SportValidationService(sportServiceClient); }

    @Test
    void returnsSnapshotWhenActive() {
        when(sportServiceClient.fetchSport(1L)).thenReturn(new SportSnapshot(1L, "FOOTBALL", "Football", true));
        SportSnapshot result = sportValidationService.validateSportForSubmission(1L);
        assertThat(result.code()).isEqualTo("FOOTBALL");
    }

    @Test
    void throws404WhenSportMissing() {
        when(sportServiceClient.fetchSport(99L)).thenThrow(new ResourceNotFoundException("Sport not found: id 99"));
        assertThatThrownBy(() -> sportValidationService.validateSportForSubmission(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void throws409WhenSportInactive() {
        when(sportServiceClient.fetchSport(2L)).thenReturn(new SportSnapshot(2L, "CRICKET", "Cricket", false));
        assertThatThrownBy(() -> sportValidationService.validateSportForSubmission(2L))
                .isInstanceOf(ConflictException.class).hasMessageContaining("not active");
    }

    @Test
    void throws503WhenServiceUnavailable() {
        when(sportServiceClient.fetchSport(3L)).thenThrow(new ServiceUnavailableException("unavailable"));
        assertThatThrownBy(() -> sportValidationService.validateSportForSubmission(3L))
                .isInstanceOf(ServiceUnavailableException.class);
    }
}
