package com.ethioloadai.security.request;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JsonRequestBodyLimitFilterTest {

    private final JsonRequestBodyLimitFilter filter =
            new JsonRequestBodyLimitFilter(1024 * 1024);

    @Test
    void allowsJsonBodyAtConfiguredLimitAndReplaysItToTheApplication() throws Exception {
        byte[] body = new byte[1024 * 1024];
        MockHttpServletRequest request = jsonRequest(body);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean();

        filter.doFilter(request, response, (wrappedRequest, wrappedResponse) -> {
            chainInvoked.set(true);
            assertThat(wrappedRequest.getInputStream().readAllBytes()).isEqualTo(body);
        });

        assertThat(chainInvoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void rejectsJsonBodyLargerThanConfiguredLimit() throws Exception {
        MockHttpServletRequest request = jsonRequest(new byte[1024 * 1024 + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean();
        FilterChain chain = (wrappedRequest, wrappedResponse) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                .contains("REQUEST_BODY_TOO_LARGE");
        assertThat(chainInvoked).isFalse();
    }

    @Test
    void doesNotApplyJsonLimitToMultipartRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContentType("multipart/form-data; boundary=test-boundary");
        request.setContent(new byte[1024 * 1024 + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainInvoked = new AtomicBoolean();
        FilterChain chain = (wrappedRequest, wrappedResponse) -> chainInvoked.set(true);

        filter.doFilter(request, response, chain);

        assertThat(chainInvoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    private MockHttpServletRequest jsonRequest(byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setContentType("application/json; charset=UTF-8");
        request.setCharacterEncoding(StandardCharsets.UTF_8.name());
        request.setContent(body);
        return request;
    }
}
