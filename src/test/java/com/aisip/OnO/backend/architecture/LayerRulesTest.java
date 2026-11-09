package com.aisip.OnO.backend.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING;

/**
 * 계층 사이 의존 방향을 고정한다.
 *
 * <p>여기 있는 규칙은 전부 <b>지금 코드가 이미 지키고 있는 것</b>이다. 2026-10-14 에 후보 규칙을
 * 현재 코드에 돌려 위반이 0 건인 것만 넣었다. 새 코드가 이 방향을 깨면 PR 단계에서 바로 걸린다.
 *
 * <p>위반이 남아 있어 넣지 않은 규칙(괄호는 당시 위반 수)은 아래와 같다. 줄어들면 여기로 옮긴다.
 * <ul>
 *   <li>controller 가 repository 를 직접 의존하지 않는다 (88, 전부 admin 화면 controller).
 *       그래서 아래에는 admin 을 뺀 범위로만 넣었다.</li>
 *   <li>도메인 패키지 사이에 순환이 없다 (100 이상, ArchUnit 이 세는 상한에 걸렸다)</li>
 *   <li>controller 메서드에 {@code @Transactional} 을 붙이지 않는다 (2, AdminUserController)</li>
 *   <li>메서드 {@code @Transactional} 은 service, repository 패키지에만 (10)</li>
 *   <li>entity 가 dto 를 의존하지 않는다 (61), entity 가 service 를 의존하지 않는다 (18)</li>
 *   <li>dto 가 repository 를 의존하지 않는다 (8), dto 가 service 를 의존하지 않는다 (1)</li>
 *   <li>controller 가 entity 를 의존하지 않는다 (21)</li>
 *   <li>필드 주입을 쓰지 않는다 (28, 대부분 설정 클래스의 {@code @Value} 필드)</li>
 *   <li>System.out 을 쓰지 않는다 (2), RuntimeException 같은 범용 예외를 던지지 않는다 (16)</li>
 *   <li>{@code @Entity} 는 entity 패키지에, Spring Data 리포지토리는 repository 패키지에 (각 1, problem.reminder)</li>
 * </ul>
 */
@DisplayName("계층 규칙")
class LayerRulesTest {

    private static JavaClasses productionClasses;

    @BeforeAll
    static void importClasses() {
        productionClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                // QueryDSL 이 만들어 낸 Q 클래스는 사람이 손대는 코드가 아니다. PersistenceRulesTest 와 같은 기준이다.
                .withImportOption(location -> !location.contains("/Q"))
                .importPackages("com.aisip.OnO.backend");
    }

    @Nested
    @DisplayName("의존 방향")
    class DependencyDirection {

        @Test
        @DisplayName("controller 는 아무 계층도 의존하지 않는다 - 요청 진입점은 맨 바깥이다")
        void nothingDependsOnControllers() {
            noClasses()
                    .that().resideOutsideOfPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..controller..")
                    .because("service 나 repository 가 controller 를 알면 웹 계층을 바꿀 때 아래 계층까지 같이 흔들린다")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("repository 는 service 를 의존하지 않는다")
        void repositoriesDoNotDependOnServices() {
            noClasses()
                    .that().resideInAPackage("..repository..")
                    .should().dependOnClassesThat().resideInAPackage("..service..")
                    .because("repository 가 service 를 부르면 트랜잭션 경계와 소유권 검증이 어디서 일어나는지 흐려진다")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("entity 는 repository 를 의존하지 않는다")
        void entitiesDoNotDependOnRepositories() {
            noClasses()
                    .that().resideInAPackage("..entity..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .because("엔티티 안에서 조회를 하면 영속성 컨텍스트 밖에서 쿼리가 나가는 경로가 생긴다")
                    .check(productionClasses);
        }

        /**
         * 앱이 부르는 API controller 는 service 를 거친다. userId 소유권 검증이 service 에 있어서,
         * controller 가 repository 를 바로 부르면 그 검증을 건너뛴 조회가 생길 수 있다.
         *
         * <p>admin 화면 controller 는 조회용 QueryRepository 를 직접 쓰는 구조라 지금 88 건이 걸린다.
         * 관리자 전용 경로(ROLE_ADMIN)라 사용자 데이터 격리 규칙과는 성격이 달라서 범위에서 뺐다.
         */
        @Test
        @DisplayName("앱 API controller 는 repository 를 직접 의존하지 않는다 - 소유권 검증이 service 에 있다")
        void apiControllersGoThroughServices() {
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .and().resideOutsideOfPackage("com.aisip.OnO.backend.admin..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .because("userId 소유권 검증은 service 에 있다. controller 가 repository 를 바로 부르면 검증 없이 데이터가 나간다")
                    .check(productionClasses);
        }
    }

    @Nested
    @DisplayName("웹 경계")
    class WebBoundary {

        @Test
        @DisplayName("entity 와 repository 는 Spring Web 과 Servlet API 를 모른다")
        void persistenceLayerIsWebAgnostic() {
            noClasses()
                    .that().resideInAnyPackage("..entity..", "..repository..")
                    .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
                    .because("영속 계층이 요청 객체를 알면 배치나 메시지 컨슈머처럼 요청이 없는 경로에서 쓸 수 없다")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("service 는 Servlet API 를 받지 않는다 - 필요한 값은 controller 가 꺼내서 넘긴다")
        void servicesDoNotTouchServletApi() {
            noClasses()
                    .that().resideInAPackage("..service..")
                    .should().dependOnClassesThat().resideInAPackage("jakarta.servlet..")
                    .because("Quartz 잡과 RabbitMQ 컨슈머도 service 를 부른다. 요청 객체에 기대면 그 경로에서 깨진다")
                    .check(productionClasses);
        }

        @Test
        @DisplayName("@RestController 와 @Controller 는 controller 패키지에 둔다")
        void controllersLiveInControllerPackages() {
            classes()
                    .that().areAnnotatedWith(RestController.class)
                    .or().areAnnotatedWith(Controller.class)
                    .should().resideInAPackage("..controller..")
                    .because("위 의존 방향 규칙이 패키지 이름으로 controller 를 가려낸다. 밖에 두면 규칙을 빠져나간다")
                    .check(productionClasses);
        }
    }

    @Nested
    @DisplayName("트랜잭션")
    class TransactionPlacement {

        @Test
        @DisplayName("클래스 단위 @Transactional 은 service 에만 붙인다")
        void classLevelTransactionalOnlyOnServices() {
            classes()
                    .that().areAnnotatedWith(Transactional.class)
                    .should().resideInAPackage("..service..")
                    .because("트랜잭션 경계는 service 가 정한다. controller 나 다른 곳에 걸면 요청 전체가 한 트랜잭션으로 묶인다")
                    .check(productionClasses);
        }

        /**
         * {@code jakarta.transaction.Transactional} 은 이름이 같아서 import 를 잘못 고르기 쉬운데,
         * readOnly 나 propagation 같은 Spring 속성이 없고 rollback 규칙도 다르다.
         */
        @Test
        @DisplayName("jakarta.transaction.Transactional 을 쓰지 않는다 - Spring 의 것만 쓴다")
        void usesSpringTransactionalOnly() {
            noClasses()
                    .should().dependOnClassesThat().haveFullyQualifiedName("jakarta.transaction.Transactional")
                    .because("readOnly, propagation 같은 속성이 없고 checked 예외 rollback 규칙도 Spring 과 다르다")
                    .check(productionClasses);
        }
    }

    @Nested
    @DisplayName("로깅")
    class Logging {

        @Test
        @DisplayName("java.util.logging 을 쓰지 않는다 - 로그는 slf4j 로 남겨 Loki 와 Sentry 로 모은다")
        void noJavaUtilLogging() {
            NO_CLASSES_SHOULD_USE_JAVA_UTIL_LOGGING.check(productionClasses);
        }

        @Test
        @DisplayName("printStackTrace 를 부르지 않는다 - 표준 에러로 나간 스택은 로그 수집에서 빠진다")
        void noPrintStackTrace() {
            noClasses()
                    .should().callMethod(Throwable.class, "printStackTrace")
                    .because("stderr 로 나간 스택은 traceId 같은 MDC 없이 섞여서 어떤 요청의 것인지 알 수 없다")
                    .check(productionClasses);
        }
    }
}
