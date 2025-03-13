package com.aisip.OnO.backend.problem.entity;

public enum ProblemTemplateType {

    SIMPLE_TEMPLATE(1L, "simple template"),
    CLEAN_TEMPLATE(2L, "clean template"),
    SPECIAL_TEMPLATE(3L, "special template");

    private final Long code;
    private final String description;

    ProblemTemplateType(Long code, String description) {
        this.code = code;
        this.description = description;
    }

    public Long getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ProblemTemplateType valueOf(Long code) {
        for (ProblemTemplateType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid TemplateType code: " + code);
    }
}
