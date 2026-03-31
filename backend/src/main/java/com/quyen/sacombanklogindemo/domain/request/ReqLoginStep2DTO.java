package com.quyen.sacombanklogindemo.domain.request;

import jakarta.validation.constraints.NotBlank;


public class ReqLoginStep2DTO {

    @NotBlank(message = "mật khẩu không được để trống")
    private String password; 

    private boolean rememberMe;

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public boolean isRememberMe() { return rememberMe; }
    public void setRememberMe(boolean rememberMe) { this.rememberMe = rememberMe; }
}
