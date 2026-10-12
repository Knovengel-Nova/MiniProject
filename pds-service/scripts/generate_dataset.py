"""
Deterministic Synthetic Dataset Generator for PDS Linear Regression Model.

Generates reproducible synthetic chat messages with numeric features and
a continuous risk score target between 0.0 and 1.0 based on a documented formula.
"""

import os
import sys
import random
import numpy as np
import pandas as pd

# Add parent directory to path to import feature_extractor
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from feature_extractor import extract_features, FEATURE_NAMES

SEED = 42
TOTAL_SAMPLES = 1500

# Templates for synthetic message generation
NORMAL_TEMPLATES = [
    "Hey, are you coming to the lab today?",
    "Can you share the notes from the morning lecture?",
    "Let's meet at 4 PM in the library to discuss the project.",
    "Did you push your commits to the repository?",
    "The assignment submission deadline is tomorrow at 5 PM.",
    "Good morning everyone! Please check the shared document.",
    "I am working on the database module right now.",
    "Could someone review my pull request?",
    "Let's test the socket connection over the LAN switch.",
    "The mini project presentation is scheduled for next Monday.",
    "Are we meeting in room 302 or 304?",
    "I will be 10 minutes late to the group meeting.",
    "Thanks for the update, that looks great!",
    "Where did you save the UML diagrams?",
    "Please send the PDF version of the report.",
    "We need to add unit tests before submitting.",
    "Let's verify the requirements traceability matrix.",
    "Is PostgreSQL running on your laptop?",
    "Everything compiled cleanly on my machine.",
    "Have you had lunch yet? Let's grab something from the canteen."
]

MODERATE_TEMPLATES = [
    "Check out this interesting article at https://techblog.org/networking-guide",
    "Don't forget to update your profile info before 6 PM today.",
    "Hey! Look at this new library release: http://github.com/tools/release-v2",
    "Please fill out this survey form for our seminar: https://forms.college.edu/survey",
    "Is this link safe? https://dev-resources.net/download-pack",
    "Quick reminder: please verify your attendance record on the portal today.",
    "Found this useful reference for Linear Regression: https://scikit-learn.org",
    "Can you check if this API endpoint works? http://192.168.1.15:8080/status",
    "Please submit your project credentials form to the coordinator.",
    "Review this document immediately if possible: https://docs.sharepoint.com/r/1284"
]

HIGH_RISK_TEMPLATES = [
    "URGENT: Your account has been suspended! Verify your password immediately at http://login-secure-portal.com/auth",
    "CONGRATULATIONS!! You have won the $5000 lottery prize!! Claim your gift card here: http://win-prize-now.biz/claim",
    "Security Alert: Unauthorized login attempt detected! Confirm your credentials and SSN now: http://bank-security-verify.org",
    "ACTION REQUIRED: Wire transfer $1000 to avoid account deactivation immediately! Click http://quick-wire-pay.net",
    "Free bitcoin giveaway! Double your crypto in 24 hours! Login now: http://crypto-free-coins.cc/claim?id=99",
    "Immediate action required! Your mailbox is full! Verify password at http://mail-upgrade-server.net to avoid deletion!",
    "URGENT NOTICE: IRS tax refund of $2450 pending. Click http://refund-irs-claim.gov.fake.org to claim before midnight!",
    "You won a free iPhone 16! Winner #49281. Claim your reward immediately: http://gift-winner-free.net/redeem!!",
    "Bank Alert: Your wire transfer was put on hold. Verify bank credentials immediately at http://secure-bank-login.xyz",
    "PASSWORD EXPIRED!! Click http://sysadmin-reset-now.top to update your network password now or lose access!!"
]

def calculate_ground_truth_risk(features: dict, rng: np.random.RandomState) -> float:
    """
    Computes continuous synthetic risk score between 0.0 and 1.0 using
    a documented deterministic linear combination with controlled Gaussian noise.
    
    Formula:
      baseline = 0.04
      + 0.12 * min(1.0, length / 200)
      + 0.30 * min(1.0, suspicious_term_count / 3)
      + 0.22 * min(1.0, 4.0 * suspicious_term_ratio)
      + 0.16 * min(1.0, url_count / 2)
      + 0.08 * min(1.0, punct_count / 4)
      + 0.05 * uppercase_ratio
      + 0.03 * digit_ratio
      + noise ~ N(0, 0.015^2)
    """
    msg_len = features["message_length"]
    susp_count = features["suspicious_term_count"]
    susp_ratio = features["suspicious_term_ratio"]
    url_count = features["url_count"]
    punct_count = features["excessive_punctuation_count"]
    upper_ratio = features["uppercase_ratio"]
    digit_ratio = features["digit_ratio"]
    
    score = (
        0.04
        + 0.12 * min(1.0, msg_len / 200.0)
        + 0.30 * min(1.0, susp_count / 3.0)
        + 0.22 * min(1.0, 4.0 * susp_ratio)
        + 0.16 * min(1.0, url_count / 2.0)
        + 0.08 * min(1.0, punct_count / 4.0)
        + 0.05 * upper_ratio
        + 0.03 * digit_ratio
    )
    
    noise = rng.normal(0.0, 0.015)
    score += noise
    
    # Clip to [0.0, 1.0]
    return float(np.clip(score, 0.0, 1.0))

def generate_synthetic_dataset(output_path: str, count: int = TOTAL_SAMPLES, seed: int = SEED):
    np.random.seed(seed)
    random.seed(seed)
    rng = np.random.RandomState(seed)
    
    rows = []
    
    # Distribution: 60% normal, 20% moderate, 20% high risk
    n_normal = int(count * 0.60)
    n_moderate = int(count * 0.20)
    n_high = count - n_normal - n_moderate
    
    def generate_message_sample(category: str):
        if category == "normal":
            base = random.choice(NORMAL_TEMPLATES)
            # Occasional variation
            if random.random() < 0.3:
                base += f" See you at {random.randint(1, 12)} PM."
            if random.random() < 0.2:
                base = base.lower()
        elif category == "moderate":
            base = random.choice(MODERATE_TEMPLATES)
            if random.random() < 0.25:
                base += f" Ref #{random.randint(100, 999)}."
        else:
            base = random.choice(HIGH_RISK_TEMPLATES)
            if random.random() < 0.3:
                base += f" Code: {random.randint(10000, 99999)}! HURRY!"
                
        features = extract_features(base)
        risk = calculate_ground_truth_risk(features, rng)
        
        row = {
            "text": base,
            "category": category,
            **features,
            "risk_score": round(risk, 4)
        }
        return row

    for _ in range(n_normal):
        rows.append(generate_message_sample("normal"))
    for _ in range(n_moderate):
        rows.append(generate_message_sample("moderate"))
    for _ in range(n_high):
        rows.append(generate_message_sample("high_risk"))
        
    random.shuffle(rows)
    df = pd.DataFrame(rows)
    
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    df.to_csv(output_path, index=False)
    print(f"Generated {len(df)} synthetic samples successfully.")
    print(f"Dataset saved to: {output_path}")
    print("Risk score summary:")
    print(df["risk_score"].describe())
    return df

if __name__ == "__main__":
    out = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "data", "synthetic_risk_dataset.csv"))
    generate_synthetic_dataset(out)
