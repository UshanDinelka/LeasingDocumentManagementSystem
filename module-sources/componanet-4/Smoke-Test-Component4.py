"""Optional HTTP smoke test using the package's sample NIC template, not a customer upload."""
import base64
import hashlib
import json
import os
from pathlib import Path
import sys
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from PIL import Image

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8001").rstrip("/")
TOKEN = os.environ.get("ALTERATION_API_TOKEN", "")
if len(TOKEN) < 32:
    raise SystemExit("Set the Python/Spring shared ALTERATION_API_TOKEN; do not include it in reports.")
source = Path(__file__).resolve().parent / "data/templates/nic_template_v1.jpg"
blob = source.read_bytes()
with Image.open(source) as image:
    w, h = image.size
ocr = {
    "fullText": "SRI LANKA NATIONAL IDENTITY CARD\nNo: 200000000001\nName: SAMPLE HOLDER",
    "imageWidth": w, "imageHeight": h, "coordinateSpace": "EXIF_UPRIGHT_FULL_IMAGE",
    "lines": [
        {"text": "SRI LANKA NATIONAL IDENTITY CARD", "boundingBox": {"left": 25, "top": 20, "right": min(480, w-2), "bottom": min(90, h-2)}},
        {"text": "No: 200000000001", "boundingBox": {"left": max(1,w//2), "top": max(1,h//2), "right": w-2, "bottom": min(h-2,h//2+30)}}
    ],
    "fields": {"nicNumber": "200000000001"}
}
body = {
  "documentType": "NIC", "imageBase64": base64.b64encode(blob).decode("ascii"),
  "imageSha256": hashlib.sha256(blob).hexdigest(),
  "quality": {"status": "PASSED", "blurPassed": True, "brightnessPassed": True, "resolutionPassed": True},
  "ocr": ocr, "customerSource": "SERVER_DATABASE",
  "customerData": {"customerId": 1, "nic": "200000000001", "fullName": "SAMPLE HOLDER"}
}
request = Request(BASE+"/analyze", json.dumps(body).encode("utf-8"),
       headers={"X-Analysis-Token": TOKEN, "Content-Type": "application/json"},method="POST")
print("Calling Component 4 directly, bypassing Spring (synthetic OCR and bundled sample image)...")
try:
    with urlopen(request, timeout=120) as response:
        data = json.load(response)
        print("HTTP:", response.status)
        for key in ("status", "algorithmVersion", "templateVerificationStatus", "customerVerificationStatus", "risk_level", "risk_score", "reasonCode", "message"):
            print(f"{key}: {str(data.get(key))[:230]}")
except HTTPError as err:
    print("HTTP:",err.code)
    print("Server error:",err.read(2000).decode("utf-8", "replace"))
    sys.exit(1)
except URLError as err:
    print("Connection error:",str(err.reason))
    sys.exit(2)
