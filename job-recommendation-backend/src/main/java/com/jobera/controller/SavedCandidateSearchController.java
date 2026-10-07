package com.jobera.controller;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.jobera.entity.SavedCandidateSearch;
import com.jobera.service.SavedCandidateSearchService;

@RestController
@RequestMapping("/api/recruiters/{recruiterId}/saved-searches")
public class SavedCandidateSearchController {

    private final SavedCandidateSearchService service;

    public SavedCandidateSearchController(SavedCandidateSearchService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<SavedCandidateSearch>> getSavedSearches(@PathVariable Long recruiterId) {
        return ResponseEntity.ok(service.getSearchesByRecruiter(recruiterId));
    }

    @PostMapping
    public ResponseEntity<?> createSavedSearch(@PathVariable Long recruiterId,
                                               @RequestBody Map<String, Object> body) {
        try {
            String name = (String) body.get("name");
            String skillFilter = (String) body.get("skillFilter");
            String locationFilter = (String) body.get("locationFilter");
            String aiQuery = (String) body.get("aiQuery");
            Boolean alertEnabled = body.get("alertEnabled") != null ? (Boolean) body.get("alertEnabled") : true;

            SavedCandidateSearch created = service.saveSearch(
                recruiterId,
                name,
                skillFilter,
                locationFilter,
                aiQuery,
                alertEnabled
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{searchId}")
    public ResponseEntity<?> deleteSavedSearch(@PathVariable Long recruiterId,
                                              @PathVariable Long searchId) {
        try {
            service.deleteSearch(searchId, recruiterId);
            return ResponseEntity.ok(Map.of("message", "Saved search deleted successfully"));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{searchId}/alert")
    public ResponseEntity<?> toggleAlert(@PathVariable Long recruiterId,
                                         @PathVariable Long searchId,
                                         @RequestBody Map<String, Object> body) {
        try {
            boolean enabled = body.get("alertEnabled") != null ? (Boolean) body.get("alertEnabled") : true;
            SavedCandidateSearch updated = service.toggleAlert(searchId, recruiterId, enabled);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{searchId}/check")
    public ResponseEntity<?> checkMatches(@PathVariable Long recruiterId,
                                          @PathVariable Long searchId) {
        try {
            int count = service.checkAndNotifyMatches(searchId, recruiterId);
            return ResponseEntity.ok(Map.of("matchesFound", count));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }
}
