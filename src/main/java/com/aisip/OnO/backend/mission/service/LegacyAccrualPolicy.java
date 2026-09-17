package com.aisip.OnO.backend.mission.service;

import com.aisip.OnO.backend.common.web.AppVersion;
import com.aisip.OnO.backend.common.web.AppVersionResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 지금 요청에서 자동 적립이 도는가를 정하는 한 곳.
 *
 * <p>XP 가 들어오는 길은 둘이다. 행동만으로 들어가는 자동 적립({@code MissionLogService})과,
 * 미션 진행도를 채운 뒤 앱에서 받는 미션 보상({@code MissionService.claim})이다.
 * <b>한 행동에는 둘 중 정확히 하나만 열려야 한다.</b> 자동 적립이 도는 요청에서 진행도까지 완료되면
 * 그 사용자가 새 앱으로 미션 화면을 열었을 때 같은 행동으로 XP 를 한 번 더 받는다.
 *
 * <p>그래서 자동 적립({@code MissionLogService.addPointToUser})과 진행도 증가
 * ({@link MissionProgressUpdater#increase})가 <b>이 판정 하나를 같이 본다.</b> 두 곳이 판정을 따로 들고 있으면
 * 한쪽만 고쳐졌을 때 다시 이중 지급이나 무지급이 생긴다.
 *
 * <p><b>HTTP 요청이 없는 자리에서 불리면 자동 적립이 도는 쪽이다.</b> 헤더가 없는 것과 같게 본다.
 * 지금 두 곳을 부르는 경로는 전부 컨트롤러에서 시작하는 HTTP 요청 안이다(Quartz 잡, RabbitMQ 소비자,
 * {@code @TransactionalEventListener}, {@code @Async} 어디에서도 부르지 않는다). 나중에 그런 경로가 생겨도
 * 두 곳이 같은 답을 받으므로 둘 중 하나만 도는 것은 유지된다.
 */
@Component
@RequiredArgsConstructor
public class LegacyAccrualPolicy {

    private final AppVersionResolver appVersionResolver;

    /**
     * 행동만으로 경험치를 주던 예전 적립을 계속 쓸지.
     *
     * <p><b>비상 스위치다.</b> 꺼지면 앱 버전을 보지 않고 전부 끈다. 자동 적립 자체에 문제가 생겼을 때
     * 배포 없이 통째로 멈출 자리가 하나 있어야 한다. 꺼지면 진행도는 앱 버전과 무관하게 오른다.
     *
     * <p>기본값이 켜짐이라 설정을 건드리지 않으면 버전 판정만 돈다.
     */
    @Value("${ono.mission.legacy-accrual.enabled:true}")
    private boolean legacyAccrualEnabled;

    /**
     * 미션을 받을 수 있는 첫 앱 버전. 이 버전 이상에서 온 요청은 자동 적립을 끄고 진행도만 올린다.
     *
     * <p>기본값이 {@code 4.0.0} 인 근거는 <b>헤더 자체가 이 버전 라인에서 처음 붙는다</b> 는 것이다
     * (AI-SIP/OnO_FRONT#214, 프론트 {@code pubspec.yaml} 이 {@code 4.0.0+70}). 헤더를 안 보내는 앱은
     * 기준값이 무엇이든 구버전으로 떨어지므로, 이 값은 <b>헤더를 보내는 앱 중 어디까지를 새 앱으로 볼지</b>만
     * 가른다. 스토어에 나간 4.0.0 빌드에는 미션 화면도 헤더도 없고, 프론트에서 헤더를 붙인 커밋은
     * 미션 조회·받기 통신 계층이 들어간 뒤에 들어갔다. 그래서 헤더를 보내는 빌드는 전부 미션을 받을 수 있고,
     * 기준을 더 높이면 그 빌드에서 이중 지급이 계속된다.
     *
     * <p>설정값으로 둔 이유는 미션 화면이 빠지거나 받기가 고장 난 버전이 뒤늦게 드러났을 때
     * 배포 없이 기준을 올려 되돌리기 위해서다.
     */
    @Value("${ono.mission.mission-capable-version:4.0.0}")
    private String missionCapableVersion;

    /**
     * 이번 요청에서 자동 적립이 도는가. {@code true} 면 자동 적립만, {@code false} 면 미션 진행도만 연다.
     */
    public boolean accruesForCurrentRequest() {
        // 비상 스위치가 먼저다. 꺼져 있으면 버전을 아예 보지 않는다.
        if (!legacyAccrualEnabled) {
            return false;
        }
        return !missionCapableRequest();
    }

    /**
     * 이번 요청이 미션을 받을 수 있는 앱에서 왔는가.
     *
     * <p><b>모르면 아니라고 답한다.</b> 헤더가 없거나, 읽을 수 없는 값이거나, 애초에 HTTP 요청이 아닌
     * 자리에서 불렸으면 전부 구버전으로 본다. 구버전으로 잘못 보면 자동 적립으로 받고 진행도가 안 오를 뿐이지만,
     * 반대로 틀리면 구버전 사용자는 받을 방법이 없어 XP 가 통째로 멈춘다. 덜 주는 쪽이 더 위험하다.
     *
     * <p>기준값이 읽히지 않을 때도 같은 이유로 아무도 새 앱으로 보지 않는다. 설정 오타 하나가
     * 전체 사용자의 XP 유입을 끊는 것보다 새 앱 사용자가 자동 적립으로 받는 편이 낫다.
     */
    private boolean missionCapableRequest() {
        return AppVersion.parse(missionCapableVersion)
                .flatMap(threshold -> appVersionResolver.resolve().map(requested -> requested.isAtLeast(threshold)))
                .orElse(false);
    }
}
