import { useState, useEffect, useCallback } from 'react';
import { initAuth } from '../api/auth';

/**
 * LoginStep1 — Step 1 của luồng đăng nhập Sacombank Proxy
 *
 * Chức năng:
 * - Khi mount: gọi /auth/init → lấy captchaImage, encryptKey, jsessionId, submitActionUrl
 * - Hiển thị captcha thật từ Sacombank
 * - Người dùng nhập username + captchaCode → nhấn TIẾP TỤC
 * - Gọi onSuccess({ username, encryptKey, jsessionId, submitActionUrl })
 *   để truyền dữ liệu sang Step 2
 */
export default function LoginStep1({ onSuccess }) {
  const [username, setUsername] = useState('');
  const [captchaCode, setCaptchaCode] = useState('');
  const [captchaImage, setCaptchaImage] = useState('');
  const [sessionData, setSessionData] = useState(null); // { encryptKey, jsessionId, submitActionUrl }
  const [loading, setLoading] = useState(false);
  const [loadingCaptcha, setLoadingCaptcha] = useState(false);
  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState('');
  const [spinning, setSpinning] = useState(false);

  // ── Load phiên mới từ Proxy Server ────────────────────────────────────────
  const loadSession = useCallback(async () => {
    setLoadingCaptcha(true);
    setSpinning(true);
    setApiError('');
    try {
      // Gọi /auth/init → Proxy gọi Sacombank → trả về captcha + session info
      const data = await initAuth();
      setCaptchaImage(data.captchaImage || '');
      setSessionData({
        encryptKey: data.encryptKey,
        jsessionId: data.jsessionId,
        submitActionUrl: data.submitActionUrl,
      });
    } catch (e) {
      setApiError(
        'Không thể kết nối đến Sacombank. Vui lòng kiểm tra backend đang chạy tại ' +
        'http://localhost:8080 và có kết nối Internet.'
      );
    } finally {
      setLoadingCaptcha(false);
      setTimeout(() => setSpinning(false), 600);
    }
  }, []);

  useEffect(() => {
    loadSession();
  }, [loadSession]);

  // ── Validation ─────────────────────────────────────────────────────────────
  const validate = () => {
    const errs = {};
    if (!username.trim()) errs.username = 'Vui lòng nhập tên đăng nhập';
    if (!captchaCode.trim()) errs.captchaCode = 'Vui lòng nhập mã xác nhận';
    return errs;
  };

  // ── Submit Step 1 ──────────────────────────────────────────────────────────
  const handleSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    const errs = validate();
    if (Object.keys(errs).length > 0) { setErrors(errs); return; }
    setErrors({});

    if (!sessionData?.jsessionId) {
      setApiError('Phiên đăng nhập chưa sẵn sàng. Vui lòng đợi hoặc làm mới captcha!');
      return;
    }

    setLoading(true);
    try {
      // Truyền sang Step 2: username + toàn bộ thông tin phiên Sacombank
      onSuccess({
        username: username.trim(),
        captchaAnswer: captchaCode.trim(),
        encryptKey: sessionData.encryptKey,
        jsessionId: sessionData.jsessionId,
        submitActionUrl: sessionData.submitActionUrl,
      });
    } catch (err) {
      setApiError(err.message);
    } finally {
      setLoading(false);
    }
  };

  // ── Render ─────────────────────────────────────────────────────────────────
  return (
    <div className="login-wrapper">
      <div className="login-card">
        {/* Header */}
        <div className="login-header">
          <div className="login-logo">
            <span className="login-logo-text">e<span>SACOMBANK</span></span>
          </div>
          <p className="login-subtitle">Đăng nhập trực tuyến an toàn</p>
        </div>

        {/* Form */}
        <form onSubmit={handleSubmit} noValidate>
          {/* Username */}
          <div className="form-group">
            <label className="form-label">
              TÊN ĐĂNG NHẬP <span className="required">*</span>
            </label>
            <input
              id="username"
              className={`form-input${errors.username ? ' input-error' : ''}`}
              type="text"
              placeholder="Nhập tên đăng nhập"
              value={username}
              onChange={e => { setUsername(e.target.value); setErrors(p => ({ ...p, username: '' })); }}
              autoComplete="username"
              autoFocus
            />
            {errors.username && <p className="error-msg">⚠ {errors.username}</p>}
          </div>

          {/* Captcha */}
          <div className="form-group">
            <label className="form-label">
              MÃ XÁC NHẬN <span className="required">*</span>
            </label>
            <div className="captcha-row">
              <input
                id="captchaCode"
                className={`form-input${errors.captchaCode ? ' input-error' : ''}`}
                type="text"
                placeholder="Nhập mã >"
                value={captchaCode}
                onChange={e => { setCaptchaCode(e.target.value); setErrors(p => ({ ...p, captchaCode: '' })); }}
                autoComplete="off"
                maxLength={10}
              />
              <div className="captcha-image-box">
                {loadingCaptcha ? (
                  <div style={{
                    height: 44, width: 120, display: 'flex',
                    alignItems: 'center', justifyContent: 'center',
                    border: '1.5px solid #d1d5db', borderRadius: 6,
                    background: '#f9fafb'
                  }}>
                    <div style={{
                      width: 20, height: 20,
                      border: '2px solid #d1d5db',
                      borderTopColor: '#1565c0',
                      borderRadius: '50%',
                      animation: 'spin 0.7s linear infinite'
                    }} />
                  </div>
                ) : captchaImage ? (
                  <img
                    src={captchaImage}
                    alt="Mã xác nhận từ Sacombank"
                    title="Ảnh captcha thật từ hệ thống Sacombank"
                    style={{ cursor: 'default', maxHeight: 44 }}
                  />
                ) : (
                  <div style={{
                    height: 44, width: 110, display: 'flex', alignItems: 'center',
                    justifyContent: 'center', border: '1.5px solid #d1d5db',
                    borderRadius: 6, fontSize: 11, color: '#9ca3af', background: '#f9fafb'
                  }}>
                    Đang tải...
                  </div>
                )}
                <button
                  type="button"
                  className={`captcha-refresh-btn${spinning ? ' spinning' : ''}`}
                  onClick={loadSession}
                  title="Làm mới captcha (lấy phiên mới từ Sacombank)"
                  disabled={loadingCaptcha}
                >
                  ↻
                </button>
              </div>
            </div>
            {errors.captchaCode && <p className="error-msg">⚠ {errors.captchaCode}</p>}
          </div>

          {/* API error */}
          {apiError && (
            <div style={{
              background: '#fef2f2', border: '1px solid #fecaca',
              borderRadius: 6, padding: '10px 14px',
              fontSize: 13, color: '#dc2626', marginTop: 10
            }}>
              ⚠ {apiError}
            </div>
          )}

          {/* Submit */}
          <button
            id="btn-login-step1"
            type="submit"
            className="btn-primary"
            disabled={loading || loadingCaptcha}
          >
            {loading ? <><div className="btn-spinner" /> Đang xử lý...</> : 'TIẾP TỤC'}
          </button>
        </form>

        {/* Footer */}
        <div className="login-footer">
          <button className="link-btn">Quên mật khẩu?</button>
        </div>
      </div>
    </div>
  );
}
