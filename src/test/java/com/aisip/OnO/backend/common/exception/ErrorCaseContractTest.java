package com.aisip.OnO.backend.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCaseContractTest {

    private static final String BASE_PACKAGE = "com.aisip.OnO.backend";

    @Test
    void errorCodesAreUnique() {
        List<ErrorCase> errorCases = findErrorCases();

        Map<Integer, List<ErrorCase>> casesByCode = errorCases.stream()
                .collect(Collectors.groupingBy(
                        ErrorCase::getErrorCode,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        Map<Integer, List<String>> duplicatedCodes = casesByCode.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(this::formatErrorCase)
                                .toList(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        assertThat(duplicatedCodes)
                .as("ErrorCase errorCode must be unique. Duplicates: %s", duplicatedCodes)
                .isEmpty();
    }

    @Test
    void errorCasesHaveValidResponseFields() {
        List<ErrorCase> errorCases = findErrorCases();

        assertThat(errorCases)
                .allSatisfy(errorCase -> {
                    assertThat(errorCase.getHttpStatusCode())
                            .as("%s httpStatusCode", formatErrorCase(errorCase))
                            .isBetween(400, 599);
                    assertThat(errorCase.getErrorCode())
                            .as("%s errorCode", formatErrorCase(errorCase))
                            .isPositive();
                    assertThat(errorCase.getMessage())
                            .as("%s message", formatErrorCase(errorCase))
                            .isNotBlank();
                });
    }

    private List<ErrorCase> findErrorCases() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(ErrorCase.class));

        return scanner.findCandidateComponents(BASE_PACKAGE).stream()
                .map(beanDefinition -> loadClass(beanDefinition.getBeanClassName()))
                .filter(Class::isEnum)
                .map(Class::getEnumConstants)
                .map(constants -> Arrays.stream(constants)
                        .map(ErrorCase.class::cast)
                        .toList())
                .flatMap(Collection::stream)
                .sorted(Comparator.comparing(ErrorCase::getErrorCode))
                .toList();
    }

    private Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Failed to load ErrorCase class: " + className, e);
        }
    }

    private String formatErrorCase(ErrorCase errorCase) {
        Enum<?> enumValue = (Enum<?>) errorCase;
        return enumValue.getDeclaringClass().getSimpleName() + "." + enumValue.name();
    }
}
