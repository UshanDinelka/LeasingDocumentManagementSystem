"""Read document layouts at service startup. Restart Python after editing configuration."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PROFILE_FILE = ROOT / "config" / "analysis_profiles.json"


def load_profiles():
    data = json.loads(PROFILE_FILE.read_text(encoding="utf-8"))
    profiles = data["documents"]
    if not isinstance(profiles, dict):
        raise ValueError("Document profiles must be an object")
    for kind, profile in profiles.items():
        template = (ROOT / "data" / "templates" / profile["template"]).resolve()
        if template.parent != (ROOT / "data" / "templates").resolve():
            raise ValueError("Invalid template filename")
        if not profile.get("titlePatterns") or not profile.get("staticRegions"):
            raise ValueError(f"Missing type validation/registration rules: {kind}")
        for box in profile["staticRegions"]:
            if len(box) != 4 or not (0 <= box[0] < box[2] <= 1 and 0 <= box[1] < box[3] <= 1):
                raise ValueError(f"Invalid static registration region: {kind}")
    return profiles


PROFILES = load_profiles()
TEMPLATE_FILES = {kind: ROOT / "data" / "templates" / p["template"] for kind, p in PROFILES.items()}
