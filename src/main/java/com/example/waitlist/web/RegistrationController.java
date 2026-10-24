package com.example.waitlist.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.waitlist.domain.DropResult;
import com.example.waitlist.domain.EnrollmentOutcome;
import com.example.waitlist.domain.EnrollmentResult;
import com.example.waitlist.domain.OfferingSnapshot;
import com.example.waitlist.service.RegistrationService;
import com.example.waitlist.web.Dtos.*;

import jakarta.validation.Valid;

/**
 * Thin REST adapter: translates HTTP to service calls. No business rules live here.
 */
@RestController
@RequestMapping("/api")
public class RegistrationController {

    private final RegistrationService service;
    private final AdminGate adminGate;

    public RegistrationController(RegistrationService service, AdminGate adminGate) {
        this.service = service;
        this.adminGate = adminGate;
    }

    /**
     * 201 when seated, 202 when waitlisted (accepted, not yet enrolled).
     */
    @PostMapping("/offering/enrollments")
    public ResponseEntity<EnrollmentResult> enroll(@Valid @RequestBody EnrollRequest request) {
        EnrollmentResult result = service.enroll(request.learnerId());
        HttpStatus status = EnrollmentOutcome.ENROLLED.name().equalsIgnoreCase(result.outcome().name()) ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(result);
    }

    /**
     * Drop from enrolment or waitlist. 404 for unknown/repeated drops.
     */
    @DeleteMapping("/offering/enrollments/{learnerId}")
    public DropResult drop(@PathVariable String learnerId) {
        return service.drop(learnerId);
    }

    @GetMapping("/offering")
    public OfferingSnapshot offering() {
        return service.snapshot();
    }

    /**
     * Stand-in for a records system: set what a learner has completed (drives the prerequisite rule).
     * Gated by the X-Admin-Key header (401 wrong/missing, 403 when no key is configured). Note: Bean Validation of
     * the body runs before this method, so a malformed body is reported as 400 even without a key.
     */
    @PutMapping("/learners/{learnerId}/completed-courses")
    public CompletedCoursesResponse setCompleted(@PathVariable String learnerId,
                                                 @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
                                                 @Valid @RequestBody CompletedCoursesRequest request) {
        adminGate.check(adminKey);
        List<String> promoted = service.updateCompletedCourses(learnerId, new java.util.HashSet<>(request.completedCourses()));
        return new CompletedCoursesResponse(learnerId.trim(), request.completedCourses(), promoted);
    }
}
