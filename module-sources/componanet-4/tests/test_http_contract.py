"""Contract checks for the v6.1 diagnostic adapter."""
import os
import unittest
from unittest.mock import patch
os.environ.setdefault("ALTERATION_API_TOKEN", "local-testing-token-which-is-longer-than-32-chars")
from fastapi.testclient import TestClient
from backend.integrated_api import app

class ServiceContractTests(unittest.TestCase):
    def test_health_requires_authentication(self):
        client=TestClient(app)
        self.assertEqual(client.get("/health").status_code, 401)
        good=client.get("/health",headers={"X-Analysis-Token":os.environ["ALTERATION_API_TOKEN"]})
        self.assertEqual(good.status_code,200)
        self.assertEqual(good.json().get("algorithmVersion"),"document-analysis-6")
        self.assertTrue(good.json().get("templatesPresent"))
    def test_unknown_document_type_not_analysed(self):
        client=TestClient(app)
        response=client.post("/analyze", headers={"X-Analysis-Token":os.environ["ALTERATION_API_TOKEN"]}, json={"documentType":"WRONG"})
        self.assertEqual(response.status_code,422)
        self.assertEqual(response.json()["detail"],"Unsupported document type")

if __name__=="__main__":unittest.main()
