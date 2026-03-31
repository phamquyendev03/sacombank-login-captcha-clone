import { useState } from 'react';
import { proxyLogin, encryptPassword, getDeviceDNA, parseSacombankResponse } from '../api/auth';

/**
 * LoginStep2 — Step 2 của luồng đăng nhập Sacombank Proxy
 *
 * Nhận props từ Step 1:
 * - username        : tên đăng nhập
 * - captchaAnswer   : mã captcha người dùng đã nhập ở Step 1
 * - encryptKey      : __JS_ENCRYPT_KEY__ từ Sacombank (format: hexE,hexN,maxDigits)
 * - jsessionId      : JSESSIONID của phiên Sacombank
 * - submitActionUrl : URL POST của form Sacombank
 *
 * Chức năng:
 * 1. Người dùng nhập password
 * 2. Bấm "XÁC NHẬN ĐĂNG NHẬP":
 *    a. Mã hóa password bằng RSA (window.encryptPasswordSacombank / forge.min.js)
 *    b. Sinh deviceDNA bằng window.getDeviceDNA / fingerprint.js
 *    c. POST đến /auth/proxy-login với toàn bộ payload
 *    d. Parse response của Sacombank để hiển thị kết quả
 */
export default function LoginStep2({ username, captchaAnswer, encryptKey, jsessionId, submitActionUrl, onSuccess, onBack }) {
  const [password, setPassword]         = useState('');
  const [rememberMe, setRememberMe]     = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading]           = useState(false);
  const [error, setError]               = useState('');

  const getAvatar = (name) => (name ? name.charAt(0).toUpperCase() : '?');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!password) { setError('Vui lòng nhập mật khẩu'); return; }
    setLoading(true);

    try {
      // ── Bước 1: Mã hóa mật khẩu bằng RSA ──────────────────────────────────
      let encryptedPassword;
      if (encryptKey) {
        encryptedPassword = encryptPassword(password, encryptKey);
      } else {
        // Fallback: gửi plaintext nếu không có encryptKey (không khuyến khích)
        console.warn('[LoginStep2] No encryptKey — sending plaintext password!');
        encryptedPassword = password;
      }

      // ── Bước 2: Sinh Device DNA ────────────────────────────────────────────
      const deviceFingerprint = getDeviceDNA();

      // ── Bước 3: Gọi API Proxy Login ────────────────────────────────────────
      const responseBody = await proxyLogin({
        username,
        password:          encryptedPassword,
        captchaAnswer,
        jsessionId,
        deviceFingerprint,
        submitActionUrl,
      });

      // ── Bước 4: Phân tích response Sacombank ───────────────────────────────
      const result = parseSacombankResponse(responseBody);
      if (result.success) {
        onSuccess(result.message);
      } else {
        setError(result.message);
      }

    } catch (err) {
      setError(err.message || 'Đã xảy ra lỗi không xác định');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="login-wrapper">
      <div className="login-card">
        {/* Header */}
        <div className="login-header">
          <div className="login-logo">
            <span className="login-logo-text">e<span>SACOMBANK</span></span>
          </div>
          <p className="login-subtitle">Nhập mật khẩu để đăng nhập</p>
        </div>

        {/* User display */}
        <div className="user-display">
          <div className="user-avatar">{getAvatar(username)}</div>
          <div>
            <div className="user-display-name">{username}</div>
            <div className="user-display-hint">
              Tài khoản đã xác thực ✓
            </div>
          </div>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} noValidate>
          {/* Password */}
          <div className="form-group">
            <label className="form-label">
              MẬT KHẨU <span className="required">*</span>
            </label>
            <div className="password-wrapper">
              <input
                id="password"
                className={`form-input${error ? ' input-error' : ''}`}
                type={showPassword ? 'text' : 'password'}
                placeholder="Nhập mật khẩu"
                value={password}
                onChange={e => { setPassword(e.target.value); setError(''); }}
                autoComplete="current-password"
                autoFocus
              />
              <button
                type="button"
                className="password-toggle"
                onClick={() => setShowPassword(v => !v)}
                tabIndex={-1}
                title={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'}
              >
                {showPassword ? '🙈' : '👁'}
              </button>
            </div>
            {error && <p className="error-msg">⚠ {error}</p>}
          </div>

          {/* Remember me */}
          <label className="checkbox-group" htmlFor="rememberMe">
            <input
              id="rememberMe"
              type="checkbox"
              checked={rememberMe}
              onChange={e => setRememberMe(e.target.checked)}
            />
            <span className="checkbox-label">Ghi nhớ đăng nhập</span>
          </label>

          {/* Submit */}
          <button
            id="btn-login-step2"
            type="submit"
            className="btn-primary"
            disabled={loading}
          >
            {loading
              ? <><div className="btn-spinner" /> Đang xác thực...</>
              : 'XÁC NHẬN ĐĂNG NHẬP'}
          </button>

          {/* Back to Step 1 */}
          {onBack && (
            <button
              type="button"
              className="link-btn"
              style={{ marginTop: 12, display: 'block', width: '100%' }}
              onClick={onBack}
              disabled={loading}
            >
              ← Quay lại
            </button>
          )}
        </form>

        {/* Footer */}
        <div className="login-footer">
          <button className="link-btn">Quên mật khẩu?</button>
        </div>
      </div>
    </div>
  );
}
