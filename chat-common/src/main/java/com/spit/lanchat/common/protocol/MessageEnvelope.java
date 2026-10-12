package com.spit.lanchat.common.protocol;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.spit.lanchat.common.codec.JsonCodec;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard protocol envelope for all client-server TCP newline-delimited JSON messages.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageEnvelope {

    private MessageType type;
    private String requestId;
    private String messageId;
    private Instant timestamp;
    private String status;
    private String error;
    private JsonNode payload;

    public MessageEnvelope() {
        this.timestamp = Instant.now();
    }

    public MessageEnvelope(MessageType type, String requestId, String messageId, String status, String error, JsonNode payload) {
        this.type = type;
        this.requestId = requestId != null ? requestId : UUID.randomUUID().toString();
        this.messageId = messageId;
        this.timestamp = Instant.now();
        this.status = status;
        this.error = error;
        this.payload = payload;
    }

    public static MessageEnvelope createRequest(MessageType type, Object payload) {
        return new MessageEnvelope(
                type,
                UUID.randomUUID().toString(),
                null,
                "OK",
                null,
                payload != null ? JsonCodec.treeFromObject(payload) : null
        );
    }

    public static MessageEnvelope createRequest(MessageType type, String messageId, Object payload) {
        return new MessageEnvelope(
                type,
                UUID.randomUUID().toString(),
                messageId,
                "OK",
                null,
                payload != null ? JsonCodec.treeFromObject(payload) : null
        );
    }

    public static MessageEnvelope createResponse(MessageType type, String requestId, String messageId, String status, Object payload) {
        return new MessageEnvelope(
                type,
                requestId,
                messageId,
                status != null ? status : "OK",
                null,
                payload != null ? JsonCodec.treeFromObject(payload) : null
        );
    }

    public static MessageEnvelope createError(MessageType type, String requestId, String errorMessage) {
        return new MessageEnvelope(
                type != null ? type : MessageType.ERROR_RESP,
                requestId != null ? requestId : UUID.randomUUID().toString(),
                null,
                "ERROR",
                errorMessage,
                null
        );
    }

    public <T> T getPayloadAs(Class<T> clazz) {
        if (payload == null || payload.isNull()) {
            return null;
        }
        return JsonCodec.treeToValue(payload, clazz);
    }

    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
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

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
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

    public JsonNode getPayload() {
        return payload;
    }

    public void setPayload(JsonNode payload) {
        this.payload = payload;
    }

    @Override
    public String toString() {
        return "MessageEnvelope{" +
                "type=" + type +
                ", requestId='" + requestId + '\'' +
                ", messageId='" + messageId + '\'' +
                ", status='" + status + '\'' +
                ", error='" + error + '\'' +
                '}';
    }
}
