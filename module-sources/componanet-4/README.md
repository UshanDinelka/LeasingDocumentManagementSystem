# Component 4 — Document Alteration Screening v6

A full Python module package for the LeasingDocumentSystem project. It extends the older template-alignment pipeline and uses Android ML Kit OCR, protected Spring Boot storage/quality checks and **database-resolved** customer information.

**Important**: this is a screening engine, not a guarantee of detecting every altered/forged document. A low score is not evidence of authenticity. Admins must review issues; edited text with no visible artefacts can be impossible to detect without a trustworthy prior image of the *same* document. A name/NIC mismatch with customer registration is a **data discrepancy**, not proof that the scan is forged.

## What is included

- OCR candidates (NIC, licence number, dates, names, financial amounts, company/registration candidates); validity/candidate checks.
- Comparison of OCR NIC and labelled names against values retrieved from the registered customer **on Spring Boot**, with `MATCH`, `MISMATCH`, `POSSIBLE_MISMATCH`, `AMBIGUOUS_OCR`, and `NOT_EXTRACTED` distinctions. A mismatch raises review priority. Uncertain OCR never claims a mismatch.
- Registered-template alignment, visible editor-interface clues, edited text/background replacement, text size anomalies, photo pasted seams, edge and signature/logo region evidence, and optional trustworthy same-document region comparison.
- Possible finger/skin-like obstruction and opaque object/covering indicators overlapping important text; uncertain results always demand visual review.
- Red/orange highlighted preview, detailed findings with pixel bounding rectangles and review-priority score. No claimed probability of fraud.
- Python integration endpoint `/analyze` with quality and SHA-256 validation, private shared-token authorization and temporary working files.
- Android admin-screen **example replacement** at `integration-examples/AlterationAnalysisScreen.kt`; not wired to Spring yet.
- Complete unittest regression suite.

## All document types in the provided Android app

| Code | Status |
|---|---|
| `NIC` | Visual alignment and multiple heuristics using the supplied NIC layout |
| `NIC_COPY` | Same NIC layout; copy-specific artefacts may reduce confidence |
| `DRIVING_LICENCE` | Visual alignment and multiple heuristics using supplied layout |
| `BANK_STATEMENT` | Supplied example layout; bank-specific variants unvalidated |
| `BUSINESS_REGISTRATION` | Supplied example layout; different document editions unvalidated |
| `VEHICLE_CR_BOOK` | Supplied example layout; edition variants unvalidated |
| `SALARY_SLIP` | OCR-only/layout-free screening; **no approved visual template supplied** |
| `UTILITY_BILL` | OCR-only/layout-free screening; **no approved visual template supplied** |

Only five source JPG templates were in Component 4. We do not invent photographic bank, utility or salary layouts. Template matching cannot verify an arbitrary issuer's page design. Training/validation images from each real edition are still required before operational use.

## Windows PowerShell startup

From this `componanet-4` folder, using Python 3.12:

```powershell
py -3.12 -m venv .venv-analysis
.\.venv-analysis\Scripts\python.exe -m pip install -r requirements-integration.txt
$env:ALTERATION_API_TOKEN = "<the existing Spring/Component4 shared secret: at least 32 chars>"
.\.venv-analysis\Scripts\python.exe -m uvicorn backend.integrated_api:app --host 127.0.0.1 --port 8001
```

Or run `powershell -ExecutionPolicy Bypass -File .\Run-Component4.ps1` after setting the token. `/health` is also protected by that token:

```powershell
$h = @{ "X-Analysis-Token" = $env:ALTERATION_API_TOKEN }
Invoke-RestMethod -Uri http://127.0.0.1:8001/health -Headers $h
```

Keep your existing Spring shared token; do not paste a new unrelated token into only one service. `backend.main:app` now exports the same protected API. The old public/unverified multipart endpoint is intentionally not enabled because it would bypass the secure submission process.

## Request / response and customer cross-check

Spring continues to send the v5 `/analyze` payload fields and may now additionally supply:

```json
{
  "customerSource":"SERVER_DATABASE",
  "customerData":{"customerId":1,"nic":"200000000001","fullName":"SAMPLE HOLDER"}
}
```

**Important:** `customerData` is selected by Spring using the document's actual `customer_id`; do not let Android send its own expected NIC/name. The Python API checks the marker and uses the same private service token, but **cannot independently prove Spring actually queried the database**. Additional optional server-approved same-document fields remain `referenceSource`, `referenceImageBase64`, `referenceImageSha256`.

The report retains existing v5 response keys, now with `algorithmVersion: document-analysis-6`, `detectorBuild`, `customerCrossCheck`, `customerVerificationStatus`, `templateVerificationStatus`, `proposal_4_4_3`, `risk_details.findings`, `normalizedImageBase64`, `highlightedImageBase64` and `verificationStatus`. `customerCrossCheck.fields.nic.status` can be `MATCH`, `MISMATCH`, `NOT_EXTRACTED`, `AMBIGUOUS_OCR`, or `REGISTERED_VALUE_INVALID`. `customerCrossCheck.fields.fullName.status` can be `MATCH`, `POSSIBLE_MISMATCH`, `NOT_EXTRACTED`, `AMBIGUOUS_OCR`, or `REVIEW_REQUIRED`.

If the registered NIC and document-extracted NIC disagree in a clear OCR reading, the report generates a `CUSTOMER_NIC_MISMATCH` and high review priority. Do not auto-reject customers: the registered value itself might be wrong. A photograph cannot be identity-verified merely by comparing to another person's generic template.

Read `integration-examples/README_Spring.md` for the Spring integration steps. **The current Spring project was not uploaded, so the backend connection is not implemented or claimed tested.**

## Test

```powershell
.\.venv-analysis\Scripts\python.exe -m unittest discover -s tests -v
```

The 17 original and additional tests include all five visual-layout templates, two layout-free document types, NIC-copy, a synthetic finger-like covering, name and ID substitutions, image-editor OCR, an opaque covering, pasted-photo seam, same-document reference, and NIC match/mismatch/ambiguity scenarios. They are synthetic/controlled tests, not real-world measured sensitivity or specificity. Additional hand/skin appearances, camera reflections, seamless digital compositing, multilingual OCR and unknown document editions can be missed.

## Files and safety

`src` contains all source detectors, `config` has document profiles/ROI regions, `data/templates` has five sample template images, `backend/integrated_api.py` is the protected service, `tests` has regressions. The sample images under `data/genuine` and `data/altered` were carried over from the previous upload; treat them as potentially containing personal information. Do not publish them or database customer details in a public Git repository. No pre-trained hand/object neural model is bundled; occlusion is a conservative CV heuristic that must be validated with real examples.
