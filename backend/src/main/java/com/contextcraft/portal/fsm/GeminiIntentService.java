package com.contextcraft.portal.fsm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Gemini-powered intent extraction service.
 *
 * Replaces the regex-based rule parsing in AiConversationHandler.
 * Calls Google Gemini API to extract structured intents from free-form user messages.
 *
 * Supports intents:
 *  - CREATE_TASK, ASSIGN_TASK, LIST_TASKS, UPDATE_TASK_STATUS, REVIEW_TASK
 *  - INVITE_USER, LIST_USERS, CREATE_DEPARTMENT, LIST_DEPARTMENTS
 *  - SHOW_ANALYTICS, SHOW_HELP, UNKNOWN
 */
@Service
public class GeminiIntentService {

    private static final Logger log = LoggerFactory.getLogger(GeminiIntentService.class);
    private static final String GEMINI_API_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public GeminiIntentService(
            @Value("${app.gemini.api-key:}") String apiKey,
            @Value("${app.gemini.model:gemini-2.0-flash}") String model,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.restTemplate = new RestTemplate();

        if (apiKey != null && !apiKey.isBlank()) {
            log.info("✓ Gemini AI initialized with model: {}", model);
        } else {
            log.warn("⚠ Gemini API key not configured. Will return UNKNOWN intent for all inputs.");
        }
    }

    /**
     * Extract intent from user message using Gemini AI.
     * Falls back to UNKNOWN intent if API key is not configured.
     */
    public AiConversationHandler.ParsedIntent extractIntent(String message, UUID businessId) {
        if (apiKey == null || apiKey.isBlank()) {
            log.debug("Gemini API key not configured, returning UNKNOWN intent");
            return createUnknownIntent();
        }

        try {
            return callGeminiForIntent(message);
        } catch (Exception e) {
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            return createUnknownIntent();
        }
    }

    /**
     * Call Gemini API via REST to extract intent and entities.
     */
    private AiConversationHandler.ParsedIntent callGeminiForIntent(String message) {
        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(message);
            String fullPrompt = systemPrompt + "\n\n" + userPrompt;

            log.debug("Calling Gemini API: model={}, message_length={}", model, message.length());

            // Build request payload
            Map<String, Object> requestPayload = new LinkedHashMap<>();
            Map<String, Object> part = new LinkedHashMap<>();
            part.put("text", fullPrompt);
            
            Map<String, Object> content = new LinkedHashMap<>();
            content.put("parts", List.of(part));
            
            requestPayload.put("contents", List.of(content));

            // Call Gemini API
            String apiUrl = GEMINI_API_URL + "?key=" + apiKey;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestPayload, headers);
            Map<String, Object> response = restTemplate.postForObject(apiUrl, request, Map.class);

            if (response == null || !response.containsKey("candidates")) {
                log.error("Invalid Gemini API response: {}", response);
                return createUnknownIntent();
            }

            // Extract text from response
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                log.error("No candidates in Gemini response");
                return createUnknownIntent();
            }

            Map<String, Object> candidate = candidates.get(0);
            Map<String, Object> contentMap = (Map<String, Object>) candidate.get("content");
            if (contentMap == null) {
                log.error("No content in Gemini candidate");
                return createUnknownIntent();
            }

            List<Map<String, Object>> parts = (List<Map<String, Object>>) contentMap.get("parts");
            if (parts == null || parts.isEmpty()) {
                log.error("No parts in Gemini content");
                return createUnknownIntent();
            }

            String responseText = (String) parts.get(0).get("text");
            log.debug("Gemini response text: {}", responseText.substring(0, Math.min(200, responseText.length())));

            // Parse JSON response
            return parseGeminiResponse(responseText);
        } catch (Exception e) {
            log.error("Error calling Gemini API: {}", e.getMessage(), e);
            return createUnknownIntent();
        }
    }

    /**
     * Build system prompt for Gemini to understand the intent extraction task.
     */
    private String buildSystemPrompt() {
        return """
                You are a Natural Language Understanding (NLU) engine for a business portal bot.
                Your task is to parse user messages and extract structured intents for performing actions on a business management system.
                
                Supported portal actions:
                - Task Management: CREATE_TASK, ASSIGN_TASK, LIST_TASKS, UPDATE_TASK_STATUS, REVIEW_TASK
                - User Management: INVITE_USER, LIST_USERS
                - Department Management: CREATE_DEPARTMENT, LIST_DEPARTMENTS
                - Analytics & Help: SHOW_ANALYTICS, SHOW_HELP
                - Fallback: UNKNOWN
                
                IMPORTANT: Always respond with a valid JSON object in this exact format:
                {
                  "intent": "<INTENT_TYPE>",
                  "confidence": <0.0-1.0>,
                  "entities": {
                    "key1": "value1",
                    "key2": "value2"
                  },
                  "missingFields": ["field1", "field2"]
                }
                
                Entity extraction guidelines:
                - For CREATE_TASK: Extract taskTitle (required), assigneeName, dueDate, priority
                - For INVITE_USER: Extract employeeName (required), employeeEmail, roleName
                - For CREATE_DEPARTMENT: Extract departmentName (required)
                - For REVIEW_TASK: Extract decision (APPROVE or REJECT), taskTitle (required)
                - For UPDATE_TASK_STATUS: Extract newStatus, taskTitle (required)
                
                Always respond with ONLY the JSON object, no additional text or markdown.
                """;
    }

    /**
     * Build user prompt with the actual message to parse.
     */
    private String buildUserPrompt(String message) {
        return String.format("Parse this user message and extract the intent:\n\n\"%s\"", message.replace("\"", "\\\""));
    }

    /**
     * Parse the JSON response from Gemini into a ParsedIntent object.
     */
    private AiConversationHandler.ParsedIntent parseGeminiResponse(String responseText) {
        try {
            // Clean up response if necessary (remove markdown code blocks if present)
            String cleanedResponse = responseText.trim();
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.substring(7);
            }
            if (cleanedResponse.startsWith("```")) {
                cleanedResponse = cleanedResponse.substring(3);
            }
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            cleanedResponse = cleanedResponse.trim();

            JsonNode jsonNode = objectMapper.readTree(cleanedResponse);

            AiConversationHandler.ParsedIntent intent = new AiConversationHandler.ParsedIntent();
            intent.intent = jsonNode.get("intent").asText("UNKNOWN");
            intent.confidence = jsonNode.get("confidence").asDouble(0.0);

            // Parse entities
            JsonNode entitiesNode = jsonNode.get("entities");
            if (entitiesNode != null && entitiesNode.isObject()) {
                entitiesNode.fields().forEachRemaining(entry ->
                        intent.entities.put(entry.getKey(), entry.getValue().asText())
                );
            }

            // Parse missing fields
            JsonNode missingNode = jsonNode.get("missingFields");
            if (missingNode != null && missingNode.isArray()) {
                missingNode.forEach(node -> intent.missingFields.add(node.asText()));
            }

            log.info("Parsed Gemini intent: {} (confidence={}, missing={})",
                    intent.intent, intent.confidence, intent.missingFields);

            return intent;
        } catch (Exception e) {
            log.error("Failed to parse Gemini JSON response: {}", responseText, e);
            return createUnknownIntent();
        }
    }

    /**
     * Create a default UNKNOWN intent.
     */
    private AiConversationHandler.ParsedIntent createUnknownIntent() {
        AiConversationHandler.ParsedIntent intent = new AiConversationHandler.ParsedIntent();
        intent.intent = "UNKNOWN";
        intent.confidence = 0.2;
        return intent;
    }
}

