"""Reference-backed, layout-independent comparison of the *entire* document.

Never use a generic layout sample as a holder-specific reference. The caller must
verify provenance and same-document ownership BEFORE passing a reference.

This module reports image differences, not forgery probability. Camera variation,
reprinting, blur, and lighting can cause differences. Ambiguous pairs fail closed
(INCOMPLETE via the caller) instead of inventing an alteration score.

The algorithm deliberately does NOT use fixed name/photo/ID coordinates. New
edits anywhere in the document have the same chance of being examined.
"""
from __future__ import annotations

from pathlib import Path
import cv2
import numpy as np
from PIL import Image

BUILD = "whole-document-reference-v2"


def _read(path: str | Path) -> np.ndarray:
    with Image.open(path) as handle:
        return np.asarray(handle.convert("RGB"))


def _odd(value: float, minimum: int = 3, maximum: int = 31) -> int:
    n = max(minimum, min(maximum, int(round(value))))
    return n if n % 2 else n + 1


def _align_by_shared_features(ref: np.ndarray, sub: np.ndarray):
    """Match many ordinary features, not known field locations or sample edits.

    The homography is estimated with RANSAC and accepted only if it is close to
    identity (the main layout registration was already performed). Using the
    majority of unchanged features prevents a localized edit from controlling
    the geometric transform.
    """
    h, w = ref.shape[:2]
    a = cv2.cvtColor(ref, cv2.COLOR_RGB2GRAY)
    b = cv2.cvtColor(sub, cv2.COLOR_RGB2GRAY)
    sift = cv2.SIFT_create(nfeatures=2500, contrastThreshold=.018)
    kp_a, des_a = sift.detectAndCompute(a, None)
    kp_b, des_b = sift.detectAndCompute(b, None)
    if des_a is None or des_b is None:
        return None, {"status": "SKIPPED", "reason": "Insufficient features"}
    pairs = cv2.BFMatcher(cv2.NORM_L2).knnMatch(des_b, des_a, k=2)
    good = [m for pair in pairs if len(pair) == 2 for m, n in [pair]
            if m.distance < .73*n.distance]
    if len(good) < 45:
        return None, {"status": "SKIPPED", "reason": "Insufficient correspondences", "matches": len(good)}
    src = np.float32([kp_b[m.queryIdx].pt for m in good]).reshape(-1, 1, 2)
    dst = np.float32([kp_a[m.trainIdx].pt for m in good]).reshape(-1, 1, 2)
    cv2.setRNGSeed(14303)
    transform, inliers = cv2.findHomography(src, dst, cv2.RANSAC, 2.5,
                                            maxIters=2500, confidence=.997)
    if transform is None or inliers is None:
        return None, {"status": "SKIPPED", "reason": "RANSAC failed"}
    good_fraction = float(inliers.mean())
    corners = np.float32([[[0,0]], [[w-1,0]], [[w-1,h-1]], [[0,h-1]]])
    projected = cv2.perspectiveTransform(corners, transform)
    displacements = np.linalg.norm(projected-corners, axis=2).ravel()
    max_displacement = float(np.max(displacements))
    diag = {"status": "REJECTED", "matches": len(good), "inliers": int(inliers.sum()),
            "inlierFraction": round(good_fraction, 4),
            "maxCornerDisplacementPixels": round(max_displacement, 2)}
    if (good_fraction < .66 or int(inliers.sum()) < 45 or
            max_displacement > max(18.0, min(w,h)*.047)):
        return None, diag
    registered = cv2.warpPerspective(sub, transform, (w,h),
                     flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT_101)
    diag["status"] = "APPLIED"
    return registered, diag


def _small_affine_registration(ref: np.ndarray, sub: np.ndarray):
    """Refine at most a small residual registration from the layout warp.

    This registration is for capture jitter only; it is never allowed to move an
    entire document section to conceal a modified field.
    """
    h, w = ref.shape[:2]
    small_size = (min(w, 900), min(h, 600))
    a = cv2.cvtColor(cv2.resize(ref, small_size), cv2.COLOR_RGB2GRAY)
    b = cv2.cvtColor(cv2.resize(sub, small_size), cv2.COLOR_RGB2GRAY)
    a = cv2.GaussianBlur(a, (5, 5), 0)
    b = cv2.GaussianBlur(b, (5, 5), 0)
    warp = np.eye(2, 3, dtype=np.float32)
    correlation = None
    try:
        correlation, warp = cv2.findTransformECC(
            a, b, warp, cv2.MOTION_EUCLIDEAN,
            (cv2.TERM_CRITERIA_EPS | cv2.TERM_CRITERIA_COUNT, 80, 1e-5),
            None, 5,
        )
    except cv2.error:
        return sub, {"status": "SKIPPED", "reason": "ECC did not converge"}
    max_angle = abs(float(np.arctan2(warp[1, 0], warp[0, 0])))
    dx = float(warp[0, 2]) * w / small_size[0]
    dy = float(warp[1, 2]) * h / small_size[1]
    max_translation = max(6.0, 0.016 * min(w, h))
    if (correlation is None or correlation < .58 or max_angle > .025
            or max(abs(dx), abs(dy)) > max_translation):
        return sub, {"status": "REJECTED", "correlation": round(float(correlation), 4),
                     "translationPixels": [round(dx, 2), round(dy, 2)]}
    full_warp = warp.copy()
    full_warp[0, 2] = dx
    full_warp[1, 2] = dy
    corrected = cv2.warpAffine(
        sub, full_warp, (w, h), flags=cv2.INTER_LINEAR | cv2.WARP_INVERSE_MAP,
        borderMode=cv2.BORDER_REFLECT_101)
    return corrected, {"status": "APPLIED", "correlation": round(float(correlation), 4),
                       "translationPixels": [round(dx, 2), round(dy, 2)]}


def _compensate_lighting(ref: np.ndarray, sub: np.ndarray):
    """Compensate exposure/white balance without erasing small local edits."""
    first = cv2.cvtColor(ref, cv2.COLOR_RGB2LAB).astype(np.float32)
    second = cv2.cvtColor(sub, cv2.COLOR_RGB2LAB).astype(np.float32)
    h, w = ref.shape[:2]
    # Exclude frame/boundary, usually affected by perspective interpolation.
    valid = np.ones((h, w), dtype=bool)
    margin = max(8, round(min(h, w) * .024))
    valid[:margin, :] = valid[-margin:, :] = False
    valid[:, :margin] = valid[:, -margin:] = False
    corrected = second.copy()
    calibrations = []
    for channel in range(3):
        x = second[:, :, channel][valid][::5]
        y = first[:, :, channel][valid][::5]
        keep = np.ones(len(x), dtype=bool)
        slope, offset = 1., 0.
        for _ in range(4):
            if np.count_nonzero(keep) < 500:
                break
            xk, yk = x[keep], y[keep]
            variance = float(np.var(xk))
            slope = float(np.clip(np.mean((xk - xk.mean()) * (yk - yk.mean())) / variance, .82, 1.22)) if variance > 6 else 1.
            offset = float(np.median(yk - slope * xk))
            residual = np.abs(y - (slope * x + offset))
            md = float(np.median(residual))
            mad = float(np.median(np.abs(residual - md)))
            keep = residual < max(9., md + 3.0 * 1.4826 * mad)
        calibrations.append({"channel": channel, "scale": round(slope, 3), "offset": round(offset, 2)})
        corrected[:, :, channel] = np.clip(slope * second[:, :, channel] + offset, 0, 255)
    # Apply a *small* low-frequency lighting correction. It cannot remove a
    # sharp boundary or ink strokes from a pasted field.
    large = _odd(min(h, w) * .10, 41, 161)
    smooth_delta = cv2.GaussianBlur(first - corrected, (large, large), 0)
    correction = np.clip(smooth_delta, -15, 15)
    corrected = np.clip(corrected + correction, 0, 255)
    return first, corrected, valid, calibrations


def _change_mask(ref: np.ndarray, sub: np.ndarray):
    h, w = ref.shape[:2]
    a, b, valid, illumination = _compensate_lighting(ref, sub)
    smooth_a = cv2.GaussianBlur(a, (3, 3), 0)
    smooth_b = cv2.GaussianBlur(b, (3, 3), 0)
    luminance = np.abs(smooth_a[:, :, 0] - smooth_b[:, :, 0])
    colour = np.linalg.norm(smooth_a[:, :, 1:] - smooth_b[:, :, 1:], axis=2)
    ga = cv2.cvtColor(ref, cv2.COLOR_RGB2GRAY).astype(np.float32)
    gb = cv2.cvtColor(sub, cv2.COLOR_RGB2GRAY).astype(np.float32)
    ga = cv2.GaussianBlur(ga, (3, 3), 0)
    gb = cv2.GaussianBlur(gb, (3, 3), 0)
    # Soft gradient residual catches character and date changes even if the
    # replacement background is colour-matched to security paper.
    gax = cv2.Sobel(ga, cv2.CV_32F, 1, 0, ksize=3)
    gay = cv2.Sobel(ga, cv2.CV_32F, 0, 1, ksize=3)
    gbx = cv2.Sobel(gb, cv2.CV_32F, 1, 0, ksize=3)
    gby = cv2.Sobel(gb, cv2.CV_32F, 0, 1, ksize=3)
    structure = np.sqrt((gax - gbx) ** 2 + (gay - gby) ** 2)
    # Empirical *starting* thresholds, not calibrated probabilities.
    changed = (((luminance > 29) & (structure > 38)) |
               ((colour > 23) & (luminance > 14)) |
               ((luminance > 49) & (colour > 9)) |
               ((structure > 110) & (luminance > 15))) & valid
    # Anti-aliasing and JPEG noise alone should not create a box.
    changed = cv2.morphologyEx(changed.astype(np.uint8), cv2.MORPH_OPEN,
                               np.ones((2, 2), np.uint8))
    scale = max(1., min(w / 1100., h / 700.))
    joined = cv2.morphologyEx(changed, cv2.MORPH_CLOSE,
                              cv2.getStructuringElement(cv2.MORPH_RECT,
                              (_odd(7 * scale, 3, 17), _odd(3 * scale, 3, 9))))
    return joined.astype(bool), changed.astype(bool), valid, {
        "luminance": luminance, "colour": colour, "structure": structure,
        "illumination": illumination,
    }


def _component_boxes(joined, strong, valid, diagnostics, doc_shape):
    h, w = doc_shape[:2]
    count, labels, stats, _ = cv2.connectedComponentsWithStats(joined.astype(np.uint8), connectivity=8)
    findings = []
    min_dim = min(h, w)
    min_pixels = max(26, int(h * w * .000035))
    for index in range(1, count):
        x, y, bw, bh, area = map(int, stats[index])
        if area < min_pixels or bw < max(4, .004 * w) or bh < max(4, .004 * h):
            continue
        local = labels[y:y+bh, x:x+bw] == index
        signal = strong[y:y+bh, x:x+bw] & local
        changed_count = int(signal.sum())
        if changed_count < max(16, min_pixels // 2):
            continue
        signal_magnitude = float(np.median(diagnostics["luminance"][y:y+bh, x:x+bw][signal]))
        gradient_magnitude = float(np.median(diagnostics["structure"][y:y+bh, x:x+bw][signal]))
        findings.append({
            "region": "whole_document", "finding_type": "REFERENCE_ANYWHERE_VISUAL_CHANGE",
            "reason": "Localized change anywhere on the document relative to the approved same-document capture. Review both images; alteration is not automatically established.",
            "score": 80.0, "x1": max(0, x - 5), "y1": max(0, y - 5),
            "x2": min(w, x + bw + 5), "y2": min(h, y + bh + 5),
            "changedPixels": changed_count, "componentPixels": area,
            "medianLuminanceResidual": round(signal_magnitude, 2),
            "medianStructureResidual": round(gradient_magnitude, 2),
            "detectionMethod": BUILD,
        })
    findings.sort(key=lambda item: item["changedPixels"], reverse=True)
    # Merge nearby text glyphs into one legible box without forcing a known
    # field location. Merge only boxes on the same text line / nearby features.
    combined = []
    for item in findings:
        merged = False
        for prior in combined:
            gapx = max(prior["x1"] - item["x2"], item["x1"] - prior["x2"], 0)
            gapy = max(prior["y1"] - item["y2"], item["y1"] - prior["y2"], 0)
            if gapx <= max(8, int(w * .009)) and gapy <= max(5, int(h * .012)):
                prior["x1"] = min(prior["x1"], item["x1"])
                prior["x2"] = max(prior["x2"], item["x2"])
                prior["y1"] = min(prior["y1"], item["y1"])
                prior["y2"] = max(prior["y2"], item["y2"])
                prior["changedPixels"] += item["changedPixels"]
                merged = True
                break
        if not merged:
            combined.append(item.copy())
    return combined[:30]


def compare_entire_document(reference_image_path, submitted_image_path):
    """Scan every interior pixel. Return status + suspicious boxes in registered coords."""
    reference = _read(reference_image_path)
    submitted = _read(submitted_image_path)
    if reference.shape != submitted.shape:
        return {"status": "UNRELIABLE", "reason": "Different registered dimensions", "findings": [], "build": BUILD}
    if min(reference.shape[:2]) < 250:
        return {"status": "UNRELIABLE", "reason": "Registered image too small", "findings": [], "build": BUILD}
    if np.array_equal(reference, submitted):
        return {"status": "ASSESSED", "reason": "Exact registered image pixel match",
                "findings": [], "build": BUILD, "changedFraction": 0.0,
                "coverageFraction": .95, "quality": "EXACT_MATCH"}
    aligned, alignment = _align_by_shared_features(reference, submitted)
    if aligned is None:
        aligned, backup_alignment = _small_affine_registration(reference, submitted)
        alignment["fallback"] = backup_alignment
    joined, strong, valid, metrics = _change_mask(reference, aligned)
    rate = float(np.count_nonzero(strong) / max(1, np.count_nonzero(valid)))
    h, w = reference.shape[:2]
    # A strong *widespread* mismatch cannot be confidently localized and is
    # more likely caused by incompatible capture conditions or alignment.
    # Fail closed rather than fabricate dozens of red boxes.
    cell_rates = []
    for row in range(4):
        for col in range(6):
            yy1, yy2 = int(h * (row+.07) / 4), int(h * (row+.91) / 4)
            xx1, xx2 = int(w * (col+.07) / 6), int(w * (col+.91) / 6)
            local = strong[yy1:yy2, xx1:xx2]
            if local.size:
                cell_rates.append(float(local.mean()))
    broad_cells = sum(v > .13 for v in cell_rates)
    # A large focus/sharpness gap can manufacture text-shaped differences in
    # *every* dark stroke. Never label an out-of-focus recapture a forgery.
    sharp_ref = float(cv2.Laplacian(cv2.cvtColor(reference, cv2.COLOR_RGB2GRAY),
                                    cv2.CV_32F).var())
    sharp_sub = float(cv2.Laplacian(cv2.cvtColor(submitted, cv2.COLOR_RGB2GRAY),
                                    cv2.CV_32F).var())
    focus_ratio = max(sharp_ref, sharp_sub) / max(1., min(sharp_ref, sharp_sub))
    # Incompatible captures, poor registration and heavy blur should trigger
    # INCOMPLETE, not a verdict that a field was digitally modified.
    unreliable = (rate > .16 or broad_cells >= 8 or
                  (alignment["status"] == "REJECTED" and rate > .055) or
                  (focus_ratio > 4.0 and rate > .003))
    if unreliable:
        return {"status": "UNRELIABLE", "reason": "Capture or registration differences affect too much of the document for reliable localization",
                "build": BUILD, "findings": [], "changedFraction": round(rate, 5),
                "widespreadCells": broad_cells, "focusRatio": round(focus_ratio, 3),
                "alignment": alignment}
    findings = _component_boxes(joined, strong, valid, metrics, reference.shape)
    return {"status": "ASSESSED", "build": BUILD,
            "findings": findings, "changedFraction": round(rate, 5),
            "widespreadCells": broad_cells, "focusRatio": round(focus_ratio, 3),
            "coverageFraction": round(float(valid.mean()), 4),
            "alignment": alignment,
            "method": "full_page_lab_gradient_residual_with_small_registration_and_quality_gate",
            "warning": "Differences may reflect editing or recapture; requires manual verification."}
