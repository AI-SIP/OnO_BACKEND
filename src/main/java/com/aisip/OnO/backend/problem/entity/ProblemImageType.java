package com.aisip.OnO.backend.problem.entity;

public enum ProblemImageType {
    PROBLEM_IMAGE(1, "PROBLEM_IMAGE"),
    ANSWER_IMAGE(2, "ANSWER_IMAGE"),
    SOLVE_IMAGE(3, "SOLVE_IMAGE"),
    PROCESS_IMAGE(4, "PROCESS_IMAGE");

    private final int code;
    private final String description;

    ProblemImageType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static ProblemImageType valueOf(int code) {
        for (ProblemImageType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid ImageType code: " + code);
    }
}
