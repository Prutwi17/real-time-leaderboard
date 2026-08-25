package com.realtimeleaderboard.sport.controller;

import com.realtimeleaderboard.sport.dto.request.StatusUpdateRequest;
import com.realtimeleaderboard.sport.dto.request.UpdateCompetitionRequest;
import com.realtimeleaderboard.sport.dto.response.CompetitionResponse;
import com.realtimeleaderboard.sport.dto.response.MessageResponse;
import com.realtimeleaderboard.sport.service.CompetitionService;
import jakarta.validation.Valid;
import java.util.List;
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
@RequestMapping("/api/competitions")
public class CompetitionController {

    private final CompetitionService competitionService;

    public CompetitionController(CompetitionService competitionService) {
        this.competitionService = competitionService;
    }

    @GetMapping
    public List<CompetitionResponse> getAll() {
        return competitionService.getAll();
    }

    @GetMapping("/{id}")
    public CompetitionResponse getById(@PathVariable Long id) {
        return competitionService.getById(id);
    }

    @PutMapping("/{id}")
    public CompetitionResponse update(@PathVariable Long id, @Valid @RequestBody UpdateCompetitionRequest request) {
        return competitionService.update(id, request);
    }

    @PatchMapping("/{id}/status")
    public CompetitionResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request) {
        return competitionService.updateStatus(id, request);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@PathVariable Long id) {
        competitionService.delete(id);
        return new MessageResponse("Competition deleted");
    }
}
