package com.juno.controller;

import com.juno.service.JobDataService;
import com.juno.service.ProfileManager;
import com.juno.service.ProfileScoreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@CrossOrigin(origins = "*")
public class ProfileController {

    private final ProfileManager profileManager;
    private final ProfileScoreService scoreService;
    private final JobDataService jobDataService;

    public ProfileController(ProfileManager profileManager,
                             ProfileScoreService scoreService,
                             JobDataService jobDataService) {
        this.profileManager = profileManager;
        this.scoreService = scoreService;
        this.jobDataService = jobDataService;
    }

    @GetMapping("/current")
    public Map<String, Object> getCurrentProfile() {
        return profileManager.load();
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/submit")
    public Map<String, Object> submitProfile(@RequestBody Map<String, Object> body) {
        var profile = (Map<String, Object>) body.getOrDefault("profile", body);
        profileManager.submit(profile);
        int score = scoreService.computeCompletionScore(profile);
        return Map.of("success", true, "completionScore", score);
    }

    @GetMapping("/whoami")
    @SuppressWarnings("unchecked")
    public Map<String, Object> whoami() {
        var profile = profileManager.load();
        var core = (Map<String, Object>) profile.getOrDefault("core", Map.of());
        var name = (Map<String, Object>) core.getOrDefault("name", Map.of());
        var rank = (Map<String, Object>) core.getOrDefault("rank", Map.of());
        return Map.of(
                "userId", profile.getOrDefault("userId", "unknown"),
                "displayName", name.getOrDefault("businessFirstName", "") + " "
                        + name.getOrDefault("businessLastName", ""),
                "email", profile.getOrDefault("email", ""),
                "rank", rank.getOrDefault("description", "")
        );
    }

    @GetMapping("/jd-detail")
    public ResponseEntity<Map<String, Object>> jdDetail(@RequestParam String jobId) {
        var job = jobDataService.getJobById(jobId);
        if (job == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(job);
    }
}
