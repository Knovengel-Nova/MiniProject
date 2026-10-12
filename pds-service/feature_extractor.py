"""
Feature extractor for the LAN Secure Chat PDS component.
Extracts numeric features from message text for the Linear Regression model.
"""

import re
from typing import Dict, List, Any

# Documented suspicious terms list
SUSPICIOUS_TERMS = [
    "urgent", "immediately", "action required", "verify", "suspended",
    "account", "password", "credential", "security alert", "wire",
    "bank", "transfer", "free", "lottery", "winner", "claim", "prize",
    "refund", "ssn", "click here", "login", "confirm", "gift card",
    "bitcoin", "crypto", "inheritance", "million dollars", "congratulations"
]

FEATURE_NAMES = [
    "message_length",
    "suspicious_term_count",
    "suspicious_term_ratio",
    "url_count",
    "excessive_punctuation_count",
    "uppercase_ratio",
    "digit_ratio"
]

URL_REGEX = re.compile(r"https?://\S+|www\.\S+", re.IGNORECASE)
PUNCT_REGEX = re.compile(r"[!?$%*]{2,}|[!]{1,}|[$]{1,}")

def extract_features(text: str) -> Dict[str, float]:
    """
    Extracts deterministic numeric features from raw message text.
    
    Returns a dictionary mapping feature names to float values.
    """
    if text is None:
        text = ""
        
    length = len(text)
    lower_text = text.lower()
    words = re.findall(r"\b\w+\b", lower_text)
    word_count = max(len(words), 1)
    
    # 1. Message length (capped at 1000 for normalization)
    msg_len = float(length)
    
    # 2. Suspicious terms count
    suspicious_count = 0
    for term in SUSPICIOUS_TERMS:
        if " " in term:
            suspicious_count += lower_text.count(term)
        else:
            suspicious_count += sum(1 for w in words if w == term)
            
    # 3. Suspicious term ratio
    suspicious_ratio = float(suspicious_count) / float(word_count)
    
    # 4. URL count
    urls = URL_REGEX.findall(text)
    url_count = float(len(urls))
    
    # 5. Excessive punctuation count (!, ?, $, %)
    punct_count = float(sum(1 for c in text if c in "!$?%*"))
    
    # 6. Uppercase ratio
    alpha_chars = [c for c in text if c.isalpha()]
    if alpha_chars:
        upper_count = sum(1 for c in alpha_chars if c.isupper())
        uppercase_ratio = float(upper_count) / float(len(alpha_chars))
    else:
        uppercase_ratio = 0.0
        
    # 7. Digit ratio
    if length > 0:
        digit_count = sum(1 for c in text if c.isdigit())
        digit_ratio = float(digit_count) / float(length)
    else:
        digit_ratio = 0.0
        
    return {
        "message_length": msg_len,
        "suspicious_term_count": float(suspicious_count),
        "suspicious_term_ratio": round(suspicious_ratio, 4),
        "url_count": url_count,
        "excessive_punctuation_count": punct_count,
        "uppercase_ratio": round(uppercase_ratio, 4),
        "digit_ratio": round(digit_ratio, 4)
    }

def features_to_vector(features: Dict[str, float]) -> List[float]:
    """Converts a feature dict into an ordered vector for model input."""
    return [features[name] for name in FEATURE_NAMES]
