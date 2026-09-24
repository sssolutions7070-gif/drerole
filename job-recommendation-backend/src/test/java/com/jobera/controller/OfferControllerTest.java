package com.jobera.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.jobera.config.AuthInterceptor;
import com.jobera.entity.JobOffer;
import com.jobera.entity.User;
import com.jobera.service.OfferService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
class OfferControllerTest {

    @Mock OfferService offerService;

    OfferController controller;
    HttpServletRequest request;
    User candidate;

    @BeforeEach
    void setUp() {
        controller = new OfferController(offerService);
        candidate = new User("candidate@example.com", "hash", "Candidate");
        candidate.setId(1L);
        request = mock(HttpServletRequest.class);
        lenient().when(request.getAttribute(AuthInterceptor.AUTHENTICATED_USER)).thenReturn(candidate);
        lenient().when(request.getRemoteAddr()).thenReturn("1.2.3.4");
    }

    @Test
    void sendingAValidOfferReturnsIt() {
        JobOffer offer = new JobOffer();
        offer.setApplicationId(1000L);
        when(offerService.sendOffer(1000L, "Backend Engineer", "$120,000", LocalDate.parse("2026-06-01"), "Equity"))
            .thenReturn(offer);

        ResponseEntity<?> response = controller.send(1000L, Map.of(
            "title", "Backend Engineer", "salaryOffered", "$120,000", "startDate", "2026-06-01", "additionalTerms", "Equity"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(offer, response.getBody());
    }

    @Test
    void sendingWithoutATitleIsRejected() {
        ResponseEntity<?> response = controller.send(1000L, Map.of("salaryOffered", "$1"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void sendingWithAnUnparseableStartDateIsRejected() {
        ResponseEntity<?> response = controller.send(1000L, Map.of(
            "title", "T", "salaryOffered", "$1", "startDate", "not-a-date"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void sendingForAMissingApplicationReturnsBadRequest() {
        when(offerService.sendOffer(anyLong(), anyString(), anyString(), any(), any()))
            .thenThrow(new OfferService.ApplicationNotFoundException(1000L));

        ResponseEntity<?> response = controller.send(1000L, Map.of("title", "T", "salaryOffered", "$1"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void gettingAnOfferReturnsIt() {
        JobOffer offer = new JobOffer();
        when(offerService.getOffer(1000L)).thenReturn(offer);

        ResponseEntity<?> response = controller.get(1000L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(offer, response.getBody());
    }

    @Test
    void gettingAMissingOfferReturnsBadRequest() {
        when(offerService.getOffer(1000L)).thenThrow(new OfferService.OfferNotFoundException(1000L));

        ResponseEntity<?> response = controller.get(1000L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void acceptingWithASignatureNameSucceeds() {
        JobOffer offer = new JobOffer();
        offer.setStatus("ACCEPTED");
        when(offerService.acceptOffer(1000L, 1L, "Candidate Person", "1.2.3.4")).thenReturn(offer);

        ResponseEntity<?> response = controller.accept(1000L, Map.of("signatureName", "Candidate Person"), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(offer, response.getBody());
    }

    @Test
    void acceptingWithoutASignatureNameIsRejected() {
        ResponseEntity<?> response = controller.accept(1000L, Map.of(), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void aRecruiterAcceptingReturnsBadRequest() {
        when(offerService.acceptOffer(1000L, 1L, "X", "1.2.3.4"))
            .thenThrow(new OfferService.NotTheCandidateException());

        ResponseEntity<?> response = controller.accept(1000L, Map.of("signatureName", "X"), request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void decliningSucceeds() {
        JobOffer offer = new JobOffer();
        offer.setStatus("DECLINED");
        when(offerService.declineOffer(1000L, 1L, "reason")).thenReturn(offer);

        ResponseEntity<?> response = controller.decline(1000L, Map.of("reason", "reason"), request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void withdrawingSucceeds() {
        JobOffer offer = new JobOffer();
        offer.setStatus("WITHDRAWN");
        when(offerService.withdrawOffer(1000L)).thenReturn(offer);

        ResponseEntity<?> response = controller.withdraw(1000L);

        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void withdrawingAnAcceptedOfferReturnsBadRequest() {
        when(offerService.withdrawOffer(1000L)).thenThrow(new OfferService.OfferAlreadyAcceptedException(1000L));

        ResponseEntity<?> response = controller.withdraw(1000L);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }
}
