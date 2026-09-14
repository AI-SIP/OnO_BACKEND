package com.aisip.OnO.backend.common.web;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 앱이 보내온 버전. {@code major.minor.patch} 세 자리만 담는다.
 *
 * <p>프론트는 {@code pubspec.yaml} 의 {@code version} 을 그대로 {@code 4.0.0+70} 꼴로 보낸다.
 * <b>{@code +} 뒤의 빌드 번호는 버린다.</b> 빌드 번호는 스토어에 올릴 때마다 올라가는 값이라
 * 같은 스토어 버전 안에서도 제각각이고, "이 버전부터 미션을 받을 수 있다" 를 가르는 데 쓸 수 없다.
 * 기준을 빌드 번호까지 내리면 설정값을 스토어 제출마다 따라 고쳐야 한다.
 *
 * <p>읽지 못한 값은 예외 대신 빈 값으로 돌려준다. 이 값을 쓰는 쪽의 규칙이 <b>"모르면 구버전"</b> 이라
 * 파싱 실패는 사고가 아니라 정상 분기다. 헤더는 앱이 채우는 값이라 무엇이 들어올지 서버가 정할 수 없고,
 * 여기서 예외를 던지면 XP 적립 경로 한가운데서 요청이 500 으로 죽는다.
 */
public record AppVersion(int major, int minor, int patch) implements Comparable<AppVersion> {

    /** 세 자리 숫자만 받는다. {@code 4}, {@code 4.0}, {@code 4.0.x} 는 버전으로 보지 않는다. */
    private static final Pattern SEMVER = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    public static Optional<AppVersion> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }

        String semver = raw.trim();

        // 빌드 번호(4.0.0+70)와 프리릴리즈 꼬리(4.1.0-beta.1)를 떼어 낸다.
        // 프리릴리즈는 지금 프론트가 쓰지 않지만, 붙어 오면 통째로 파싱 실패해
        // 내부 배포 빌드가 전부 구버전으로 떨어지는 것보다 앞자리만 읽는 편이 낫다.
        int buildAt = semver.indexOf('+');
        if (buildAt >= 0) {
            semver = semver.substring(0, buildAt);
        }
        int preReleaseAt = semver.indexOf('-');
        if (preReleaseAt >= 0) {
            semver = semver.substring(0, preReleaseAt);
        }

        var matcher = SEMVER.matcher(semver);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new AppVersion(
                    Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)),
                    Integer.parseInt(matcher.group(3))
            ));
        } catch (NumberFormatException e) {
            // 자리 수가 int 를 넘는 값. 정상 앱에서 나올 수 없지만 헤더는 위조할 수 있다.
            return Optional.empty();
        }
    }

    @Override
    public int compareTo(AppVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    /** {@code other} 와 같거나 그보다 높은 버전인가. */
    public boolean isAtLeast(AppVersion other) {
        return compareTo(other) >= 0;
    }
}
