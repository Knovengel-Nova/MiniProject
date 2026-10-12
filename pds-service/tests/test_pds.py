"""
Unit and integration tests for the Python PDS component.
Tests feature extraction, Linear Regression inference, clipping,
threshold decisions, and Flask API endpoints.
"""

import os
import sys
import unittest
import json

# Add parent directory
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from feature_extractor import extract_features, features_to_vector, FEATURE_NAMES
from app import app, model, MODEL_VERSION

class TestPDSComponent(unittest.TestCase):

    def setUp(self):
        self.client = app.test_client()

    def test_feature_extraction_normal_message(self):
        text = "Hello, are we meeting at 3 PM for the project discussion?"
        feats = extract_features(text)
        self.assertEqual(len(feats), len(FEATURE_NAMES))
        self.assertEqual(feats["suspicious_term_count"], 0)
        self.assertEqual(feats["url_count"], 0)
        self.assertGreater(feats["message_length"], 20)

    def test_feature_extraction_suspicious_message(self):
        text = "URGENT!! Your account has been suspended! Verify your password at https://fake-bank.com immediately! $$$"
        feats = extract_features(text)
        self.assertGreater(feats["suspicious_term_count"], 2)
        self.assertEqual(feats["url_count"], 1)
        self.assertGreater(feats["excessive_punctuation_count"], 3)
        self.assertGreaterEqual(feats["uppercase_ratio"], 0.1)

    def test_model_loaded(self):
        self.assertIsNotNone(model)
        import pandas as pd
        vector_df = pd.DataFrame([[100.0, 3.0, 0.2, 1.0, 4.0, 0.3, 0.05]], columns=FEATURE_NAMES)
        pred = model.predict(vector_df)
        self.assertEqual(len(pred), 1)

    def test_health_endpoint(self):
        response = self.client.get("/health")
        self.assertEqual(response.status_code, 200)
        data = response.get_json()
        self.assertEqual(data["status"], "ok")
        self.assertTrue(data["modelLoaded"])
        self.assertEqual(data["modelVersion"], MODEL_VERSION)

    def test_predict_normal_message(self):
        payload = {
            "requestId": "req-1",
            "messageId": "msg-1",
            "text": "Hi team, please find the class notes in our shared folder."
        }
        response = self.client.post("/predict", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.get_json()
        self.assertEqual(data["requestId"], "req-1")
        self.assertEqual(data["messageId"], "msg-1")
        self.assertEqual(data["status"], "ok")
        self.assertIn("predictedRiskScore", data)
        self.assertGreaterEqual(data["predictedRiskScore"], 0.0)
        self.assertLessEqual(data["predictedRiskScore"], 1.0)
        # Normal text should NOT be flagged with 0.70 threshold
        self.assertFalse(data["flagged"])
        self.assertLess(data["predictedRiskScore"], 0.70)

    def test_predict_suspicious_message(self):
        payload = {
            "requestId": "req-2",
            "messageId": "msg-2",
            "text": "URGENT ACTION REQUIRED: Account suspended! Confirm your password and SSN immediately at http://login-secure-portal.com/auth or transfer $5000!!"
        }
        response = self.client.post("/predict", json=payload)
        self.assertEqual(response.status_code, 200)
        data = response.get_json()
        self.assertEqual(data["requestId"], "req-2")
        self.assertEqual(data["messageId"], "msg-2")
        self.assertEqual(data["status"], "ok")
        self.assertGreaterEqual(data["predictedRiskScore"], 0.70)
        self.assertTrue(data["flagged"])

    def test_predict_missing_text_error(self):
        payload = {
            "requestId": "req-3",
            "messageId": "msg-3"
        }
        response = self.client.post("/predict", json=payload)
        self.assertEqual(response.status_code, 400)
        data = response.get_json()
        self.assertEqual(data["status"], "error")

    def test_predict_invalid_json(self):
        response = self.client.post("/predict", data="not json", content_type="text/plain")
        self.assertEqual(response.status_code, 400)

if __name__ == "__main__":
    unittest.main()
