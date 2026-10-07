package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.entity.SavedCandidateSearch;
import com.jobera.service.SavedCandidateSearchService;

@ExtendWith(MockitoExtension.class)
class SavedCandidateSearchControllerTest {

    @Mock
    private SavedCandidateSearchService service;

    private SavedCandidateSearchController controller;

    @BeforeEach
    void setUp() {
        controller = new SavedCandidateSearchController(service);
    }

    @Test
    void getSavedSearchesReturnsList() {
        SavedCandidateSearch s = new SavedCandidateSearch(5L, "React Search", "React", null, null, true);
        when(service.getSearchesByRecruiter(5L)).thenReturn(List.of(s));

        ResponseEntity<List<SavedCandidateSearch>> res = controller.getSavedSearches(5L);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        assertEquals(1, res.getBody().size());
    }

    @Test
    void createSavedSearchSuccess() {
        SavedCandidateSearch s = new SavedCandidateSearch(5L, "React Search", "React", "Pune", null, true);
        when(service.saveSearch(eq(5L), eq("React Search"), eq("React"), eq("Pune"), any(), anyBoolean()))
            .thenReturn(s);

        ResponseEntity<?> res = controller.createSavedSearch(5L, Map.of(
            "name", "React Search",
            "skillFilter", "React",
            "locationFilter", "Pune",
            "alertEnabled", true
        ));

        assertEquals(HttpStatus.CREATED, res.getStatusCode());
        assertEquals(s, res.getBody());
    }

    @Test
    void deleteSavedSearchSuccess() {
        ResponseEntity<?> res = controller.deleteSavedSearch(5L, 2L);
        assertEquals(HttpStatus.OK, res.getStatusCode());
        verify(service).deleteSearch(2L, 5L);
    }
}
