import { useState } from 'react';
import LoginStep1 from './pages/LoginStep1';
import LoginStep2 from './pages/LoginStep2';
import './index.css';

// Success screen
function LoginSuccess({ username, message, onReset }) {
  return (
    <div className="login-wrapper">
      <div className="login-card">
        <div className="success-card">
          <div className="success-icon">🎉</div>
          <div className="success-title">Đăng nhập thành công!</div>
          <p className="success-msg" style={{ marginTop: 8, marginBottom: 20 }}>
            Chào mừng <strong>{username}</strong>!<br />
            <span style={{ fontSize: 12, color: '#9ca3af' }}>{message}</span>
          </p>
          <button
            className="btn-primary"
            style={{ marginTop: 8 }}
            onClick={onReset}
          >
            Đăng xuất
          </button>
        </div>
      </div>
    </div>
  );
}

export default function App() {
  const [step, setStep] = useState(1);

  /**
   * userData chứa toàn bộ thông tin phiên Sacombank từ Step 1:
   * - username        : tên đăng nhập
   * - captchaAnswer   : mã captcha đã nhập
   * - encryptKey      : __JS_ENCRYPT_KEY__ từ Sacombank
   * - jsessionId      : JSESSIONID của phiên Sacombank
   * - submitActionUrl : URL POST form Sacombank
   */
  const [userData, setUserData] = useState({
    username:        '',
    captchaAnswer:   '',
    encryptKey:      '',
    jsessionId:      '',
    submitActionUrl: '',
  });
  const [successMsg, setSuccessMsg] = useState('');

  // Khi Step 1 hoàn tất: lưu session data và chuyển sang Step 2
  const handleStep1Success = ({ username, captchaAnswer, encryptKey, jsessionId, submitActionUrl }) => {
    setUserData({ username, captchaAnswer, encryptKey, jsessionId, submitActionUrl });
    setStep(2);
  };

  const handleStep2Success = (msg) => {
    setSuccessMsg(msg);
    setStep('success');
  };

  const handleReset = () => {
    setStep(1);
    setUserData({ username: '', captchaAnswer: '', encryptKey: '', jsessionId: '', submitActionUrl: '' });
    setSuccessMsg('');
  };

  if (step === 'success') {
    return (
      <LoginSuccess
        username={userData.username}
        message={successMsg}
        onReset={handleReset}
      />
    );
  }

  if (step === 2) {
    return (
      <LoginStep2
        username={userData.username}
        captchaAnswer={userData.captchaAnswer}
        encryptKey={userData.encryptKey}
        jsessionId={userData.jsessionId}
        submitActionUrl={userData.submitActionUrl}
        onBack={() => setStep(1)}
        onSuccess={handleStep2Success}
      />
    );
  }

  return <LoginStep1 onSuccess={handleStep1Success} />;
}
