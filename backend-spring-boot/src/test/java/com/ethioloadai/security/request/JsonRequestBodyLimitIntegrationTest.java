package com.ethioloadai.security.request;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JsonRequestBodyLimitIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void acceptsValidJsonBodyBelowOneMegabyte() throws Exception {
        String body = validLoginJsonWithPadding(1024 * 1024 - 1);

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsValidJsonBodyAboveOneMegabyte() throws Exception {
        String body = validLoginJsonWithPadding(1024 * 1024 + 1);

        mvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge());
    }

    private String validLoginJsonWithPadding(int targetSize) {
        String json = "{\"identifier\":\"f4-size-limit-user-never-exists\",\"password\":\"x\"}";
        return json + " ".repeat(targetSize - json.length());
    }
}
