package com.aisip.OnO.backend.admin.repository;

/**
 * 관리자 집계에서 실사용자만 세기 위한 SQL 조각.
 *
 * <p>게스트 계정은 출시 초기에 섞여 들어왔고 스모크 테스트도 게스트로 돌기 때문에, 그대로 세면
 * 가입, 활성, 학습 지표가 실제 사용량보다 부풀려진다. 관리자 계정도 같은 이유로 뺀다.
 * 개별 목록과 상세 화면에는 쓰지 않고 숫자를 세는 쿼리에만 붙인다.
 *
 * <p>탈퇴 여부는 보지 않는다. 탈퇴한 회원의 과거 기록까지 사라지면 날짜별 추이가 바뀐다.
 */
public final class AdminSqlFilters {

    private static final String TEST_PLATFORMS = "('GUEST', 'ADMIN')";

    private AdminSqlFilters() {
    }

    /** 유저 id 컬럼이 게스트나 관리자 계정을 가리키는 행을 뺀다. 앞에 {@code AND} 가 붙어 있다. */
    public static String excludeTestUsers(String userIdColumn) {
        return " AND " + userIdColumn + " NOT IN (SELECT tu.id FROM `user` tu"
                + " WHERE UPPER(COALESCE(tu.platform, '')) IN " + TEST_PLATFORMS + ") ";
    }

    /** {@code user} 테이블을 직접 셀 때 쓰는 조건. 앞에 {@code AND} 가 붙어 있다. */
    public static String countedUser(String alias) {
        String column = alias == null || alias.isBlank() ? "platform" : alias + ".platform";
        return " AND UPPER(COALESCE(" + column + ", '')) NOT IN " + TEST_PLATFORMS + " ";
    }
}
