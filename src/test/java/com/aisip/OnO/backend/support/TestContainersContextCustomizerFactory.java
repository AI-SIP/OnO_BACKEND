package com.aisip.OnO.backend.support;

import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.util.List;

/**
 * 모든 스프링 테스트 컨텍스트에 Testcontainers 접속 정보를 자동으로 주입한다.
 *
 * <p>이전에는 {@link IntegrationTestSupport} 의 {@code @DynamicPropertySource} 에만
 * 의존했는데, 그 베이스를 상속하지 않은 기존 테스트들은 datasource 설정을 전혀 받지 못해
 * "Failed to determine a suitable driver class" 로 컨텍스트가 통째로 뜨지 않았다.
 * 실패 210건 중 138건이 이 한 가지 원인의 연쇄였다.
 *
 * <p>{@code META-INF/spring.factories} 에 등록되어 테스트 종류와 상속 구조에 관계없이 적용된다.
 * 베이스 클래스를 상속하는 것을 잊어도 접속 정보만큼은 항상 올바르게 들어간다.
 *
 * <p>모든 테스트에 동일한 커스터마이저 인스턴스가 적용되어야 컨텍스트 캐시 키가 갈라지지 않으므로
 * {@link ContextCustomizer} 구현은 equals/hashCode 를 값 기반으로 고정한다.
 */
public class TestContainersContextCustomizerFactory implements ContextCustomizerFactory {

    private static final ContextCustomizer CUSTOMIZER = new TestContainersContextCustomizer();

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass,
                                                     List<ContextConfigurationAttributes> configAttributes) {
        return CUSTOMIZER;
    }

    private static final class TestContainersContextCustomizer implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context,
                                     MergedContextConfiguration mergedConfig) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context,
                    TestContainers.asInlinedProperties()
            );
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof TestContainersContextCustomizer;
        }

        @Override
        public int hashCode() {
            return TestContainersContextCustomizer.class.hashCode();
        }
    }
}
