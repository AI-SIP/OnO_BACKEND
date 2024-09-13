package com.aisip.OnO.backend.Dto.Problem;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemRegisterDto {

    private Long problemId;

    private MultipartFile problemImage;

    private MultipartFile answerImage;

    private MultipartFile solveImage;

    private String memo;

    private String reference;

    private Long folderId;

    private boolean process = true;

    private LocalDateTime solvedAt;

    private String colors;  // JSON 문자열 형태로 유지

    private List<Map<String, Integer>> colorsList;  // 파싱된 결과를 저장할 리스트

    public void initColorsList() {
        if (colors != null && !colors.isEmpty()) {
            ObjectMapper objectMapper = new ObjectMapper();
            try {
                this.colorsList = objectMapper.readValue(colors, new TypeReference<List<Map<String, Integer>>>() {
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            this.colorsList = Arrays.asList(null, null, null);
        }
    }
}
