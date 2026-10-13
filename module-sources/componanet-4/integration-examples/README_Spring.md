# Spring Boot integration — existing Spring files are still needed

The user uploaded `app.zip` containing Kotlin source but **not** the current Spring Boot backend. This package therefore cannot safely modify `AnalysisService`, `AnalysisController`, `CustomerRepository`, or secure-storage code. **It is not end-to-end connected yet.**

## Protected request contract

Spring Boot receives authenticated Android `POST /api/analysis/documents/{id}` and its current `AnalysisRequest(imageSha256, ocrJson, referenceDocumentId)` unchanged. Spring then must:

1. Load the `document` row by `{id}` and retrieve its `customer_id` and document-type code. Never trust a client-supplied customer ID here.
2. Load that customer from `customer` DB using the document's actual `customer_id`; obtain `customerId`, `nic`, and `fullName`.
3. Use the already-integrated integrity module to load and hash-verify the unmodified image bytes. Compare the image SHA-256 with the submitted digest. Verify the stored capture-quality gate PASSED.
4. Parse the OCR JSON from `AnalysisRequest.ocrJson`; preserve the full image dimensions, bounding boxes and `coordinateSpace`.
5. Build the Python request using `Component4RequestFactory.build(...)` included here, supplying verified capture-quality fields: `status: PASSED`, `blurPassed: true`, `brightnessPassed: true`, `resolutionPassed: true`.
6. Optional: resolve `referenceDocumentId` only if it belongs to the **same customer and document type**, has verified integrity and approval, and is selected from server storage; call `addTrustedSameDocumentReference(...)`. This is important for visual proof-of-change comparison.
7. Send JSON **server-to-server** to `http://127.0.0.1:8001/analyze`, with header `X-Analysis-Token: <same 32+ character shared secret>` and `Content-Type: application/json`.
8. Store/report `customerCrossCheck`, `risk_details.findings`, risk score, images, `templateVerificationStatus`, and `referenceComparisonStatus` in your existing result/reports. Avoid logging base64 images, raw identifiers or OCR payloads.
9. The optional Android admin screen bundled under `android-admin-patch` displays these new fields. Copy only after aligning it with your actual app version.

**Request shape (illustrative, not real personal data)**

```json
{
  "documentType": "NIC",
  "imageBase64": "<base64 of integrity-verified image bytes>",
  "imageSha256": "<sha256 of those exact bytes>",
  "quality": {"status":"PASSED","blurPassed":true,"brightnessPassed":true,"resolutionPassed":true},
  "ocr": {
    "fullText": "NATIONAL IDENTITY CARD\nNo: 200000000001\nName: SAMPLE HOLDER",
    "imageWidth": 1044,
    "imageHeight": 633,
    "coordinateSpace": "EXIF_UPRIGHT_FULL_IMAGE",
    "lines": [
      {"text":"NATIONAL IDENTITY CARD","boundingBox":{"left":200,"top":80,"right":600,"bottom":110}},
      {"text":"No: 200000000001","boundingBox":{"left":680,"top":130,"right":920,"bottom":160}}
    ],
    "fields": {"nicNumber":"200000000001","nicCandidates":["200000000001"]}
  },
  "customerSource":"SERVER_DATABASE",
  "customerData":{"customerId":1,"nic":"200000000001","fullName":"SAMPLE HOLDER"}
}
```

The phone should NOT send `customerData`. Spring builds it from its own stored customer row. Avoid approving solely because data matches; registration data were initially typed by an agent and OCR can misread.

## Data exposure and service isolation

The internal Python service is bound to loopback and protected with a shared token; it is **not** a public Android API. Deploy over an authenticated private channel if services are on different hosts. Keep raw document images in encrypted Spring storage and avoid plaintext persistence in Component 4. 
