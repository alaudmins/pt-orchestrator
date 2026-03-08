package com.cvs.orchestrator.controller;

import com.cvs.orchestrator.model.ConfigProfileEntity;
import com.cvs.orchestrator.service.ConfigProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST API for Integration Profiles (e.g. Jenkins, Git Instances).
 */
@RestController
@RequestMapping("/api/profiles")
@RequiredArgsConstructor
public class ConfigProfileController {

    private final ConfigProfileService profileService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> saveProfile(@RequestBody ProfileRequest request) {
        ConfigProfileEntity entity = profileService.saveProfile(
                request.id(),
                request.name(),
                request.profileType(),
                request.url(),
                request.username(),
                request.secretReference());
        return ResponseEntity.ok(Map.of(
                "id", entity.getId(),
                "name", entity.getName(),
                "message", "Profile saved successfully"));
    }

    @GetMapping
    public ResponseEntity<List<ConfigProfileService.ProfileSummary>> listProfiles(
            @RequestParam(required = false) String type) {
        return ResponseEntity.ok(profileService.listProfiles(type));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteProfile(@PathVariable UUID id) {
        profileService.deleteProfile(id);
        return ResponseEntity.ok(Map.of("message", "Profile deleted successfully"));
    }

    public record ProfileRequest(String id, String name, String profileType, String url, String username,
            String secretReference) {
    }
}
