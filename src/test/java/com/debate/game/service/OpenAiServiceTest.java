package com.debate.game.service;

import com.debate.game.exception.AiConfigurationException;
import com.debate.game.exception.AiServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class OpenAiServiceTest {
    private OpenAiService service;
    private MockRestServiceServer server;

    @Test
    void beanStartsWithoutApiKey() {
        try (var context = new org.springframework.context.annotation.AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(
                    new org.springframework.core.env.MapPropertySource("test", java.util.Map.of(
                            "openai.api-key", "", "openai.model", "test-model",
                            "openai.api-url", "https://example.invalid/chat")));
            context.register(org.springframework.context.support.PropertySourcesPlaceholderConfigurer.class,
                    OpenAiService.class);
            context.refresh();
            assertNotNull(context.getBean(OpenAiService.class));
        }
    }

    @Test
    void networkFailureIsWrapped() {
        server.expect(anything()).andRespond(withException(new java.io.IOException("test failure")));
        assertThrows(AiServiceException.class, () -> service.generateSituationCards("topic", 5));
        server.verify();
    }

    @BeforeEach
    void setup() {
        service = new OpenAiService();
        ReflectionTestUtils.setField(service, "apiKey", "test-only-placeholder");
        ReflectionTestUtils.setField(service, "model", "test-model");
        ReflectionTestUtils.setField(service, "apiUrl", "https://example.invalid/chat");
        server = MockRestServiceServer.createServer((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate"));
    }

    @Test
    void missingKeyFailsBeforeAnyRequest() {
        for (String key : new String[]{null, "", "  "}) {
            ReflectionTestUtils.setField(service, "apiKey", key);
            assertThrows(AiConfigurationException.class, () -> service.generateSituationCards("topic", 5));
        }
        server.verify();
    }

    @Test
    void validResponseAndRequestArePreserved() {
        server.expect(requestTo("https://example.invalid/chat"))
                .andExpect(jsonPath("$.model").value("test-model"))
                .andExpect(jsonPath("$.temperature").value(0.7))
                .andExpect(jsonPath("$.messages[1].content").value("주제: topic"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"[]\"}}]}", MediaType.APPLICATION_JSON));
        assertEquals("[]", service.generateSituationCards("topic", 5));
        server.verify();
    }

    @Test
    void upstreamFailureIsWrapped() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("sensitive-detail"));
        assertThrows(AiServiceException.class, () -> service.generateSituationCards("topic", 5));
        server.verify();
    }

    @Test
    void malformedResponsesAreWrapped() {
        for (String response : new String[]{"null", "{}", "{\"choices\":[]}", "{\"choices\":[{}]}",
                "{\"choices\":[{\"message\":{\"content\":\" \"}}]}", "invalid-json"}) {
            server.reset();
            server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            assertThrows(AiServiceException.class, () -> service.generateSituationCards("topic", 5));
            server.verify();
        }
    }
}
