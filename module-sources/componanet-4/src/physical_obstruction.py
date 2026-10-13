"""Lightweight physical finger/object *screening* (no claims of object identity).

Works on an aligned RGB document. Requires an anomalous, coherent skin-like
region in a normally printed text/security area. Photographs are excluded;
document color and lighting can yield misses/false positives.
"""
from __future__ import annotations
import cv2
import numpy as np


def detect_obstructions(rgb, template_rgb, regions, *, max_findings=5):
    h,w=rgb.shape[:2]
    if min(h,w)<160: return []
    # Screen within document body, excluding any declared holder photo.
    excluded=np.zeros((h,w),np.uint8)
    for region in regions.values():
        if region.get('type')=='photo':
            x1,y1,x2,y2=(int(region[k]) for k in ('x1','y1','x2','y2'))
            cv2.rectangle(excluded,(max(0,x1-8),max(0,y1-8)),(min(w-1,x2+8),min(h-1,y2+8)),255,-1)
    hsv=cv2.cvtColor(rgb,cv2.COLOR_RGB2HSV)
    ycc=cv2.cvtColor(rgb,cv2.COLOR_RGB2YCrCb)
    # Both chroma ranges are intentionally conservative. This is an anomaly
    # detector, not a classifier of the person's skin colour.
    skin=(cv2.inRange(ycc,np.array([40,132,75],np.uint8),np.array([245,182,137],np.uint8))>0)
    skin &= (hsv[:,:,1]>=35) & (hsv[:,:,1]<=230)
    if template_rgb.shape==rgb.shape:
        diff=np.abs(cv2.GaussianBlur(rgb.astype(np.float32),(9,9),0)-cv2.GaussianBlur(template_rgb.astype(np.float32),(9,9),0)).mean(axis=2)
        # Regions that already have comparable print in the template should
        # not be classed as an occluding object.
        skin &= diff>30
    skin[:int(h*.08),:]=0; skin[-int(h*.055):,:]=0
    skin[:,:int(w*.04)]=0; skin[:,-int(w*.04):]=0
    skin[excluded>0]=False
    binary=(skin.astype('uint8')*255)
    kernel=cv2.getStructuringElement(cv2.MORPH_ELLIPSE,(max(5,round(w/180)|1),max(5,round(h/130)|1)))
    binary=cv2.morphologyEx(binary,cv2.MORPH_CLOSE,kernel,iterations=2)
    binary=cv2.morphologyEx(binary,cv2.MORPH_OPEN,kernel)
    contours,_=cv2.findContours(binary,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
    findings=[]
    for contour in contours:
        area=cv2.contourArea(contour)
        if area < .008*w*h or area > .35*w*h:continue
        x,y,bw,bh=cv2.boundingRect(contour)
        if bw<.055*w or bh<.07*h:continue
        fill=area/max(1,bw*bh)
        if fill<.30:continue
        # A plausible object should cover/overlap a named text or security area.
        overlap=[]
        for name,r in regions.items():
            if r.get('type') not in ('text','signature','logo','security_feature'):continue
            a=max(0,min(x+bw,r['x2'])-max(x,r['x1']))*max(0,min(y+bh,r['y2'])-max(y,r['y1']))
            if a>max(.004*w*h,.06*bw*bh):overlap.append((a,name))
        if not overlap:continue
        overlap.sort(reverse=True)
        # Camera shadows can resemble obstructions: keep risk as a screening
        # priority only; never claim this proves physical obstruction.
        score=float(min(74.,55+min(18,area/(w*h)*220)))
        findings.append({'code':'POSSIBLE_PHYSICAL_OCCLUSION','component':'physical_obstruction',
            'region':overlap[0][1], 'x1':int(x),'y1':int(y),'x2':int(x+bw),'y2':int(y+bh),
            'score':round(score,1),
            'reason':'Coherent foreground-colour region overlaps important printed content; inspect for finger, object, shadow, or covering.',
            'message':'Possible physical object covers a sensitive field',
            'comparisonMode':'SINGLE_IMAGE_HEURISTIC',
            'measurements':{'areaFraction':round(area/(w*h),4),'fillRatio':round(fill,3)}})
    return sorted(findings,key=lambda a:a['score'],reverse=True)[:max_findings]


def detect_opaque_coverings(rgb, template_rgb, regions, *, max_findings=5):
    """Screen for broad, abnormally flat *non-skin* opaque field coverings.

    Requires verified geometry and compatible template appearance. Results are
    medium-priority possible coverings, not proof that an object is a finger.
    """
    if rgb.shape != template_rgb.shape:
        return []
    h,w=rgb.shape[:2]
    if min(h,w)<160: return []
    gray=cv2.cvtColor(rgb,cv2.COLOR_RGB2GRAY).astype(np.float32)
    other=cv2.cvtColor(template_rgb,cv2.COLOR_RGB2GRAY).astype(np.float32)
    difference=np.abs(cv2.GaussianBlur(gray,(11,11),0)-cv2.GaussianBlur(other,(11,11),0))
    # Global exposure/template mismatch is not a local pasted object.
    if float(np.median(difference))>22:
        return []
    mu=cv2.boxFilter(gray,-1,(17,17),normalize=True)
    std=np.sqrt(np.maximum(0,cv2.boxFilter(gray*gray,-1,(17,17),normalize=True)-mu*mu))
    mask=np.zeros((h,w),np.uint8)
    for region in regions.values():
        if region.get('type') not in ('text','signature','logo','security_feature'):
            continue
        x1,y1,x2,y2=[int(region[k]) for k in ('x1','y1','x2','y2')]
        cv2.rectangle(mask,(max(0,x1),max(0,y1)),(min(w-1,x2),min(h-1,y2)),255,-1)
    binary=((difference>42)&(std<10)&(mask>0)).astype(np.uint8)*255
    binary=cv2.morphologyEx(binary,cv2.MORPH_OPEN,np.ones((7,7),np.uint8))
    binary=cv2.morphologyEx(binary,cv2.MORPH_CLOSE,np.ones((7,7),np.uint8))
    contours,_=cv2.findContours(binary,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
    findings=[]
    for contour in contours:
        area=cv2.contourArea(contour)
        if area<.012*w*h or area>.35*w*h:continue
        x,y,bw,bh=cv2.boundingRect(contour)
        if bw<.09*w or bh<.045*h:continue
        if area/max(1,bw*bh)<.52:continue
        overlaps=[]
        for name,region in regions.items():
            if region.get('type')!='text':continue
            dx=max(0,min(x+bw,region['x2'])-max(x,region['x1']))
            dy=max(0,min(y+bh,region['y2'])-max(y,region['y1']))
            if dx*dy>max(.008*w*h,.3*bw*bh):overlaps.append((dx*dy,name))
        if not overlaps:continue
        overlaps.sort(reverse=True)
        findings.append({'code':'POSSIBLE_OPAQUE_FIELD_COVERING','component':'physical_obstruction',
            'region':overlaps[0][1],'x1':int(x),'y1':int(y),'x2':int(x+bw),'y2':int(y+bh),
            'score':57.0,'reason':'Large unusually smooth area differs from printed background near a sensitive field; inspect for object, covering, glare or digital fill.',
            'message':'Possible covering of a document field','comparisonMode':'TEMPLATE_LAYOUT_HEURISTIC',
            'measurements':{'areaFraction':round(area/(w*h),4)}})
    return sorted(findings,key=lambda x:x['score'],reverse=True)[:max_findings]
