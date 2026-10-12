package com.spit.lanchat.server.pds;

import com.spit.lanchat.common.dto.PdsPredictionResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class PDSServiceTest {

    @Test
    public void testPdsServiceUnavailableFallback() {
        // Point to an unused local port to simulate offline service
        PDSService service = new PDSService("http://127.0.0.1:59999/predict", 200);

        PdsPredictionResult result = service.classifyMessage("msg-test-1", "Suspicious text example");
        assertNotNull(result);
        assertEquals("msg-test-1", result.getMessageId());
        assertEquals("unavailable", result.getStatus());
        assertNull(result.getPredictedRiskScore());
        assertFalse(result.isFlagged(), "Unclassified message must not be marked as safe or flagged erroneously");
        assertNotNull(result.getError());
    }

    @Test
    public void testEmptyMessageHandling() {
        PDSService service = new PDSService("http://127.0.0.1:59999/predict", 200);

        PdsPredictionResult result = service.classifyMessage("msg-test-2", "   ");
        assertNotNull(result);
        assertEquals("ok", result.getStatus());
        assertEquals(0.0, result.getPredictedRiskScore());
        assertFalse(result.isFlagged());
    }
}
