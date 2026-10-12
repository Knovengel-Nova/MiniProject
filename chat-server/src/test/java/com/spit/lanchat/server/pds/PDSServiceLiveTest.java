package com.spit.lanchat.server.pds;

import com.spit.lanchat.common.dto.PdsPredictionResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

public class PDSServiceLiveTest {

    private static HttpServer mockHttpServer;
    private static int mockPort;

    @BeforeAll
    public static void setup() throws Exception {
        mockHttpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockPort = mockHttpServer.getAddress().getPort();

        mockHttpServer.createContext("/predict", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                    String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    String resp;
                    if (reqBody.contains("urgent") || reqBody.contains("lottery")) {
                        resp = "{\"requestId\":\"req-123\",\"messageId\":\"msg-123\",\"predictedRiskScore\":0.88,\"flagged\":true,\"threshold\":0.70,\"modelVersion\":\"linear-regression-v1\",\"status\":\"ok\"}";
                    } else if (reqBody.contains("slow")) {
                        try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                        resp = "{\"requestId\":\"req-slow\",\"messageId\":\"msg-slow\",\"predictedRiskScore\":0.10,\"flagged\":false,\"threshold\":0.70,\"modelVersion\":\"linear-regression-v1\",\"status\":\"ok\"}";
                    } else {
                        resp = "{\"requestId\":\"req-123\",\"messageId\":\"msg-123\",\"predictedRiskScore\":0.15,\"flagged\":false,\"threshold\":0.70,\"modelVersion\":\"linear-regression-v1\",\"status\":\"ok\"}";
                    }
                    byte[] bytes = resp.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(bytes);
                    }
                } else {
                    exchange.sendResponseHeaders(405, -1);
                }
            }
        });

        mockHttpServer.start();
    }

    @AfterAll
    public static void tearDown() {
        if (mockHttpServer != null) {
            mockHttpServer.stop(0);
        }
    }

    @Test
    public void testSuccessfulLiveClassificationNormalMessage() {
        PDSService service = new PDSService("http://127.0.0.1:" + mockPort + "/predict", 1000);
        PdsPredictionResult result = service.classifyMessage("msg-1", "Are we meeting in the lab?");

        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        assertEquals(0.15, result.getPredictedRiskScore(), 0.001);
        assertFalse(result.isFlagged());
        assertEquals("linear-regression-v1", result.getModelVersion());
    }

    @Test
    public void testSuccessfulLiveClassificationHighRiskMessage() {
        PDSService service = new PDSService("http://127.0.0.1:" + mockPort + "/predict", 1000);
        PdsPredictionResult result = service.classifyMessage("msg-2", "urgent: claim your lottery prize now!");

        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        assertEquals(0.88, result.getPredictedRiskScore(), 0.001);
        assertTrue(result.isFlagged());
    }

    @Test
    public void testPdsTimeoutFallback() {
        // Timeout set to 100ms, but mock server sleeps for 500ms on 'slow'
        PDSService service = new PDSService("http://127.0.0.1:" + mockPort + "/predict", 100);
        PdsPredictionResult result = service.classifyMessage("msg-3", "slow query test");

        assertNotNull(result);
        assertEquals("unavailable", result.getStatus());
        assertNull(result.getPredictedRiskScore());
        assertFalse(result.isFlagged());
        assertTrue(result.getError().contains("timeout") || result.getError().contains("PDS"));
    }
}
