// src/main/java/com/debate/game/service/OpenAiService.java
package com.debate.game.service;

import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class OpenAiService {

    @Value("${openai.api-key}")
    private String apiKey;

    @Value("${openai.model}")
    private String model;

    @Value("${openai.api-url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    // 쟁점(Topic)을 던져주면 AI가 상황 카드 데이터를 JSON 텍스트로 뱉어내는 메서드
    public String generateSituationCards(String topic, int maxPickCount) {
        
        // 1. 프롬프트 작성 (JSON 배열 형태로 강제)
        String systemPrompt = "너는 정책 심의 토론 게임의 상황 카드 생성기야. " +
                "주어진 정책 주제에 대해 찬성 측 유리한 상황 " + maxPickCount + "개, 반대 측 유리한 상황 " + maxPickCount + "개를 생성해. " +
                "반드시 아래 JSON 배열 규격으로만 대답해. 다른 말은 절대 하지마.\n" +
                "[{\"keyword\": \"핵심단어\", \"content\": \"상황 설명 내용\", \"ownerRole\": \"PRO\"}, ...]";

        // 2. 헤더 세팅
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey); // "Bearer sk-..." 형태로 자동 변환

        // 3. 바디(요청 데이터) 세팅
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", List.of(
                Map.put("role", "system"), Map.put("content", systemPrompt),
                Map.put("role", "user"), Map.put("content", "주제: " + topic)
        ));
        requestBody.put("temperature", 0.7); // 창의성 조절 (0~2)

        // 4. API 호출
        HttpEntity> entity = new HttpEntity<>(requestBody, headers);
        Map<String, Object> response = restTemplate.postForObject(apiUrl, entity, Map.class);

        // 5. 응답 파싱 (Chat Completions API의 응답 구조에서 텍스트만 추출)
        List> choices = (List>) response.get("choices");
        Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
        
        return (String) message.get("content"); // AI가 만들어준 JSON String 반환
    }
}