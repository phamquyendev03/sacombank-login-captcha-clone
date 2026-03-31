package com.quyen.sacombanklogindemo.domain.response;

public class ResCaptchaDTO {
    private String captchaId;
    private String base64Image;

    public ResCaptchaDTO(String captchaId, String base64Image) {
        this.captchaId = captchaId;
        this.base64Image = base64Image;
    }

    public String getCaptchaId() {
        return captchaId;
    }

    public void setCaptchaId(String captchaId) {
        this.captchaId = captchaId;
    }

    public String getBase64Image() {
        return base64Image;
    }

    public void setBase64Image(String base64Image) {
        this.base64Image = base64Image;
    }
}
