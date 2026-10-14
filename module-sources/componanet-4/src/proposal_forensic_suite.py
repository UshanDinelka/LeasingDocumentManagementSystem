"""Eight auditable screening requirements from proposal §4.4.3.

A document *layout* is never treated as a trusted holder-specific original.
Signals from one photographed image are heuristic. Reference-backed differences
are considered only after the calling Spring service has authorized an earlier,
admin-approved image of that *same* customer/document type.

No score in this module is a probability of fraud or a declaration of authenticity.
"""
from __future__ import annotations

from datetime import datetime
from pathlib import Path
from collections import defaultdict
import re
import cv2
import numpy as np
from PIL import Image

from src.ocr_validation import parse_date_candidate

BUILD = "proposal-4.4.3-all-eight-v1"
CATEGORIES = (
    "ocr_analysis", "text_analysis", "altered_text_detection",
    "background_analysis", "symbol_analysis", "photo_analysis", "edge_analysis",
)


def _box(x1, y1, x2, y2, w, h):
    a, b, c, d = (int(round(v)) for v in (x1, y1, x2, y2))
    return max(0, min(w-1, a)), max(0, min(h-1, b)), max(1, min(w, c)), max(1, min(h, d))


def _finding(kind, box, score, message, region="full_document", evidence="HEURISTIC"):
    x1, y1, x2, y2 = box
    return {"finding_type": kind, "region": region, "reason": message,
            "score": round(float(np.clip(score, 0, 100)), 2),
            "x1": int(x1), "y1": int(y1), "x2": int(x2), "y2": int(y2),
            "evidence_level": evidence}


def _section(method, status="ASSESSED", findings=None, details=None, limitations=None):
    f = findings or []
    return {"status": status, "method": method, "finding_count": len(f),
            "findings": f, "details": details or {}, "limitations": limitations or []}


def _bbox_from_ml(line, width, height):
    box = line.get("boundingBox") if isinstance(line, dict) else None
    if not isinstance(box, dict):
        return None
    try:
        x1, y1, x2, y2 = (float(box[k]) for k in ("left", "top", "right", "bottom"))
    except (TypeError, ValueError, KeyError):
        return None
    if not all(np.isfinite([x1, y1, x2, y2])) or x2 <= x1 or y2 <= y1:
        return None
    if x1 < -4 or y1 < -4 or x2 > width + 4 or y2 > height + 4:
        return None
    return _box(x1, y1, x2, y2, width, height)


def _ocr(submitted_ocr, validation, w, h):
    ocr = submitted_ocr or {}
    fields = ocr.get("fields") if isinstance(ocr.get("fields"), dict) else {}
    dates = fields.get("dates") if isinstance(fields.get("dates"), list) else []
    amounts = fields.get("amountCandidates") or fields.get("amounts") or []
    identifiers = {k: v for k, v in fields.items() if any(
        term in k.lower() for term in ("nicnumber", "licencenumber", "companynumber", "accountnumber", "registrationnumber"))}
    candidates = []
    for line in ocr.get("lines", [])[:5000]:
        text = line.get("text", "") if isinstance(line, dict) else ""
        if isinstance(text, str):
            candidates.extend(re.findall(r"\b(?:\d{4}[-/]\d{1,2}[-/]\d{1,2}|\d{1,2}[-/]\d{1,2}[-/]\d{4})\b", text))
    dates = list(dict.fromkeys(str(x) for x in dates + candidates))[:50]
    invalid = [d for d in dates if parse_date_candidate(d) is None]
    warnings = validation.get("issues", []) if isinstance(validation, dict) else []
    warnings_as_findings = []
    for d in invalid[:10]:
        warnings_as_findings.append({
            "finding_type": "OCR_INVALID_DATE_CANDIDATE", "region": "ocr_fields",
            "reason": "A recognized date candidate is not a valid calendar date; verify OCR and the physical document.",
            "value": d, "score": 35.0, "evidence_level": "OCR_FORMAT_WARNING"})
    return _section("ML Kit OCR field parsing and format consistency", findings=warnings_as_findings, details={
        "extracted_identifiers": identifiers,
        "extracted_dates": dates,
        "extracted_amounts": amounts,
        "possible_name": fields.get("possibleName"),
        "invalid_date_candidates": invalid,
        "ocr_issue_count": len(warnings),
        "ocr_warning_codes": [x.get("code", "UNKNOWN") for x in warnings[:50] if isinstance(x, dict)],
        "ocr_lines_with_valid_geometry": sum(_bbox_from_ml(x,w,h) is not None for x in ocr.get("lines",[]) if isinstance(x,dict)),
    }, limitations=["OCR text/format cannot by itself establish that a printed value is genuine.",
                    "Android ML Kit Latin OCR is not a reliable Sinhala/Tamil text recognizer."])


def _text_geometry(ocr, w, h):
    lines = []
    for line in (ocr or {}).get("lines", [])[:5000]:
        if not isinstance(line, dict):
            continue
        b = _bbox_from_ml(line, w, h)
        text = line.get("text")
        if b and isinstance(text, str) and len(text.strip()) >= 3:
            lines.append((b, text.strip()))
    if len(lines) < 5:
        return _section("OCR typography proxy: height, spacing, alignment", "INCONCLUSIVE",
                        details={"valid_lines":len(lines)}, limitations=["At least five spatially valid OCR lines required."])
    sizes = np.asarray([b[3]-b[1] for b, t in lines], np.float32)
    font_height = float(np.median(sizes))
    lefts = np.asarray([b[0] for b, t in lines], np.float32)
    results = []
    # A single tall label among unrelated document headings is normal. Report
    # outliers only as weak warnings, not evidence of alteration by itself.
    for (box, text), size in zip(lines, sizes):
        if len(text) < 4 or font_height <= 0:
            continue
        is_outlier = size > 2.9*font_height or size < .30*font_height
        if is_outlier:
            results.append(_finding("OCR_TEXT_SIZE_OUTLIER", box, 20,
                "Unusual OCR line height; inspect document typography and capture perspective.",
                evidence="WEAK_HEURISTIC"))
    results = sorted(results, key=lambda x:x["score"], reverse=True)[:12]
    return _section("OCR line geometry: height, spacing and alignment proxies", findings=results,
        details={"valid_lines":len(lines),"median_line_height":round(font_height,2),
                 "line_height_iqr": [float(np.quantile(sizes,.25)),float(np.quantile(sizes,.75))],
                 "median_left_offset":float(np.median(lefts))},
        limitations=["Font family cannot be determined reliably from a camera image.",
                     "OCR geometry warnings are not sufficient for a HIGH risk result."])


def _value_from_reference(same_document, kind):
    if not same_document or same_document.get("status") != "ASSESSED":
        return []
    return [f for f in same_document.get("findings", [])
            if f.get("finding_type") in kind]


def _overlap(f, roi):
    return max(0, min(f["x2"], roi["x2"])-max(f["x1"],roi["x1"])) * max(
        0, min(f["y2"],roi["y2"])-max(f["y1"],roi["y1"]))


def _category_reference_matches(same_document, metadata, regions, category):
    if not same_document or same_document.get("status") != "ASSESSED":
        return []
    hits = []
    for candidate in same_document.get("findings", []):
        if not all(k in candidate for k in ("x1","y1","x2","y2")):
            continue
        bw = max(1,(candidate["x2"]-candidate["x1"])*(candidate["y2"]-candidate["y1"]))
        for name, region in metadata.items():
            if region["type"] in regions and _overlap(candidate,region) / bw >= .2:
                hits.append({"region":name,"reference_finding_type":candidate.get("finding_type"),
                             "score":candidate.get("score",0),"description":category,
                             "x1":candidate["x1"],"y1":candidate["y1"],
                             "x2":candidate["x2"],"y2":candidate["y2"],
                             "finding_type":"REFERENCE_REGION_DISCREPANCY",
                             "reason":category,"evidence_level":"SAME_DOCUMENT_REFERENCE"})
                break
    return hits[:50]


def _local_edge_seams(rgb, metadata):
    """Candidate straight pasted edges, weak without corroboration.

    Excludes outer card border and excludes a long straight border on its own:
    real cards, their photos and printed forms naturally contain straight lines.
    """
    h, w = rgb.shape[:2]
    gray = cv2.cvtColor(rgb, cv2.COLOR_RGB2GRAY)
    edges = cv2.Canny(gray, 70, 175)
    lines = cv2.HoughLinesP(edges, 1, np.pi/180, threshold=max(35,int(w*.05)),
                            minLineLength=max(24,int(w*.045)),maxLineGap=4)
    detected=[]
    if lines is not None:
        for item in lines[:,0,:]:
            x1,y1,x2,y2=map(int,item)
            if min(x1,x2)<.03*w or max(x1,x2)>.97*w or min(y1,y2)<.04*h or max(y1,y2)>.96*h:
                continue
            if abs(x2-x1)>4 and abs(y2-y1)>4:
                continue
            detected.append((x1,y1,x2,y2))
    # A real pasted rectangle is a set of consistent edges, not a line on its own.
    rectangles=[]
    for name,roi in metadata.items():
        count=sum(1 for line in detected if _overlap(
            {"x1": min(line[0],line[2])-1,"y1":min(line[1],line[3])-1,
             "x2":max(line[0],line[2])+2,"y2":max(line[1],line[3])+2},roi)>0)
        if count >= 3:
            rectangles.append({"region":name,"straight_edge_candidates":count})
    return detected[:200],rectangles


def _region_image_stats(rgb, roi):
    arr=rgb[roi["y1"]:roi["y2"],roi["x1"]:roi["x2"]]
    if arr.size == 0:
        return None
    g=cv2.cvtColor(arr,cv2.COLOR_RGB2GRAY)
    return {"sharpness_laplacian_var":round(float(cv2.Laplacian(g,cv2.CV_32F).var()),2),
            "mean_brightness":round(float(g.mean()),2),
            "edge_pixel_fraction":round(float(np.mean(cv2.Canny(g,60,150)>0)),4)}


def _unique_evidence_candidates(findings,w,h):
    """Group same-pixel evidence to avoid artificially multiplying one red box."""
    relevant=[]
    for f in findings:
        if not isinstance(f,dict) or not all(k in f for k in ("x1","y1","x2","y2","score")):
            continue
        x1,y1,x2,y2=_box(f["x1"],f["y1"],f["x2"],f["y2"],w,h)
        if x2 <= x1 or y2 <= y1: continue
        c=dict(f,x1=x1,y1=y1,x2=x2,y2=y2)
        if float(c.get("score",0))<30: continue
        relevant.append(c)
    relevant.sort(key=lambda f:float(f["score"]),reverse=True)
    clusters=[]
    for finding in relevant[:150]:
        first=None
        for cluster in clusters:
            r=cluster["box"]
            ix=max(0,min(finding["x2"],r[2])-max(finding["x1"],r[0]))
            iy=max(0,min(finding["y2"],r[3])-max(finding["y1"],r[1]))
            overlap=ix*iy
            a=max(1,(finding["x2"]-finding["x1"])*(finding["y2"]-finding["y1"]))
            b=max(1,(r[2]-r[0])*(r[3]-r[1]))
            if overlap/max(1,min(a,b))>.50:
                first=cluster;break
        if first is None:
            clusters.append({"box":[finding[k] for k in ("x1","y1","x2","y2")],
                             "evidence":[finding],"score":float(finding["score"])})
        else:
            first["evidence"].append(finding)
            first["score"]=max(first["score"],float(finding["score"]))
    return clusters


def _combine(clusters, sections, reference_assessed, ocr_validation):
    # Nominal prioritization index, never an estimate of P(fraud).
    scores=sorted((c["score"] for c in clusters),reverse=True)
    main=scores[0] if scores else 0.
    extra=min(10.,sum(max(0.,s-35)*.035 for s in scores[1:]))
    score=round(float(min(100.,main+extra)),2)
    level="HIGH" if score>=65 else "MEDIUM" if score>=35 else "LOW"
    low_confidence=["OCR_TEXT_SIZE_OUTLIER", "SINGLE_STRAIGHT_EDGE"]
    categories={key:sec["status"] for key,sec in sections.items()}
    coverage={"active": [key for key,value in categories.items() if value=="ASSESSED"],
              "partially_assessed": [key for key,value in categories.items() if value=="INCONCLUSIVE"],
              "not_assessed": [key for key,value in categories.items() if value=="NOT_ASSESSED"]}
    if not reference_assessed:
        coverage["not_assessed"].extend(["actual_identity_field_values", "same_document_photo_replacement",
                                         "signature_identity_verification", "reference_source_provenance"])
    return {"risk_score":score,"risk_level":level,"component_scores":{
                key:max((x.get("score",0) for x in sec.get("findings",[])),default=0)
                for key,sec in sections.items()},
            "detector_coverage": coverage,
            "identity_verification_status": "REFERENCE_COMPARED" if reference_assessed else "NOT_VERIFIED_NO_SAME_DOCUMENT_REFERENCE",
            "risk_category":"SCREENING_EVIDENCE_NOT_AUTHENTICITY",
            "recommended_action":"MANUAL_REVIEW_REQUIRED",
            "thresholds":{"low":"0 to <35","medium":"35 to <65","high":"65 to 100"},
            "scoring_method":"Highest independent region plus capped extra independent regions; overlapping findings clustered",
            "finding_count":sum(len(c["evidence"]) for c in clusters),
            "cluster_count":len(clusters),
            "findings":[f for c in clusters for f in c["evidence"]],
            "capture_obstruction_detected":any(f.get("finding_type")=="POSSIBLE_CAPTURE_OBSTRUCTION" for c in clusters for f in c["evidence"]),
            "decision_note":"Screening priority only. LOW does not establish authenticity. Approval requires an authorized reference and manual review.",
            "authenticity_verdict":"NOT_ESTABLISHED"}


def analyze_proposal_requirements(document_path, metadata, submitted_ocr=None,
                                  ocr_validation=None, same_document=None,
                                  base_findings=None, reference_image_path=None):
    """Run all seven evidence checks and aggregate the eighth (risk scoring).

    Coordinates are always pixels in the normalized/registered document image.
    This function returns JSON-serializable objects under existing API keys.
    """
    with Image.open(document_path) as handle:
        rgb=np.asarray(handle.convert("RGB"))
    h,w=rgb.shape[:2]
    assessed = bool(same_document and same_document.get("status")=="ASSESSED")
    sections={}
    sections["ocr_analysis"]=_ocr(submitted_ocr,ocr_validation,w,h)
    sections["text_analysis"]=_text_geometry(submitted_ocr,w,h)
    preexisting=base_findings or []
    overlays=[f for f in preexisting if f.get("finding_type")=="POSSIBLE_OPAQUE_TEXT_OVERLAY"]
    text_ref=_category_reference_matches(same_document,metadata,{"text"},"Possible field change compared to original")
    sections["altered_text_detection"]=_section(
        "Whole-card patch detection + full-page reference comparison + OCR conflicts",
        findings=overlays + text_ref, details={"reference_text_region_changes":text_ref,
                                   "reference_comparison_performed":assessed},
        limitations=["Erased, overwritten and seamless replacements cannot always be identified from one image."])
    fixed=[f for f in preexisting if f.get("finding_type") in {
        "POSSIBLE_CAPTURE_OBSTRUCTION","FIXED_ARTWORK_DISCREPANCY","VISUAL_DISCREPANCY"}]
    bg_ref=_category_reference_matches(same_document,metadata,{"security_feature","text"},"Background change")
    sections["background_analysis"]=_section(
        "Registered fixed-artwork residual plus reference-backed pattern changes",
        findings=fixed + bg_ref, details={"reference_background_regions":bg_ref,"template_is_identity_reference":False},
        limitations=["Printed security patterns vary with lighting and document edition."])
    symbol_rois={k:v for k,v in metadata.items() if v["type"] in {"logo","security_feature","signature"}}
    symbol_hits=_category_reference_matches(same_document,metadata,{"logo","security_feature","signature"},"Official symbol or signature change")
    sections["symbol_analysis"]=_section(
        "Official marks checked against template; signatures only against authorized same-document original",
        status="ASSESSED" if assessed else "INCONCLUSIVE", findings=symbol_hits, details={
            "reference_symbol_discrepancies":symbol_hits,
            "region_measurements":{k:_region_image_stats(rgb,v) for k,v in symbol_rois.items()},
            "signature_authenticity_verified":False},
        limitations=["Single capture cannot authenticate a person's signature, official seal or logo."])
    photo_rois={k:v for k,v in metadata.items() if v["type"]=="photo"}
    photo_hits=_category_reference_matches(same_document,metadata,{"photo"},"Photograph differs from reference")
    sections["photo_analysis"]=_section(
        "Photograph region and full-image difference against same-document reference",
        status="ASSESSED" if assessed else "INCONCLUSIVE",findings=photo_hits,details={
            "photo_reference_differences":photo_hits,
            "region_measurements":{k:_region_image_stats(rgb,v) for k,v in photo_rois.items()}},
        limitations=["A realistic face replacement may leave no single-image artifact; a human reviewer must inspect it."])
    straight,regions=_local_edge_seams(rgb,metadata)
    # A raw edge alone is normal on an ID. Only show corroborating candidate
    # pasted-region evidence as an edge finding, never score straight lines.
    edge_hits = [dict(f) for f in overlays if any(
        _overlap(f, roi)>0 for roi in metadata.values())]
    sections["edge_analysis"]=_section(
        "Canny/Hough seam proposals cross-checked with localized visual differences",
        findings=edge_hits,
        details={"interior_straight_edges":len(straight),"regions_with_multiple_lines":regions},
        limitations=["Straight printed lines and photo borders are normal; edges alone are not scored as tampering."])
    # General full-page comparison findings count ONCE, regardless of the many
    # category descriptions they may support.
    independent = list(preexisting)
    # OCR calendar/format warnings contribute only a limited review signal;
    # they do not establish that an image was altered. They lack image coords
    # so they never create a misleading red box.
    format_warnings = sections["ocr_analysis"]["findings"]
    # Avoid using old non-reference ROI similarity as an identity proof.
    clusters=_unique_evidence_candidates(independent,w,h)
    risk=_combine(clusters,sections,assessed,ocr_validation)
    if format_warnings and risk["risk_score"] < 35:
        risk["risk_score"] = 35.0
        risk["risk_level"] = "MEDIUM"
        risk["decision_note"] = "OCR format warning: human check required; does not prove a digital alteration."
    return {"sections":sections,"risk_scoring":risk,
            "findings":[c["evidence"][0] for c in clusters],
            "all_evidence_count":len(independent),
            "algorithm_build":BUILD}
