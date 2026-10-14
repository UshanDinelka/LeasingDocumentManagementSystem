"""Compare extracted identifiers to server-resolved customer data.

This is consistency checking, not proof of identity, ownership, or forgery.
Only Spring Boot may supply the expected values after looking up the
submitted document's customerId in the database. OCR is fallible and ambiguous
reads are never turned into definite mismatches.
"""
from __future__ import annotations

import re
import unicodedata
from difflib import SequenceMatcher

NIC_RE = re.compile(r'(?<![A-Z0-9])(?:\d{12}|\d{9}[VX])(?![A-Z0-9])', re.I)


def normal_nic(value):
    return re.sub(r'[^0-9VX]', '', str(value or '').upper())


def is_nic(value):
    return bool(re.fullmatch(r'(?:\d{12}|\d{9}[VX])', normal_nic(value)))


def normalize_name(value):
    raw = unicodedata.normalize('NFKC', str(value or '')).upper()
    return ' '.join(re.findall(r'[^\W\d_]+', raw, re.UNICODE))


def _str_values(value):
    if isinstance(value, str):
        return [value]
    if isinstance(value, (list, tuple)):
        return [v for v in value if isinstance(v, str)]
    return []


def ocr_nic_candidates(ocr):
    fields = ocr.get('fields') or {}
    # Only values witnessed in OCR text itself count as evidence. Never let a
    # separately supplied fields dictionary invent a matching identifier.
    text = '\n'.join([str(ocr.get('fullText') or '')] + [str(x.get('text','')) for x in ocr.get('lines', []) if isinstance(x, dict)])
    raw_values = [m.group() for m in NIC_RE.finditer(text.upper())]
    return sorted({normal_nic(x) for x in raw_values if is_nic(x)})


def _name_candidates(ocr):
    fields = ocr.get('fields') or {}
    # The Android fields object is not a trusted text source. Require explicit
    # labelled name evidence in OCR lines/fullText.
    values = []
    text_lines = [str(v.get('text', '')).strip() for v in ocr.get('lines', []) if isinstance(v,dict)]
    text_lines.extend(str(ocr.get('fullText', '')).splitlines())
    for i, line in enumerate(text_lines):
        found = re.match(r'^\s*(?:full\s+name|name\s+of\s+(?:holder|owner)|name)\s*[:：\-]?\s*(.*)$',line,re.I)
        if found:
            value = found.group(1).strip() or (text_lines[i+1].strip() if i+1<len(text_lines) else '')
            values.append(value)
    normalized = []
    for value in values:
        candidate = normalize_name(value)
        if 5 <= len(candidate) <= 90 and len(candidate.split()) >= 2 and not any(t in candidate.split() for t in ('SIGNATURE','IDENTITY','LICENCE','NATIONAL','DOCUMENT','BIRTH')):
            normalized.append(candidate)
    return sorted(set(normalized))


def compare_customer(ocr, customer, kind):
    result = {'status':'NOT_PROVIDED', 'source':'SERVER_DATABASE', 'fields':{},
              'discrepancies':[], 'note':'Comparison checks registration consistency; it cannot establish genuine identity.'}
    if customer is None:
        return result
    if not isinstance(customer,dict) or customer.get('customerId') is None:
        return dict(result, status='INVALID_CUSTOMER_CONTEXT', note='Customer lookup was not supplied or did not include a database customerId.')
    result['status'] = 'ASSESSED'
    result['customerId'] = customer['customerId']
    expected_nic = normal_nic(customer.get('nic'))
    recognized = ocr_nic_candidates(ocr)
    ocr_text = str(ocr.get('fullText') or '')
    explicitly_labeled_nic = bool(re.search(r'(?i)\b(?:NIC|national\s+identity\s+(?:card|number))\b\s*(?:no\.?|number|:|#|\-)?\s*\d{12}',ocr_text))
    if kind in ('NIC','NIC_COPY','DRIVING_LICENCE') or explicitly_labeled_nic:
        record = {'status':'NOT_CHECKED','expected':expected_nic or None, 'extractedCandidates':recognized}
        if not is_nic(expected_nic):
            record['status'] = 'REGISTERED_VALUE_INVALID'
        elif not recognized:
            record['status'] = 'NOT_EXTRACTED'
        elif len(recognized)>1:
            record['status'] = 'AMBIGUOUS_OCR'
        else:
            record['status'] = 'MATCH' if expected_nic == recognized[0] else 'MISMATCH'
            if record['status']=='MISMATCH':
                result['discrepancies'].append({'field':'nic','code':'CUSTOMER_NIC_MISMATCH',
                    'reason':'The document NIC read by OCR differs from the NIC saved for the registered customer. Check OCR and registration data.'})
        result['fields']['nic'] = record
    expected_name = normalize_name(customer.get('fullName'))
    names = _name_candidates(ocr)
    if expected_name and kind not in ('BUSINESS_REGISTRATION','VEHICLE_CR_BOOK'):
        record = {'status':'NOT_EXTRACTED','expected':expected_name,'extractedCandidates':names}
        if len(names)>1: record['status']='AMBIGUOUS_OCR'
        elif len(names)==1:
            similarity = SequenceMatcher(None, expected_name, names[0]).ratio()
            et, gt = set(expected_name.split()),set(names[0].split())
            # A mismatch needs a strongly legible, explicitly labelled name
            # and should never by itself be treated as a proven edit.
            if expected_name == names[0] or (len(et & gt)>=2 and similarity>.85):
                record['status']='MATCH'
            elif len(names[0].split())>=2 and similarity < .50 and len(et & gt)==0:
                record['status']='POSSIBLE_MISMATCH'
                result['discrepancies'].append({'field':'name','code':'CUSTOMER_NAME_POSSIBLE_MISMATCH',
                    'reason':'A labelled name appears different from the registered customer name; verify OCR spelling and registration.'})
            else: record['status']='REVIEW_REQUIRED'
        result['fields']['fullName']=record
    if result['discrepancies']:
        result['status']='DISCREPANCY_FOUND'
    elif any(v['status'] in ('NOT_EXTRACTED','AMBIGUOUS_OCR','REVIEW_REQUIRED','REGISTERED_VALUE_INVALID') for v in result['fields'].values()):
        result['status']='PARTIAL'
    return result
