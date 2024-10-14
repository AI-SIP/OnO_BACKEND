package com.aisip.OnO.backend.Dto.Problem;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemRepeatDto {
    private Long id;
    private String solveImageUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
