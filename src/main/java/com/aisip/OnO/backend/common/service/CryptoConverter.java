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
        // AttributeConverter 계약상 null 은 그대로 통과시켜야 한다.
        // 예전에는 여기서 NPE 가 나 RuntimeException("암호화 오류") 로 감싸져 500 이 됐다.
        if (attribute == null) {
            return null;
        }
        try {
            return cryptoService.encrypt(attribute); // ✅ static 제거된 메서드 사용
        } catch (Exception e) {
            throw new RuntimeException("암호화 오류", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return cryptoService.decrypt(dbData); // ✅ static 제거된 메서드 사용
        } catch (Exception e) {
            throw new RuntimeException("복호화 오류", e);
        }
    }
}
