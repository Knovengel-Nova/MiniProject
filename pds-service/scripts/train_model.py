"""
Training script for the PDS Linear Regression model.
Trains a scikit-learn LinearRegression model on the synthetic dataset,
computes MAE and RMSE on a held-out test split, and persists the model.
"""

import os
import sys
import json
import joblib
import numpy as np
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LinearRegression
from sklearn.metrics import mean_absolute_error, root_mean_squared_error, r2_score

# Add parent directory to path to import feature_extractor
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from feature_extractor import FEATURE_NAMES

def train():
    base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    csv_path = os.path.join(base_dir, "data", "synthetic_risk_dataset.csv")
    model_path = os.path.join(base_dir, "models", "linear_regression_model.joblib")
    meta_path = os.path.join(base_dir, "models", "model_metadata.json")
    
    if not os.path.exists(csv_path):
        raise FileNotFoundError(f"Dataset not found at {csv_path}. Run generate_dataset.py first.")
        
    df = pd.read_csv(csv_path)
    print(f"Loaded {len(df)} samples from {csv_path}")
    
    X = df[FEATURE_NAMES]
    y = df["risk_score"]
    
    # 80/20 train/test split with deterministic random_state
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.20, random_state=42
    )
    print(f"Training set: {len(X_train)} samples; Test set: {len(X_test)} samples")
    
    model = LinearRegression()
    model.fit(X_train, y_train)
    
    # Evaluate on held-out test set
    y_pred = model.predict(X_test)
    y_pred_clipped = np.clip(y_pred, 0.0, 1.0)
    
    mae = float(mean_absolute_error(y_test, y_pred_clipped))
    rmse = float(root_mean_squared_error(y_test, y_pred_clipped))
    r2 = float(r2_score(y_test, y_pred_clipped))
    
    print("\n=== EVALUATION RESULTS ON HELD-OUT TEST SET ===")
    print(f"MAE  (Mean Absolute Error):     {mae:.6f}")
    print(f"RMSE (Root Mean Squared Error): {rmse:.6f}")
    print(f"R^2  (Coefficient of Determ.):  {r2:.6f}")
    print("\nLearned Model Parameters:")
    print(f"Intercept: {float(model.intercept_):.6f}")
    for name, coef in zip(FEATURE_NAMES, model.coef_):
        print(f"  Feature '{name}': coef = {float(coef):.6f}")
        
    # Save model
    os.makedirs(os.path.dirname(model_path), exist_ok=True)
    joblib.dump(model, model_path)
    print(f"\nModel saved to: {model_path}")
    
    metadata = {
        "modelType": "LinearRegression",
        "algorithm": "Ordinary Least Squares (scikit-learn)",
        "modelVersion": "linear-regression-v1",
        "features": FEATURE_NAMES,
        "intercept": float(model.intercept_),
        "coefficients": {name: float(coef) for name, coef in zip(FEATURE_NAMES, model.coef_)},
        "evaluationMetrics": {
            "testSplitRatio": 0.20,
            "randomState": 42,
            "testSampleCount": int(len(X_test)),
            "mae": round(mae, 6),
            "rmse": round(rmse, 6),
            "r2": round(r2, 6)
        },
        "defaultThreshold": 0.70,
        "limitations": "Educational demonstration using synthetic data. Not a real-world calibrated spam filter."
    }
    
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(metadata, f, indent=2)
    print(f"Metadata saved to: {meta_path}")

if __name__ == "__main__":
    train()
