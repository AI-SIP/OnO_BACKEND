package com.aisip.OnO.backend.entity.Problem;

public enum TemplateType {

    SIMPLE_TEMPLATE(1, "simple template"),
    CLEAN_TEMPLATE(2, "clean template"),
    SPECIAL_TEMPLATE(3, "special template");

    private final int code;
    private final String description;

    TemplateType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static TemplateType valueOf(int code) {
        for (TemplateType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid TemplateType code: " + code);
    }
}
