package com.spit.lanchat.server.pds;

import com.fasterxml.jackson.databind.JsonNode;
import com.spit.lanchat.common.codec.JsonCodec;
import com.spit.lanchat.common.dto.PdsPredictionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Dedicated Java PDS Service for calling the Python Linear Regression inference API.
 * This is the ONLY component in the Java server responsible for communicating with Python PDS.
 * Gracefully degrades if Python is unreachable or returns an error.
 */
public class PDSService {

    private static final Logger logger = LoggerFactory.getLogger(PDSService.class);

    private final String endpointUrl;
    private final Duration timeout;
    private final HttpClient httpClient;

    public PDSService(String endpointUrl, int timeoutMs) {
        this.endpointUrl = endpointUrl;
        this.timeout = Duration.ofMillis(timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
    }

    /**
     * Sends message text to the Python Linear Regression inference endpoint.
     * Returns prediction result, or an 'unavailable' result if Python is down or times out.
     */
    public PdsPredictionResult classifyMessage(String messageId, String text) {
        String requestId = UUID.randomUUID().toString();

        if (text == null || text.trim().isEmpty()) {
            return new PdsPredictionResult(
                    requestId, messageId, 0.0, false, 0.70, "linear-regression-v1", "ok", null
            );
        }

        try {
            Map<String, Object> reqMap = new HashMap<>();
            reqMap.put("requestId", requestId);
            reqMap.put("messageId", messageId);
            reqMap.put("text", text);

            String requestBody = JsonCodec.toJson(reqMap);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpointUrl))
                    .timeout(this.timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = JsonCodec.getMapper().readTree(response.body());
                Double score = root.has("predictedRiskScore") ? root.get("predictedRiskScore").asDouble() : null;
                boolean flagged = root.has("flagged") && root.get("flagged").asBoolean();
                Double threshold = root.has("threshold") ? root.get("threshold").asDouble() : 0.70;
                String modelVersion = root.has("modelVersion") ? root.get("modelVersion").asText() : "linear-regression-v1";
                String status = root.has("status") ? root.get("status").asText() : "ok";

                return new PdsPredictionResult(
                        requestId, messageId, score, flagged, threshold, modelVersion, status, null
                );
            } else {
                logger.warn("PDS service returned HTTP status {} (requestId: {}, messageId: {})",
                        response.statusCode(), requestId, messageId);
                return PdsPredictionResult.unavailable(requestId, messageId, "HTTP " + response.statusCode());
            }

        } catch (java.net.ConnectException e) {
            logger.warn("PDS service unreachable at {} (requestId: {}, messageId: {}): {}",
                    endpointUrl, requestId, messageId, e.getMessage());
            return PdsPredictionResult.unavailable(requestId, messageId, "PDS service offline");
        } catch (java.net.http.HttpTimeoutException e) {
            logger.warn("PDS service request timed out after {}ms (messageId: {})",
                    timeout.toMillis(), messageId);
            return PdsPredictionResult.unavailable(requestId, messageId, "PDS timeout");
        } catch (Exception e) {
            logger.warn("PDS classification failed for messageId {}: {}", messageId, e.getMessage());
            return PdsPredictionResult.unavailable(requestId, messageId, e.getMessage());
        }
    }
}
