"""
Evaluation script for the PDS Linear Regression model.
Loads the trained model and held-out dataset to recompute MAE, RMSE and R2.
"""

import os
import sys
import json
import joblib
import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.metrics import mean_absolute_error, root_mean_squared_error, r2_score

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from feature_extractor import FEATURE_NAMES

def evaluate():
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    csv_path = os.path.join(base_dir, "data", "synthetic_risk_dataset.csv")
    model_path = os.path.join(base_dir, "models", "linear_regression_model.joblib")
    
    if not os.path.exists(model_path):
        raise FileNotFoundError(f"Model not found at {model_path}. Run train_model.py first.")
    if not os.path.exists(csv_path):
        raise FileNotFoundError(f"Dataset not found at {csv_path}. Run generate_dataset.py first.")
        
    model = joblib.load(model_path)
    df = pd.read_csv(csv_path)
    
    X = df[FEATURE_NAMES]
    y = df["risk_score"]
    
    # Same split
    _, X_test, _, y_test = train_test_split(X, y, test_size=0.20, random_state=42)
    
    y_pred = model.predict(X_test)
    y_pred_clipped = np.clip(y_pred, 0.0, 1.0)
    
    mae = mean_absolute_error(y_test, y_pred_clipped)
    rmse = root_mean_squared_error(y_test, y_pred_clipped)
    r2 = r2_score(y_test, y_pred_clipped)
    
    print("==================================================")
    print("     PDS LINEAR REGRESSION EVALUATION REPORT      ")
    print("==================================================")
    print(f"Total Test Observations:       {len(X_test)}")
    print(f"MAE  (Mean Absolute Error):     {mae:.6f}")
    print(f"RMSE (Root Mean Squared Error): {rmse:.6f}")
    print(f"R^2  Score:                     {r2:.6f}")
    print(f"Configurable Alert Threshold:  0.70")
    
    # Check sample predictions
    high_count = sum(1 for p in y_pred_clipped if p >= 0.70)
    print(f"Test samples flagged (>= 0.70): {high_count} / {len(X_test)} ({high_count/len(X_test)*100:.1f}%)")
    print("==================================================")
    
    return {
        "testSamples": len(X_test),
        "mae": float(mae),
        "rmse": float(rmse),
        "r2": float(r2)
    }

if __name__ == "__main__":
    evaluate()
