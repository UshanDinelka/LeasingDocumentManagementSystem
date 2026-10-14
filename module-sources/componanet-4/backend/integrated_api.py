"""Private Spring Boot adapter. All visual detectors remain in the existing src/ files.
Run from Component 4 root: python -m uvicorn backend.integrated_api:app --host 127.0.0.1 --port 8001
"""
import base64
import hashlib
import hmac
import io
import logging
import uuid
import json
from pathlib import Path
import tempfile
import threading

from fastapi import FastAPI, HTTPException, Request
from starlette.concurrency import run_in_threadpool
from PIL import Image, ImageOps
from src.analysis_pipeline import run_analysis_pipeline, TEMPLATE_FILES
from src.ocr_validation import validate_ocr_payload
from src.analysis_preflight import check_document_text
from src.customer_crosscheck import compare_customer
from src.forensic_analysis import aggregate_risk, annotate
from src.document_profiles import PROFILES
from src.layout_free_pipeline import run_layout_free
from src.document_fields import enrich_ocr
import numpy as np
VERSION = "document-analysis-6"
_LOGGER = logging.getLogger("component4.api")

app = FastAPI(title="Integrated Component 4", version=VERSION)
_GATE = threading.Lock()
MAX_BODY = 56 * 1024 * 1024
MAX_IMAGE = 20 * 1024 * 1024
Image.MAX_IMAGE_PIXELS = 12_000_000


@app.get("/health")
def health():
    return {"status": "OK", "algorithmVersion": VERSION, "supportedTypes": sorted(set(TEMPLATE_FILES) | {"SALARY_SLIP", "UTILITY_BILL"}),
            "approvedTemplateTypes": sorted(TEMPLATE_FILES),
            "ocrOnlyTypes": ["SALARY_SLIP", "UTILITY_BILL"],
            "templatesPresent": all(Path(p).is_file() for p in TEMPLATE_FILES.values())}


def analyse_payload(payload):
    kind = payload.get("documentType")
    if kind not in (set(TEMPLATE_FILES) | {"SALARY_SLIP", "UTILITY_BILL"}):
        raise HTTPException(422, "Unsupported document type")
    customer = payload.get("customerData")
    if customer is not None:
        if payload.get("customerSource") != "SERVER_DATABASE" or not isinstance(customer,dict):
            raise HTTPException(422, "Customer details must be resolved from server database")
        if not isinstance(customer.get("customerId"),int) or customer["customerId"] <= 0:
            raise HTTPException(422, "Invalid database customerId")
        for field in ("nic", "fullName"):
            if not isinstance(customer.get(field), str) or len(customer[field]) > 255:
                raise HTTPException(422, "Invalid registered customer field")
    quality = payload.get("quality", {})
    if quality.get("status") != "PASSED" or not all(
            quality.get(k) is True for k in ("blurPassed", "brightnessPassed", "resolutionPassed")):
        raise HTTPException(422, "All capture quality checks must have passed")
    try:
        raw = base64.b64decode(payload["imageBase64"], validate=True)
    except (KeyError, ValueError, TypeError):
        raise HTTPException(422, "Invalid image encoding")
    if not raw or len(raw) > MAX_IMAGE:
        raise HTTPException(413, "Image size exceeds secure capture limit")
    digest = hashlib.sha256(raw).hexdigest()
    if not hmac.compare_digest(digest, str(payload.get("imageSha256", "")).lower()):
        raise HTTPException(422, "Verified image hash does not match")
    # Reference images must be selected by the authenticated Spring service
    # from a server-controlled trusted source, NOT supplied by an agent.
    reference_raw = None
    if "referenceImageBase64" in payload:
        if kind in ("SALARY_SLIP", "UTILITY_BILL"):
            raise HTTPException(422, "Trusted reference comparison requires an approved layout profile")
        if payload.get("referenceSource") != "SERVER_VERIFIED_SAME_DOCUMENT":
            raise HTTPException(422, "Only a server-verified same-document reference is acceptable")
        try:
            reference_raw = base64.b64decode(payload["referenceImageBase64"], validate=True)
        except (ValueError, TypeError):
            raise HTTPException(422, "Invalid trusted reference encoding")
        if not reference_raw or len(reference_raw) > MAX_IMAGE:
            raise HTTPException(413, "Trusted reference size exceeds limit")
        ref_digest = hashlib.sha256(reference_raw).hexdigest()
        if not hmac.compare_digest(ref_digest, str(payload.get("referenceImageSha256", "")).lower()):
            raise HTTPException(422, "Trusted reference digest mismatch")
    ocr = payload.get("ocr")
    if not isinstance(ocr, dict) or not isinstance(ocr.get("fullText"), str):
        raise HTTPException(422, "Invalid OCR object")
    if not isinstance(ocr.get("lines"), list) or len(ocr["lines"]) > 5000:
        raise HTTPException(422, "Invalid OCR lines")
    if not isinstance(ocr.get("fields"), dict):
        raise HTTPException(422, "Invalid OCR fields")
    if len(json.dumps(ocr)) > 1_000_000:
        raise HTTPException(413, "OCR payload exceeds limit")
    with tempfile.TemporaryDirectory(prefix="leasing-analysis-") as tmp:
        work = Path(tmp)
        # Original bytes are used for hash checks; only a derived working image is oriented.
        with Image.open(io.BytesIO(raw)) as original:
            if original.width * original.height > 12_000_000:
                raise HTTPException(413, "Image pixel count exceeds capture limit")
            with ImageOps.exif_transpose(original) as oriented:
                if ocr.get("imageWidth") != oriented.width or ocr.get("imageHeight") != oriented.height:
                    raise HTTPException(422, "OCR coordinate dimensions do not match the upright image")
                if ocr.get("coordinateSpace") != "EXIF_UPRIGHT_FULL_IMAGE":
                    raise HTTPException(422, "OCR coordinate space mismatch")
                image_path = work / "upright.png"
                oriented.convert("RGB").save(image_path)
        trusted_reference_path = None
        if reference_raw is not None:
            with Image.open(io.BytesIO(reference_raw)) as ref_image:
                if ref_image.width * ref_image.height > 12_000_000:
                    raise HTTPException(413, "Trusted reference pixel count too high")
                trusted_reference_path = work / "trusted_reference.png"
                ImageOps.exif_transpose(ref_image).convert("RGB").save(trusted_reference_path)
        ocr = enrich_ocr(ocr, kind)
        validation = validate_ocr_payload(ocr, kind, image_path)
        output = work / "analysis"
        if kind in ("SALARY_SLIP", "UTILITY_BILL"):
            issue = None if len(ocr.get('fullText','').strip()) >= 8 else ("OCR_INCOMPLETE", "Readable document text is required")
        else:
            issue = check_document_text(ocr, kind)
        invalid_boxes = any(i.get("code", "").startswith(("INVALID_BOUNDING", "INCOMPLETE_BOUNDING", "BOUNDING_BOX_OUTSIDE"))
                            for i in validation.get("issues", []))
        if issue or invalid_boxes:
            result = {"status": "INCOMPLETE", "reasonCode": issue[0] if issue else "OCR_COORDINATES_INVALID",
                      "message": issue[1] if issue else "OCR coordinates are invalid. Run OCR again on the original image.",
                      "risk_score": None, "risk_level": None, "suspicious_area_count": 0,
                      "recommended_action": "MANUAL_REVIEW_REQUIRED"}
        else:
            result = run_analysis_pipeline(image_path, kind, output, ocr_validation=validation,
                                           ocr=ocr, trusted_reference_path=trusted_reference_path)
        if result.get("status") not in ("COMPLETE", "INCOMPLETE"):
            raise HTTPException(502, "Analysis pipeline did not complete")
        # Customer expectations come ONLY from the authenticated Spring service.
        # Compare to OCR on the submitted document, not to an unrelated template.
        check = compare_customer(ocr, customer, kind)
        result["customerCrossCheck"] = check
        result["customerVerificationStatus"] = check["status"]
        if result.get("status") == "COMPLETE" and check.get("discrepancies"):
            from src.forensic_analysis import _finding
            from src.document_profiles import ROOT
            from src.forensic_analysis import annotate
            with Image.open(output / "normalized" / "document.png") as image:
                arr = np.asarray(image.convert("RGB"))
            h,w=arr.shape[:2]
            region_conf = json.loads((ROOT / "config" / "document_regions.json").read_text())
            rects = region_conf.get(kind,{}).get('regions',{})
            extra=[]
            for discrepancy in check['discrepancies']:
                field_name=discrepancy['field']
                r = rects.get('nic_number' if field_name=='nic' else 'name')
                if r:
                    box=(int(r['x1']*w),int(r['y1']*h),int(r['x2']*w),int(r['y2']*h))
                else:
                    # Not localized: only the extracted text is inconsistent.
                    box=(0,0,0,0)
                extra.append(_finding(discrepancy['code'],"customer_crosscheck",field_name,
                    box, 86 if field_name=='nic' else 57, discrepancy['reason'],
                    'REGISTERED_CUSTOMER_VS_OCR'))
            prior=result['risk_details']
            merged=aggregate_risk(prior['findings']+extra,result.get('referenceComparisonStatus','NOT_PROVIDED'))
            merged['detector_coverage']['active'].append('registered_customer_consistency')
            result['risk_details']=merged
            result['risk_score']=merged['risk_score']; result['risk_level']=merged['risk_level']
            result['suspicious_area_count']=merged['finding_count']
            result['altered_text_localization']['suspicious_areas']=merged['findings']
            result['altered_text_localization']['suspicious_area_count']=merged['finding_count']
            annotate(arr,merged['findings'],output/'highlighted_suspicious_areas.png')
        elif result.get("status") == "COMPLETE":
            result['risk_details']['detector_coverage']['active'].append('registered_customer_consistency' if customer is not None else 'no_registered_customer_context')
        result["detectorBuild"] = "proposal-4.4.3-all-eight-v2"
        result["templateVerificationStatus"] = result.get('templateVerificationStatus',
            'APPROVED_LAYOUT_MATCHED' if result.get('status')=='COMPLETE' else 'NOT_VERIFIED')
        result["verificationStatus"] = ('REFERENCE_COMPARED' if result.get('referenceComparisonStatus')=='ASSESSED'
            else 'NOT_VERIFIED_NO_SAME_DOCUMENT_REFERENCE')
        result["same_document_verification"] = {
            'status': result.get('referenceComparisonStatus','NOT_PROVIDED'),
            'note':'No reference was verified as the same document by Component 4; provenance must be established by Spring Boot.'
        }
        result["referenceProvenanceNotice"] = ('Trusted-reference comparison requires Spring Boot to verify the stored reference belongs to the same customer and document.'
            if reference_raw is not None else 'No verified same-document reference was supplied.')
        findings = result.get('risk_details',{}).get('findings',[])
        sections = {
            'ocr_analysis': ['ocr','customer_crosscheck'],
            'text_analysis': ['altered_text'],
            'altered_text_detection':['altered_text','background'],
            'background_analysis':['background','fixed_artwork'],
            'symbol_analysis':['signature_logo'],
            'photo_analysis':['photo'],
            'edge_analysis':['physical_obstruction'],
            'physical_obstruction':['physical_obstruction'],
        }
        visual_ok = result.get('templateVerificationStatus')=='APPROVED_LAYOUT_MATCHED'
        checks = {}
        for label, components in sections.items():
            enabled = result['status']=='COMPLETE' and (visual_ok or label=='ocr_analysis')
            checks[label] = {'status':'ASSESSED' if enabled else 'LIMITED_OR_NOT_ASSESSED',
                'finding_count':len([f for f in findings if f.get('component') in components]),
                'limitations': ([] if enabled else ['No approved registered document layout or analysis incomplete.'])}
        result['proposal_4_4_3']={'checks':checks,
            'risk_scoring':{'status':'ASSESSED' if result['status']=='COMPLETE' else 'NOT_ASSESSED',
                            'note':'Review-priority score, not probability of forgery.'}}
        if any(f.get('code')=='VISIBLE_EDITOR_ARTIFACTS' for f in findings):
            result['captureContextWarning']='POSSIBLE_DOCUMENT_EDITOR_SCREEN_IN_CAPTURE'
        result["imageSha256"] = digest
        result["ocr"] = ocr
        result["ocr_validation"] = validation
        result["algorithmVersion"] = VERSION
        result["screeningOnly"] = True
        result["referenceSupplied"] = reference_raw is not None
        result["recommended_action"] = "MANUAL_REVIEW_REQUIRED"
        if "risk_details" in result:
            result["risk_details"]["recommended_action"] = "MANUAL_REVIEW_REQUIRED"
        if result["status"] == "COMPLETE":
            crop_path = output / "preprocessing" / "cropped_document.png"
            if crop_path.is_file():
                result["croppedImageBase64"] = base64.b64encode(crop_path.read_bytes()).decode("ascii")
            result["highlightedImageBase64"] = base64.b64encode(
                (output / "highlighted_suspicious_areas.png").read_bytes()).decode("ascii")
            result["normalizedImageBase64"] = base64.b64encode(
                (output / "normalized" / "document.png").read_bytes()).decode("ascii")
            if result.get("referenceComparisonStatus") == "ASSESSED":
                result["message"] = ("Multiple image screening detectors executed. Holder fields were compared "
                    "to a server-supplied same-document reference. Differences require administrator review.")
            else:
                result["message"] = ("Single-image alteration screening and registered-customer consistency checks completed. "
                    "Unverified or edited values may be undetectable without a trusted same-document image. "
                    "A mismatch is a review signal, not proof of forgery; LOW does not establish authenticity.")
        else:
            # An incomplete analysis has an unmodified preview, no highlights and no risk score.
            with Image.open(image_path) as preview:
                preview.thumbnail((1600, 1600), Image.Resampling.LANCZOS)
                buffer = io.BytesIO()
                preview.save(buffer, format="PNG")
                result["originalImageBase64"] = base64.b64encode(buffer.getvalue()).decode("ascii")
        # Missing OCR fields require review; they are not evidence of alteration.
        # Remove temporary filesystem paths from nested diagnostic metadata.
        def sanitize(value):
            if isinstance(value, dict):
                return {k: sanitize(v) for k, v in value.items()}
            if isinstance(value, list):
                return [sanitize(v) for v in value]
            if isinstance(value, str) and str(work) in value:
                return Path(value).name
            return value
        return sanitize(result)


@app.post("/analyze")
async def analyze(request: Request):
    body = bytearray()
    async for chunk in request.stream():
        body.extend(chunk)
        if len(body) > MAX_BODY:
            raise HTTPException(413, "Request exceeds limit")
    try:
        payload = json.loads(body)
    except (ValueError, UnicodeError):
        raise HTTPException(400, "Invalid JSON")
    if not isinstance(payload, dict):
        raise HTTPException(400, "Expected JSON object")
    if not _GATE.acquire(blocking=False):
        raise HTTPException(409, "Another document is being analysed; retry after it finishes")
    try:
        return await run_in_threadpool(analyse_payload, payload)
    except HTTPException:
        raise
    except Exception:
        # Keep raw customer data/image bytes out of the HTTP response.
        # The diagnostic identifier lets administrators find the exact traceback
        # in the Python service console without exposing the uploaded document.
        diagnostic_id = uuid.uuid4().hex[:12]
        _LOGGER.exception("Component 4 unexpected analysis error [%s]", diagnostic_id)
        raise HTTPException(502, f"Component 4 internal error [{diagnostic_id}]; inspect Python service log")
    finally:
        _GATE.release()
