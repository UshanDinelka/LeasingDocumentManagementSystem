import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Copy this class into your Spring Boot backend's appropriate package.
 *
 * Spring MUST first: authenticate the administrator; load the document from
 * trusted encrypted storage; verify its SHA-256; check capture-quality results;
 * look up the customerId for THIS document in the database; and select any
 * older approved same-document reference from protected storage.
 * Never accept the expected NIC/fullName or a trusted reference from an
 * arbitrary client-provided request body.
 *
 * This class only constructs a JSON request. It does not perform those trust
 * checks or send the HTTP request. The existing backend adapter must do that.
 */
public final class Component4RequestFactory {
    private Component4RequestFactory() { }

    public static Map<String, Object> build(
            byte[] integrityVerifiedImage,
            String documentTypeCode,
            Map<String, Object> androidOcr,
            Map<String, Object> verifiedCaptureQuality,
            long databaseCustomerId,
            String databaseCustomerNic,
            String databaseCustomerFullName) {
        if (integrityVerifiedImage == null || integrityVerifiedImage.length == 0) {
            throw new IllegalArgumentException("No verified document image was loaded");
        }
        if (databaseCustomerId < 1 || databaseCustomerNic == null || databaseCustomerFullName == null) {
            throw new IllegalArgumentException("Load registered customer details from the database");
        }
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("customerId", databaseCustomerId);
        customer.put("nic", databaseCustomerNic);
        customer.put("fullName", databaseCustomerFullName);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("documentType", documentTypeCode);
        request.put("imageBase64", Base64.getEncoder().encodeToString(integrityVerifiedImage));
        request.put("imageSha256", sha256(integrityVerifiedImage));
        request.put("ocr", androidOcr);
        request.put("quality", verifiedCaptureQuality);
        request.put("customerSource", "SERVER_DATABASE");
        request.put("customerData", customer);
        return request;
    }

    public static void addTrustedSameDocumentReference(
            Map<String,Object> request, byte[] serverVerifiedReference) {
        if (serverVerifiedReference == null || serverVerifiedReference.length == 0) {
            throw new IllegalArgumentException("Missing approved same-document reference");
        }
        request.put("referenceSource", "SERVER_VERIFIED_SAME_DOCUMENT");
        request.put("referenceImageBase64", Base64.getEncoder().encodeToString(serverVerifiedReference));
        request.put("referenceImageSha256", sha256(serverVerifiedReference));
    }

    public static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : digest) {
                hex.append(Character.forDigit((value >>> 4) & 0xF, 16));
                hex.append(Character.forDigit(value & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing", e);
        }
    }
}
