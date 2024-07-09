package com.aisip.OnO.backend.entity.Image;

import com.aisip.OnO.backend.entity.Problem;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "image_data")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImageData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "image_url")
    private String imageUrl;

    @Enumerated(EnumType.STRING) // Enum 타입을 문자열로 저장
    @Column(name = "image_type")
    private ImageType imageType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "problem_id")
    private Problem problem;
}
