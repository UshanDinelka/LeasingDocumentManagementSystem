# Component 4 diagnostics-ready source package (v6.1 tooling)

The detection algorithm remains **document-analysis-6**. This package adds:
- `Diagnose-Component4.ps1` (read-only configuration, template, dependency, authentication and HTTP health checks)
- `Smoke-Test-Component4.py` (known-good synthetic-registered-customer request directly to Python, so you can distinguish Python failures from Spring Boot failures)
- Traceback logging with a random incident ID for unexpected Python `/analyze` exceptions, without returning image/OCR content in the HTTP error
- Explicit algorithm version in `/health` response, plus endpoint-contract regression tests

**This DOES NOT claim to have fixed the Spring Boot integration.** The latest Spring Boot files were not part of the upload. The message on the Android screen could be an error created in Spring or from a different installed Android build. Keep currently working source backed up.

## Windows test sequence

1. Keep Spring Boot running on 8080 in its normal window.
2. Open a **new PowerShell window** at this Component 4 directory. Set `$env:ALTERATION_API_TOKEN` to the exact shared secret Spring Boot uses. Do not paste that value in chats or screenshots.
3. Start Python in this PowerShell window: `powershell -ExecutionPolicy Bypass -File .\Run-Component4.ps1`
4. In a **second PowerShell window**, set the same environment variable there, then run `powershell -ExecutionPolicy Bypass -File .\Diagnose-Component4.ps1`
5. If `/health` succeeds, run `& .\.venv-analysis\Scripts\python.exe .\Smoke-Test-Component4.py`
6. If step 5 succeeds, Python is reachable and accepts a valid NIC request. Click **Run OCR and analysis** on Document 3 in Android while watching both the Spring console and Python console. Record status codes and sanitized exception lines (NO customer data or shared tokens).
7. Supply the current `leasing-document-backend.zip` for an exact Spring fix. Do not modify Spring without its current source.

## Common results

- **Cannot connect:** Python process not running, host/port mismatch, or Spring and Python are in different machines/containers. `127.0.0.1` is only appropriate when both services run on the same computer.
- **HTTP 401:** the token supplied to Python differs from the one its process loaded at startup. The Spring token must also match.
- **HTTP 422:** request validation failed. Check document-type code, OCR bounding boxes, `coordinateSpace`, quality flags, SHA-256, and the optional customer-data shape. Correct the Spring request or OCR, rather than weakening the Python validations.
- **HTTP 409:** Python is already analyzing another document; retry after completion.
- **HTTP 502 + diagnostic ID:** look up that ID in the Python Uvicorn console; the Python exception traceback identifies the module/line involved.
- **Direct Python smoke test succeeds but Android analysis fails:** request-building/persistence/error-handling in Spring or a stale installed Android build is the likely next place to investigate. Not proof without backend log.

**Security:** Do not put shared tokens or actual customer records into test screenshots; do not publicly commit the old `results` or `.venv-analysis` folder.
