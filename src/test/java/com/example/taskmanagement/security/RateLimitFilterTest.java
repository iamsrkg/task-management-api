package com.example.taskmanagement.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    @Test
    void allowsUpToCapacityThenAnswers429WithRetryAfter() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(3, 0.5);

        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse ok = send(filter, "203.0.113.7");
            assertThat(ok.getStatus()).isEqualTo(200);
        }

        MockHttpServletResponse limited = send(filter, "203.0.113.7");
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getHeader("Retry-After")).isNotNull();
        assertThat(limited.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(limited.getContentAsString()).contains("\"success\":false");
    }

    @Test
    void bucketsArePerClient() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 0.1);
        assertThat(send(filter, "198.51.100.1").getStatus()).isEqualTo(200);
        assertThat(send(filter, "198.51.100.1").getStatus()).isEqualTo(429);
        assertThat(send(filter, "198.51.100.2").getStatus()).isEqualTo(200);
    }

    private static MockHttpServletResponse send(RateLimitFilter filter, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/tasks");
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }
}
