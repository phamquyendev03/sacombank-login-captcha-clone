package com.quyen.sacombanklogindemo.domain.request;

import lombok.Data;

@Data
public class ProxyLoginRequestDTO {

    /**
     * Tên đăng nhập (USER_ID trên Sacombank)
     */
    private String username;

    /**
     * Mật khẩu đã được mã hóa RSA bằng jCryption trên Frontend
     * Format: "password=<plaintext>_SALT_COMPONENT_=<random>" (rồi RSA encrypt)
     */
    private String password;

    /**
     * Chuỗi captcha người dùng nhập (CAPTCHA_ANSWER)
     */
    private String captchaAnswer;

    /**
     * JSESSIONID nhận được từ API /auth/init, cần gắn vào Cookie khi proxy POST
     */
    private String jsessionId;

    /**
     * Device fingerprint sinh ra bởi fingerprint.js trên Frontend
     */
    private String deviceFingerprint;

    /**
     * URL POST đầy đủ trả về từ API /auth/init (đã chứa jsessionid + bwayparam)
     */
    private String submitActionUrl;
}
