package com.aisip.OnO.backend.folder.repository;

import com.aisip.OnO.backend.folder.entity.Folder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * 문제를 폴더에 넣는 경로(등록, 이동)에서 폴더를 공유 잠금(FOR SHARE)으로 읽는다. (#233)
     *
     * <p>잠금 없이 읽으면 폴더 삭제가 아직 커밋되지 않은 폴더를 살아 있는 것으로 보고 문제를 넣어,
     * 삭제된 폴더를 가리키는 고아 문제가 남았다. 공유 잠금은 삭제의 배타 잠금과만 충돌하므로
     * 같은 폴더로 들어오는 등록끼리는 서로 기다리지 않는다. 삭제가 먼저 잡았으면 커밋까지 기다린 뒤
     * 최신 행을 다시 읽고, 그때는 {@code deleted_at} 이 찍혀 있어 {@code @SQLRestriction} 에 걸러진다.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select f from Folder f where f.id = :folderId")
    Optional<Folder> findByIdForShare(@Param("folderId") Long folderId);

    /** {@link #findByIdForShare} 의 일괄 등록용. 잠금은 PK 순서로 잡힌다. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select f from Folder f where f.id in :folderIds")
    List<Folder> findAllByIdInForShare(@Param("folderIds") Collection<Long> folderIds);

    /**
     * 폴더 삭제 전에 사용자의 폴더 전체를 배타 잠금(FOR UPDATE)으로 잡는다. (#233)
     *
     * <p>삭제할 폴더만이 아니라 사용자 폴더 전체를 잡는 이유는 하위 폴더 때문이다. 하위 폴더 목록은
     * 잠그기 전에는 알 수 없고, 하위 폴더로 들어오는 등록도 막아야 한다.
     *
     * <p><b>반드시 트랜잭션의 첫 조회여야 한다.</b> REPEATABLE READ 는 첫 일반 조회 시점에 스냅숏을 만든다.
     * 잠금보다 먼저 일반 조회를 하면, 잠금을 기다리는 동안 커밋된 등록이 그 스냅숏에 보이지 않아
     * 문제 삭제에서 빠진다. 잠금 조회(FOR UPDATE)는 스냅숏을 만들지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select f from Folder f where f.userId = :userId")
    List<Folder> lockAllByUserId(@Param("userId") Long userId);

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
