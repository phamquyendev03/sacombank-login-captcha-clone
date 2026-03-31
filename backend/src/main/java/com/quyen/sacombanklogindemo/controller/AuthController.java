package com.quyen.sacombanklogindemo.controller;

import com.quyen.sacombanklogindemo.domain.request.ProxyLoginRequestDTO;
import com.quyen.sacombanklogindemo.service.SacombankProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AuthController — Proxy Controller
 *
 * Hai endpoint chính:
 *   GET  /auth/init        → Khởi tạo phiên, lấy captcha + encryptKey từ Sacombank
 *   POST /auth/proxy-login → Forward thông tin đăng nhập đến Sacombank
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SacombankProxyService sacombankProxyService;

    // ─────────────────────────────────────────────────────────────────────────
    // GET /auth/init
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Endpoint khởi tạo phiên đăng nhập.
     *
     * Frontend gọi endpoint này khi component mount để:
     * 1. Lấy ảnh CAPTCHA (Base64) hiển thị cho người dùng
     * 2. Lấy __JS_ENCRYPT_KEY__ để mã hóa mật khẩu
     * 3. Lấy JSESSIONID để gắn vào request proxy login
     * 4. Lấy submitActionUrl và hiddenFields để build POST request
     *
     * Response JSON:
     * {
     *   "captchaImage"    : "data:image/jpeg;base64,/9j/...",
     *   "encryptKey"      : "exponent,modulus,maxDigits",
     *   "jsessionId"      : "ABC123...",
     *   "submitActionUrl" : "https://www.isacombank.com.vn/corp/AuthenticationController;jsessionid=...",
     *   "hiddenFields"    : { "FORMSGROUP_ID__": "AuthenticationFG", ... }
     * }
     */
    @GetMapping("/init")
    public ResponseEntity<?> initSession(HttpServletRequest request) {
        try {
            log.info("[AuthController] GET /auth/init — initiating Sacombank proxy session");
            Map<String, Object> sessionData = sacombankProxyService.initSession();
            return ResponseEntity.ok(sessionData);
        } catch (Exception e) {
            log.error("[AuthController] initSession failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Không thể khởi tạo phiên đăng nhập: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /auth/proxy-login
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Endpoint proxy forward đăng nhập đến Sacombank.
     *
     * Frontend gửi lên:
     * {
     *   "username"         : "tên đăng nhập",
     *   "password"         : "mật khẩu đã được mã hóa RSA bằng jCryption/forge",
     *   "captchaAnswer"    : "chuỗi captcha người dùng nhập",
     *   "jsessionId"       : "JSESSIONID lấy từ /auth/init",
     *   "deviceFingerprint": "chuỗi fingerprint từ fingerprint.js",
     *   "submitActionUrl"  : "URL form action lấy từ /auth/init"
     * }
     *
     * Backend sẽ:
     * - Build form fields đầy đủ (username, password, captcha, device DNA, hidden fields quan trọng)
     * - POST đến submitActionUrl với Cookie JSESSIONID và giả lập browser headers
     * - Trả nguyên response của Sacombank về Frontend
     */
    @PostMapping("/proxy-login")
    public ResponseEntity<?> proxyLogin(@RequestBody ProxyLoginRequestDTO dto) {
        try {
            log.info("[AuthController] POST /auth/proxy-login — username: {}", dto.getUsername());

            // Validate input cơ bản
            if (dto.getJsessionId() == null || dto.getJsessionId().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Phiên đăng nhập không hợp lệ, vui lòng tải lại trang!"));
            }
            if (dto.getUsername() == null || dto.getUsername().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng nhập tên đăng nhập!"));
            }
            if (dto.getPassword() == null || dto.getPassword().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Vui lòng nhập mật khẩu!"));
            }

            // Build map các field POST đến Sacombank
            Map<String, String> formFields = buildLoginFormFields(dto);

            // Proxy forward đến Sacombank
            ResponseEntity<String> sacombankResponse = sacombankProxyService.proxyLogin(
                    dto.getSubmitActionUrl(),
                    dto.getJsessionId(),
                    formFields
            );

            log.info("[AuthController] Sacombank response status: {}", sacombankResponse.getStatusCode());
            return sacombankResponse;

        } catch (Exception e) {
            log.error("[AuthController] proxyLogin failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Lỗi kết nối đến Sacombank: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Build map các field form cần POST đến Sacombank.
     *
     * Các field quan trọng (dựa trên network inspection):
     * - AuthenticationFG.USER_ID           → tên đăng nhập
     * - AuthenticationFG.PASSWORD          → mật khẩu (đã mã hóa RSA)
     * - AuthenticationFG.CAPTCHA_ANSWER    → chuỗi captcha
     * - AuthenticationFG.DEVICE_C_FINGERPRINT → device fingerprint
     * - FORMSGROUP_ID__                    → "AuthenticationFG"
     * - __START_TRAN_FLAG__                → "N" (sau lần init)
     * - FG_BUTTONS__                       → "PROCEED"
     * - ACTION.PROCEED                     → "Y"
     * - AuthenticationFG.LOGIN_FLAG        → "1"
     * - BANK_ID                            → "303"
     * - LANGUAGE_ID                        → "003"
     */
    private Map<String, String> buildLoginFormFields(ProxyLoginRequestDTO dto) {
        Map<String, String> fields = new HashMap<>();

        // User input fields
        fields.put("AuthenticationFG.USER_ID", dto.getUsername());
        fields.put("AuthenticationFG.PASSWORD", dto.getPassword());
        fields.put("AuthenticationFG.CAPTCHA_ANSWER", dto.getCaptchaAnswer());
        fields.put("AuthenticationFG.DEVICE_C_FINGERPRINT",
                dto.getDeviceFingerprint() != null ? dto.getDeviceFingerprint() : "");

        // Required hidden fields (Sacombank form constants)
        fields.put("FORMSGROUP_ID__", "AuthenticationFG");
        fields.put("__START_TRAN_FLAG__", "N");
        fields.put("FG_BUTTONS__", "PROCEED");
        fields.put("ACTION.PROCEED", "Y");
        fields.put("AuthenticationFG.LOGIN_FLAG", "1");
        fields.put("BANK_ID", "303");
        fields.put("LANGUAGE_ID", "003");

        // Fields required for JavaScript encryption tracking
        fields.put("IS_ENCRYPTION_REQUIRED", "Y");
        fields.put("DECRYPT_FLAG", "Y");
        fields.put("JS_ENABLED_FLAG", "Y");

        return fields;
    }
}
