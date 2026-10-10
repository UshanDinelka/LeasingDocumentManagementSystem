"""Conservative type-specific OCR candidates. No identity or financial values are inferred."""
import copy
import re
from src.document_profiles import PROFILES


def enrich_ocr(ocr, kind):
    result = copy.deepcopy(ocr)
    fields = result.setdefault("fields", {})
    text = result.get("fullText", "")
    def candidates(pattern):
        return list(dict.fromkeys(m.group(1).strip() for m in re.finditer(pattern, text, re.IGNORECASE)))[:200]
    dates = candidates(r"\b([0-9]{4}\s*[-/.]\s*[0-9]{1,2}\s*[-/.]\s*[0-9]{1,2}|[0-9]{1,2}\s*[-/.]\s*[0-9]{1,2}\s*[-/.]\s*[0-9]{4})\b")
    # Preserve ambiguous candidates; choosing one value is the reviewer's decision.
    if not fields.get("dates"):
        fields["dates"] = dates
    policy = PROFILES.get(kind, {}).get("fieldPolicy", "UNCONFIGURED")
    fields["documentFieldPolicy"] = policy
    if policy == "FINANCIAL":
        fields["accountNumberCandidates"] = candidates(r"(?:A\s*/\s*C\s*No\.?|Account\s*(?:No\.?|Number))\s*[:：]?\s*([0-9][0-9 -]{5,35})")
        fields["amountCandidates"] = candidates(r"(?<![A-Za-z0-9])([0-9]{1,3}(?:,[0-9]{3})+\.[0-9]{2}|[0-9]+\.[0-9]{2})(?![A-Za-z0-9])")
        fields["financialNote"] = "Unassigned amount candidates; debit/credit/balance positions and arithmetic need manual checking."
    elif policy == "REGISTRATION":
        fields["companyNumberCandidates"] = candidates(r"(?<![A-Za-z0-9])((?:PV|PB|GA|GL)\s*[0-9]{3,12})(?![A-Za-z0-9])")
    elif policy == "VEHICLE":
        fields["registrationNumberCandidates"] = candidates(r"(?:Registration\s*(?:No\.?|Number))\s*[:：]?\s*([A-Z0-9][A-Z0-9 -]{3,18})")
        fields["chassisNumberCandidates"] = candidates(r"(?:Chassis\s*(?:No\.?|Number))\s*[:：]?\s*([A-Z0-9][A-Z0-9-]{4,25})")
    fields["candidateNote"] = "OCR candidates require manual confirmation. Missing or ambiguous values are not filled in."
    return result
