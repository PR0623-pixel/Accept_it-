
package com.debate.game.service;

import com.debate.game.exception.AiConfigurationException;
import com.debate.game.exception.AiServiceException;
import org.springframework.web.client.RestClientException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OpenAiService {

    @Value("${openai.api-key:}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.api-url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    public String generateSituationCards(String topic, int maxPickCount) {
        if (apiKey == null || apiKey.isBlank() || model == null || model.isBlank()) {
            throw new AiConfigurationException();
        }

        String systemPrompt =
                "너는 정책 심의 토론 게임의 상황 카드 생성기야. " +
                        "주어진 정책 주제에 대해 찬성 측 유리한 상황 " +
                        maxPickCount + "개, 반대 측 유리한 상황 " +
                        maxPickCount + "개를 생성해. " +
                        "반드시 JSON 배열로만 응답해. 다른 대답은 절대 하지마.";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", "주제: " + topic)
        ));
        requestBody.put("temperature", 0.7);

        HttpEntity<Map<String, Object>> entity =
                new HttpEntity<>(requestBody, headers);

        Map<?, ?> response;
        try {
            response = restTemplate.postForObject(apiUrl, entity, Map.class);
        } catch (RestClientException e) {
            throw new AiServiceException("AI request failed.", e);
        }

        if (response == null ||
                !(response.get("choices") instanceof List<?> choices) ||
                choices.isEmpty()) {
            throw new AiServiceException("AI 응답이 비어 있습니다.");
        }

        if (!(choices.get(0) instanceof Map<?, ?> choice) ||
                !(choice.get("message") instanceof Map<?, ?> message) ||
                !(message.get("content") instanceof String content)) {
            throw new AiServiceException("AI 응답 형식이 올바르지 않습니다.");
        }

        if (content.isBlank()) {
            throw new AiServiceException("AI response content is empty.");
        }
        return content;
    }
}
