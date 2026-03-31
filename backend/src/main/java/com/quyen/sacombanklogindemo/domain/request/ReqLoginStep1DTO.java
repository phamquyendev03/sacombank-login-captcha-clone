package com.quyen.sacombanklogindemo.domain.request;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO cho Login Step 1: chỉ cần username + captchaCode
 * Password sẽ được gửi riêng ở Step 2
 */
public class ReqLoginStep1DTO {

    @NotBlank(message = "username không được để trống")
    private String username;

    @NotBlank(message = "mã xác nhận không được để trống")
    private String captchaCode;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getCaptchaCode() { return captchaCode; }
    public void setCaptchaCode(String captchaCode) { this.captchaCode = captchaCode; }
}
