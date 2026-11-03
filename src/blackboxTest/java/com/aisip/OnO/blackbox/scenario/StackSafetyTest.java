package com.aisip.OnO.blackbox.scenario;

import com.aisip.OnO.blackbox.support.BlackboxStack;
import com.aisip.OnO.blackbox.support.BlackboxTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 블랙박스 스택의 안전장치를 확인한다. 이 테스트가 깨지면 다른 시나리오가 실제 외부 서비스(FCM, OpenAI, S3)에
 * 닿을 수 있다는 뜻이라 나머지 결과도 믿을 수 없다.
 */
class StackSafetyTest extends BlackboxTest {

    @ParameterizedTest
    @CsvSource({
            "fcm.googleapis.com, 443",
            "api.openai.com, 443",
            "s3.ap-northeast-2.amazonaws.com, 443",
            "discord.com, 443",
            "1.1.1.1, 443"
    })
    void 앱_컨테이너는_외부로_나갈_수_없다(String host, int port) {
        assertThat(BlackboxStack.get().appCanConnect(host, port))
                .as("앱 컨테이너가 %s:%d 에 연결됐습니다. internal 네트워크 설정을 확인하세요", host, port)
                .isFalse();
    }

    @Test
    void 앱_컨테이너는_스택_안의_서버에는_닿는다() {
        // 위 테스트가 연결 확인 방법 자체가 고장 나서 통과하는 것이 아님을 보인다.
        assertThat(BlackboxStack.get().appCanConnect("wiremock", 8080)).isTrue();
        assertThat(BlackboxStack.get().appCanConnect("s3mock", 9090)).isTrue();
    }
}
