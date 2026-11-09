package com.aisip.OnO.backend.auth.social;

/**
 * 플랫폼별 소셜 로그인 토큰 확인기.
 *
 * <p>토큰이 그 플랫폼이 우리 앱에 발급한 것인지 확인하고, 토큰이 가리키는 사용자 식별자를 돌려준다.
 * 호출한 쪽은 이 값을 요청의 identifier 와 비교한다.
 */
public interface SocialTokenVerifier {

    SocialPlatform platform();

    /** 확인에 필요한 client id 나 app id 가 설정돼 있는지. 비어 있으면 확인할 수 없다. */
    boolean isConfigured();

    /**
     * @return 토큰이 가리키는 사용자 식별자(Google·Apple 의 sub, Kakao 의 회원번호)
     * @throws SocialTokenVerificationException 토큰이 맞지 않거나 플랫폼 서버에 닿지 못했을 때
     */
    String verify(String token);
}
