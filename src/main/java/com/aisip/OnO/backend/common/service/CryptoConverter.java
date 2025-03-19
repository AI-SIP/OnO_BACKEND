package com.aisip.OnO.backend.common.service;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Converter
@Component
public class CryptoConverter implements AttributeConverter<String, String> {

    private final CryptoService cryptoService;

    // ✅ 생성자 주입 방식으로 CryptoService 사용 가능하게 수정
    @Autowired
    public CryptoConverter(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        try {
            return cryptoService.encrypt(attribute); // ✅ static 제거된 메서드 사용
        } catch (Exception e) {
            throw new RuntimeException("암호화 오류", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        try {
            return cryptoService.decrypt(dbData); // ✅ static 제거된 메서드 사용
        } catch (Exception e) {
            throw new RuntimeException("복호화 오류", e);
        }
    }
}
