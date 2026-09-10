package com.patchamomma.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class GeminiVitalsService {

    @Value("${gemini.api-key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Data
    @Builder
    public static class ParsedVitalsResult {
        private Integer systolicBp;
        private Integer diastolicBp;
        private Integer bloodSugarMgDl;
        private Boolean medicationsGiven;
        private Boolean mealTaken;
        private String summaryNotes;
    }

    /**
     * Parses raw voice notes or unstructured text logs using Gemini API (with robust local parser fallback).
     */
    public ParsedVitalsResult parseVoiceNote(String rawNote) {
        if (rawNote == null || rawNote.trim().isEmpty()) {
            return ParsedVitalsResult.builder()
                    .medicationsGiven(false)
                    .mealTaken(false)
                    .summaryNotes("No notes provided")
                    .build();
        }

        // If Gemini API Key is provided, call Google Gemini REST API
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            try {
                ParsedVitalsResult geminiResult = callGeminiApi(rawNote);
                if (geminiResult != null) {
                    return geminiResult;
                }
            } catch (Exception e) {
                log.warn("Gemini API call failed, falling back to local extraction engine: {}", e.getMessage());
            }
        }

        // Fallback: Local rule & regex extraction engine
        return parseLocally(rawNote);
    }

    private ParsedVitalsResult callGeminiApi(String rawNote) {
        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s", model, apiKey);

        String prompt = "You are a medical healthcare telemetry parser for in-home care sessions. " +
                "Extract structured patient vitals from the following voice note into strict JSON format with keys: " +
                "\"systolicBp\" (integer or null), \"diastolicBp\" (integer or null), \"bloodSugarMgDl\" (integer or null), " +
                "\"medicationsGiven\" (boolean), \"mealTaken\" (boolean), \"summaryNotes\" (concise summary string). " +
                "Return ONLY the raw JSON object without markdown fences.\n\n" +
                "Caregiver Voice Note:\n" + rawNote;

        Map<String, Object> requestBody = Map.of(
                "contents", List.of(
                        Map.of("parts", List.of(
                                Map.of("text", prompt)
                        ))
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                String candidateText = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();

                // Clean potential markdown fences ```json ... ```
                candidateText = candidateText.replaceAll("```json", "").replaceAll("```", "").trim();
                JsonNode json = objectMapper.readTree(candidateText);

                Integer systolic = json.hasNonNull("systolicBp") ? json.get("systolicBp").asInt() : null;
                Integer diastolic = json.hasNonNull("diastolicBp") ? json.get("diastolicBp").asInt() : null;
                Integer sugar = json.hasNonNull("bloodSugarMgDl") ? json.get("bloodSugarMgDl").asInt() : null;
                boolean meds = json.has("medicationsGiven") && json.get("medicationsGiven").asBoolean();
                boolean meal = json.has("mealTaken") && json.get("mealTaken").asBoolean();
                String summary = json.hasNonNull("summaryNotes") ? json.get("summaryNotes").asText() : "Gemini Telemetry Recorded";

                return ParsedVitalsResult.builder()
                        .systolicBp(systolic)
                        .diastolicBp(diastolic)
                        .bloodSugarMgDl(sugar)
                        .medicationsGiven(meds)
                        .mealTaken(meal)
                        .summaryNotes(summary)
                        .build();
            } catch (Exception e) {
                log.warn("Failed to parse Gemini response JSON: {}", e.getMessage());
            }
        }
        return null;
    }

    private ParsedVitalsResult parseLocally(String rawNote) {
        Integer systolic = null;
        Integer diastolic = null;
        Integer sugar = null;
        boolean meds = false;
        boolean meal = false;

        // 1. Extract Blood Pressure (e.g., 120/80 or 120 over 80)
        Pattern bpPattern = Pattern.compile("(\\d{2,3})\\s*(?:/|over)\\s*(\\d{2,3})", Pattern.CASE_INSENSITIVE);
        Matcher bpMatcher = bpPattern.matcher(rawNote);
        if (bpMatcher.find()) {
            try {
                systolic = Integer.parseInt(bpMatcher.group(1));
                diastolic = Integer.parseInt(bpMatcher.group(2));
            } catch (NumberFormatException ignored) {}
        }

        // 2. Extract Blood Sugar (e.g., 110 mg/dL or sugar 110)
        Pattern sugarPattern = Pattern.compile("(?:sugar|glucose|sugar level|bg)?\\s*(\\d{2,3})\\s*(?:mg/dl|mgdl|mg)?", Pattern.CASE_INSENSITIVE);
        Matcher sugarMatcher = sugarPattern.matcher(rawNote);
        if (sugarMatcher.find()) {
            try {
                int val = Integer.parseInt(sugarMatcher.group(1));
                if (val >= 40 && val <= 500 && val != systolic && val != diastolic) {
                    sugar = val;
                }
            } catch (NumberFormatException ignored) {}
        }

        // 3. Medication Given check
        String lower = rawNote.toLowerCase();
        if (lower.contains("med") || lower.contains("medication") || lower.contains("medicine") || lower.contains("pills")) {
            meds = !lower.contains("no med") && !lower.contains("didn't give med");
        }

        // 4. Meal Taken check
        if (lower.contains("meal") || lower.contains("lunch") || lower.contains("breakfast") || lower.contains("dinner") || lower.contains("food") || lower.contains("ate")) {
            meal = !lower.contains("no food") && !lower.contains("refused food") && !lower.contains("didn't eat");
        }

        return ParsedVitalsResult.builder()
                .systolicBp(systolic)
                .diastolicBp(diastolic)
                .bloodSugarMgDl(sugar)
                .medicationsGiven(meds)
                .mealTaken(meal)
                .summaryNotes("AI Structured Vitals: BP " + (systolic != null ? systolic + "/" + diastolic : "N/A") +
                        ", Glucose: " + (sugar != null ? sugar + " mg/dL" : "N/A") +
                        ", Meds: " + (meds ? "Completed" : "Not Logged") +
                        ", Meals: " + (meal ? "Completed" : "Not Logged"))
                .build();
    }
}
