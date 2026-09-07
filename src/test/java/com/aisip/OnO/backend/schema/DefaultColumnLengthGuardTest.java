package com.aisip.OnO.backend.schema;

import com.aisip.OnO.backend.support.IntegrationTestSupport;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 길이를 지정하지 않은 문자열 컬럼을 감시한다.
 *
 * <p>엔티티에 {@code @Column(length = ...)} 를 붙이지 않으면 Hibernate 는 아무 경고 없이
 * {@code varchar(255)} 를 만든다. 프로덕션에서 난
 * {@code Data truncation: Data too long for column 'memo'} 가 정확히 이 경우였다.
 * 앱은 메모를 1000자까지 받는데 컬럼은 255자였고, 아무도 그 사실을 몰랐다.
 *
 * <p>이 테스트는 기본 길이로 남은 컬럼 목록을 고정한다. 새 컬럼이 길이 지정 없이 추가되면
 * 여기서 걸리므로, 255자로 충분한지 한 번은 판단하고 넘어가게 된다.
 * 충분하다고 판단했으면 {@link #ACKNOWLEDGED_DEFAULT_LENGTH_COLUMNS} 에 추가해라.
 */
@DisplayName("스키마 가드")
class DefaultColumnLengthGuardTest extends IntegrationTestSupport {

    /**
     * varchar(255) 로 남아도 된다고 판단이 끝난 컬럼들.
     * 형식은 {@code 테이블.컬럼}. 새로 추가할 때는 "이 값이 255자를 넘을 수 있는가"를 먼저 따져라.
     */
    private static final Set<String> ACKNOWLEDGED_DEFAULT_LENGTH_COLUMNS = Set.of(
            // 사용자가 직접 긴 값을 넣을 수 없는 짧은 식별자·이름들
            "folder.name",
            "practice_note.title",
            "practice_note.repeat_type",
            "problem.reference",
            // 원본 problem.reference 가 varchar(255) 이므로 스냅샷도 255 면 충분하다.
            "problem_review_reminder.problem_reference_snapshot",
            "problem_analysis.problem_type",
            "problem_analysis.subject",
            "study_room_weekly_report.cheer_message",
            "study_room_weekly_report.longest_streak_name",
            "study_room_weekly_report.top_member_name",
            "user.email",
            "user.name",
            "user.password",
            "user.platform",

            // 아래는 255자를 넘을 여지가 있다고 판단했으나 담당 도메인에서 별도로 다루는 중이다.
            // 여기서 중복으로 실패시키지 않되, 각 도메인 테스트가 실제 길이를 검증한다.
            "fcm_token.token",
            "image_data.image_url",
            "problem_solve_image_data.image_url",
            "study_room.thumbnail_url",
            "user.identifier",
            "user.profile_image_url"
    );

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @DisplayName("길이를 지정하지 않은 문자열 컬럼은 모두 검토를 거친 것이어야 한다")
    @SuppressWarnings("unchecked")
    void everyDefaultLengthColumnIsAcknowledged() {
        List<Object[]> rows = entityManager.createNativeQuery("""
                        SELECT table_name, column_name
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND data_type = 'varchar'
                          AND character_maximum_length = 255
                          AND table_name NOT LIKE 'QRTZ_%'
                          AND table_name <> 'flyway_schema_history'
                        ORDER BY table_name, column_name
                        """)
                .getResultList();

        List<String> defaultLengthColumns = rows.stream()
                .map(row -> row[0] + "." + row[1])
                .toList();

        List<String> unacknowledged = defaultLengthColumns.stream()
                .filter(column -> !ACKNOWLEDGED_DEFAULT_LENGTH_COLUMNS.contains(column))
                .collect(Collectors.toList());

        assertThat(unacknowledged)
                .as("""
                        길이 지정 없이 varchar(255)로 생성된 컬럼이다.
                        이 값이 255자를 넘을 수 있으면 @Column(length=...) 를 주고 마이그레이션을 만들어야 한다.
                        255자로 충분하다면 ACKNOWLEDGED_DEFAULT_LENGTH_COLUMNS 에 추가해라.""")
                .isEmpty();
    }

    @Test
    @DisplayName("problem.memo 는 앱이 허용하는 1000자를 담을 수 있어야 한다")
    void problemMemoFitsClientLimit() {
        assertThat(columnLength("problem", "memo"))
                .as("앱은 메모를 1000자까지 입력받는다. 컬럼이 더 좁으면 저장 시점에 500이 난다")
                .isGreaterThanOrEqualTo(1000);
    }

    /**
     * 복습 리마인더는 발송 시점의 메모를 스냅샷으로 들고 있는다.
     * V21 마이그레이션이 이 컬럼을 VARCHAR(255)로 못박아 뒀기 때문에,
     * memo 를 넓히는 순간 스냅샷 저장에서 같은 truncation 이 재발한다.
     */
    @Test
    @DisplayName("리마인더 메모 스냅샷은 원본 메모를 그대로 담을 수 있어야 한다")
    void reminderSnapshotFitsSourceColumn() {
        assertThat(columnLength("problem_review_reminder", "problem_memo_snapshot"))
                .as("스냅샷 컬럼이 원본 memo 보다 좁으면 리마인더 생성에서 500이 난다")
                .isGreaterThanOrEqualTo(columnLength("problem", "memo"));
    }

    private int columnLength(String tableName, String columnName) {
        Number length = (Number) entityManager.createNativeQuery("""
                        SELECT character_maximum_length
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = :tableName
                          AND column_name = :columnName
                        """)
                .setParameter("tableName", tableName)
                .setParameter("columnName", columnName)
                .getSingleResult();
        return length.intValue();
    }
}
