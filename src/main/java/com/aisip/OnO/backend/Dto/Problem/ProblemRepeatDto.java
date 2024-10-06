package com.aisip.OnO.backend.Dto.Problem;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemRepeatDto {
    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
