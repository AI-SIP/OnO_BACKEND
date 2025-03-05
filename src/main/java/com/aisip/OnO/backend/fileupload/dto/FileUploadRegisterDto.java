package com.aisip.OnO.backend.fileupload.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadRegisterDto {
    private String fullUrl;
    private List<Map<String, Integer>> colorsList;
    private List<List<Double>> points;
    private List<Long> labels;
    private int intensity;
}
