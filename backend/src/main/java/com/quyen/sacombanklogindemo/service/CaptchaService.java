package com.quyen.sacombanklogindemo.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.google.code.kaptcha.impl.DefaultKaptcha;

@Service
public class CaptchaService {

    private final DefaultKaptcha kaptchaProducer;

    private final Map<String, String> captchaStore = new ConcurrentHashMap<>();

    public CaptchaService(DefaultKaptcha kaptchaProducer) {
        this.kaptchaProducer = kaptchaProducer;
    }

    public String[] generateCaptchaTextAndImage() {
        String capText = kaptchaProducer.createText();

        BufferedImage bi = kaptchaProducer.createImage(capText);

        String base64Img = "";
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(bi, "jpg", bos);
            byte[] imageBytes = bos.toByteArray();
            base64Img = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new String[]{capText, base64Img};
    }

    public boolean validateCaptcha(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null) {
            return false;
        }

        String storedCode = captchaStore.get(captchaId);

        if (storedCode != null && storedCode.equalsIgnoreCase(captchaCode)) {
            captchaStore.remove(captchaId);
            return true;
        }
        return false;
    }
}
