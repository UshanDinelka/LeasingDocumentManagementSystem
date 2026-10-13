"""Conservative one-image detection of opaque, rectangular overlays in printed text regions.

This is a screening heuristic, NOT proof that text has been altered. It intentionally
misses seamless edits and may flag genuinely white labels, stickers or glare.
Only field ROIs from the configured document layout are examined.
"""

from pathlib import Path

DETECTOR_VERSION = "adaptive-overlay-v2-2026-10-11"

import cv2
import numpy as np
from PIL import Image


def detect_opaque_text_overlays(image_path, regions):
    with Image.open(Path(image_path)) as im:
        rgb = np.asarray(im.convert("RGB"))
    height, width = rgb.shape[:2]
    findings = []
    for name, region in regions.items():
        if region.get("type") != "text":
            continue
        x1 = max(0, int(region["x1"]))
        y1 = max(0, int(region["y1"]))
        x2 = min(width, int(region["x2"]))
        y2 = min(height, int(region["y2"]))
        if x2-x1 < 50 or y2-y1 < 35:
            continue
        crop = rgb[y1:y2, x1:x2]
        bright = crop.min(axis=2) >= 241
        neutral = crop.max(axis=2).astype(np.int16) - crop.min(axis=2) < 20
        white = (bright & neutral).astype(np.uint8)
        # Join white runs interrupted by printing, without joining distant patches.
        kernel_width = max(9, min(25, (width // 45) | 1))
        closed = cv2.morphologyEx(
            white, cv2.MORPH_CLOSE,
            cv2.getStructuringElement(cv2.MORPH_RECT, (kernel_width, 7)),
        )
        contours, _ = cv2.findContours(closed, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        for contour in contours:
            x, y, bw, bh = cv2.boundingRect(contour)
            if (bw < 0.075*width or bh < 0.023*height or
                    bw / max(1, bh) < 2.5 or bh > 0.15*height):
                continue
            if (x < 2 or y < 3 or x+bw+2 >= crop.shape[1] or y+bh+3 >= crop.shape[0]):
                continue
            piece = crop[y:y+bh, x:x+bw]
            whiteness = float(white[y:y+bh, x:x+bw].mean())
            coverage = float(cv2.contourArea(contour) / max(1, bw*bh))
            if whiteness < .53 or coverage < .75:
                continue
            # Require a genuinely dark text/mark inside the otherwise white patch.
            grayscale = cv2.cvtColor(piece, cv2.COLOR_RGB2GRAY)
            dark_fraction = float((grayscale < 125).mean())
            if not .01 <= dark_fraction <= .36:
                continue
            # Abrupt, approximately rectangular white area against coloured paper.
            top = crop[max(0,y-12):y-2, x:x+bw]
            bottom = crop[y+bh+2:min(crop.shape[0],y+bh+12), x:x+bw]
            if top.size < 100 or bottom.size < 100:
                continue
            surround_luma = float(np.median(np.concatenate((top.reshape(-1, 3), bottom.reshape(-1, 3)),axis=0).mean(axis=1)))
            white_luma = float(np.median(piece[white[y:y+bh, x:x+bw].astype(bool)].mean(axis=1)))
            if white_luma-surround_luma < 24:
                continue
            # Strong candidate for local manual review, not a forgery classification.
            findings.append({
                "region": name,
                "finding_type": "POSSIBLE_OPAQUE_TEXT_OVERLAY",
                "reason": "Unnaturally uniform light rectangle with dark print interrupts the coloured document background; inspect for pasted/covered text. Not proof of forgery.",
                "score": 78.0,
                "x1": int(x1+x), "y1": int(y1+y),
                "x2": int(x1+x+bw), "y2": int(y1+y+bh),
                "white_pixel_fraction": round(whiteness, 4),
                "dark_print_fraction": round(dark_fraction, 4),
                "background_contrast": round(white_luma-surround_luma, 2),
            })
    # Camera/display tolerant check. In a phone photo a pure-white text box can
    # become purple/grey, so a threshold like RGB >= 241 misses it completely.
    # This is an anomaly heuristic, NOT confirmation of a changed identity.
    for name, region in regions.items():
        if region.get("type") != "text":
            continue
        x1 = max(0, int(region["x1"]))
        y1 = max(0, int(region["y1"]))
        x2 = min(width, int(region["x2"]))
        y2 = min(height, int(region["y2"]))
        if x2 - x1 < 80 or y2 - y1 < 40:
            continue
        crop = rgb[y1:y2, x1:x2]
        gray = cv2.cvtColor(crop, cv2.COLOR_RGB2GRAY)
        region_width = crop.shape[1]
        # The local median models surrounding security-paper illumination;
        # it does not assume a particular absolute brightness or white balance.
        median_kernel = max(31, min(121, (region_width // 5) | 1))
        background = cv2.medianBlur(gray, median_kernel)
        relative_light = gray.astype(np.int16) - background.astype(np.int16)
        mask = (relative_light > 13).astype(np.uint8)
        close_width = max(9, min(31, ((int(width * 0.022) // 2) * 2) + 1))
        mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE,
                                cv2.getStructuringElement(cv2.MORPH_RECT, (close_width, 5)))
        count, _, stats, _ = cv2.connectedComponentsWithStats(mask, connectivity=8)
        for index in range(1, count):
            xx, yy, bw, bh, occupancy = (int(value) for value in stats[index])
            if (bw < 0.10 * width or bh < 0.025 * height or
                    bw / max(1, bh) < 2.8 or bh > 0.13 * height or
                    occupancy / max(1, bw * bh) < 0.55):
                continue
            if xx <= 2 or yy <= 2 or xx + bw >= region_width - 2 or yy + bh >= len(crop) - 2:
                continue
            body = gray[yy:yy + bh, xx:xx + bw]
            # A coloured blank patch can qualify when it is strongly different
            # from its surroundings, but subtle rectangles must have dark print.
            dark_print_fraction = float((body < np.median(body) - 40).mean())
            above = gray[max(0, yy - 14):yy, xx:xx + bw]
            below = gray[yy + bh:min(len(gray), yy + bh + 14), xx:xx + bw]
            nearby = [sample.reshape(-1) for sample in (above, below) if sample.size]
            if not nearby:
                continue
            local_contrast = float(np.median(body) - np.median(np.concatenate(nearby)))
            if dark_print_fraction < 0.03 and local_contrast < 22:
                continue
            findings.append({
                "region": name,
                "finding_type": "POSSIBLE_OPAQUE_TEXT_OVERLAY",
                "detection_method": "LOCAL_RELATIVE_LUMINANCE_RECTANGLE_V2",
                "reason": "Unusually light and rectangular low-texture area in a printed field; may be a pasted overlay or photographed editor UI. Recapture and inspect; not proof of falsification.",
                "score": 78.0,
                "x1": int(x1 + xx), "y1": int(y1 + yy),
                "x2": int(x1 + xx + bw), "y2": int(y1 + yy + bh),
                "relative_bright_area_fraction": round(occupancy / max(1, bw * bh), 4),
                "dark_print_fraction": round(dark_print_fraction, 4),
                "local_luminance_contrast": round(local_contrast, 2),
            })

    # Drop overlapping candidates (e.g., name ROI overlapping sex ROI).
    findings.sort(key=lambda item: (item["x1"], item["y1"]))
    unique = []
    for item in findings:
        if any((min(item["x2"], old["x2"]) - max(item["x1"], old["x1"]) >
                .75*min(item["x2"]-item["x1"],old["x2"]-old["x1"]) and
                min(item["y2"], old["y2"]) - max(item["y1"], old["y1"]) >
                .75*min(item["y2"]-item["y1"],old["y2"]-old["y1"])) for old in unique):
            continue
        unique.append(item)
    return unique
