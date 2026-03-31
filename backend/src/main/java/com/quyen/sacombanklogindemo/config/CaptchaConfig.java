package com.quyen.sacombanklogindemo.config;

import java.util.Properties;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.code.kaptcha.impl.DefaultKaptcha;
import com.google.code.kaptcha.util.Config;

@Configuration
public class CaptchaConfig {

    @Bean
    public DefaultKaptcha kaptchaProducer() {
        Properties properties = new Properties();
        properties.setProperty("kaptcha.image.width", "200");
        properties.setProperty("kaptcha.image.height", "40");

        properties.setProperty("kaptcha.textproducer.font.size", "30");
        properties.setProperty("kaptcha.textproducer.font.names", "Arial,Courier");
        properties.setProperty("kaptcha.textproducer.font.color", "black");

        properties.setProperty("kaptcha.textproducer.char.length", "6");

        properties.setProperty("kaptcha.textproducer.char.string", "23456789abcdefghkmnpqrstuvwxyzABCDEFGHKMNPQRSTUVWXYZ");

        properties.setProperty("kaptcha.border", "no");

        properties.setProperty("kaptcha.noise.color", "black");
        properties.setProperty("kaptcha.noise.impl", "com.google.code.kaptcha.impl.DefaultNoise");
        properties.setProperty("kaptcha.obscurificator.impl", "com.google.code.kaptcha.impl.WaterRipple");

        Config config = new Config(properties);
        DefaultKaptcha defaultKaptcha = new DefaultKaptcha();
        defaultKaptcha.setConfig(config);
        return defaultKaptcha;
    }
}
