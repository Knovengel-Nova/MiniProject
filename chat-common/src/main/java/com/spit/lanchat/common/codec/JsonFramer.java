package com.spit.lanchat.common.codec;

import com.spit.lanchat.common.protocol.MessageEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * Handles framing of JSON messages over stream sockets using newline-delimited JSON.
 * Protects against buffer overflows and handles connection closures safely.
 */
public class JsonFramer {

    private static final Logger logger = LoggerFactory.getLogger(JsonFramer.class);
    public static final int MAX_FRAME_BYTES = 64 * 1024; // 64 KB limit per message

    private final BufferedReader reader;
    private final BufferedWriter writer;
    private final Object writeLock = new Object();

    public JsonFramer(InputStream in, OutputStream out) {
        this.reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        this.writer = new BufferedWriter(new OutputStreamWriter(out, StandardCharsets.UTF_8));
    }

    /**
     * Reads the next newline-delimited JSON message envelope from the socket.
     * Returns null on EOF (clean disconnect).
     */
    public MessageEnvelope readMessage() throws IOException {
        String line = reader.readLine();
        if (line == null) {
            return null; // Peer disconnected
        }
        line = line.trim();
        if (line.isEmpty()) {
            return null;
        }

        if (line.getBytes(StandardCharsets.UTF_8).length > MAX_FRAME_BYTES) {
            throw new IOException("Frame exceeded maximum allowed size of " + MAX_FRAME_BYTES + " bytes");
        }

        try {
            return JsonCodec.fromJson(line, MessageEnvelope.class);
        } catch (Exception e) {
            logger.warn("Malformed JSON line received: {}", e.getMessage());
            throw new IOException("Failed to parse JSON envelope: " + e.getMessage(), e);
        }
    }

    /**
     * Serializes and writes a message envelope followed by a newline.
     * Thread-safe against concurrent sends.
     */
    public void writeMessage(MessageEnvelope envelope) throws IOException {
        if (envelope == null) {
            return;
        }
        String json = JsonCodec.toJson(envelope);
        synchronized (writeLock) {
            writer.write(json);
            writer.newLine();
            writer.flush();
        }
    }

    public void close() {
        try {
            reader.close();
        } catch (Exception ignored) {}
        try {
            writer.close();
        } catch (Exception ignored) {}
    }
}
