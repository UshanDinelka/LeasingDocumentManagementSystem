"""Shared OpenCV layout registration and document cropping. Personal fields do not establish alignment."""
import re
import cv2
import numpy as np
from PIL import Image, ImageOps
from src.document_profiles import PROFILES

VERSION = "document-analysis-4"


def check_document_text(ocr, kind):
    if kind not in PROFILES:
        return "NOT_SUPPORTED", "Analysis is not configured for this document type."
    text = re.sub(r"\s+", " ", ocr.get("fullText", "")).upper().strip()
    if len(text) < 20 or len(ocr.get("lines", [])) < 2:
        return "OCR_INCOMPLETE", "Insufficient readable text. Capture the complete document with clear lighting."
    if not any(re.search(p, text, re.IGNORECASE) for p in PROFILES[kind]["titlePatterns"]):
        return "DOCUMENT_TYPE_UNVERIFIED", "OCR does not confirm the selected type for the configured layout."
    return None


def _small(image, limit):
    scale = min(1.0, limit / max(image.shape[:2]))
    return (cv2.resize(image, None, fx=scale, fy=scale, interpolation=cv2.INTER_AREA)
            if scale < 1 else image), scale


def _ordered(points):
    points = np.asarray(points, np.float32).reshape(4, 2)
    center = points.mean(axis=0)
    order = np.argsort(np.arctan2(points[:, 1]-center[1], points[:, 0]-center[0]))
    points = points[order]
    points = np.roll(points, -int(np.argmin(points.sum(axis=1))), axis=0)
    if points[1, 0] < points[-1, 0]:
        points = points[[0, 3, 2, 1]]
    return points


def _valid_quad(quad, shape):
    height, width = shape[:2]
    quad = np.asarray(quad, np.float32).reshape(4, 2)
    if not np.isfinite(quad).all() or not cv2.isContourConvex(quad):
        return False
    area = abs(cv2.contourArea(quad))
    if not .015*width*height <= area <= 1.35*width*height:
        return False
    if (quad[:, 0].min() < -.12*width or quad[:, 0].max() > 1.12*width or
            quad[:, 1].min() < -.12*height or quad[:, 1].max() > 1.12*height):
        return False
    lengths = np.linalg.norm(quad-np.roll(quad, -1, axis=0), axis=1)
    return lengths.min() >= 30 and lengths.max()/lengths.min() <= 12


def _contour_crop(rgb, projected):
    """Select a border by overlap with a verified template projection."""
    small, scale = _small(rgb, 1200)
    gray = cv2.cvtColor(small, cv2.COLOR_RGB2GRAY)
    blur = cv2.GaussianBlur(gray, (5, 5), 0)
    edges = cv2.Canny(blur, 30, 90)
    edges = cv2.morphologyEx(edges, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    _, binary = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    target = _ordered(projected) * scale
    target_area = abs(cv2.contourArea(target))
    best = None
    for mask in (edges, binary):
        contours, _ = cv2.findContours(mask, cv2.RETR_LIST, cv2.CHAIN_APPROX_SIMPLE)
        for contour in contours:
            area = abs(cv2.contourArea(contour))
            if not .45*target_area <= area <= 1.8*target_area:
                continue
            perimeter = cv2.arcLength(contour, True)
            for epsilon in (.01, .02, .03, .04):
                approx = cv2.approxPolyDP(contour, epsilon*perimeter, True)
                if len(approx) != 4 or not cv2.isContourConvex(approx):
                    continue
                quad = _ordered(approx)
                intersection, _ = cv2.intersectConvexConvex(target, quad)
                union = target_area + abs(cv2.contourArea(quad))-intersection
                overlap = intersection/union if union > 0 else 0
                if overlap >= .50 and (best is None or overlap > best[0]):
                    best = overlap, quad/scale
                break
    quad = best[1] if best else _ordered(projected)
    top = np.linalg.norm(quad[1]-quad[0]); bottom = np.linalg.norm(quad[2]-quad[3])
    left = np.linalg.norm(quad[3]-quad[0]); right = np.linalg.norm(quad[2]-quad[1])
    width = max(32, round(max(top, bottom))); height = max(32, round(max(left, right)))
    factor = min(1.0, 1600/max(width, height))
    width = round(width*factor); height = round(height*factor)
    destination = np.float32([[0, 0], [width-1, 0], [width-1, height-1], [0, height-1]])
    transform = cv2.getPerspectiveTransform(quad.astype(np.float32), destination)
    cropped = cv2.warpPerspective(rgb, transform, (width, height), flags=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_CONSTANT, borderValue=(235, 235, 235))
    return Image.fromarray(cropped), {"method": "VERIFIED_DOCUMENT_BORDER" if best else "TEMPLATE_PROJECTED_BOUNDARY",
        "corners": quad.round(2).tolist(), "width": width, "height": height,
        "boundaryOverlap": round(best[0], 4) if best else None}


def register_document(image, template, kind):
    if kind not in PROFILES:
        return None, {"status": "UNVERIFIED", "message": "No configured document profile."}
    original = np.asarray(ImageOps.exif_transpose(image).convert("RGB"))
    reference = np.asarray(template.convert("RGB"))
    source, source_scale = _small(original, 1600)
    target, target_scale = _small(reference, 1000)
    gray_source = cv2.cvtColor(source, cv2.COLOR_RGB2GRAY)
    gray_target = cv2.cvtColor(target, cv2.COLOR_RGB2GRAY)
    mask = np.zeros(gray_target.shape, np.uint8)
    height, width = mask.shape
    for x1, y1, x2, y2 in PROFILES[kind]["staticRegions"]:
        mask[round(y1*height):round(y2*height), round(x1*width):round(x2*width)] = 255
    detector = cv2.SIFT_create(nfeatures=3000, contrastThreshold=.02)
    tk, td = detector.detectAndCompute(gray_target, mask)
    sk, sd = detector.detectAndCompute(gray_source, None)
    unavailable = {"status": "UNVERIFIED", "message": "Template alignment could not be verified. Capture the complete document with readable headings and no glare."}
    if td is None or sd is None or len(sk) < 12 or len(tk) < 12:
        return None, dict(unavailable, reasonCode="INSUFFICIENT_LAYOUT_FEATURES")
    pairs = cv2.BFMatcher(cv2.NORM_L2).knnMatch(td, sd, k=2)
    matches = sorted((p[0] for p in pairs if len(p) == 2 and p[0].distance < .72*p[1].distance), key=lambda m: m.distance)
    unique = []; used = set()
    for match in matches:
        if match.trainIdx not in used:
            unique.append(match); used.add(match.trainIdx)
    matches = unique
    if len(matches) < 12:
        return None, dict(unavailable, reasonCode="TOO_FEW_LAYOUT_MATCHES", matches=len(matches))
    ref_points = np.float32([tk[m.queryIdx].pt for m in matches])
    src_points = np.float32([sk[m.trainIdx].pt for m in matches])
    cv2.setRNGSeed(271828)
    transform, inliers = cv2.findHomography(ref_points, src_points, cv2.RANSAC, 3.0)
    if transform is None or inliers is None:
        return None, dict(unavailable, reasonCode="HOMOGRAPHY_FAILED")
    accepted = inliers.ravel().astype(bool)
    count = int(accepted.sum()); fraction = count/len(matches)
    spread = np.ptp(ref_points[accepted], axis=0) if count else [0, 0]
    if count < 12 or fraction < .5 or spread[0] < .25*width or spread[1] < .025*height:
        return None, dict(unavailable, reasonCode="INCONSISTENT_LAYOUT_MATCHES", inliers=count,
                          matches=len(matches), inlierFraction=round(fraction, 4))
    projected_matches = cv2.perspectiveTransform(ref_points[accepted,None,:], transform).reshape(-1,2)
    error = float(np.median(np.linalg.norm(projected_matches-src_points[accepted], axis=1)))
    full_transform = np.diag([1/source_scale, 1/source_scale, 1]) @ transform @ np.diag([target_scale, target_scale, 1])
    rh, rw = reference.shape[:2]
    ref_corners = np.float32([[[0, 0], [rw-1, 0], [rw-1, rh-1], [0, rh-1]]])
    projected = cv2.perspectiveTransform(ref_corners, full_transform).reshape(4, 2)
    if error > 2.5 or not _valid_quad(projected, original.shape):
        return None, dict(unavailable, reasonCode="IMPLAUSIBLE_DOCUMENT_GEOMETRY", inliers=count)
    aligned = cv2.warpPerspective(original, np.linalg.inv(full_transform), (rw, rh), flags=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_CONSTANT, borderValue=(235, 235, 235))
    crop, crop_info = _contour_crop(original, projected)
    metadata = {"status": "VERIFIED", "method": "STATIC_FEATURE_MATCHING_RANSAC_PERSPECTIVE",
        "matches": len(matches), "inliers": count, "inlierFraction": round(fraction, 4),
        "medianReprojectionError": round(error, 4), "templateToSourceTransform": full_transform.tolist(),
        "projectedTemplateCorners": projected.round(2).tolist(), "crop": crop_info}
    metadata["_croppedImage"] = crop
    return Image.fromarray(aligned), metadata
