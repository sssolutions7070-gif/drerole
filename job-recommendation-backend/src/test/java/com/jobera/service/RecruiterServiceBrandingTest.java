package com.jobera.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.jobera.entity.Recruiter;
import com.jobera.repository.RecruiterRepository;
import com.jobera.repository.UserRepository;

/** Only the richer employer-branding additions (mission statement, benefits, social links,
 *  founded year, headquarters, logo/banner) - this service had no prior dedicated test file. */
@ExtendWith(MockitoExtension.class)
class RecruiterServiceBrandingTest {

    @Mock RecruiterRepository recruiterRepository;
    @Mock UserRepository userRepository;

    RecruiterService service;

    @BeforeEach
    void setUp() {
        service = new RecruiterService(recruiterRepository, userRepository);
    }

    private Recruiter recruiter(long userId) {
        Recruiter r = new Recruiter();
        r.setId(1L);
        return r;
    }

    @Test
    void updatesAllTheBrandingFields() {
        Recruiter recruiter = recruiter(10L);
        when(recruiterRepository.findByUserId(10L)).thenReturn(Optional.of(recruiter));
        when(recruiterRepository.save(recruiter)).thenReturn(recruiter);

        Recruiter result = service.updateRecruiterProfile(10L, null, null, null, null, null, null,
            "We build the future of hiring.", "[\"Remote-first\",\"Unlimited PTO\"]",
            "https://linkedin.com/company/acme", "https://twitter.com/acme", 2015, "Austin, TX");

        assertEquals("We build the future of hiring.", result.getMissionStatement());
        assertEquals("[\"Remote-first\",\"Unlimited PTO\"]", result.getBenefits());
        assertEquals("https://linkedin.com/company/acme", result.getCompanyLinkedin());
        assertEquals("https://twitter.com/acme", result.getCompanyTwitter());
        assertEquals(2015, result.getFoundedYear());
        assertEquals("Austin, TX", result.getHeadquarters());
    }

    @Test
    void theOldSixArgOverloadLeavesBrandingFieldsUntouched() {
        Recruiter recruiter = recruiter(10L);
        recruiter.setMissionStatement("Existing mission");
        when(recruiterRepository.findByUserId(10L)).thenReturn(Optional.of(recruiter));
        when(recruiterRepository.save(recruiter)).thenReturn(recruiter);

        Recruiter result = service.updateRecruiterProfile(10L, "New description", null, null, null, null, null);

        assertEquals("New description", result.getCompanyDescription());
        assertEquals("Existing mission", result.getMissionStatement());
    }

    @Test
    void updatesTheCompanyLogo() {
        Recruiter recruiter = recruiter(10L);
        when(recruiterRepository.findByUserId(10L)).thenReturn(Optional.of(recruiter));
        when(recruiterRepository.save(recruiter)).thenReturn(recruiter);

        Recruiter result = service.updateCompanyLogo(10L, "/uploads/company-logos/10_abc.png");

        assertEquals("/uploads/company-logos/10_abc.png", result.getCompanyLogo());
    }

    @Test
    void updatesTheCompanyBanner() {
        Recruiter recruiter = recruiter(10L);
        when(recruiterRepository.findByUserId(10L)).thenReturn(Optional.of(recruiter));
        when(recruiterRepository.save(recruiter)).thenReturn(recruiter);

        Recruiter result = service.updateCompanyBanner(10L, "/uploads/company-banners/10_abc.png");

        assertEquals("/uploads/company-banners/10_abc.png", result.getCompanyBanner());
    }

    @Test
    void throwsWhenUpdatingTheLogoForAnUnknownRecruiter() {
        when(recruiterRepository.findByUserId(404L)).thenReturn(Optional.empty());

        assertThrows(RecruiterService.RecruiterException.class, () -> service.updateCompanyLogo(404L, "x"));
    }
}
