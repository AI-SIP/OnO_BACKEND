package com.aisip.OnO.backend.common.exception;

import com.aisip.OnO.backend.common.response.CommonResponse;
import com.aisip.OnO.backend.util.fcm.exception.FcmErrorCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 모든 도메인 ErrorCase 가 지켜야 하는 공통 계약.
 *
 * <p>errorCode 는 앱이 분기하는 식별자이고 httpStatusCode 는 그대로 응답 상태가 되므로,
 * 새 ErrorCase 를 추가할 때 중복 코드나 잘못된 상태코드가 섞이면 클라이언트가 조용히 깨진다.
 * 도메인별로 흩어진 enum 을 클래스패스에서 모두 모아 한 번에 검증한다.
 */
@DisplayName("ErrorCase 계약")
class ErrorCaseContractTest {

    private static final String BASE_PACKAGE = "com.aisip.OnO.backend";

    /** 도메인별 ErrorCase enum 개수. 새 도메인을 추가하면 늘어난다. */
    private static final int MINIMUM_ERROR_CASE_TYPES = 14;

    @Nested
    @DisplayName("전체 집합")
    class WholeSet {

        @Test
        @DisplayName("errorCode 는 도메인 전체에서 유일하다")
        void errorCodesAreUnique() {
            Map<Integer, List<String>> duplicatedCodes = findErrorCases().stream()
                    .collect(Collectors.groupingBy(
                            ErrorCase::getErrorCode,
                            LinkedHashMap::new,
                            Collectors.toList()
                    ))
                    .entrySet().stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> entry.getValue().stream().map(ErrorCaseContractTest::format).toList(),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));

            assertThat(duplicatedCodes)
                    .as("errorCode 가 겹치면 앱이 서로 다른 에러를 같은 것으로 처리한다. 중복: %s", duplicatedCodes)
                    .isEmpty();
        }

        @Test
        @DisplayName("도메인 ErrorCase enum 이 빠짐없이 스캔된다")
        void scansEveryDomainErrorCaseEnum() {
            List<String> types = findErrorCases().stream()
                    .map(errorCase -> ((Enum<?>) errorCase).getDeclaringClass().getSimpleName())
                    .distinct()
                    .sorted()
                    .toList();

            assertThat(types)
                    .as("스캐너가 비어 있으면 이 클래스의 모든 검증이 무의미해진다. 발견된 타입: %s", types)
                    .hasSizeGreaterThanOrEqualTo(MINIMUM_ERROR_CASE_TYPES);
        }

        @Test
        @DisplayName("ErrorCase 구현체는 모두 enum 이다")
        void everyImplementationIsEnum() {
            List<String> nonEnumImplementations = scanErrorCaseClasses().stream()
                    .filter(type -> !type.isEnum())
                    .map(Class::getName)
                    .toList();

            assertThat(nonEnumImplementations)
                    .as("enum 이 아닌 구현체는 스캔 대상에서 빠져 계약 검증을 통과해버린다: %s", nonEnumImplementations)
                    .isEmpty();
        }

        @Test
        @DisplayName("5xx 로 선언된 ErrorCase 는 Discord 알림 대상이므로 목록이 고정돼 있다")
        void serverErrorCasesAreExplicitlyListed() {
            List<ErrorCase> serverErrorCases = findErrorCases().stream()
                    .filter(errorCase -> errorCase.getHttpStatusCode() >= 500)
                    .toList();

            assertThat(serverErrorCases)
                    .as("5xx ErrorCase 를 늘리려면 Discord 알림 폭증을 감수할지 먼저 판단해야 한다")
                    .containsExactly(FcmErrorCase.FCM_SEND_FAILED);
        }
    }

    @Nested
    @DisplayName("개별 ErrorCase")
    class EachErrorCase {

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.aisip.OnO.backend.common.exception.ErrorCaseContractTest#allErrorCases")
        @DisplayName("httpStatusCode 는 실재하는 4xx/5xx 상태코드다")
        void httpStatusCodeIsValidClientOrServerError(String name, ErrorCase errorCase) {
            assertThat(errorCase.getHttpStatusCode())
                    .as("%s httpStatusCode", name)
                    .isBetween(400, 599);
            assertThat(HttpStatus.resolve(errorCase.getHttpStatusCode()))
                    .as("%s 는 표준 HTTP 상태코드여야 한다", name)
                    .isNotNull();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.aisip.OnO.backend.common.exception.ErrorCaseContractTest#allErrorCases")
        @DisplayName("errorCode 는 도메인 대역을 쓰는 네 자리 이상 양수다")
        void errorCodeIsPositiveDomainCode(String name, ErrorCase errorCase) {
            assertThat(errorCase.getErrorCode())
                    .as("%s errorCode - HTTP 상태코드와 구분되도록 1000 이상을 쓴다", name)
                    .isGreaterThanOrEqualTo(1000);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.aisip.OnO.backend.common.exception.ErrorCaseContractTest#allErrorCases")
        @DisplayName("message 는 그대로 사용자에게 노출되므로 비어 있거나 공백으로 시작하지 않는다")
        void messageIsUserFacing(String name, ErrorCase errorCase) {
            String message = errorCase.getMessage();

            assertThat(message).as("%s message", name).isNotBlank();
            assertThat(message).as("%s message 앞뒤 공백", name).isEqualTo(message.strip());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.aisip.OnO.backend.common.exception.ErrorCaseContractTest#allErrorCases")
        @DisplayName("ApplicationException 은 ErrorCase 의 메시지를 그대로 물고 간다")
        void applicationExceptionCarriesErrorCase(String name, ErrorCase errorCase) {
            ApplicationException exception = new ApplicationException(errorCase);

            assertThat(exception.getErrorCase()).as("%s errorCase", name).isSameAs(errorCase);
            assertThat(exception.getMessage()).as("%s message", name).isEqualTo(errorCase.getMessage());
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("com.aisip.OnO.backend.common.exception.ErrorCaseContractTest#allErrorCases")
        @DisplayName("CommonResponse 로 변환하면 errorCode/message 가 그대로 실린다")
        void convertsIntoCommonResponse(String name, ErrorCase errorCase) {
            CommonResponse<Object> response = CommonResponse.error(errorCase);

            assertThat(response.getErrorCode()).as("%s errorCode", name).isEqualTo(errorCase.getErrorCode());
            assertThat(response.getMessage()).as("%s message", name).isEqualTo(errorCase.getMessage());
            assertThat(response.getData()).as("%s 는 에러 응답이므로 data 가 없다", name).isNull();
        }
    }

    static List<org.junit.jupiter.params.provider.Arguments> allErrorCases() {
        return findErrorCases().stream()
                .map(errorCase -> org.junit.jupiter.params.provider.Arguments.of(format(errorCase), errorCase))
                .toList();
    }

    private static List<ErrorCase> findErrorCases() {
        return scanErrorCaseClasses().stream()
                .filter(Class::isEnum)
                .map(Class::getEnumConstants)
                .map(constants -> Arrays.stream(constants).map(ErrorCase.class::cast).toList())
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(ErrorCase::getErrorCode))
                .toList();
    }

    private static List<Class<?>> scanErrorCaseClasses() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCase.class));

        return scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .<Class<?>>map(beanDefinition -> loadClass(beanDefinition.getBeanClassName()))
                .filter(type -> !type.isInterface())
                .toList();
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load ErrorCase class: " + className, e);
        }
    }

    private static String format(ErrorCase errorCase) {
        Enum<?> enumValue = (Enum<?>) errorCase;
        return enumValue.getDeclaringClass().getSimpleName() + "." + enumValue.name();
    }
}
