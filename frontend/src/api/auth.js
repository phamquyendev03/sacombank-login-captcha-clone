const BASE_URL = 'http://localhost:8080';

export async function fetchCaptcha() {
  const res = await fetch(`${BASE_URL}/auth/captcha`, {
    credentials: 'include', 
  });
  if (!res.ok) throw new Error('Không thể tải mã xác nhận');
  return res.json(); 
}

export async function loginStep1(username, captchaCode) {
  const res = await fetch(`${BASE_URL}/auth/login-step1`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, captchaCode }),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(text || 'Đăng nhập thất bại');
  return text;
}

export async function loginStep2(encryptedPassword, rememberMe) {
  const res = await fetch(`${BASE_URL}/auth/login-step2`, {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password: encryptedPassword, rememberMe }),
  });
  const text = await res.text();
  if (!res.ok) throw new Error(text || 'Sai mật khẩu đăng nhập');
  return text;
}

export async function encryptPasswordRSA(password, publicKeyBase64) {
  const binaryStr = atob(publicKeyBase64);
  const bytes = new Uint8Array(binaryStr.length);
  for (let i = 0; i < binaryStr.length; i++) {
    bytes[i] = binaryStr.charCodeAt(i);
  }

  const cryptoKey = await window.crypto.subtle.importKey(
    'spki',
    bytes.buffer,
    { name: 'RSA-OAEP', hash: 'SHA-256' },
    false,
    ['encrypt']
  );

  const encoder = new TextEncoder();
  const encoded = encoder.encode(password);
  const encrypted = await window.crypto.subtle.encrypt(
    { name: 'RSA-OAEP' },
    cryptoKey,
    encoded
  );

  const encryptedBytes = new Uint8Array(encrypted);
  let binary = '';
  encryptedBytes.forEach(b => (binary += String.fromCharCode(b)));
  return btoa(binary);
}
