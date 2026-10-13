"""Compatibility startup alias: both ASGI entrypoints use the protected v6 API.

Use: python -m uvicorn backend.integrated_api:app --host 127.0.0.1 --port 8001
The former unauthenticated multipart route was intentionally disabled: it
bypassed the image-integrity/quality gate required for this workflow.
"""
from backend.integrated_api import app
