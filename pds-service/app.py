"""
Flask HTTP JSON API for PDS Linear Regression Inference.
Runs exclusively on the server PC, bound to 127.0.0.1.
Loaded once at startup; serves /predict and /health endpoints.
"""

import os
import sys
import logging
import joblib
import numpy as np
import pandas as pd
from flask import Flask, request, jsonify

# Ensure local modules can be imported
sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))
from feature_extractor import extract_features, features_to_vector, FEATURE_NAMES

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("PDS-Service")

app = Flask(__name__)

# Configuration
DEFAULT_PORT = int(os.environ.get("PDS_PORT", 5005))
DEFAULT_THRESHOLD = float(os.environ.get("PDS_THRESHOLD", 0.70))
MODEL_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), "models", "linear_regression_model.joblib"))
MODEL_VERSION = "linear-regression-v1"

# Load model once at startup
logger.info(f"Loading trained Linear Regression model from: {MODEL_PATH}")
if not os.path.exists(MODEL_PATH):
    logger.error(f"Model file not found at {MODEL_PATH}!")
    raise FileNotFoundError(f"Model file missing: {MODEL_PATH}")

model = joblib.load(MODEL_PATH)
logger.info("Linear Regression model successfully loaded into memory.")

@app.route("/health", methods=["GET"])
def health():
    return jsonify({
        "status": "ok",
        "service": "LAN Secure Chat PDS",
        "modelLoaded": model is not None,
        "modelVersion": MODEL_VERSION,
        "defaultThreshold": DEFAULT_THRESHOLD,
        "algorithm": "LinearRegression (scikit-learn)"
    }), 200

@app.route("/predict", methods=["POST"])
def predict():
    if not request.is_json:
        return jsonify({
            "error": "Request body must be application/json",
            "status": "error"
        }), 400
        
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        return jsonify({
            "error": "Invalid JSON payload",
            "status": "error"
        }), 400
        
    request_id = data.get("requestId", "")
    message_id = data.get("messageId", "")
    text = data.get("text")
    threshold = float(data.get("threshold", DEFAULT_THRESHOLD))
    
    if text is None:
        return jsonify({
            "requestId": request_id,
            "messageId": message_id,
            "error": "Missing required field 'text'",
            "status": "error"
        }), 400
        
    try:
        # Feature extraction identical to training pipeline
        features = extract_features(text)
        vector_df = pd.DataFrame([features_to_vector(features)], columns=FEATURE_NAMES)
        
        # Linear Regression prediction
        raw_pred = float(model.predict(vector_df)[0])
        
        # Clip to [0.0, 1.0]
        clipped_score = float(np.clip(raw_pred, 0.0, 1.0))
        flagged = clipped_score >= threshold
        
        # Explicit terminology: predictedRiskScore, flagged, threshold, modelVersion, status
        return jsonify({
            "requestId": request_id,
            "messageId": message_id,
            "predictedRiskScore": round(clipped_score, 4),
            "flagged": flagged,
            "threshold": threshold,
            "modelVersion": MODEL_VERSION,
            "status": "ok"
        }), 200
        
    except Exception as e:
        logger.exception("Error evaluating message text in PDS service")
        return jsonify({
            "requestId": request_id,
            "messageId": message_id,
            "error": "Inference failure",
            "status": "error"
        }), 500

if __name__ == "__main__":
    logger.info(f"Starting PDS HTTP API server on 127.0.0.1:{DEFAULT_PORT}")
    app.run(host="127.0.0.1", port=DEFAULT_PORT, debug=False)
