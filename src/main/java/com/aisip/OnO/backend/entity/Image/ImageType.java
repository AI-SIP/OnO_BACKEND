package com.aisip.OnO.backend.entity.Image;

public enum ImageType {
    PROBLEM_IMAGE(1, "problemImage"),
    ANSWER_IMAGE(2, "answerImage"),
    SOLVE_IMAGE(3, "solveImage"),
    PROCESS_IMAGE(4, "processImage");

    private final int code;
    private final String description;

    ImageType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    // code로 enum 찾기
    public static ImageType valueOf(int code) {
        for (ImageType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Invalid ImageType code: " + code);
    }
}
