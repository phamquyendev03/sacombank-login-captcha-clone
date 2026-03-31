package com.quyen.sacombanklogindemo.controller;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.quyen.sacombanklogindemo.domain.User;
import com.quyen.sacombanklogindemo.domain.request.ReqLoginStep1DTO;
import com.quyen.sacombanklogindemo.domain.request.ReqLoginStep2DTO;
import com.quyen.sacombanklogindemo.repository.UserRepository;
import com.quyen.sacombanklogindemo.service.CaptchaService;
import com.quyen.sacombanklogindemo.util.RSAUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final CaptchaService captchaService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(CaptchaService captchaService,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.captchaService = captchaService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping("/captcha")
    public ResponseEntity<?> getCaptcha(HttpServletRequest request) {
        try {
            HttpSession session = request.getSession(true);

            String[] captchaData = captchaService.generateCaptchaTextAndImage();
            String captchaText  = captchaData[0];
            String base64Image  = captchaData[1];
            session.setAttribute("CAPTCHA_CODE", captchaText);

            KeyPair keyPair = RSAUtil.generateKeyPair();
            session.setAttribute("RSA_PRIVATE_KEY", keyPair.getPrivate());

            String publicKeyStr = RSAUtil.getPublicKeyBase64(keyPair.getPublic());

            Map<String, String> resp = new HashMap<>();
            resp.put("base64Image", base64Image);
            resp.put("publicKey",   publicKeyStr);
            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body("Lỗi khi tạo phiên đăng nhập!");
        }
    }

    @PostMapping("/login-step1")
    public ResponseEntity<?> loginStep1(
            @Valid @RequestBody ReqLoginStep1DTO dto,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.badRequest()
                    .body("Phiên đăng nhập đã hết hạn, vui lòng tải lại trang!");
        }

        String sessionCaptcha = (String) session.getAttribute("CAPTCHA_CODE");
        if (sessionCaptcha == null || !sessionCaptcha.equalsIgnoreCase(dto.getCaptchaCode())) {
            return ResponseEntity.badRequest()
                    .body("Mã xác nhận không chính xác hoặc đã hết hạn!");
        }

        User dbUser = userRepository.findByUsername(dto.getUsername());
        if (dbUser == null) {
            return ResponseEntity.badRequest()
                    .body("Tài khoản không tồn tại trong hệ thống!");
        }

        session.setAttribute("LOGIN_USERNAME", dto.getUsername());

        session.removeAttribute("CAPTCHA_CODE");

        return ResponseEntity.ok("OK");
    }

    @PostMapping("/login-step2")
    public ResponseEntity<?> loginStep2(
            @Valid @RequestBody ReqLoginStep2DTO dto,
            HttpServletRequest request) {

        HttpSession session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.badRequest()
                    .body("Phiên đăng nhập đã hết hạn, vui lòng bắt đầu lại!");
        }

        String username = (String) session.getAttribute("LOGIN_USERNAME");
        if (username == null) {
            return ResponseEntity.badRequest()
                    .body("Phiên đăng nhập không hợp lệ, vui lòng thực hiện lại bước 1!");
        }

        PrivateKey privateKey = (PrivateKey) session.getAttribute("RSA_PRIVATE_KEY");
        if (privateKey == null) {
            return ResponseEntity.badRequest()
                    .body("Lỗi khóa bảo mật RSA, vui lòng tải lại trang!");
        }

        String decryptedPassword;
        try {
            decryptedPassword = RSAUtil.decrypt(dto.getPassword(), privateKey);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Giải mã mật khẩu thất bại, dữ liệu có thể bị sai lệch!");
        }

        session.removeAttribute("LOGIN_USERNAME");
        session.removeAttribute("RSA_PRIVATE_KEY");

        User dbUser = userRepository.findByUsername(username);
        if (dbUser == null) {
            return ResponseEntity.badRequest().body("Tài khoản không tồn tại!");
        }

        boolean isMatch = passwordEncoder.matches(decryptedPassword, dbUser.getPassword());
        if (!isMatch) {
            return ResponseEntity.badRequest().body("Sai mật khẩu đăng nhập!");
        }

        String msg = dto.isRememberMe()
                ? "Đăng nhập thành công! Phiên đăng nhập sẽ được ghi nhớ."
                : "Đăng nhập thành công!";

        return ResponseEntity.ok(msg);
    }


    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody User user) {
        if (userRepository.findByUsername(user.getUsername()) != null) {
            return ResponseEntity.badRequest().body("Tài khoản đã tồn tại!");
        }
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        userRepository.save(user);
        return ResponseEntity.ok("Đăng ký thành công! Username: " + user.getUsername());
    }
}
