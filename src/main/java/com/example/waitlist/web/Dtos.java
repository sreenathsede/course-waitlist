package com.example.waitlist.web;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request/response DTOs, grouped in one file because each is a tiny record.
 */
public final class Dtos {
    private Dtos() {
    }

    /**
     * POST body for enrolment. Detailed id format checks live in the service.
     */
    public record EnrollRequest(@NotBlank @Size(max = 64) String learnerId) {
    }

    /**
     * PUT body replacing a learner's completed courses.
     */
    public record CompletedCoursesRequest(@NotNull @Size(max = 100) List<String> completedCourses) {
    }

    public record CompletedCoursesResponse(String learnerId, List<String> completedCourses,
                                           List<String> promotedLearners) {
    }

    /**
     * Uniform error body returned for every failure.
     */
    public record ErrorResponse(Instant timestamp, int status, String code, String message, String path) {
    }
}
