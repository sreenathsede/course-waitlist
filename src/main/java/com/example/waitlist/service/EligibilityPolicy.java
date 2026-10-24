package com.example.waitlist.service;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.example.waitlist.config.OfferingProperties;

/**
 * The prerequisite rule (a fixed rule, deliberately NOT a policy engine):
 * a learner is eligible when the offering has no prerequisite, or the learner's completed
 * courses contain the prerequisite code (case-insensitive).
 */
@Component
public class EligibilityPolicy {

    private final String prerequisite;

    public EligibilityPolicy(OfferingProperties props) {
        String p = props.prerequisite();
        this.prerequisite = (p == null || p.isBlank()) ? null : p.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isEligible(Set<String> completedCourses) {
        return prerequisite == null || completedCourses.contains(prerequisite);
    }

    /** @return normalised prerequisite code or null when there is none */
    public String prerequisite() { return prerequisite; }
}
