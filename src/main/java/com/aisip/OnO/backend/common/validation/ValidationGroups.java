package com.aisip.OnO.backend.common.validation;

/**
 * Bean Validation 그룹.
 *
 * <p>등록과 수정이 같은 DTO 를 쓰는 경우가 있다(ProblemRegisterDto, FolderRegisterDto).
 * 등록할 때는 id 가 비어 있는 게 정상이고, 수정할 때만 id 가 꼭 있어야 한다.
 * 수정 엔드포인트에서만 {@code @Validated(ValidationGroups.Update.class)} 로 검사한다.
 */
public final class ValidationGroups {

    private ValidationGroups() {
    }

    public interface Update {
    }
}
