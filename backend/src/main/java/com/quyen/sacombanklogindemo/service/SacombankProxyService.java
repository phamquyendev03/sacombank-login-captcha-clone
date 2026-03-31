package com.quyen.sacombanklogindemo.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SacombankProxyService
 *
 * Hoạt động như một Reverse Proxy, gọi trực tiếp đến hệ thống Sacombank.
 *
 * Luồng:
 * 1. initSession()  → GET trang login Sacombank
 *                   → Lấy JSESSIONID từ Set-Cookie header
 *                   → Jsoup parse HTML: lấy __JS_ENCRYPT_KEY__, action URL form, captcha URL
 *                   → Tải ảnh captcha → convert sang Base64
 *                   → Trả về Map chứa: captchaImage, encryptKey, jsessionId, submitActionUrl, + tất cả hidden fields
 *
 * 2. proxyLogin()   → POST đến action URL của form Sacombank
 *                   → Gắn JSESSIONID vào header Cookie
 *                   → Fake User-Agent và Referer để bypass WAF/tường lửa
 *                   → Trả về raw response của Sacombank
 */
@Slf4j
@Service
public class SacombankProxyService {

    private static final String SACOMBANK_BASE = "https://www.isacombank.com.vn";
    private static final String SACOMBANK_CORP_PATH = "/corp/AuthenticationController";

    /**
     * Query string để khởi tạo phiên đăng nhập (load trang login Step 1)
     */
    private static final String INIT_QUERY =
            "?FORMSGROUP_ID__=AuthenticationFG" +
            "&__START_TRAN_FLAG__=Y" +
            "&FG_BUTTONS__=LOAD" +
            "&ACTION.LOAD=Y" +
            "&AuthenticationFG.LOGIN_FLAG=1" +
            "&BANK_ID=303" +
            "&LANGUAGE_ID=003";

    // ─── Browser Headers giả lập ───────────────────────────────────────────────
    private static final String FAKE_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/124.0.0.0 Safari/537.36";

    private static final String REFERER_INIT =
            SACOMBANK_BASE + SACOMBANK_CORP_PATH + INIT_QUERY;

    private final WebClient webClient;

    public SacombankProxyService() {
        this.webClient = WebClient.builder()
                .baseUrl(SACOMBANK_BASE)
                // Không follow redirect tự động để giữ nguyên response header
                .build();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 1. INIT SESSION
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Gọi GET đến trang login Sacombank và trích xuất:
     * - JSESSIONID (từ Set-Cookie header)
     * - __JS_ENCRYPT_KEY__ (từ hidden input trong HTML)
     * - URL lấy ảnh CAPTCHA
     * - action URL của form (để POST login)
     * - Tất cả hidden fields cần thiết
     * - Ảnh CAPTCHA dưới dạng Base64
     *
     * @return Map chứa tất cả dữ liệu cần thiết cho Frontend
     */
    public Map<String, Object> initSession() throws Exception {

        // ── Step 1: GET trang login, lấy HTML và headers ─────────────────────
        log.info("[Proxy] Calling Sacombank init URL: {}{}{}", SACOMBANK_BASE, SACOMBANK_CORP_PATH, INIT_QUERY);

        // Dùng exchange để đọc được cả header (Set-Cookie)
        Map<String, String> rawHeaders = new HashMap<>();

        String htmlBody = webClient.get()
                .uri(SACOMBANK_CORP_PATH + INIT_QUERY)
                .header(HttpHeaders.USER_AGENT, FAKE_USER_AGENT)
                .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                .exchangeToMono(response -> {
                    // Lấy tất cả Set-Cookie headers
                    HttpHeaders headers = response.headers().asHttpHeaders();
                    String cookies = String.join("; ", headers.getOrEmpty(HttpHeaders.SET_COOKIE));
                    rawHeaders.put("setCookie", cookies);
                    log.info("[Proxy] Set-Cookie received: {}", cookies);
                    return response.bodyToMono(String.class);
                })
                .block();

        if (htmlBody == null || htmlBody.isEmpty()) {
            throw new RuntimeException("Sacombank trả về response rỗng");
        }

        // ── Step 2: Trích xuất JSESSIONID ────────────────────────────────────
        String jsessionId = extractJsessionId(rawHeaders.get("setCookie"));
        if (jsessionId == null) {
            jsessionId = extractJsessionIdFromHtml(htmlBody);
        }
        log.info("[Proxy] JSESSIONID extracted: {}", jsessionId != null ? jsessionId.substring(0, Math.min(20, jsessionId.length())) + "..." : "NOT FOUND");

        // ── Step 3: Parse HTML bằng Jsoup ────────────────────────────────────
        Document doc = Jsoup.parse(htmlBody);

        // Lấy __JS_ENCRYPT_KEY__ từ hidden input
        String encryptKey = extractEncryptKey(doc);
        log.info("[Proxy] __JS_ENCRYPT_KEY__ extracted: {}", encryptKey != null ? encryptKey.substring(0, Math.min(30, encryptKey.length())) + "..." : "NOT FOUND");

        // Lấy form action URL
        String formAction = extractFormAction(doc, jsessionId);
        log.info("[Proxy] Form action URL: {}", formAction);

        // Lấy tất cả hidden fields quan trọng
        Map<String, String> hiddenFields = extractHiddenFields(doc);
        log.info("[Proxy] Hidden fields extracted: {}", hiddenFields.keySet());

        // Lấy URL của ảnh captcha
        String captchaImageUrl = extractCaptchaUrl(doc, htmlBody, jsessionId);
        log.info("[Proxy] Captcha URL: {}", captchaImageUrl);

        // ── Step 4: Tải ảnh captcha và convert sang Base64 ───────────────────
        String captchaBase64 = downloadCaptchaAsBase64(captchaImageUrl, jsessionId);

        // ── Step 5: Build response ────────────────────────────────────────────
        Map<String, Object> result = new HashMap<>();
        result.put("captchaImage", captchaBase64);
        result.put("encryptKey", encryptKey);
        result.put("jsessionId", jsessionId);
        result.put("submitActionUrl", formAction);
        result.put("hiddenFields", hiddenFields);
        return result;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // 2. PROXY LOGIN
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Forward POST request đến Sacombank với đầy đủ headers giả lập browser.
     *
     * @param submitActionUrl  URL đầy đủ nhận từ initSession() (có chứa jsessionid)
     * @param jsessionId       JSESSIONID để gắn vào Cookie header
     * @param formFields       Map chứa tất cả field cần POST (username, password đã mã hóa, captcha, hidden fields)
     * @return ResponseEntity chứa body và status từ Sacombank
     */
    public ResponseEntity<String> proxyLogin(
            String submitActionUrl,
            String jsessionId,
            Map<String, String> formFields) {

        log.info("[Proxy] POST login to: {}", submitActionUrl);
        log.info("[Proxy] Fields being sent: {}", formFields.keySet());

        // Build form body (application/x-www-form-urlencoded)
        MultiValueMap<String, String> bodyMap = new LinkedMultiValueMap<>();
        formFields.forEach(bodyMap::add);

        try {
            String responseBody = webClient.post()
                    .uri(submitActionUrl.replace(SACOMBANK_BASE, ""))
                    .header(HttpHeaders.USER_AGENT, FAKE_USER_AGENT)
                    .header(HttpHeaders.REFERER, REFERER_INIT)
                    .header(HttpHeaders.ORIGIN, SACOMBANK_BASE)
                    .header("Cookie", "JSESSIONID=" + jsessionId)
                    .header(HttpHeaders.ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header(HttpHeaders.ACCEPT_LANGUAGE, "vi-VN,vi;q=0.9")
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .body(BodyInserters.fromFormData(bodyMap))
                    .retrieve()
                    .onStatus(status -> status.is3xxRedirection(), response -> {
                        // Khi Sacombank redirect (đăng nhập thành công), capture location
                        String location = response.headers().asHttpHeaders().getFirst(HttpHeaders.LOCATION);
                        log.info("[Proxy] Redirect received → Location: {}", location);
                        return Mono.empty();
                    })
                    .bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .block();

            log.info("[Proxy] Sacombank response body length: {}", responseBody != null ? responseBody.length() : 0);
            return ResponseEntity.ok(responseBody != null ? responseBody : "");

        } catch (WebClientResponseException e) {
            log.error("[Proxy] Sacombank responded with error: {} {}", e.getStatusCode(), e.getMessage());
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[Proxy] Unexpected error during proxy login: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Lỗi kết nối đến máy chủ Sacombank: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═══════════════════════════════════════════════════════════════════════════

    /**
     * Trích xuất JSESSIONID từ chuỗi Set-Cookie header.
     * VD: "JSESSIONID=ABC123DEF; Path=/corp; Secure; HttpOnly"
     */
    private String extractJsessionId(String setCookieHeader) {
        if (setCookieHeader == null || setCookieHeader.isEmpty()) return null;
        Pattern pattern = Pattern.compile("JSESSIONID=([^;,\\s]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(setCookieHeader);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractJsessionIdFromHtml(String htmlBody) {
        // try to match action="AuthenticationController;jsessionid=([^:?"]+)" 
        Pattern p1 = Pattern.compile("jsessionid=([^:?\"&]+)");
        Matcher m1 = p1.matcher(htmlBody);
        if (m1.find()) {
            return m1.group(1);
        }
        
        // try varLoginSessionID
        Pattern p2 = Pattern.compile("varLoginSessionID\\s*=\\s*[\"']([^\"']+)[\"']");
        Matcher m2 = p2.matcher(htmlBody);
        if (m2.find()) {
            return m2.group(1);
        }
        return null;
    }

    /**
     * Trích xuất giá trị của __JS_ENCRYPT_KEY__ từ hidden input trong HTML.
     * Format value: "exponent,modulus,maxDigits"
     * Jsoup tìm: <input type="hidden" name="__JS_ENCRYPT_KEY__" value="...">
     */
    private String extractEncryptKey(Document doc) {
        // Thử tìm theo name attribute
        Element keyElement = doc.select("input[name=__JS_ENCRYPT_KEY__]").first();
        if (keyElement != null) {
            return keyElement.attr("value");
        }
        // Backup: tìm theo id
        keyElement = doc.select("input#__JS_ENCRYPT_KEY__").first();
        if (keyElement != null) {
            return keyElement.attr("value");
        }
        // Backup 2: regex trong raw HTML nếu Jsoup miss
        log.warn("[Proxy] __JS_ENCRYPT_KEY__ not found via Jsoup selector");
        return null;
    }

    /**
     * Trích xuất action URL của form login.
     * Sacombank dùng form action chứa jsessionid trong URL path.
     */
    private String extractFormAction(Document doc, String jsessionId) {
        Element form = doc.select("form").first();
        if (form != null) {
            String action = form.attr("action");
            if (!action.isEmpty()) {
                // Nếu action là relative URL, thêm base
                if (!action.startsWith("http")) {
                    if (action.startsWith("/")) action = SACOMBANK_BASE + action;
                    else action = SACOMBANK_BASE + "/corp/" + action;
                }
                return action;
            }
        }
        // Fallback: tự build URL với jsessionid
        String fallback = SACOMBANK_BASE + SACOMBANK_CORP_PATH;
        if (jsessionId != null) {
            fallback += ";jsessionid=" + jsessionId;
        }
        log.warn("[Proxy] Form action not found, using fallback: {}", fallback);
        return fallback;
    }

    /**
     * Trích xuất tất cả hidden fields quan trọng từ form:
     * FORMSGROUP_ID__, __START_TRAN_FLAG__, FG_BUTTONS__, ACTION.LOAD, v.v.
     */
    private Map<String, String> extractHiddenFields(Document doc) {
        Map<String, String> fields = new HashMap<>();
        doc.select("input[type=hidden]").forEach(input -> {
            String name = input.attr("name");
            String value = input.attr("value");
            if (!name.isEmpty()) {
                fields.put(name, value);
            }
        });
        return fields;
    }

    /**
     * Trích xuất URL ảnh captcha từ HTML.
     * Sacombank thường dùng <img> tag có id hoặc name liên quan đến "captcha" hoặc "CAPTCHA".
     */
    private String extractCaptchaUrl(Document doc, String rawHtml, String jsessionId) {
        // Tìm img tag có src chứa "captcha" hoặc "CAPTCHA" (case insensitive)
        Element captchaImg = doc.select("img[src*=captcha], img[src*=CAPTCHA], img[id*=captcha], img[id*=CAPTCHA]").first();
        if (captchaImg != null) {
            String src = captchaImg.attr("src");
            if (!src.startsWith("http")) {
                if (src.startsWith("/")) src = SACOMBANK_BASE + src;
                else src = SACOMBANK_BASE + "/corp/" + src;
            }
            return src;
        }

        // Backup: Tìm img tag bằng regex trong raw HTML
        Pattern p = Pattern.compile("src=[\"']([^\"']*[Cc][Aa][Pp][Tt][Cc][Hh][Aa][^\"']*)[\"']");
        Matcher m = p.matcher(rawHtml);
        if (m.find()) {
            String src = m.group(1);
            if (!src.startsWith("http")) {
                if (src.startsWith("/")) src = SACOMBANK_BASE + src;
                else src = SACOMBANK_BASE + "/corp/" + src;
            }
            return src;
        }

        // Backup 2: URL pattern của AuthenticationController với view_id=FBALoginCaptcha
        if (jsessionId != null) {
            // Pattern từ network inspection: URL chứa jsessionid và bwayparam cho captcha
            return SACOMBANK_BASE + SACOMBANK_CORP_PATH +
                   ";jsessionid=" + jsessionId +
                   "?FORMSGROUP_ID__=AuthenticationFG" +
                   "&__START_TRAN_FLAG__=N" +
                   "&FG_BUTTONS__=PROCEED" +
                   "&ACTION.PROCEED=Y" +
                   "&AuthenticationFG.LOGIN_FLAG=1" +
                   "&BANK_ID=303" +
                   "&LANGUAGE_ID=003" +
                   "&AuthenticationFG.GET_CAPTCHA=Y";
        }

        log.warn("[Proxy] Could not find captcha image URL");
        return null;
    }

    /**
     * Tải ảnh captcha từ URL và convert thành chuỗi Base64 data URI.
     * Gắn JSESSIONID vào Cookie để Sacombank trả đúng captcha cho phiên này.
     */
    private String downloadCaptchaAsBase64(String captchaUrl, String jsessionId) {
        if (captchaUrl == null) return null;

        try {
            log.info("[Proxy] Downloading captcha from: {}", captchaUrl);

            // Xác định phần URI relative
            String captchaUri = captchaUrl.replace(SACOMBANK_BASE, "");

            byte[] imageBytes = webClient.get()
                    .uri(captchaUri)
                    .header(HttpHeaders.USER_AGENT, FAKE_USER_AGENT)
                    .header(HttpHeaders.REFERER, REFERER_INIT)
                    .header("Cookie", jsessionId != null ? "JSESSIONID=" + jsessionId : "")
                    .header(HttpHeaders.ACCEPT, "image/webp,image/apng,image/*,*/*;q=0.8")
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();

            if (imageBytes == null || imageBytes.length == 0) {
                log.warn("[Proxy] Captcha image bytes is empty");
                return null;
            }

            String base64 = Base64.getEncoder().encodeToString(imageBytes);
            log.info("[Proxy] Captcha image downloaded, size: {} bytes → Base64 length: {}", imageBytes.length, base64.length());
            return "data:image/jpeg;base64," + base64;

        } catch (Exception e) {
            log.error("[Proxy] Failed to download captcha: {}", e.getMessage(), e);
            return null;
        }
    }
}
