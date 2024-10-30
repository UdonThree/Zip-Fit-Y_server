package com.zipfity.zip_fit_y.gpt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Component
public class GptClient {

    private final Gson gson = new Gson();
    private final ObjectMapper objectMapper = new ObjectMapper(); // ObjectMapper 초기화

    @Value("${gpt.api.url}")
    private String apiUrl;

    @Value("${gpt.api.key}")
    private String apiKey;

    @Value("${gpt.model.name}")
    private String modelName;

    // 사용자 요청과 역할 설명을 GPT에 전달하여 응답을 받는 메서드
    public String sendMessageToGpt(ChatRequest chatRequest, String rolePrompt) {
        RestTemplate restTemplate = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Content-Type", "application/json");

        try {
            String requestBody = buildRequestBody(chatRequest, rolePrompt);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);
            return extractContentFromResponse(response.getBody()); // 응답을 추출하여 반환

        } catch (Exception e) {
            System.out.println("Error in sendMessageToGpt: " + e.getMessage());
            throw new RuntimeException("Error in sendMessageToGpt", e);
        }
    }

    // JSON 형식의 요청 바디를 생성하는 메서드
    private String buildRequestBody(ChatRequest chatRequest, String rolePrompt) throws Exception {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", rolePrompt));
        messages.add(Map.of("role", "user", "content", buildPrompt(chatRequest)));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("messages", messages);
        requestBody.put("max_tokens", 300);

        return objectMapper.writeValueAsString(requestBody);
    }

    // 응답에서 `content` 필드 값만 추출
    private String extractContentFromResponse(String responseBody) throws Exception {
        JsonNode rootNode = objectMapper.readTree(responseBody);

        // Check if choices exist
        if (rootNode.path("choices").isMissingNode() || rootNode.path("choices").size() == 0) {
            throw new Exception("Invalid response format: choices are missing or empty.");
        }

        // Get content directly
        return rootNode.path("choices").get(0).path("message").path("content").asText();
    }

    // ChatRequest 데이터를 프롬프트로 구성하는 메서드
    private String buildPrompt(ChatRequest chatRequest) {
        JsonObject promptJson = new JsonObject();
        promptJson.addProperty("userId", chatRequest.getUserId());
        promptJson.addProperty("chatNumber", chatRequest.getChatNumber());
        promptJson.addProperty("chatStatus", chatRequest.getChatStatus());

        // Check if answer is null and replace with empty string
        String answer = chatRequest.getAnswer() != null ? chatRequest.getAnswer().replace("\n", " ") : "";
        promptJson.addProperty("answer", answer);

        return gson.toJson(promptJson);
    }
}
