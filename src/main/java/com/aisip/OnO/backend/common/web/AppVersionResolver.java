package com.aisip.OnO.backend.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 지금 처리 중인 HTTP 요청의 {@code X-App-Version} 헤더를 읽는다.
 *
 * <p>프론트가 모든 요청에 {@code 4.0.0+70} 꼴로 붙인다(AI-SIP/OnO_FRONT#214).
 * <b>버전을 못 읽으면 프론트는 헤더를 아예 보내지 않는다.</b> 빈 문자열이나 {@code unknown} 은 오지 않는다.
 *
 * <p><b>왜 인자로 넘기지 않고 요청 스코프에서 꺼내는가.</b> 이 값이 필요한 곳은
 * {@code MissionLogService.addPointToUser} 한 군데인데, 거기까지 가는 길이
 * 컨트롤러 → {@code ProblemService}/{@code PracticeNoteService}/{@code ProblemSolveService} → 적립 으로
 * 서너 단계다. 버전을 인자로 물려 내리면 그 경로의 메서드 시그니처가 전부 바뀌고, 적립과 아무 상관 없는
 * 중간 호출부까지 버전을 들고 다녀야 한다. 읽는 곳이 하나뿐인 값 때문에 호출 경로 전체를 오염시킬 이유가 없다.
 *
 * <p><b>왜 필터나 인터셉터 + 요청 스코프 빈을 새로 두지 않는가.</b> {@code FrameworkServlet} 이 요청마다
 * {@code RequestContextHolder} 를 채우고 끝나면 지운다. 헤더를 읽기만 하면 되는 일에 등록 순서와
 * 생명주기를 가진 컴포넌트를 하나 더 얹을 이유가 없다. MockMvc 도 같은 경로를 타기 때문에
 * 통합 테스트에서 실제 헤더가 서비스까지 닿는지 그대로 확인된다.
 *
 * <p><b>HTTP 요청이 없는 곳에서 불려도 터지지 않는다.</b> 요청이 없으면 예외를 던지는
 * {@code currentRequestAttributes()} 대신 {@code getRequestAttributes()} 를 쓴다. 지금 적립을 부르는
 * 경로는 전부 HTTP 요청 안이지만(Quartz 잡 5종과 RabbitMQ 소비자 4종 어디에서도 적립을 부르지 않는다),
 * 나중에 배치나 비동기 경로가 하나 늘어도 조용히 "모르는 버전" 으로 떨어져야 한다.
 * {@code @Async} 스레드처럼 요청 컨텍스트가 물려지지 않는 자리도 마찬가지다.
 */
@Component
public class AppVersionResolver {

    public static final String APP_VERSION_HEADER = "X-App-Version";

    public Optional<AppVersion> resolve() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servletAttributes)) {
            return Optional.empty();
        }

        HttpServletRequest request = servletAttributes.getRequest();
        return AppVersion.parse(request.getHeader(APP_VERSION_HEADER));
    }
}
