// ─── Base URL của Backend Proxy ───────────────────────────────────────────────
const BASE_URL = 'http://localhost:8080';

// ═════════════════════════════════════════════════════════════════════════════
// API INIT — Khởi tạo phiên, lấy captcha + encryptKey từ Sacombank qua Proxy
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Gọi GET /auth/init để:
 * - Lấy ảnh Captcha (Base64 data URI)
 * - Lấy __JS_ENCRYPT_KEY__ (format: "hexE,hexN,maxDigits")
 * - Lấy JSESSIONID của phiên Sacombank
 * - Lấy submitActionUrl để dùng khi POST login
 * - Lấy hiddenFields (các trường ẩn trong form Sacombank)
 *
 * @returns {Promise<{captchaImage: string, encryptKey: string, jsessionId: string, submitActionUrl: string, hiddenFields: Object}>}
 */
export async function initAuth() {
  const res = await fetch(`${BASE_URL}/auth/init`, {
    credentials: 'include',
  });
  if (!res.ok) {
    const body = await res.text();
    throw new Error(body || 'Không thể khởi tạo phiên đăng nhập');
  }
  return res.json();
}

// ═════════════════════════════════════════════════════════════════════════════
// Mã hóa mật khẩu bằng RSA (goi hàm từ index.html / forge.min.js)
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Mã hóa mật khẩu sử dụng hàm window.encryptPasswordSacombank đã được
 * định nghĩa trong index.html (dùng forge.min.js + __JS_ENCRYPT_KEY__).
 *
 * @param {string} rawPassword - Mật khẩu gốc
 * @param {string} encryptKey  - Giá trị __JS_ENCRYPT_KEY__ từ initAuth()
 * @returns {string} - Chuỗi hex sau khi mã hóa RSA
 */
export function encryptPassword(rawPassword, encryptKey) {
  if (!window.encryptPasswordSacombank) {
    throw new Error('Thư viện mã hóa chưa được tải. Vui lòng tải lại trang!');
  }
  return window.encryptPasswordSacombank(rawPassword, encryptKey);
}

// ═════════════════════════════════════════════════════════════════════════════
// Sinh Device DNA / Fingerprint
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Gọi hàm window.getDeviceDNA đã định nghĩa trong index.html
 * (sử dụng fingerprint.js) để lấy chuỗi fingerprint của trình duyệt.
 *
 * @returns {string}
 */
export function getDeviceDNA() {
  if (!window.getDeviceDNA) {
    console.warn('[auth.js] getDeviceDNA not available, using fallback');
    return String(Date.now());
  }
  return window.getDeviceDNA();
}

// ═════════════════════════════════════════════════════════════════════════════
// API PROXY LOGIN — Forward đăng nhập đến Sacombank qua Proxy
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Gọi POST /auth/proxy-login để forward thông tin đăng nhập đến Sacombank.
 *
 * @param {Object} payload
 * @param {string} payload.username          - Tên đăng nhập
 * @param {string} payload.password          - Mật khẩu đã mã hóa RSA (hex string)
 * @param {string} payload.captchaAnswer     - Chuỗi captcha người dùng nhập
 * @param {string} payload.jsessionId        - JSESSIONID từ initAuth()
 * @param {string} payload.deviceFingerprint - Device DNA từ getDeviceDNA()
 * @param {string} payload.submitActionUrl   - Form action URL từ initAuth()
 *
 * @returns {Promise<string>} - Raw HTML response từ Sacombank (hoặc JSON nếu lỗi)
 */
export async function proxyLogin({ username, password, captchaAnswer, jsessionId, deviceFingerprint, submitActionUrl }) {
  const res = await fetch(`${BASE_URL}/auth/proxy-login`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username,
      password,
      captchaAnswer,
      jsessionId,
      deviceFingerprint,
      submitActionUrl,
    }),
  });

  const text = await res.text();
  if (!res.ok) {
    // Thử parse JSON error từ proxy
    try {
      const json = JSON.parse(text);
      throw new Error(json.error || json.message || 'Đăng nhập thất bại');
    } catch (_) {
      throw new Error(text || 'Đăng nhập thất bại');
    }
  }
  return text;
}

// ═════════════════════════════════════════════════════════════════════════════
// Hàm phân tích response của Sacombank để xác định kết quả đăng nhập
// ═════════════════════════════════════════════════════════════════════════════

/**
 * Phân tích HTML/text response của Sacombank để xác định:
 * - Đăng nhập thành công → { success: true }
 * - Sai captcha         → { success: false, message: "..." }
 * - Sai mật khẩu        → { success: false, message: "..." }
 * - Lỗi khác            → { success: false, message: "..." }
 *
 * @param {string} responseBody - Raw response từ proxyLogin()
 * @returns {{ success: boolean, message: string }}
 */
export function parseSacombankResponse(responseBody) {
  if (!responseBody) {
    return { success: false, message: 'Không nhận được phản hồi từ máy chủ' };
  }

  const body = responseBody.toLowerCase();

  // Đăng nhập thành công thường redirect hoặc có dashboard content
  if (
    body.includes('dashboard') ||
    body.includes('welcome') ||
    body.includes('chào mừng') ||
    body.includes('tài khoản') ||
    body.includes('logout') ||
    body.includes('đăng xuất') ||
    body.length < 100 // Response ngắn thường là redirect thành công
  ) {
    return { success: true, message: 'Đăng nhập thành công!' };
  }

  // Các thông báo lỗi phổ biến từ Sacombank
  if (body.includes('captcha') || body.includes('mã xác nhận')) {
    return { success: false, message: 'Mã xác nhận không chính xác. Vui lòng thử lại!' };
  }
  if (body.includes('invalid') || body.includes('incorrect') || body.includes('sai mật khẩu') || body.includes('password')) {
    return { success: false, message: 'Tên đăng nhập hoặc mật khẩu không chính xác!' };
  }
  if (body.includes('locked') || body.includes('block') || body.includes('khóa')) {
    return { success: false, message: 'Tài khoản đã bị khóa. Vui lòng liên hệ ngân hàng!' };
  }

  // Fallback: trả về lỗi chung
  return {
    success: false,
    message: 'Đăng nhập thất bại. Vui lòng kiểm tra lại thông tin và thử lại!',
  };
}
