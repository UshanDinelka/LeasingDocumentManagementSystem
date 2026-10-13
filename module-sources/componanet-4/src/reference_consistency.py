"""Reference-backed change localization for REGISTERED images of the SAME document.

This compares personal fields as well as photos and signatures. Neither a layout
sample nor another person's identity card is a valid reference. Results mean
"visually different", not "forged". Thresholds MUST be calibrated on local data.

Coordinates and regions are supplied by the existing, trusted document profile.
No arbitrary client coordinates or claimed ground-truth values are used.
"""
from pathlib import Path
import cv2
import numpy as np
from PIL import Image

DETECTOR_VERSION = "reference-consistency-v1"


def _load(path):
    with Image.open(Path(path)) as handle:
        return np.asarray(handle.convert("RGB"))


def _blue_redactions(rgb):
    # Exclude the blue anonymization marks in development screenshots only.
    # DO NOT interpret a blue mark as evidence that a document was validated.
    x = rgb.astype(np.int16)
    redacted = ((x[:, :, 2] > 95) & (x[:, :, 2] > x[:, :, 0] + 35)
                & (x[:, :, 2] > x[:, :, 1] + 8) & (x[:, :, 0] < 170))
    return cv2.dilate(redacted.astype(np.uint8), np.ones((11, 11), np.uint8)) > 0


def _residual(reference, candidate):
    """Exposure-compensated colour + structural residual in LAB colour space."""
    a = cv2.cvtColor(reference, cv2.COLOR_RGB2LAB).astype(np.float32)
    b = cv2.cvtColor(candidate, cv2.COLOR_RGB2LAB).astype(np.float32)
    a = cv2.GaussianBlur(a, (3, 3), 0)
    b = cv2.GaussianBlur(b, (3, 3), 0)
    # Per-region exposure/white-balance compensation; median is resistant to small edits.
    offsets = np.median((a-b).reshape(-1, 3), axis=0)
    b += np.clip(offsets, -30, 30)[None, None, :]
    delta = np.abs(a-b)
    gray_ref = cv2.cvtColor(reference, cv2.COLOR_RGB2GRAY)
    gray_new = cv2.cvtColor(candidate, cv2.COLOR_RGB2GRAY)
    grad_a = cv2.Laplacian(cv2.GaussianBlur(gray_ref, (3, 3), 0), cv2.CV_32F, ksize=3)
    grad_b = cv2.Laplacian(cv2.GaussianBlur(gray_new, (3, 3), 0), cv2.CV_32F, ksize=3)
    structure = np.abs(grad_a-grad_b)
    return (delta[:, :, 0], np.linalg.norm(delta[:, :, 1:], axis=2), structure)


def _local_findings(ref, sub, name, region, redacted):
    height, width = ref.shape[:2]
    x1, y1, x2, y2 = (int(region[k]) for k in ("x1", "y1", "x2", "y2"))
    x1, x2 = max(0, x1), min(width, x2)
    y1, y2 = max(0, y1), min(height, y2)
    if x2-x1 < 35 or y2-y1 < 20:
        return [], {"status": "TOO_SMALL"}
    first = ref[y1:y2, x1:x2]
    second = sub[y1:y2, x1:x2]
    redactions = redacted[y1:y2, x1:x2]
    # Independent template registrations have slightly different interpolation.
    # Compensate *small* field-level affine jitter before forensic comparison.
    # Do not accept a large warp that could conceal an actual replacement.
    alignment_score = None
    try:
        reference_gray = cv2.cvtColor(first, cv2.COLOR_RGB2GRAY)
        submitted_gray = cv2.cvtColor(second, cv2.COLOR_RGB2GRAY)
        warp = np.eye(2, 3, dtype=np.float32)
        alignment_score, warp = cv2.findTransformECC(
            cv2.GaussianBlur(reference_gray, (5, 5), 0),
            cv2.GaussianBlur(submitted_gray, (5, 5), 0), warp,
            cv2.MOTION_AFFINE,
            (cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 75, 1e-5),
            inputMask=(~redactions).astype(np.uint8)*255, gaussFiltSize=5)
        if (np.max(np.abs(warp[:, :2] - np.eye(2))) < .025 and
                np.max(np.abs(warp[:, 2])) < 12 and alignment_score > .65):
            second = cv2.warpAffine(second, warp, (first.shape[1], first.shape[0]),
                                    flags=cv2.INTER_LINEAR | cv2.WARP_INVERSE_MAP,
                                    borderMode=cv2.BORDER_REFLECT)
    except cv2.error:
        pass
    valid = ~redactions
    margin = max(2, round(min(first.shape[:2])*.02))
    valid[:margin, :] = False
    valid[-margin:, :] = False
    valid[:, :margin] = False
    valid[:, -margin:] = False
    coverage = float(valid.mean())
    if coverage < .4:
        return [], {"status": "NOT_ASSESSED_REDACTED_OR_MISSING", "validFraction": round(coverage, 3)}
    lum, chroma, structure = _residual(first, second)
    # Exposure changes alone should not be treated as deliberate text edits.
    # Candidate must have a meaningful colour or structural change.
    change = (((lum > 29) & (structure > 16)) |
              ((chroma > 20) & (lum > 15)) |
              ((structure > 65) & (lum > 14))) & valid
    # Remove isolated image noise and retain contiguous significant changes.
    mask = cv2.morphologyEx(change.astype(np.uint8), cv2.MORPH_CLOSE,
                            cv2.getStructuringElement(cv2.MORPH_RECT,
                                (min(31, max(7, first.shape[1]//25)), 5)))
    mask = cv2.morphologyEx(mask, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8))
    components, _, stats, _ = cv2.connectedComponentsWithStats(mask)
    valid_count = int(np.count_nonzero(valid))
    changed_fraction = float(np.count_nonzero(change)/max(1, valid_count))
    minimum = max(75, int(valid_count * (.002 if region["type"] != "photo" else .02)))
    candidates = []
    # Below this fraction, isolated anti-aliased edges or small residual
    # template-alignment errors frequently look like "changed text".
    # A trusted reference still does NOT guarantee detection of a tiny edit.
    if changed_fraction < .0015:
        return [], {"status": "ASSESSED", "validFraction": round(coverage, 4),
                    "changedFraction": round(changed_fraction, 5),
                    "largestComponent": int(max([s[cv2.CC_STAT_AREA] for s in stats[1:]], default=0)),
                    "candidateCount": 0,
                    "localAlignmentCorrelation": (round(float(alignment_score), 4) if alignment_score is not None else None)}
    for index in range(1, components):
        bx, by, bw, bh, count = (int(v) for v in stats[index])
        if count < minimum or bw < 10 or bh < 7:
            continue
        # Return a candidate bounding box, not a statement about authenticity.
        candidates.append({
            "region": name,
            "finding_type": "REFERENCE_FIELD_CHANGE" if region["type"] == "text" else
                            "REFERENCE_PHOTO_CHANGE" if region["type"] == "photo" else
                            "REFERENCE_SIGNATURE_CHANGE",
            "reason": "Local visual difference from approved same-document reference. Could reflect editing, camera variation, obstruction or recapture; review both images.",
            "score": 78.0 if region["type"] == "text" else 82.0,
            "x1": max(x1, x1+bx-5), "y1": max(y1, y1+by-5),
            "x2": min(x2, x1+bx+bw+5), "y2": min(y2, y1+by+bh+5),
            "changedPixelFraction": round(changed_fraction, 5),
            "componentArea": count,
            "verificationMethod": DETECTOR_VERSION,
        })
    candidates.sort(key=lambda c: c["componentArea"], reverse=True)
    return candidates[:3], {
        "status": "ASSESSED", "validFraction": round(coverage, 4),
        "changedFraction": round(changed_fraction, 5),
        "largestComponent": int(max([s[cv2.CC_STAT_AREA] for s in stats[1:]], default=0)),
        "candidateCount": len(candidates),
        "localAlignmentCorrelation": (round(float(alignment_score), 4) if alignment_score is not None else None),
    }


def detect_reference_changes(reference_image_path, submitted_image_path, regions):
    """Caller MUST verify reference ownership/provenance and align both to one layout."""
    ref = _load(reference_image_path)
    sub = _load(submitted_image_path)
    if ref.shape != sub.shape:
        return {"status": "UNRELIABLE", "reason": "Different registered image sizes", "findings": []}
    redacted = _blue_redactions(ref) | _blue_redactions(sub)
    findings, diagnostics = [], {}
    for name, region in regions.items():
        if region.get("type") not in {"text", "photo", "signature"}:
            continue
        local, diagnostics[name] = _local_findings(ref, sub, name, region, redacted)
        findings.extend(local)
    assessed = sum(d.get("status") == "ASSESSED" for d in diagnostics.values())
    requested = len(diagnostics)
    if not assessed:
        return {"status": "UNRELIABLE", "reason": "No holder-specific field could be compared", "findings": [], "diagnostics": diagnostics}
    # A large residue across unchanged printed labels often means blur, glare,
    # perspective/scale differences or a camera photographing a screen. The
    # configured ROIs currently include printed labels as well as personal data.
    # When such changes dominate, do not report HIGH as if a specific field edit
    # were localized: the correct outcome is an inconclusive reference pair.
    gross_change = any(d.get("changedFraction", 0) > .12 for name,d in diagnostics.items()
                       if regions[name].get("type") == "text")
    widespread = sum(d.get("changedFraction", 0) > .06 for d in diagnostics.values())
    if gross_change or widespread >= 3:
        return {"status": "UNRELIABLE", "reason": "Large field-wide or widespread visual changes: registration, focus or capture differences prevent reliable localization. Recapture and manually inspect.", "findings": [], "diagnostics": diagnostics}
    # Merge duplicate, overlapping detections from intentionally broad name ROIs.
    deduped = []
    for area in sorted(findings, key=lambda f: f["componentArea"], reverse=True):
        def iou(a, b):
            x = max(0, min(a["x2"],b["x2"])-max(a["x1"],b["x1"]))
            y = max(0, min(a["y2"],b["y2"])-max(a["y1"],b["y1"]))
            both = x*y
            ar1 = (a["x2"]-a["x1"])*(a["y2"]-a["y1"])
            ar2 = (b["x2"]-b["x1"])*(b["y2"]-b["y1"])
            return both / max(1, ar1+ar2-both)
        if not any(iou(area, other) > .45 for other in deduped):
            deduped.append(area)
    return {"status": "ASSESSED", "detectorVersion": DETECTOR_VERSION,
            "fieldsAssessed": assessed, "fieldsConfigured": requested,
            "findings": deduped[:25], "diagnostics": diagnostics,
            "summary": "Compare these differences with the trusted original; this is not a forgery verdict."}
