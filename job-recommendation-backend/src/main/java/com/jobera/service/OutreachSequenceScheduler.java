package com.jobera.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Advances every active outreach sequence whose next step is due, or marks it REPLIED if the
 *  candidate has responded since the last step went out. */
@Component
public class OutreachSequenceScheduler {

    private final OutreachSequenceService outreachSequenceService;

    public OutreachSequenceScheduler(OutreachSequenceService outreachSequenceService) {
        this.outreachSequenceService = outreachSequenceService;
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 180_000)
    public void runDueSteps() {
        outreachSequenceService.processDueSteps();
    }
}
