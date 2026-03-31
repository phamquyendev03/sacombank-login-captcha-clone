import { useState } from 'react';
import { loginStep2, encryptPasswordRSA } from '../api/auth';

export default function LoginStep2({ username, publicKey, onSuccess }) {
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const getAvatar = (name) => (name ? name.charAt(0).toUpperCase() : '?');

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    if (!password) { setError('Vui lòng nhập mật khẩu'); return; }
    setLoading(true);
    try {
      let encryptedPassword = password;
      if (publicKey) {
        encryptedPassword = await encryptPasswordRSA(password, publicKey);
      }
      const result = await loginStep2(encryptedPassword, rememberMe);
      onSuccess(result);
    } catch (err) {
      setError(err.message);
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
            <div className="user-display-hint">Tài khoản đã xác thực ✓</div>
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
        </form>

        {/* Footer */}
        <div className="login-footer">
          <button className="link-btn">Quên mật khẩu?</button>
        </div>
      </div>
    </div>
  );
}
