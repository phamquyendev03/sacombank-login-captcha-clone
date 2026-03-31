import { useState, useEffect, useCallback } from 'react';
import { fetchCaptcha, loginStep1 } from '../api/auth';

export default function LoginStep1({ onSuccess }) {
  const [username, setUsername] = useState('');
  const [captchaCode, setCaptchaCode] = useState('');
  const [captchaImage, setCaptchaImage] = useState('');
  const [publicKey, setPublicKey] = useState('');
  const [useMSign, setUseMSign] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadingCaptcha, setLoadingCaptcha] = useState(false);
  const [errors, setErrors] = useState({});
  const [apiError, setApiError] = useState('');
  const [spinning, setSpinning] = useState(false);

  const loadCaptcha = useCallback(async () => {
    setLoadingCaptcha(true);
    setSpinning(true);
    try {
      const data = await fetchCaptcha();
      setCaptchaImage(data.base64Image);
      setPublicKey(data.publicKey);
      setApiError('');
    } catch (e) {
      setApiError('Không thể kết nối tới máy chủ. Vui lòng đảm bảo backend đang chạy tại http://localhost:8080');
    } finally {
      setLoadingCaptcha(false);
      setTimeout(() => setSpinning(false), 600);
    }
  }, []);

  useEffect(() => {
    loadCaptcha();
  }, [loadCaptcha]);

  const validate = () => {
    const errs = {};
    if (!username.trim()) errs.username = 'Vui lòng nhập tên đăng nhập';
    if (!captchaCode.trim()) errs.captchaCode = 'Vui lòng nhập mã xác nhận';
    return errs;
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setApiError('');
    const errs = validate();
    if (Object.keys(errs).length > 0) { setErrors(errs); return; }
    setErrors({});
    setLoading(true);
    try {
      await loginStep1(username.trim(), captchaCode.trim());
      onSuccess({ username: username.trim(), publicKey });
    } catch (err) {
      setApiError(err.message);
      loadCaptcha();
      setCaptchaCode('');
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

          <div className="form-group">
            <label className="form-label">
              MÃ XÁC NHẬN <span className="required">*</span>
            </label>
            <div className="captcha-row">
              <input
                id="captchaCode"
                className={`form-input${errors.captchaCode ? ' input-error' : ''}`}
                type="text"
                placeholder="Nhập mã xác nhận"
                value={captchaCode}
                onChange={e => { setCaptchaCode(e.target.value); setErrors(p => ({ ...p, captchaCode: '' })); }}
                autoComplete="off"
                maxLength={10}
              />
              <div className="captcha-image-box">
                {loadingCaptcha ? (
                  <div style={{
                    height: 44, width: 110, display: 'flex',
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
                    alt="Mã xác nhận"
                    style={{ cursor: 'default' }}
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
                  onClick={loadCaptcha}
                  title="Làm mới mã xác nhận"
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
            disabled={loading}
          >
            {loading ? <><div className="btn-spinner" /> Đang xử lý...</> : 'ĐĂNG NHẬP'}
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
