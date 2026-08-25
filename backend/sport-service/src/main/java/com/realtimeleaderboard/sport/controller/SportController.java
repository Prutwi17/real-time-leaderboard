package com.realtimeleaderboard.sport.controller;

import com.realtimeleaderboard.sport.dto.request.CreateCompetitionRequest;
import com.realtimeleaderboard.sport.dto.request.CreateSportRequest;
import com.realtimeleaderboard.sport.dto.request.StatusUpdateRequest;
import com.realtimeleaderboard.sport.dto.request.UpdateSportRequest;
import com.realtimeleaderboard.sport.dto.response.CompetitionResponse;
import com.realtimeleaderboard.sport.dto.response.MessageResponse;
import com.realtimeleaderboard.sport.dto.response.SportResponse;
import com.realtimeleaderboard.sport.entity.SportCode;
import com.realtimeleaderboard.sport.service.CompetitionService;
import com.realtimeleaderboard.sport.service.SportService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sports")
public class SportController {

    private final SportService sportService;
    private final CompetitionService competitionService;

    public SportController(SportService sportService, CompetitionService competitionService) {
        this.sportService = sportService;
        this.competitionService = competitionService;
    }

    @GetMapping
    public List<SportResponse> getAll() {
        return sportService.getAll();
    }

    @GetMapping("/{id}")
    public SportResponse getById(@PathVariable Long id) {
        return sportService.getById(id);
    }

    @GetMapping("/code/{code}")
    public SportResponse getByCode(@PathVariable SportCode code) {
        return sportService.getByCode(code);
    }

    @PostMapping
    public ResponseEntity<SportResponse> create(@Valid @RequestBody CreateSportRequest request) {
        SportResponse created = sportService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public SportResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSportRequest request) {
        return sportService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public SportResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return sportService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@PathVariable Long id) {
        sportService.delete(id);
        return new MessageResponse("Sport deleted");
    }

    @GetMapping("/{sportId}/competitions")
    public List<CompetitionResponse> getCompetitions(@PathVariable Long sportId) {
        return competitionService.getBySportId(sportId);
    }

    @PostMapping("/{sportId}/competitions")
    public ResponseEntity<CompetitionResponse> createCompetition(@PathVariable Long sportId,
                                                                 @Valid @RequestBody CreateCompetitionRequest request) {
        CompetitionResponse created = competitionService.create(sportId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
