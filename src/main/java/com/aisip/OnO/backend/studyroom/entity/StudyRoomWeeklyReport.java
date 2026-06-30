package com.aisip.OnO.backend.studyroom.entity;

import com.aisip.OnO.backend.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Entity
@Builder(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "study_room_weekly_report",
        uniqueConstraints = @UniqueConstraint(name = "uk_study_room_weekly_report", columnNames = {"room_id", "week_start"}))
public class StudyRoomWeeklyReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private StudyRoom room;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false)
    private LocalDate weekEnd;

    @Column(name = "top_member_name")
    private String topMemberName;

    @Column(name = "top_member_problem_count", nullable = false)
    private Integer topMemberProblemCount;

    @Column(name = "longest_streak_name")
    private String longestStreakName;

    @Column(name = "longest_streak_days", nullable = false)
    private Integer longestStreakDays;

    @Column(name = "top_member_profile_image_url", length = 512)
    private String topMemberProfileImageUrl;

    @Column(name = "longest_streak_profile_image_url", length = 512)
    private String longestStreakProfileImageUrl;

    @Column(name = "total_problems", nullable = false)
    private Integer totalProblems;

    @Column(name = "challenges_completed", nullable = false)
    private Integer challengesCompleted;

    @Column(name = "cheer_message", nullable = false)
    private String cheerMessage;

    public static StudyRoomWeeklyReport create(StudyRoom room, LocalDate weekStart, LocalDate weekEnd,
                                               String topMemberName, String topMemberProfileImageUrl,
                                               int topMemberProblemCount,
                                               String longestStreakName, String longestStreakProfileImageUrl,
                                               int longestStreakDays,
                                               int totalProblems, int challengesCompleted, String cheerMessage) {
        return StudyRoomWeeklyReport.builder()
                .room(room)
                .weekStart(weekStart)
                .weekEnd(weekEnd)
                .topMemberName(topMemberName)
                .topMemberProfileImageUrl(topMemberProfileImageUrl)
                .topMemberProblemCount(topMemberProblemCount)
                .longestStreakName(longestStreakName)
                .longestStreakProfileImageUrl(longestStreakProfileImageUrl)
                .longestStreakDays(longestStreakDays)
                .totalProblems(totalProblems)
                .challengesCompleted(challengesCompleted)
                .cheerMessage(cheerMessage)
                .build();
    }
}
