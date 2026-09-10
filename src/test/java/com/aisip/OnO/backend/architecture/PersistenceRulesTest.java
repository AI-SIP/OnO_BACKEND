package com.aisip.OnO.backend.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 반복적으로 프로덕션 장애를 만들어 온 실수를 구조적으로 막는다.
 *
 * <p>여기 있는 규칙은 전부 이 서비스에서 <b>실제로 장애를 낸 결함</b>에서 나왔다.
 * 개별 건을 고치는 것으로 끝내면 같은 형태가 다시 들어온다. 규칙으로 올려 두면
 * 그 결함 자체가 다시 들어올 수 없다.
 *
 * <p>이미 존재하는 위반 중 지금 고칠 수 없는 것은 목록으로 고정해 둔다.
 * 새로 들어오는 코드만 막고, 목록은 줄여 나간다.
 */
@DisplayName("아키텍처 규칙")
class PersistenceRulesTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                // QueryDSL 이 만들어 낸 Q 클래스는 사람이 손대는 코드가 아니다.
                .withImportOption(location -> !location.contains("/Q"))
                .importPackages("com.aisip.OnO.backend");
    }

    @Nested
    @DisplayName("트랜잭션 경계")
    class TransactionBoundary {

        /**
         * Spring AOP 는 프록시 기반이라 같은 클래스 안에서 호출되는 private 메서드를 가로챌 수 없다.
         * 즉 private 메서드의 {@code @Transactional} 은 아무 일도 하지 않는다.
         *
         * <p>실제로 ProblemService 의 analysisProblemWithoutOwnerCheck, deleteProblemWithoutOwnerCheck,
         * deleteFolderProblems 와 FolderService 의 findFolderEntity 가 이 상태였다. 작성자는
         * 트랜잭션 경계가 있다고 믿었지만 실제로는 없었고, 그 사실을 알려 주는 것은 아무것도 없었다.
         */
        @Test
        @DisplayName("@Transactional 은 public 메서드에만 붙는다 - 프록시가 가로채지 못하면 무효다")
        void transactionalOnlyOnPublicMethods() {
            methods()
                    .that().areAnnotatedWith(Transactional.class)
                    .should().bePublic()
                    .because("Spring AOP 프록시는 private·protected 메서드를 가로챌 수 없어 "
                            + "@Transactional 이 조용히 무효가 된다")
                    .check(productionClasses);
        }
    }

    @Nested
    @DisplayName("엔티티 매핑")
    class EntityMapping {

        /**
         * {@code @Enumerated} 를 빼면 JPA 기본값은 ORDINAL 이라 enum 의 <b>선언 순서</b>가 DB 에 저장된다.
         * 나중에 상수를 중간에 끼워 넣거나 순서를 바꾸면 이미 저장된 모든 행의 의미가 조용히 뒤바뀐다.
         */
        private static final Set<String> ENUMERATED_EXCEPTIONS = Set.of(
                // 이미 ORDINAL 로 저장된 데이터가 쌓여 있다. STRING 으로 바꾸려면 데이터 마이그레이션이
                // 선행돼야 해서 이번 범위에서 다루지 못했다. 현재 선언 순서는
                // UserMissionStatusTest.pinsOrdinalOrder() 가 고정하고 있다.
                "com.aisip.OnO.backend.mission.entity.MissionLog.missionType"
        );

        @Test
        @DisplayName("엔티티의 enum 필드는 @Enumerated 를 붙인다 - 순서가 아니라 이름으로 저장하도록")
        void enumFieldsDeclareEnumerated() {
            fields()
                    .that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
                    .and().haveRawType(enumType())
                    .and(persistentField())
                    .and(not(knownException()))
                    .should().beAnnotatedWith(Enumerated.class)
                    .because("생략하면 ORDINAL 로 저장돼 enum 선언 순서를 바꾸는 순간 기존 데이터의 의미가 바뀐다")
                    .check(productionClasses);
        }

        private DescribedPredicate<JavaField> knownException() {
            return new DescribedPredicate<>("이미 알려진 예외") {
                @Override
                public boolean test(JavaField field) {
                    return ENUMERATED_EXCEPTIONS.contains(field.getFullName().replaceAll("\\.\\w+$", "." + field.getName()));
                }
            };
        }
    }

    @Nested
    @DisplayName("시간대")
    class TimeZone {

        /**
         * {@code LocalDate.now()} 는 JVM 기본 시간대를 쓴다. 서비스는 한국 사용자 기준으로 하루를 세므로
         * 서버 시간대가 KST 가 아니면 "오늘"이 어긋난다.
         *
         * <p>실제로 mission 도메인은 JVM 기본 시간대를, problem·studyroom 은 Asia/Seoul 을 쓰고 있었고,
         * LearningReportService 는 <b>같은 클래스 안에서도</b> 메서드마다 기준이 갈라져 있었다.
         * 그 결과 자정 부근 기록이 도메인마다 다른 날짜로 집계됐다.
         */
        /**
         * 이미 존재하는 위반 목록. 새 코드만 막고 이 목록은 줄여 나간다.
         *
         * <p>프로덕션 컨테이너는 {@code TZ: Asia/Seoul} 과 {@code -Duser.timezone=Asia/Seoul} 로 뜨기 때문에
         * 지금 당장은 인자 없는 {@code now()} 도 KST 를 낸다. 즉 이 목록은 현재 장애 원인이 아니라
         * <b>잠재 위험</b>이다. 서버 시간대 설정이 빠지거나 다른 환경에서 돌리는 순간 하루 경계가 어긋난다.
         * 한 번에 24곳을 바꾸는 것은 이 PR 의 범위(테스트 개선) 대비 위험이 커서 고정만 해 둔다.
         */
        private static final Set<String> ZONE_LESS_NOW_EXCEPTIONS = Set.of(
                "com.aisip.OnO.backend.admin.controller.AdminAnalysisController",
                "com.aisip.OnO.backend.feedback.service.FeedbackService",
                "com.aisip.OnO.backend.mission.repository.MissionLogRepositoryImpl",
                "com.aisip.OnO.backend.practicenote.entity.PracticeNote",
                "com.aisip.OnO.backend.studyroom.quartz.ChallengeNotificationScheduler",
                "com.aisip.OnO.backend.studyroom.service.StudyRoomChallengeService",
                "com.aisip.OnO.backend.studyroom.service.StudyRoomInviteService",
                "com.aisip.OnO.backend.studyroom.service.StudyRoomWeeklyReportService",
                "com.aisip.OnO.backend.user.service.UserService",
                "com.aisip.OnO.backend.util.fileupload.service.FileUploadService"
        );

        @Test
        @DisplayName("현재 시각은 시간대를 명시해서 읽는다 - 서버 시간대에 따라 하루가 밀리지 않도록")
        void currentTimeIsReadWithExplicitZone() {
            noClasses()
                    .that(not(alreadyKnownToUseZonelessNow()))
                    .should().callMethod(LocalDate.class, "now")
                    .orShould().callMethod(LocalDateTime.class, "now")
                    .because("인자 없는 now() 는 JVM 기본 시간대를 쓴다. "
                            + "서비스 기준은 Asia/Seoul 이므로 now(ZoneId) 로 명시해야 한다")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("기본 시간대를 끌어다 쓰지 않는다 - 하루 경계는 항상 명시된 시간대로")
        void doesNotFallBackToJvmDefaultZone() {
            // now(ZoneId.systemDefault()) 는 인자 없는 now() 와 똑같이 JVM 기본 시간대를 쓰면서
            // 위 규칙만 피해 간다. 게다가 한 번 static final 로 잡아 두면 동작으로는 구별할 수 없어
            // 테스트로 잡을 방법이 없다. 그래서 호출 자체를 여기서 막는다.
            noClasses()
                    .should().callMethod(ZoneId.class, "systemDefault")
                    .because("서비스 기준 시간대는 Asia/Seoul 이다. "
                            + "JVM 기본값을 끌어다 쓰면 배포 환경 설정이 빠지는 순간 하루 경계가 어긋난다")
                    .check(productionClasses);
        }

        private DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass> alreadyKnownToUseZonelessNow() {
            return new DescribedPredicate<>("이미 알려진 위반") {
                @Override
                public boolean test(com.tngtech.archunit.core.domain.JavaClass javaClass) {
                    return ZONE_LESS_NOW_EXCEPTIONS.contains(javaClass.getName());
                }
            };
        }
    }

    // ─────────────────────────── 조건 정의 ───────────────────────────

    private static DescribedPredicate<com.tngtech.archunit.core.domain.JavaClass> enumType() {
        return new DescribedPredicate<>("enum 타입") {
            @Override
            public boolean test(com.tngtech.archunit.core.domain.JavaClass javaClass) {
                return javaClass.isEnum();
            }
        };
    }

    /** 컬럼으로 매핑되지 않는 필드는 규칙 대상이 아니다. */
    private static DescribedPredicate<JavaField> persistentField() {
        return new DescribedPredicate<>("영속 대상 필드") {
            @Override
            public boolean test(JavaField field) {
                return !field.isAnnotatedWith(Transient.class)
                        && !field.isAnnotatedWith(Id.class)
                        && !field.getModifiers().contains(JavaModifier.STATIC);
            }
        };
    }

    private static <T> DescribedPredicate<T> not(DescribedPredicate<T> predicate) {
        return DescribedPredicate.not(predicate);
    }
}
