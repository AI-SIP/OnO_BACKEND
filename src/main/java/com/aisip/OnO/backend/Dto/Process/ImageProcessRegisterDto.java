package com.aisip.OnO.backend.Dto.Process;

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
public class ImageProcessRegisterDto {
    private String fullUrl;
    private List<Map<String, Integer>> colorsList;
    private List<List<Double>> points;
    private int intensity;
}
