package com.aisip.OnO.backend.learningcalendar.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Getter
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "learning_calendar_mood",
        uniqueConstraints = @UniqueConstraint(name = "uk_learning_calendar_mood_user_date", columnNames = {"user_id", "study_date"}))
public class LearningCalendarMood extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "study_date", nullable = false)
    private LocalDate studyDate;

    @Column(name = "emoji_key", nullable = false, length = 80)
    private String emojiKey;

    public static LearningCalendarMood create(Long userId, LocalDate studyDate, String emojiKey) {
        return LearningCalendarMood.builder()
                .userId(userId)
                .studyDate(studyDate)
                .emojiKey(emojiKey)
                .build();
    }

    public void updateEmojiKey(String emojiKey) {
        this.emojiKey = emojiKey;
    }
}
