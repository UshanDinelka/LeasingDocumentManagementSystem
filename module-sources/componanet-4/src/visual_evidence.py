"""Localise large discrepancies in fixed artwork and document background.

Personal fields are deliberately excluded from reference pixel comparisons:
another holder's photograph, text or signature is not evidence of an edit.
Scores describe measured discrepancies, not calibrated probabilities.
"""
import cv2
import numpy as np
from PIL import Image
from src.document_profiles import PROFILES
from src.risk_scoring import calculate_ocr_score


def analyse_visual_evidence(template_path, document_path, document_type, regions):
    with Image.open(template_path) as image:
        reference = np.asarray(image.convert("RGB"))
    with Image.open(document_path) as image:
        submitted = np.asarray(image.convert("RGB"))
    h, w = reference.shape[:2]
    if submitted.shape != reference.shape:
        raise ValueError("Visual evidence requires registered images of identical size")
    mask = np.ones((h, w), np.uint8)
    border = max(5, round(min(h, w) * .025))
    mask[:border] = mask[-border:] = 0
    mask[:, :border] = mask[:, -border:] = 0
    # Exclude every variable field, with a guard against registration jitter.
    guard = max(3, round(min(h, w) * .008))
    for region in regions.values():
        if region["type"] in {"text", "photo", "signature"}:
            x1, y1, x2, y2 = (region[k] for k in ("x1", "y1", "x2", "y2"))
            mask[max(0, y1-guard):min(h, y2+guard), max(0, x1-guard):min(w, x2+guard)] = 0
    # Fixed titles/table headings are valid comparisons even when classed as text.
    for x1, y1, x2, y2 in PROFILES[document_type]["staticRegions"]:
        mask[round(y1*h):round(y2*h), round(x1*w):round(x2*w)] = 1
    interior = np.zeros_like(mask)
    bx1, by1, bx2, by2 = PROFILES[document_type].get("analysisBounds", [.025,.025,.975,.975])
    interior[round(by1*h):round(by2*h), round(bx1*w):round(bx2*w)] = 1
    mask &= interior
    valid = mask.astype(bool)
    scale = max(1., min(h, w) / 400.)
    ref = cv2.GaussianBlur(cv2.cvtColor(reference, cv2.COLOR_RGB2LAB).astype(np.float32), (0, 0), 2.5*scale)
    doc = cv2.GaussianBlur(cv2.cvtColor(submitted, cv2.COLOR_RGB2LAB).astype(np.float32), (0, 0), 2.5*scale)
    # Robust colour/illumination fit: large local changes cannot dominate the fit.
    corrected = doc.copy()
    fits = []
    for channel in range(3):
        x, y = doc[:,:,channel][valid], ref[:,:,channel][valid]
        keep = np.ones(x.shape, dtype=bool)
        slope, offset = 1., 0.
        for _ in range(4):
            if np.std(x[keep]) > 2:
                slope = float(np.clip(np.cov(x[keep], y[keep], bias=True)[0,1] / np.var(x[keep]), .8, 1.25))
            offset = float(np.median(y[keep] - slope*x[keep]))
            error = np.abs(y - (slope*x+offset))
            median = np.median(error)
            keep = error <= max(6., median + 3.*np.median(np.abs(error-median)))
        corrected[:,:,channel] = slope*doc[:,:,channel]+offset
        fits.append({"scale": round(slope,4), "offset": round(offset,4)})
    delta = np.abs(ref-corrected)
    chroma = np.linalg.norm(delta[:,:,1:], axis=2)
    lum = delta[:,:,0]
    baseline = chroma[valid]
    median = float(np.median(baseline))
    mad = float(np.median(np.abs(baseline-median)))
    chroma_limit = max(24., median+6.*1.4826*mad)
    # Luminance alone is frequently a capture shadow. Require strong structural change.
    ref_edges = cv2.Canny(reference, 70, 160)
    doc_edges = cv2.Canny(submitted, 70, 160)
    edge_delta = cv2.GaussianBlur((np.abs(doc_edges.astype(float)-ref_edges.astype(float))/255).astype(np.float32), (0,0), 5*scale)
    seeds = ((chroma > chroma_limit) | ((lum > 65) & (edge_delta > .12))) & valid
    candidates = ((chroma > max(15., chroma_limit*.5)) & ((lum > 25) | (chroma > chroma_limit))).astype(np.uint8)
    candidates |= seeds.astype(np.uint8)
    candidates[:border] = candidates[-border:] = 0
    candidates[:, :border] = candidates[:, -border:] = 0
    candidates &= interior
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (max(3,round(7*scale))|1,)*2)
    candidates = cv2.morphologyEx(candidates, cv2.MORPH_CLOSE, kernel)
    count, labels, stats, _ = cv2.connectedComponentsWithStats(candidates)
    areas = []
    min_area = max(100, round(w*h*.003))
    for label in range(1,count):
        x,y,bw,bh,pixels = map(int,stats[label])
        if pixels < min_area or bw < 10*scale or bh < 10*scale:
            continue
        component = labels == label
        assessed_pixels = int(np.count_nonzero(component & seeds))
        if assessed_pixels < min_area:
            continue
        magnitude = float(np.median(np.maximum(chroma[component & seeds], lum[component & seeds]*.45)))
        fraction = pixels/(w*h)
        score = min(95., 35.+min(35., fraction*700)+min(25., max(0.,magnitude-24)*.5))
        overlap = []
        for name, region in regions.items():
            if region["type"] in {"logo", "security_feature"} and min(x+bw,region['x2']) > max(x,region['x1']) and min(y+bh,region['y2']) > max(y,region['y1']):
                overlap.append(name)
        areas.append({"region": ", ".join(overlap) or "fixed_background", "reason": "Large local colour/structure discrepancy after alignment and lighting correction", "score": round(score,2), "x1": x, "y1": y, "x2": x+bw, "y2": y+bh, "changed_pixel_fraction": round(fraction,5), "strong_pixel_fraction": round(assessed_pixels/(w*h),5), "median_residual": round(magnitude,2)})
    areas.sort(key=lambda area: area["score"], reverse=True)
    broad_mismatch = float(seeds[valid].mean()) > .30
    return {"method": "registered_fixed_artwork_residual", "comparison_status": "UNRELIABLE" if broad_mismatch else "ASSESSED", "candidate_threshold": round(chroma_limit,2), "suspicious_area_count": len(areas), "suspicious_areas": areas, "analysed_pixel_fraction": round(float(valid.mean()),4), "lighting_correction": fits, "coverage_note": "Fixed artwork and unmasked background only. Holder text, photographs and signatures are excluded; their authenticity is not assessed."}


def score_evidence(evidence, ocr_validation):
    visual = max((area["score"] for area in evidence["suspicious_areas"]), default=0.)
    ocr = calculate_ocr_score(ocr_validation)
    # OCR format problems are findings, not proof that a document was altered.
    score = round(visual,2)
    level = "HIGH" if score >= 65 else "MEDIUM" if score >= 35 else "LOW"
    findings = [{"component": "fixed_artwork", "region": area["region"], "message": area["reason"], "score": area["score"]} for area in evidence["suspicious_areas"]]
    if ocr:
        findings.append({"component": "ocr", "message": "OCR format inconsistencies found; inspect the OCR validation details.", "score": ocr})
    return {"risk_score": score, "risk_level": level, "recommended_action": "MANUAL_REVIEW_REQUIRED" if score else "CONTINUE_NORMAL_PROCESS", "component_scores": {"fixed_artwork": visual, "ocr_format": ocr}, "finding_count": len(findings), "findings": findings, "detector_coverage": {"active": ["fixed_artwork", "unmasked_background", "ocr_format"], "not_assessed": ["personal_text_alterations", "portrait_replacement", "signature_authenticity"], "analysed_pixel_fraction": evidence["analysed_pixel_fraction"]}, "thresholds": {"low": "0 - 34.99", "medium": "35 - 64.99", "high": "65 - 100"}, "decision_note": "Score measures fixed-layout discrepancies, not a probability of alteration. LOW means no qualifying discrepancy in assessed areas."}
