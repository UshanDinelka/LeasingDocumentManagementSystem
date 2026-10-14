"""Compare two captures of THE SAME physical document.

The reference must be enrolled by a trusted backend; a generic layout template cannot
establish that a holder's name, NIC, signature or face was altered. Returns evidence
of visual change, not a probability of forgery or a biometric identity decision.
"""
from pathlib import Path
import re

import cv2
import numpy as np
from PIL import Image

# These explicit field names are mapped to document ROI names. Unrecognized keys
# are intentionally ignored rather than trusting caller-provided coordinates.
FIELD_MAP = {
    "NIC": {"nicNumber": "nic_number", "possibleName": "name", "dateOfBirth": "date_of_birth", "sex": "sex"},
    "DRIVING_LICENCE": {"nicNumber": "nic_number", "licenceNumber": "licence_number", "possibleName": "name", "dateOfBirth": "date_of_birth", "bloodGroup": "blood_group", "address": "address"},
    "BUSINESS_REGISTRATION": {"companyNumber": "company_number", "companyName": "company_name"},
    "VEHICLE_CR_BOOK": {"registrationNumber": "registration_number", "chassisNumber": "chassis_number"},
}


def _rgb(path):
    with Image.open(path) as im:
        return np.asarray(im.convert("RGB"))


def _redaction_mask(rgb):
    """Exclude manual saturated blue marker strokes in demonstration images."""
    red, green, blue = (rgb[:, :, i].astype(np.int16) for i in range(3))
    mark = (blue > red + 35) & (blue > green + 8) & (blue > 100) & (red < 160)
    return cv2.dilate(mark.astype(np.uint8), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (13, 13))) > 0


def _normalize_field(value):
    if value is None or not isinstance(value, (str, int, float)):
        return ""
    return re.sub(r"[^\w]", "", str(value).casefold(), flags=re.UNICODE)


def _field_candidates(ocr, key):
    if not isinstance(ocr, dict) or not isinstance(ocr.get("fields"), dict):
        return ""
    fields = ocr["fields"]
    value = fields.get(key)
    if key == "dateOfBirth" and not value:
        # Current Android OCR often provides an unassigned dates[] candidate list.
        # Use it only when unambiguous; never guess among multiple dates.
        dates = fields.get("dates")
        if isinstance(dates, list) and len(dates) == 1:
            value = dates[0]
    return _normalize_field(value)


def _feature_match(first, second, valid):
    """Visual correspondence, not facial identification. No templates are stored."""
    if min(first.shape[:2]) < 45:
        return None
    detector = cv2.SIFT_create(nfeatures=900, contrastThreshold=.017)
    ga = cv2.cvtColor(first, cv2.COLOR_RGB2GRAY)
    gb = cv2.cvtColor(second, cv2.COLOR_RGB2GRAY)
    mask = (valid.astype(np.uint8) * 255)
    kp_a, des_a = detector.detectAndCompute(ga, mask)
    kp_b, des_b = detector.detectAndCompute(gb, mask)
    if des_a is None or des_b is None or len(des_a) < 10 or len(des_b) < 10:
        return None
    pairs = cv2.BFMatcher().knnMatch(des_a, des_b, k=2)
    good = [m for pair in pairs if len(pair) == 2 for m, n in [pair] if m.distance < .71 * n.distance]
    if len(good) < 5:
        return {"keypointsReference": len(kp_a), "keypointsSubmitted": len(kp_b),
                "correspondences": len(good), "inliers": 0, "inlierFraction": 0.}
    pts_a = np.float32([kp_a[m.queryIdx].pt for m in good])
    pts_b = np.float32([kp_b[m.trainIdx].pt for m in good])
    cv2.setRNGSeed(22118)
    affine, inliers = cv2.estimateAffinePartial2D(pts_a, pts_b, method=cv2.RANSAC,
                                                  ransacReprojThreshold=4., maxIters=2000)
    good_inliers = int(inliers.sum()) if inliers is not None else 0
    return {"keypointsReference": len(kp_a), "keypointsSubmitted": len(kp_b),
            "correspondences": len(good), "inliers": good_inliers,
            "inlierFraction": round(good_inliers / max(1, len(good)), 4)}


def _appearance_change(reference, submitted, valid):
    # Colour-independent comparison avoids labelling moderate exposure shifts as edits.
    ref_lab = cv2.cvtColor(reference, cv2.COLOR_RGB2LAB).astype(np.float32)
    sub_lab = cv2.cvtColor(submitted, cv2.COLOR_RGB2LAB).astype(np.float32)
    good = valid & (ref_lab[:, :, 0] > 30) & (sub_lab[:, :, 0] > 30)
    if np.count_nonzero(good) < 400:
        return None
    # Robust per-ROI brightness correction. Keep chroma effects separate.
    ref_l = cv2.GaussianBlur(ref_lab[:, :, 0], (0, 0), 1.9)
    sub_l = cv2.GaussianBlur(sub_lab[:, :, 0], (0, 0), 1.9)
    offset = float(np.median(ref_l[good] - sub_l[good]))
    offset = np.clip(offset, -32, 32)
    lum = np.abs(ref_l - (sub_l + offset))
    reference_chroma = cv2.GaussianBlur(ref_lab[:, :, 1:], (0, 0), 2)
    submitted_chroma = cv2.GaussianBlur(sub_lab[:, :, 1:], (0, 0), 2)
    chroma_offsets = [float(np.median(reference_chroma[:, :, i][good] - submitted_chroma[:, :, i][good])) for i in (0, 1)]
    sub_color = submitted_chroma + np.array(chroma_offsets, dtype=np.float32)
    chroma = np.linalg.norm(reference_chroma - sub_color, axis=2)
    # Only large enough connected changes, not single pixel OCR/print texture noise.
    residual = ((lum > 35) | ((chroma > 25) & (lum > 12))) & good
    area_frac = float(residual.sum() / max(1, good.sum()))
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    mask = cv2.morphologyEx(residual.astype(np.uint8), cv2.MORPH_CLOSE, kernel)
    count, _, stats, _ = cv2.connectedComponentsWithStats(mask)
    largest = max(stats[1:], key=lambda s: int(s[cv2.CC_STAT_AREA]), default=None)
    substantial = int(largest[cv2.CC_STAT_AREA]) if largest is not None else 0
    candidate_box = None if largest is None else [int(largest[cv2.CC_STAT_LEFT]),
        int(largest[cv2.CC_STAT_TOP]), int(largest[cv2.CC_STAT_LEFT] + largest[cv2.CC_STAT_WIDTH]),
        int(largest[cv2.CC_STAT_TOP] + largest[cv2.CC_STAT_HEIGHT])]
    return {"changedFraction": round(area_frac, 5),
            "largestChangedPatchFraction": round(substantial / max(1, good.sum()), 5),
            "candidateBox": candidate_box,
            "validFraction": round(float(good.mean()), 4)}


def compare_same_document(reference_path, submitted_path, document_type, regions,
                          reference_ocr=None, submitted_ocr=None):
    """Both inputs must already be separately registered to the same layout template."""
    ref = _rgb(reference_path)
    sub = _rgb(submitted_path)
    if ref.shape != sub.shape:
        return {"status": "UNRELIABLE", "reason": "Dimensions differ after layout registration", "findings": []}
    h, w = ref.shape[:2]
    excluded = _redaction_mask(ref) | _redaction_mask(sub)
    findings = []
    diagnostics = {}
    for name, region in regions.items():
        if region["type"] not in {"text", "photo", "signature"}:
            continue
        x1, y1, x2, y2 = [int(region[n]) for n in ("x1", "y1", "x2", "y2")]
        x1, x2 = max(0, x1), min(w, x2)
        y1, y2 = max(0, y1), min(h, y2)
        if x2 - x1 < 25 or y2 - y1 < 20:
            continue
        a = ref[y1:y2, x1:x2]
        b = sub[y1:y2, x1:x2]
        valid = ~excluded[y1:y2, x1:x2]
        # Ignore perimeter artifacts from template alignment / cropped paper margins.
        margin = max(3, int(.015 * min(a.shape[:2])))
        valid[:margin] = valid[-margin:] = False
        valid[:, :margin] = valid[:, -margin:] = False
        coverage = float(valid.mean())
        if coverage < .28:
            diagnostics[name] = {"status": "REDACTED_OR_UNREADABLE", "validFraction": round(coverage, 3)}
            continue
        match = _feature_match(a, b, valid)
        difference = _appearance_change(a, b, valid)
        entry = {"status": "ASSESSED", "validFraction": round(coverage, 3),
                 "features": match, "appearance": difference}
        diagnostics[name] = entry
        # Photo swaps typically destroy distinctive local correspondences. Require
        # the reference photograph to have sufficient texture and keypoints first.
        if region["type"] == "photo" and match is not None:
            has_reference_texture = match["keypointsReference"] >= 28
            has_submitted_texture = match["keypointsSubmitted"] >= 28
            poor_correspondence = match["inliers"] < 7
            # Difference alone can reflect lighting, interpolation, or a different photo scan.
            visual_change = difference is not None and (difference["changedFraction"] > .16)
            if has_reference_texture and has_submitted_texture and poor_correspondence and visual_change:
                findings.append({"region": name, "finding_type": "PORTRAIT_VISUAL_MISMATCH",
                                 "reason": "Photograph differs from the supplied same-document reference. Verify manually; this does not identify the subject.",
                                 "score": 85., "x1": x1, "y1": y1, "x2": x2, "y2": y2})
    # Judge appearance changes relative to OTHER fields in the same image pair.
    # This suppresses false positives when camera perspective causes widespread
    # pixel residuals rather than a localized edit.
    appearance_levels = [v["appearance"]["changedFraction"] for n, v in diagnostics.items()
                         if n != "photo" and v.get("appearance") is not None]
    global_misalignment = sum(value > .09 for value in appearance_levels) >= 3
    for name, region in regions.items():
        if region["type"] not in {"text", "signature"} or name not in diagnostics:
            continue
        sample = diagnostics[name]
        diff = sample.get("appearance")
        if diff is None or global_misalignment:
            continue
        baseline = float(np.median([v["appearance"]["changedFraction"]
            for n, v in diagnostics.items() if n not in (name, "photo") and v.get("appearance") is not None]
            or [0.]))
        minimum_fraction = .04 if region["type"] == "signature" else .05
        minimum_patch = .025 if region["type"] == "signature" else .03
        is_localized = (diff["largestChangedPatchFraction"] >=
                        (.65 if region["type"] == "signature" else .85) * diff["changedFraction"])
        if (diff["changedFraction"] >= max(minimum_fraction, 2.4*baseline)
                and diff["largestChangedPatchFraction"] >= minimum_patch
                and is_localized):
            x1, y1, x2, y2 = [int(region[k]) for k in ("x1", "y1", "x2", "y2")]
            local_box = diff.get("candidateBox")
            if local_box:
                bx1, by1, bx2, by2 = local_box
                pad = 5
                box = dict(x1=max(x1, x1+bx1-pad), y1=max(y1, y1+by1-pad),
                           x2=min(x2, x1+bx2+pad), y2=min(y2, y1+by2+pad))
            else:
                box = {k: int(region[k]) for k in ("x1", "y1", "x2", "y2")}
            findings.append({"region": name, "finding_type": "FIELD_VISUAL_MISMATCH",
                             "reason": "Localized appearance differs from same-document reference; inspect for edited or obscured print and camera artifacts.",
                             "score": 73., **box})
    # Independently compare supplied OCR field strings for exact value conflicts.
    # Values must come from a trustworthy server-side record to claim verification;
    # otherwise these remain only comparison indications.
    field_map = FIELD_MAP.get(document_type, {})
    for field, region_name in field_map.items():
        if region_name not in regions:
            continue
        expected = _field_candidates(reference_ocr, field)
        observed = _field_candidates(submitted_ocr, field)
        if not expected or not observed or expected == observed:
            continue
        box = regions[region_name]
        vis = diagnostics.get(region_name, {}).get("appearance")
        corroborated = vis is not None and vis["changedFraction"] >= .045
        findings.append({"region": region_name,
                         "finding_type": "OCR_FIELD_MISMATCH" if corroborated else "OCR_VALUE_CONFLICT_ONLY",
                         "field": field,
                         "reason": f"OCR value for {field} conflicts with the supplied reference. Review OCR accuracy and reference provenance before attributing to editing.",
                         "score": 78. if corroborated else 45.,
                         **{k: int(box[k]) for k in ("x1", "y1", "x2", "y2")}})
    findings.sort(key=lambda value: value["score"], reverse=True)
    return {"status": "ASSESSED", "reference_kind": "SAME_DOCUMENT_CAPTURE",
            "provenance": "PROVIDED_FOR_COMPARISON_NOT_AUTHENTICITY_PROOF",
            "findings": findings, "region_diagnostics": diagnostics,
            "summary": "Differences from a supplied image/field reference; assess trust in reference separately."}
