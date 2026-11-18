package io.interviewcodi.backend.practice.controller;

import io.interviewcodi.backend.auth.domain.LoginUser;
import io.interviewcodi.backend.practice.dto.CreatePracticeRequest;
import io.interviewcodi.backend.practice.dto.PracticeListResponse;
import io.interviewcodi.backend.practice.dto.PracticeResponse;
import io.interviewcodi.backend.practice.service.PracticeService;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/practices")
class PracticeController {

    private final PracticeService practiceService;

    PracticeController(PracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @PostMapping
    ResponseEntity<PracticeResponse> create(LoginUser loginUser, @RequestBody CreatePracticeRequest request) {
        long practiceId = practiceService.create(loginUser.userId(), request.startMode());
        return ResponseEntity.created(URI.create("/api/practices/" + practiceId))
                .body(practiceService.get(loginUser.userId(), practiceId));
    }

    @GetMapping
    PracticeListResponse list(LoginUser loginUser) {
        return practiceService.list(loginUser.userId());
    }

    @GetMapping("/{practiceId}")
    PracticeResponse get(LoginUser loginUser, @PathVariable long practiceId) {
        return practiceService.get(loginUser.userId(), practiceId);
    }

    @PostMapping("/{practiceId}/complete")
    PracticeResponse complete(LoginUser loginUser, @PathVariable long practiceId) {
        practiceService.complete(loginUser.userId(), practiceId);
        return practiceService.get(loginUser.userId(), practiceId);
    }

    @DeleteMapping("/{practiceId}")
    ResponseEntity<Void> delete(LoginUser loginUser, @PathVariable long practiceId) {
        practiceService.delete(loginUser.userId(), practiceId);
        return ResponseEntity.noContent().build();
    }
}
