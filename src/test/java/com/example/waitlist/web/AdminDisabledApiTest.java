package com.example.waitlist.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Fail-closed default: with no admin key configured, the records endpoint is off, even for a caller sending a key.
 */
@SpringBootTest(properties = "admin.api-key=")
@AutoConfigureMockMvc
class AdminDisabledApiTest {

    @Autowired
    MockMvc mvc;

    @Test
    void recordsEndpointIsDisabledWhenNoKeyConfigured() throws Exception {
        mvc.perform(put("/api/learners/a/completed-courses").header("X-Admin-Key", "anything")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"completedCourses\":[\"CS101\"]}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_DISABLED"));
    }
}
