package com.confessionverse.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void publicEndpointsShouldBeReachableWithoutJwt() throws Exception {
        assertNotAuthRejected(get("/api/confessions/public"));
        assertNotAuthRejected(get("/v3/api-docs"));

        assertNotAuthRejected(
                post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"t\",\"email\":\"bad\",\"password\":\"short\"}")
        );

        assertNotAuthRejected(
                post("/api/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}")
        );

        assertNotAuthRejected(
                post("/api/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"invalid\",\"newPassword\":\"newPassword123\"}")
        );

        assertNotAuthRejected(
                post("/api/billing/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"evt_test\",\"type\":\"invoice.paid\",\"data\":{\"object\":{}}}")
        );
    }

    @Test
    void protectedEndpointsShouldRejectMissingJwt() throws Exception {
        assertAuthRejected(get("/api/auth/me"));
        assertAuthRejected(get("/api/users"));
        assertAuthRejected(get("/api/chatrooms"));
        assertAuthRejected(get("/api/messages/chatroom/1"));
        assertAuthRejected(get("/api/admin/secure"));
    }

    private void assertAuthRejected(MockHttpServletRequestBuilder request) throws Exception {
        ResultActions result = mockMvc.perform(request);
        int status = result.andReturn().getResponse().getStatus();
        if (status != 401 && status != 403) {
            throw new AssertionError("Expected 401/403 but got " + status + " for " + request);
        }
    }

    private void assertNotAuthRejected(MockHttpServletRequestBuilder request) throws Exception {
        ResultActions result = mockMvc.perform(request);
        int status = result.andReturn().getResponse().getStatus();
        if (status == 401 || status == 403) {
            throw new AssertionError("Expected non-401/403 but got " + status + " for " + request);
        }
    }
}
