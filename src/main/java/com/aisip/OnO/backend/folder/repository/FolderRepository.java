package com.aisip.OnO.backend.folder.repository;

import com.aisip.OnO.backend.folder.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FolderRepository extends JpaRepository<Folder, Long>, FolderRepositoryCustom {

    Optional<Folder> findByUserIdAndParentFolderIsNull(Long userId);

    List<Folder> findAllByUserId(Long userId);

    /**
     * 훈장 '정리의 신' 판정용. <b>루트 폴더는 빼고</b> 센다.
     *
     * <p>{@code FolderService.initializeDefaultFoldersIfAbsent} 가 가입 시점에 루트 폴더와 기본 하위
     * 폴더를 자동으로 만든다. 전부 세면 아무것도 안 한 사람이 2/10 에서 시작해 "폴더를 열 개나 만들어
     * 정리했어요" 라는 말과 안 맞는다.
     *
     * <p>기본 하위 폴더까지 빼지 않는 이유는 그쪽은 이름으로만 거를 수 있기 때문이다. 사용자가 이름을
     * 바꾸는 순간 판정이 달라진다. 루트는 {@code parentFolder IS NULL} 이라는 구조로 걸러져 이름과 무관하다.
     * 그래서 갓 가입한 사람은 1/10 에서 시작하고, 그 하나는 실제로 자기 폴더 트리에 있는 폴더다.
     *
     * <p>소프트 삭제된 폴더는 {@code @SQLRestriction} 이 걸러 준다.
     */
    long countByUserIdAndParentFolderIsNotNull(Long userId);

    @Modifying
    @Query("delete from Folder f where f.id in :folderIds")
    void deleteAllByIdIn(@Param("folderIds") Collection<Long> folderIds);

    /**
     * 폴더를 소프트 삭제한다. {@code @SQLDelete} 와 같은 결과를 내는 벌크 UPDATE 다.
     *
     * <p>{@code deleteAll(entities)} 로 지우면 삭제가 조용히 취소되는 경우가 있었다.
     * {@code Folder.subFolderList} 가 {@code CascadeType.ALL} 이라, 삭제 대상 폴더가
     * 영속성 컨텍스트에 남은 다른 폴더의 초기화된 하위 폴더 컬렉션에서 여전히 참조되면
     * 플러시 시점의 cascade persist 가 삭제 예약을 되돌린다(엔티티 부활). 그 결과
     * "폴더 두 개를 지웠는데 아무것도 지워지지 않는" 상태가 됐다.
     *
     * <p>벌크 UPDATE 는 영속성 컨텍스트의 cascade 를 타지 않으므로 항상 그대로 실행된다.
     * 앞선 변경(문제 소프트 삭제 등)을 먼저 반영하기 위해 flush 하고, 실행 후에는 남아 있는
     * 엔티티가 삭제 사실을 모르는 상태이므로 컨텍스트를 비운다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Folder f set f.deletedAt = CURRENT_TIMESTAMP where f.id in :folderIds")
    void softDeleteAllByIdIn(@Param("folderIds") Collection<Long> folderIds);

    @Query("""
            SELECT p.folder.id, COUNT(p.id)
            FROM Problem p
            WHERE p.folder.id IN :folderIds
            GROUP BY p.folder.id
            """)
    List<Object[]> countProblemsByFolderIds(@Param("folderIds") Collection<Long> folderIds);
}
