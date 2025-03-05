package com.aisip.OnO.backend.problem.entity;

public enum ProblemImageType {
    PROBLEM_IMAGE(1, "problemImage"),
    ANSWER_IMAGE(2, "answerImage"),
    SOLVE_IMAGE(3, "solveImage"),
    PROCESS_IMAGE(4, "processImage");

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
