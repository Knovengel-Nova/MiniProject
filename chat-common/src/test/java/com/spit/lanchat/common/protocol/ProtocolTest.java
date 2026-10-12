package com.spit.lanchat.common.protocol;

import com.spit.lanchat.common.codec.JsonCodec;
import com.spit.lanchat.common.codec.JsonFramer;
import com.spit.lanchat.common.dto.*;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ProtocolTest {

    @Test
    public void testDirectMessageEnvelopeSerialization() {
        String msgId = UUID.randomUUID().toString();
        DirectMessageRequest req = new DirectMessageRequest(msgId, "bob", "Hello Bob, are you in lab?");
        MessageEnvelope env = MessageEnvelope.createRequest(MessageType.DIRECT_MSG_REQ, msgId, req);

        String json = JsonCodec.toJson(env);
        assertNotNull(json);
        assertTrue(json.contains("DIRECT_MSG_REQ"));
        assertTrue(json.contains("Hello Bob"));

        MessageEnvelope deserialized = JsonCodec.fromJson(json, MessageEnvelope.class);
        assertEquals(MessageType.DIRECT_MSG_REQ, deserialized.getType());
        assertEquals(msgId, deserialized.getMessageId());

        DirectMessageRequest payload = deserialized.getPayloadAs(DirectMessageRequest.class);
        assertNotNull(payload);
        assertEquals("bob", payload.getRecipientUsername());
        assertEquals("Hello Bob, are you in lab?", payload.getMessageText());
    }

    @Test
    public void testJsonFramerRoundTrip() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[0]);
        JsonFramer framer = new JsonFramer(in, out);

        MessageEnvelope env1 = MessageEnvelope.createRequest(MessageType.LOGIN_REQ, new LoginRequest("alice", "secret123"));
        MessageEnvelope env2 = MessageEnvelope.createRequest(MessageType.LOGOUT_REQ, null);

        framer.writeMessage(env1);
        framer.writeMessage(env2);

        byte[] bytes = out.toByteArray();
        String wireString = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = wireString.split("\n");
        assertEquals(2, lines.length);

        JsonFramer readFramer = new JsonFramer(new ByteArrayInputStream(bytes), new ByteArrayOutputStream());
        MessageEnvelope read1 = readFramer.readMessage();
        assertNotNull(read1);
        assertEquals(MessageType.LOGIN_REQ, read1.getType());

        MessageEnvelope read2 = readFramer.readMessage();
        assertNotNull(read2);
        assertEquals(MessageType.LOGOUT_REQ, read2.getType());

        MessageEnvelope readEof = readFramer.readMessage();
        assertNull(readEof);
    }

    @Test
    public void testMalformedJsonHandling() {
        String badJson = "{ malformed json line }\n";
        ByteArrayInputStream in = new ByteArrayInputStream(badJson.getBytes(StandardCharsets.UTF_8));
        JsonFramer framer = new JsonFramer(in, new ByteArrayOutputStream());

        assertThrows(IOException.class, () -> {
            framer.readMessage();
        });
    }

    @Test
    public void testMaxFrameSizeExceeded() {
        // Create line larger than 64KB
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 70000; i++) {
            sb.append('A');
        }
        sb.append("\n");

        ByteArrayInputStream in = new ByteArrayInputStream(sb.toString().getBytes(StandardCharsets.UTF_8));
        JsonFramer framer = new JsonFramer(in, new ByteArrayOutputStream());

        assertThrows(IOException.class, () -> {
            framer.readMessage();
        });
    }

    @Test
    public void testGroupMessagePushSerialization() {
        String msgId = UUID.randomUUID().toString();
        GroupMessagePush push = new GroupMessagePush(
                msgId,
                "grp-1",
                "Study Group",
                "alice",
                "Meeting at 5pm",
                Instant.now(),
                0.12,
                false,
                "ok"
        );

        MessageEnvelope env = MessageEnvelope.createRequest(MessageType.GROUP_MSG_PUSH, msgId, push);
        String json = JsonCodec.toJson(env);
        MessageEnvelope restored = JsonCodec.fromJson(json, MessageEnvelope.class);
        GroupMessagePush payload = restored.getPayloadAs(GroupMessagePush.class);

        assertEquals("Study Group", payload.getGroupName());
        assertEquals("alice", payload.getSenderUsername());
        assertEquals(0.12, payload.getPredictedRiskScore(), 0.0001);
        assertFalse(payload.isFlagged());
    }
}
