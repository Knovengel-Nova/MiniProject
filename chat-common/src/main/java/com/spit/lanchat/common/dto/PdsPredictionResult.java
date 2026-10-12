package com.spit.lanchat.common.dto;

public class PdsPredictionResult {
    private String requestId;
    private String messageId;
    private Double predictedRiskScore;
    private boolean flagged;
    private Double threshold;
    private String modelVersion;
    private String status;
    private String error;

    public PdsPredictionResult() {}

    public PdsPredictionResult(String requestId, String messageId, Double predictedRiskScore,
                               boolean flagged, Double threshold, String modelVersion,
                               String status, String error) {
        this.requestId = requestId;
        this.messageId = messageId;
        this.predictedRiskScore = predictedRiskScore;
        this.flagged = flagged;
        this.threshold = threshold;
        this.modelVersion = modelVersion;
        this.status = status;
        this.error = error;
    }

    public static PdsPredictionResult unavailable(String requestId, String messageId, String reason) {
        return new PdsPredictionResult(
                requestId,
                messageId,
                null,
                false,
                0.70,
                "none",
                "unavailable",
                reason
        );
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Double getPredictedRiskScore() {
        return predictedRiskScore;
    }

    public void setPredictedRiskScore(Double predictedRiskScore) {
        this.predictedRiskScore = predictedRiskScore;
    }

    public boolean isFlagged() {
        return flagged;
    }

    public void setFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
