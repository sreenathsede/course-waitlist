package com.example.waitlist.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * End-to-end through controller, validation, service, repository and error handler. A fresh context per test
 * resets the in-memory state. Test properties: capacity 2, prerequisite CS101.
 */
@SpringBootTest(properties = {"offering.capacity=2", "offering.prerequisite=CS101", "admin.api-key=test-key"})
@AutoConfigureMockMvc
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
class RegistrationApiTest {

    @Autowired
    MockMvc mvc;

    private ResultActions qualify(String id) throws Exception {
        return mvc.perform(put("/api/learners/" + id + "/completed-courses")
                .header("X-Admin-Key", "test-key")
                .contentType(MediaType.APPLICATION_JSON).content("{\"completedCourses\":[\"CS101\"]}"));
    }

    private ResultActions enroll(String id) throws Exception {
        return mvc.perform(post("/api/offering/enrollments")
                .contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\":\"" + id + "\"}"));
    }

    @Test
    void enrolReturns201WhenSeatAvailable() throws Exception {
        qualify("a").andExpect(status().isOk());
        enroll("a").andExpect(status().isCreated())
                .andExpect(jsonPath("$.outcome").value("ENROLLED"));
    }

    @Test
    void fullOfferingReturns202WithWaitlistPosition() throws Exception {
        for (String id : new String[]{"a", "b", "c"}) {
            qualify(id);
            enroll(id);
        }
        mvc.perform(get("/api/offering")).andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolled.length()").value(2))
                .andExpect(jsonPath("$.waitlist[0]").value("c"));
        qualify("d");
        enroll("d").andExpect(status().isAccepted())
                .andExpect(jsonPath("$.outcome").value("WAITLISTED"))
                .andExpect(jsonPath("$.position").value(2));
    }

    @Test
    void duplicateEnrollmentReturns409() throws Exception {
        qualify("a");
        enroll("a");
        enroll("a").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_ENROLLED"))
                .andExpect(jsonPath("$.path").value("/api/offering/enrollments"));
    }

    @Test
    void missingPrerequisiteReturns422AndNothingIsStored() throws Exception {
        enroll("nobody").andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PREREQUISITE_NOT_MET"));
        mvc.perform(get("/api/offering"))
                .andExpect(jsonPath("$.enrolled.length()").value(0))
                .andExpect(jsonPath("$.waitlist.length()").value(0));
    }

    @Test
    void dropPromotesFifoAndRepeatedDropReturns404() throws Exception {
        for (String id : new String[]{"a", "b", "c"}) {
            qualify(id);
            enroll(id);
        }
        mvc.perform(delete("/api/offering/enrollments/a")).andExpect(status().isOk())
                .andExpect(jsonPath("$.droppedFrom").value("ENROLLMENT"))
                .andExpect(jsonPath("$.promotedLearners[0]").value("c"));
        mvc.perform(delete("/api/offering/enrollments/a")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_REGISTERED"));
        mvc.perform(get("/api/offering"))
                .andExpect(jsonPath("$.seatsAvailable").value(0))
                .andExpect(jsonPath("$.enrolled.length()").value(2));
    }

    @Test
    void blankLearnerIdReturns400() throws Exception {
        mvc.perform(post("/api/offering/enrollments").contentType(MediaType.APPLICATION_JSON).content("{\"learnerId\":\"  \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void malformedOrMissingBodyReturns400() throws Exception {
        mvc.perform(post("/api/offering/enrollments").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/offering/enrollments").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void illegalCharactersInPathIdReturn400() throws Exception {
        mvc.perform(delete("/api/offering/enrollments/bad id")).andExpect(status().isBadRequest());
    }

    @Test
    void nullCompletedCoursesReturns400() throws Exception {
        mvc.perform(put("/api/learners/a/completed-courses").header("X-Admin-Key", "test-key").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ---- records endpoint is gated ----
    @Test
    void recordsEndpointWithoutKeyReturns401() throws Exception {
        mvc.perform(put("/api/learners/a/completed-courses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"completedCourses\":[\"CS101\"]}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("ADMIN_UNAUTHORIZED"));
        enroll("a").andExpect(status().isUnprocessableEntity()); // nothing was recorded
    }

    @Test
    void recordsEndpointWithWrongKeyReturns401() throws Exception {
        mvc.perform(put("/api/learners/a/completed-courses").header("X-Admin-Key", "nope")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completedCourses\":[\"CS101\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publicEndpointsNeedNoKey() throws Exception {
        mvc.perform(get("/api/offering")).andExpect(status().isOk());
        mvc.perform(delete("/api/offering/enrollments/ghost")).andExpect(status().isNotFound());
    }
}
