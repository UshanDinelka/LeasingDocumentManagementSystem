"""Document discrepancy screening, v5.

Two modes are deliberately distinct:
  1. A genuinely trusted *same-document* image supports change localization.
  2. A generic layout template supports only single-image forensic indicators.

All outputs are REVIEW INDICATORS, not forgery diagnoses or calibrated probabilities.
Image processing is in template coordinates, using alignment already verified by
analysis_preflight.register_document. The module does not perform face recognition.
"""
from __future__ import annotations

import re
from typing import Any

import cv2
import numpy as np
from PIL import Image, ImageDraw
from src.physical_obstruction import detect_obstructions, detect_opaque_coverings


CATEGORIES = {"text": "altered_text", "photo": "photo", "signature": "signature_logo",
              "logo": "signature_logo", "security_feature": "signature_logo"}
EDITING_MARKERS = {
    "show toolbar": "Visible editing toolbar text",
    "background fill": "Editing background-fill control",
    "font size": "Visible font-size control",
    "text box": "Visible editor text-box control",
    "resize handles": "Visible editor resize handles",
    "calibri": "Font-selection text visible in capture",
    "arial": "Font-selection text visible in capture",
    "times new roman": "Font-selection text visible in capture",
}


def _box(region: dict, w: int, h: int):
    return (max(0, int(region["x1"])), max(0, int(region["y1"])),
            min(w, int(region["x2"])), min(h, int(region["y2"])))


def _finding(code, category, region, box, score, reason, mode, metric=None):
    x1, y1, x2, y2 = (int(v) for v in box)
    return {"code": code, "component": category, "region": region,
            "x1": x1, "y1": y1, "x2": x2, "y2": y2,
            "score": round(float(np.clip(score, 0, 100)), 1),
            "reason": reason, "message": reason, "comparisonMode": mode,
            "measurements": metric or {}}


def _correct_exposure(ref, img, ignore_mask):
    """Fit per-channel global lighting while down-weighting changed pixels."""
    reference = cv2.cvtColor(ref, cv2.COLOR_RGB2LAB).astype(np.float32)
    captured = cv2.cvtColor(img, cv2.COLOR_RGB2LAB).astype(np.float32)
    valid = ignore_mask > 0
    corrected = captured.copy()
    for channel in range(3):
        x = captured[:, :, channel][valid]
        y = reference[:, :, channel][valid]
        if len(x) < 200:
            continue
        keep = np.ones(len(x), dtype=bool)
        slope, offset = 1.0, 0.0
        for _ in range(3):
            if np.std(x[keep]) > 2:
                slope = float(np.clip(np.cov(x[keep], y[keep], bias=True)[0, 1] /
                                      (np.var(x[keep]) + 1e-5), .83, 1.20))
            offset = float(np.median(y[keep] - slope*x[keep]))
            residual = np.abs(slope*x + offset - y)
            keep = residual <= max(9.0, np.quantile(residual, .75))
        corrected[:, :, channel] = captured[:, :, channel]*slope + offset
    return reference, np.clip(corrected, 0, 255)


def compare_same_document(reference_rgb: np.ndarray, document_rgb: np.ndarray, regions: dict):
    """Locate strong localized changes between photographs of the same document.

    Requires two independently acquired, co-registered images. Template equality
    or merely a matching document type is NOT sufficient.
    """
    if reference_rgb.shape != document_rgb.shape:
        raise ValueError("Same-document comparison requires identically registered images")
    h, w = reference_rgb.shape[:2]
    reference = cv2.GaussianBlur(reference_rgb, (3, 3), 0)
    document = cv2.GaussianBlur(document_rgb, (3, 3), 0)
    valid = np.zeros((h, w), np.uint8)
    margin = max(5, round(min(h, w)*.018))
    valid[margin:h-margin, margin:w-margin] = 1
    ref_lab, doc_lab = _correct_exposure(reference, document, valid)
    # Small alignment and compression residuals are common: ignore low contrast.
    delta = np.linalg.norm((ref_lab - doc_lab)*np.array([.8, 1.1, 1.1], np.float32), axis=2)
    delta = cv2.GaussianBlur(delta.astype(np.float32), (3, 3), 0)
    refgray = cv2.cvtColor(reference, cv2.COLOR_RGB2GRAY)
    docgray = cv2.cvtColor(document, cv2.COLOR_RGB2GRAY)
    gradref = cv2.Laplacian(refgray, cv2.CV_32F, ksize=3)
    graddoc = cv2.Laplacian(docgray, cv2.CV_32F, ksize=3)
    # Don't amplify small source-camera differences. A changed field can be
    # detected by large color OR structural differences, not simply by sharpness.
    gradient = cv2.GaussianBlur(np.abs(gradref - graddoc), (5, 5), 0)
    base = delta[valid > 0]
    med = float(np.median(base)); mad = float(np.median(np.abs(base - med)))
    threshold = max(23.0, med + 5*1.4826*mad)
    changed = (((delta > threshold) & (gradient > 10)) |
               (delta > max(threshold*1.6, 37))).astype(np.uint8)
    changed &= valid
    global_changed_fraction = float(changed.sum()/max(1, valid.sum()))
    if global_changed_fraction > .30 or med > 35:
        return {"comparison_status": "UNRELIABLE", "reason": "Images differ widely in color, angle or artwork; a trustworthy local change cannot be isolated.",
                "globalChangedFraction": round(global_changed_fraction, 4), "medianResidual": round(med, 2), "findings": []}
    # Connect letters within the same edit, while preserving separate regions.
    findings = []
    for name, r in regions.items():
        x1, y1, x2, y2 = _box(r, w, h)
        if x2-x1 < 15 or y2-y1 < 12:
            continue
        roi_change = changed[y1:y2, x1:x2].copy()
        rh, rw = roi_change.shape
        if rw*rh < 100:
            continue
        kernel = cv2.getStructuringElement(cv2.MORPH_RECT,
            (max(3, min(19, (rw//30)*2+1)), max(3, min(9, (rh//45)*2+1))))
        joined = cv2.morphologyEx(roi_change, cv2.MORPH_CLOSE, kernel)
        joined = cv2.dilate(joined, np.ones((3, 3), np.uint8), iterations=1)
        n, labels, stats, _ = cv2.connectedComponentsWithStats(joined, 8)
        candidates = []
        for i in range(1, n):
            x, y, bw, bh, area = (int(v) for v in stats[i])
            actual = int(np.count_nonzero(roi_change[y:y+bh, x:x+bw]))
            if actual < max(12, round(rw*rh*.002)) or area < 18:
                continue
            if bw < max(7, rw*.04) and bh < max(6, rh*.12):
                continue
            fraction = actual/max(1, rw*rh)
            # High score for substantial, localized genuine-vs-supplied differences.
            score = min(97, 68 + 20*min(1., fraction/.025) + 9*min(1., actual/300.))
            bx = [max(0, x1+x-3), max(0, y1+y-3), min(w, x1+x+bw+3), min(h, y1+y+bh+3)]
            candidates.append(_finding("REFERENCE_REGION_CHANGED", CATEGORIES.get(r["type"], "background"),
                name, bx, score, "The corresponding region differs from the trusted image of this SAME document. Investigate whether it was replaced, covered, or recaptured differently.",
                "TRUSTED_SAME_DOCUMENT", {"changedPixels": actual, "fieldChangedFraction": round(fraction, 5)}))
        findings.extend(sorted(candidates, key=lambda f: f["score"], reverse=True)[:3])
    # Find suspicious changes outside configured regions as well.
    region_mask = np.zeros((h, w), np.uint8)
    for r in regions.values():
        x1, y1, x2, y2 = _box(r, w, h)
        region_mask[y1:y2, x1:x2] = 1
    background = changed*(1-region_mask)
    thick = cv2.morphologyEx(background, cv2.MORPH_CLOSE, np.ones((9, 9), np.uint8))
    n, _, stats, _ = cv2.connectedComponentsWithStats(thick, 8)
    for x, y, bw, bh, area in stats[1:]:
        if area < max(90, w*h*.002):
            continue
        actual = int(np.count_nonzero(background[y:y+bh, x:x+bw]))
        if actual < 50:
            continue
        findings.append(_finding("REFERENCE_BACKGROUND_CHANGED", "background", "document_background",
            (x,y,x+bw,y+bh), min(91, 64+actual/(w*h)*800),
            "Background differs locally from the trusted image of this same document.",
            "TRUSTED_SAME_DOCUMENT", {"changedPixels": actual}))
    return {"comparison_status": "ASSESSED", "globalChangedFraction": round(global_changed_fraction, 4),
            "medianResidual": round(med, 2), "threshold": round(threshold, 2),
            "findings": sorted(findings, key=lambda f: f["score"], reverse=True)[:25]}


def scan_field_surface(document_rgb, regions):
    """Conservative single-image search for suspicious flat rectangular patches.

    Not evidence that personal details differ from a person's actual details;
    shadows, laminated surfaces, and normal printing can mimic these features.
    """
    h, w = document_rgb.shape[:2]
    gray = cv2.cvtColor(document_rgb, cv2.COLOR_RGB2GRAY)
    lab = cv2.cvtColor(document_rgb, cv2.COLOR_RGB2LAB)
    findings = []
    for name, r in regions.items():
        if r.get("type") not in ("text", "photo", "signature", "logo", "security_feature"):
            continue
        x1, y1, x2, y2 = _box(r, w, h)
        rw, rh = x2-x1, y2-y1
        if rw < 35 or rh < 22:
            continue
        patch = gray[y1:y2, x1:x2]
        # Long nearly perfectly straight orthogonal lines can indicate a pasted
        # rectangle or a visible UI bounding box. Normal printed frames also
        # produce lines, therefore LOW/MEDIUM review indicator only.
        edges = cv2.Canny(patch, 45, 120)
        lines = cv2.HoughLinesP(edges, 1, np.pi/180, 19,
                               minLineLength=max(18, int(min(rw, rh)*.18)), maxLineGap=6)
        if lines is None:
            continue
        horizontal, vertical = [], []
        for xA,yA,xB,yB in lines[:,0]:
            length = float(np.hypot(xB-xA, yB-yA))
            if length < 20: continue
            if abs(yB-yA) <= 2 and length >= max(25, rw*.18):
                horizontal.append((min(xA,xB),max(xA,xB),round((yA+yB)/2),length))
            if abs(xB-xA) <= 2 and length >= max(20, rh*.20):
                vertical.append((min(yA,yB),max(yA,yB),round((xA+xB)/2),length))
        # Find aligned rectangle corners rather than flagging a single underline.
        best = None
        for h1 in horizontal:
            for h2 in horizontal:
                if h2[2]-h1[2] < max(11,rh*.12): continue
                left, right = max(h1[0],h2[0]), min(h1[1],h2[1])
                if right-left < max(18,rw*.17): continue
                ytop, ybottom = h1[2], h2[2]
                ls = [v for v in vertical if abs(v[2]-left)<7 and v[0]<=ytop+7 and v[1]>=ybottom-7]
                rs = [v for v in vertical if abs(v[2]-right)<7 and v[0]<=ytop+7 and v[1]>=ybottom-7]
                if not (ls and rs): continue
                if best is None or (right-left)*(ybottom-ytop) > best[0]:
                    best=((right-left)*(ybottom-ytop),(left,ytop,right,ybottom))
        # An internal patch may have a partially obscured top edge (letters or
        # portrait crossing it). Use the parallel vertical pair as primary
        # evidence and check for horizontal edges near both endpoints.
        for left_v in vertical:
            for right_v in vertical:
                left, right = left_v[2], right_v[2]
                if right-left < rw*.22 or left<rw*.055 or right>rw*.95:continue
                top=max(left_v[0],right_v[0]);bottom=min(left_v[1],right_v[1])
                if bottom-top < rh*.20:continue
                top_edges=[line for line in horizontal if abs(line[2]-top)<=8 and
                           max(0,min(line[1],right)-max(line[0],left)) >= .52*(right-left)]
                bottom_edges=[line for line in horizontal if abs(line[2]-bottom)<=8 and
                              max(0,min(line[1],right)-max(line[0],left)) >= .52*(right-left)]
                if not top_edges or not bottom_edges:continue
                if best is None or (right-left)*(bottom-top)>best[0]:
                    best=((right-left)*(bottom-top),(left,top,right,bottom))
        if best:
            left, top, right, bottom = best[1]
            # Reject boxes that simply follow the full ROI perimeter.
            if right-left < .92*rw and bottom-top < .94*rh:
                findings.append(_finding("RECTANGULAR_OVERLAY_EDGE", CATEGORIES[r["type"]], name,
                    (x1+left,y1+top,x1+right,y1+bottom), 53 if r["type"] == "photo" else 43,
                    "Straight rectangular boundaries inside a sensitive field may indicate an overlay or editing handles; printed boxes can look similar.",
                    "SINGLE_IMAGE", {"edgeAreaPixels": int(best[0])}))
        # Search for broad almost-uniform filled rectangles with a sharply
        # discontinuous color boundary, using several candidate window sizes.
        # Ignore clean fields without strong border discontinuities.
        lab_patch = lab[y1:y2, x1:x2].astype(np.float32)
        mono = cv2.GaussianBlur(lab_patch[:,:,0], (7,7), 0)
        gy, gx = np.gradient(mono)
        for frac in (.2, .35):
            kw = max(15,int(rw*frac)); kh = max(12,int(rh*frac*.55))
            if kw>=rw-8 or kh>=rh-8: continue
            # Low-variance patch with a boundary sharply different from nearby
            # pixels. This alone is insufficient for HIGH classification.
            gray_blur = cv2.GaussianBlur(patch, (5, 5), 0).astype(np.float32)
            mean = cv2.boxFilter(gray_blur,-1,(kw,kh), normalize=True)
            sq = cv2.boxFilter(gray_blur**2,-1,(kw,kh), normalize=True)
            std = np.sqrt(np.maximum(0,sq-mean**2))
            boundary = cv2.boxFilter(np.hypot(gx,gy),-1,(kw,kh), normalize=True)
            candidate = (std<4.2)&(boundary>6.5)
            candidate[:kh//2,:]=0;candidate[-kh//2:,:]=0
            candidate[:,:kw//2]=0;candidate[:,-kw//2:]=0
            count = int(candidate.sum())
            if count >= 4:
                cy,cx=np.unravel_index(int(np.argmax(boundary*candidate)),candidate.shape)
                rect=(x1+cx-kw//2,y1+cy-kh//2,x1+cx+kw//2,y1+cy+kh//2)
                findings.append(_finding("FLAT_PATCH_ANOMALY", CATEGORIES[r["type"]], name,
                    rect, 38, "Flat area with an abrupt image boundary near an important field; inspect for covering or erasure.",
                    "SINGLE_IMAGE", {"localContrast": round(float(boundary[cy,cx]),2)}))
                break
    return findings


def inspect_ocr(ocr, regions, document_shape, alignment=None):
    """Flag visible editor controls and sizeable OCR typography inconsistencies.

    OCR geometries are converted from ORIGINAL-image coordinates to registered
    document coordinates with the inverse template-to-source homography.
    """
    text = re.sub(r"\s+", " ", str(ocr.get("fullText", ""))).lower()
    markers = [v for k,v in EDITING_MARKERS.items() if re.search(r"\b"+re.escape(k)+r"\b",text)]
    h,w=document_shape[:2]
    suspicious = []
    if len(markers) >= 2 or any(k in text for k in ("show toolbar", "background fill")):
        strength = 86 if len(markers)>=3 else 76
        suspicious.append(_finding("VISIBLE_EDITOR_ARTIFACTS", "altered_text", "document_capture",
            (0,0,w,h), strength,
            "OCR contains text associated with a document-editing interface: "+"; ".join(markers[:5])+". Verify that only the physical document was captured.",
            "SINGLE_IMAGE", {"markers": markers}))
    lines = ocr.get("lines",[])
    transform = None
    try:
        transform = np.linalg.inv(np.asarray(alignment["templateToSourceTransform"],np.float64))
    except (TypeError, ValueError, KeyError, np.linalg.LinAlgError):
        pass
    parsed = []
    if transform is not None:
        for line in lines[:500]:
            if not isinstance(line,dict) or not isinstance(line.get("text"),str): continue
            b = line.get("boundingBox",{})
            if not isinstance(b,dict): continue
            try:
                a,bx,c,d=(float(b[k]) for k in ("left","top","right","bottom"))
                if c<=a or d<=bx:continue
                points=np.float32([[[a,bx]],[[c,d]],[[a,d]],[[c,bx]]])
                projected=cv2.perspectiveTransform(points,transform).reshape(4,2)
                minx,miny=projected.min(axis=0);maxx,maxy=projected.max(axis=0)
                if minx<0 or miny<0 or maxx>w or maxy>h: continue
                parsed.append({"text":line["text"],"bbox":(float(minx),float(miny),float(maxx),float(maxy)),
                               "height":float(maxy-miny)})
            except (KeyError,TypeError,ValueError,cv2.error):continue
    # Font-size is not directly knowable from a photo. OCR bounding-box height
    # is an imperfect proxy, so only extreme outliers receive modest scores.
    valid_heights=[p["height"] for p in parsed if len(p["text"].strip())>=3 and p["height"]>5]
    if len(valid_heights)>=7:
        mid=float(np.median(valid_heights))
        for p in parsed:
            if p["height"]<=max(2.0*mid,32):continue
            px1,py1,px2,py2=p["bbox"]
            for name,region in regions.items():
                if region["type"]!="text":continue
                x1,y1,x2,y2=(region[k] for k in ("x1","y1","x2","y2"))
                overlap=max(0,min(px2,x2)-max(px1,x1))*max(0,min(py2,y2)-max(py1,y1))
                if overlap/((px2-px1)*(py2-py1)+1)<.7:continue
                suspicious.append(_finding("TEXT_HEIGHT_OUTLIER","altered_text",name,
                    (px1,py1,px2,py2),36,
                    "Text line height differs strongly from the document's other OCR lines; investigate font size or scaling.",
                    "SINGLE_IMAGE",{"lineHeight":round(p["height"],1),"medianHeight":round(mid,1)}))
                break
    return suspicious


def aggregate_risk(findings, reference_status, baseline=None):
    """Evidence-based review priority. Numbers are NOT fraud probabilities."""
    unique = {}
    for f in findings:
        key = (f["code"], f["region"], f["x1"]//20, f["y1"]//20)
        if key not in unique or f["score"]>unique[key]["score"]:
            unique[key] = f
    findings = sorted(unique.values(), key=lambda f:f["score"], reverse=True)
    if findings:
        top = findings[0]["score"]
        # Independent supplementary findings can raise review priority modestly.
        second = max((f["score"] for f in findings[1:] if f["code"] != findings[0]["code"]), default=0.)
        score = min(100., top + min(8., second*.09))
    else:
        score = 0.
    if baseline is not None and baseline > score:
        score = baseline
    risk = "HIGH" if score>=65 else "MEDIUM" if score>=35 else "LOW"
    active = ["template_fixed_artwork", "single_image_surface", "physical_occlusion_screening", "ocr_editor_artifacts"]
    if reference_status == "ASSESSED":
        active.append("same_document_change_localization")
        limited = ["identity_validation", "signature_authenticity", "fraud_probability"]
    else:
        limited = ["verified_name_change", "verified_id_change", "verified_photo_change",
                   "verified_signature_change", "identity_validation", "fraud_probability"]
    return {"risk_score": round(score,1), "risk_level":risk, "recommended_action":"MANUAL_REVIEW_REQUIRED",
            "finding_count":len(findings), "findings":findings[:35],
            "component_scores":{key:max((f["score"] for f in findings if f["component"]==key),default=0)
                                for key in ("altered_text","photo","signature_logo","background","ocr","fixed_artwork","physical_obstruction","customer_crosscheck")},
            "detector_coverage":{"active": active, "not_assessed":limited,
                                 "trustedReferenceAvailable":reference_status=="ASSESSED"},
            "decision_note":"Evidence-priority score, NOT probability of forgery. LOW does not verify authenticity. All flagged items require human review.",
            "reference_comparison_status":reference_status}


def annotate(rgb, findings, save_path):
    image = Image.fromarray(rgb).convert("RGB")
    draw = ImageDraw.Draw(image)
    w,h=image.size
    for f in findings:
        if f["region"]=="document_capture":
            continue  # Text-only OCR warning has no justified localized box.
        x1,y1,x2,y2=(int(f[k]) for k in ("x1","y1","x2","y2"))
        if x2<=x1 or y2<=y1:continue
        color = (255,40,35) if f["score"]>=65 else (255,160,0)
        draw.rectangle((max(0,x1),max(0,y1),min(w-1,x2),min(h-1,y2)),outline=color,width=3)
        draw.text((max(0,x1),max(0,y1-14)),f["code"][:32],fill=color,stroke_width=1,stroke_fill=(0,0,0))
    image.save(save_path)


def evaluate_document(template_path, document_path, document_type, metadata, ocr, alignment,
                      trusted_reference_path=None, ocr_validation=None):
    with Image.open(template_path) as f: reference_template=np.asarray(f.convert("RGB"))
    with Image.open(document_path) as f: doc=np.asarray(f.convert("RGB"))
    h,w=doc.shape[:2]
    mode="NOT_PROVIDED"; reference_evidence={}
    if trusted_reference_path is not None:
        with Image.open(trusted_reference_path) as f:
            trusted=np.asarray(f.convert("RGB"))
        reference_evidence=compare_same_document(trusted,doc,metadata)
        mode=reference_evidence["comparison_status"]
    static = []
    # Existing comparison correctly ignores holder-variant fields.
    from src.visual_evidence import analyse_visual_evidence
    fixed=analyse_visual_evidence(template_path, document_path, document_type, metadata)
    if fixed["comparison_status"] == "ASSESSED":
        for area in fixed["suspicious_areas"]:
            static.append(_finding("FIXED_ARTWORK_DIFFERENCE", "fixed_artwork",area["region"],
                (area["x1"],area["y1"],area["x2"],area["y2"]),area["score"],
                "Fixed artwork differs from the configured document layout template.", "TEMPLATE_ONLY"))
    surface = scan_field_surface(doc,metadata)
    # A printed rectangle already present on approved stationery is not a
    # pasted-photo/text seam. Reject matching artwork before risk aggregation.
    def printed_in_template(f):
        if f['code'] != 'RECTANGULAR_OVERLAY_EDGE': return False
        x1,y1,x2,y2=[int(f[k]) for k in ('x1','y1','x2','y2')]
        x1=max(0,x1);y1=max(0,y1);x2=min(w,x2);y2=min(h,y2)
        if x2-x1<10 or y2-y1<10: return False
        a=cv2.cvtColor(reference_template[y1:y2,x1:x2],cv2.COLOR_RGB2GRAY)
        b=cv2.cvtColor(doc[y1:y2,x1:x2],cv2.COLOR_RGB2GRAY)
        if a.size==0 or b.size==0: return False
        ea=cv2.dilate(cv2.Canny(a,60,160),np.ones((3,3),np.uint8))>0
        eb=cv2.dilate(cv2.Canny(b,60,160),np.ones((3,3),np.uint8))>0
        return float(np.mean(np.abs(a.astype(np.float32)-b.astype(np.float32))))<8 and \
            (np.count_nonzero(ea & eb)/max(1,np.count_nonzero(ea | eb)))>.65
    surface=[f for f in surface if not printed_in_template(f)]
    physical = (detect_obstructions(doc,reference_template,metadata) +
                detect_opaque_coverings(doc,reference_template,metadata))
    field_background = compare_template_field_background(reference_template,doc,metadata)
    editor = inspect_ocr(ocr,metadata,doc.shape,alignment)
    ocr_findings = []
    for issue in (ocr_validation or {}).get("issues", [])[:15]:
        code = str(issue.get("code", ""))
        # OCR failures due to camera quality are not proof of document forgery.
        # They are displayed as follow-up items without a HIGH risk score.
        ocr_findings.append(_finding("OCR_FIELD_REVIEW", "ocr", str(issue.get("field", "text")),
            (0,0,w,h), 24 if code.startswith("INVALID_") else 12,
            "OCR needs manual verification: " + str(issue.get("message", code))[:180],
            "OCR_FORMAT_CHECK", {"issueCode":code}))
    findings = static + surface + physical + field_background + editor + ocr_findings + reference_evidence.get("findings",[])
    risk = aggregate_risk(findings,mode)
    return {"risk":risk,"reference":reference_evidence,"referenceStatus":mode,
            "templateEvidence":fixed,"surfaceFindings":surface,"physicalFindings":physical,"fieldBackgroundFindings":field_background,"editorFindings":editor,"ocrFindings":ocr_findings}


def compare_template_field_background(template_rgb, document_rgb, regions):
    """Check preserved printed BACKGROUND behind editable text regions.

    Uses local upper-envelope luminance to suppress black typography in both
    images. Designed to find white-out, pasted flat boxes and overwritten
    patterned background, NOT to tell whether a different person's text matches.
    """
    if template_rgb.shape != document_rgb.shape:
        return []
    h, w = document_rgb.shape[:2]
    gray_t = cv2.cvtColor(template_rgb, cv2.COLOR_RGB2GRAY)
    gray_d = cv2.cvtColor(document_rgb, cv2.COLOR_RGB2GRAY)
    # Morphological closing fills dark ink strokes so unrelated names are not
    # directly compared. Bright-polarity embossing remains a limitation.
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(17,17))
    bg_t = cv2.GaussianBlur(cv2.morphologyEx(gray_t,cv2.MORPH_CLOSE,kernel),(0,0),5)
    bg_d = cv2.GaussianBlur(cv2.morphologyEx(gray_d,cv2.MORPH_CLOSE,kernel),(0,0),5)
    difference = cv2.absdiff(bg_t,bg_d).astype(np.float32)
    # Baseline from fixed header, excluding borders and variable fields.
    mask = np.ones((h,w),np.uint8)
    mask[:max(8,h//40)]=0;mask[-max(8,h//40):]=0
    mask[:,:max(8,w//40)]=0;mask[:,-max(8,w//40):]=0
    for reg in regions.values():
        x1,y1,x2,y2=_box(reg,w,h)
        mask[y1:y2,x1:x2]=0
    base=difference[mask>0]
    median=float(np.median(base));mad=float(np.median(np.abs(base-median)))
    threshold=max(28, median + 5*1.4826*mad)
    if median>24:
        # Template edition or capture conditions differ too much for this test.
        return []
    findings=[]
    for name,reg in regions.items():
        if reg.get('type')!='text':continue
        x1,y1,x2,y2=_box(reg,w,h)
        rh,rw=y2-y1,x2-x1
        if rh<23 or rw<50:continue
        deviation=difference[y1:y2,x1:x2]
        # Require thick sustained bright/washed-out regions, not isolated ink.
        binary=(deviation>threshold).astype(np.uint8)
        binary=cv2.morphologyEx(binary,cv2.MORPH_OPEN,np.ones((5,5),np.uint8))
        n,labels,stats,_=cv2.connectedComponentsWithStats(binary,8)
        for index in range(1,n):
            x,y,bw,bh,area=(int(v) for v in stats[index])
            if area<max(100,rw*rh*.005) or bw<max(17,rw*.045) or bh<10:continue
            # Bright paper-white patches replacing watermarks are typical.
            roi_luma=bg_d[y1+y:y1+y+bh,x1+x:x1+x+bw]
            reference_luma=bg_t[y1+y:y1+y+bh,x1+x:x1+x+bw]
            measured=float(np.median(deviation[y:y+bh,x:x+bw][labels[y:y+bh,x:x+bw]==index]))
            score=min(67.,39+max(0,measured-threshold)*.55+min(10,area/(rw*rh)*130))
            findings.append(_finding('FIELD_BACKGROUND_DISCONTINUITY','background',name,
                (x1+x,y1+y,x1+x+bw,y1+y+bh),score,
                'Background under a personal-information field differs substantially from approved printed artwork. Possible covering or replacement; check shadows and template edition.',
                'TEMPLATE_BACKGROUND_ONLY',{'localMedianDeviation':round(measured,1),
                   'referenceMedianBrightness':round(float(np.median(reference_luma)),1),
                   'documentMedianBrightness':round(float(np.median(roi_luma)),1)}))
    return sorted(findings,key=lambda f:f['score'],reverse=True)[:15]
