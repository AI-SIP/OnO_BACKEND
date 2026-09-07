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
