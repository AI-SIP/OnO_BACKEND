package com.aisip.OnO.backend.entity.Problem;

public enum TemplateType {

    SIMPLE_TEMPLATE(1L, "simple template"),
    CLEAN_TEMPLATE(2L, "clean template"),
    SPECIAL_TEMPLATE(3L, "special template");

    private final Long code;
    private final String description;

    TemplateType(Long code, String description) {
        this.code = code;
        this.description = description;
    }

    public Long getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static TemplateType valueOf(Long code) {
        for (TemplateType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid TemplateType code: " + code);
    }
}
