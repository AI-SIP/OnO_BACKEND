package com.aisip.OnO.backend.folder.repository;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.entity.QFolder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

import static com.aisip.OnO.backend.folder.entity.QFolder.folder;
import static com.aisip.OnO.backend.problem.entity.QProblem.problem;

public class FolderRepositoryImpl implements FolderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public FolderRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Optional<Long> findRootFolderId(Long userId) {
        Long folderId = queryFactory
                .select(folder.id)
                .from(folder)
                .where(folder.userId.eq(userId).and(folder.parentFolder.isNull()))
                .fetchOne();

        return Optional.ofNullable(folderId);
    }

    @Override
    public Optional<Folder> findRootFolder(Long userId) {
        Folder rootFolder = queryFactory
                    .selectFrom(folder)
                    .leftJoin(folder.subFolderList, new QFolder("subFolder")).fetchJoin()
                    .where(folder.userId.eq(userId).and(folder.parentFolder.isNull()))
                    .fetchOne();

        return Optional.ofNullable(rootFolder);
    }

    @Override
    public Optional<Folder> findFolderWithDetailsByFolderId(Long folderId) {
        Folder folder = queryFactory.selectFrom(QFolder.folder)
                .leftJoin(QFolder.folder.subFolderList, new QFolder("subFolder")).fetchJoin()
                .leftJoin(QFolder.folder.parentFolder, new QFolder("parentFolder")).fetchJoin()
                .where(QFolder.folder.id.eq(folderId))
                .fetchOne();

        return Optional.ofNullable(folder);
    }

    @Override
    public List<Folder> findAllFoldersWithDetailsByUserId(Long userId) {
        return queryFactory.selectDistinct(folder)
                .from(folder)
                .leftJoin(folder.subFolderList, new QFolder("subFolder")).fetchJoin()
                .leftJoin(folder.parentFolder, new QFolder("parentFolder")).fetchJoin()
                .where(folder.userId.eq(userId))
                .orderBy(folder.id.asc())
                .fetch();
    }

    @Override
    public List<Long> findProblemIdsByFolder(Long folderId) {
        return queryFactory
                .select(problem.id)
                .from(problem)
                .where(problem.folder.id.eq(folderId))
                .orderBy(problem.id.asc())
                .fetch();
    }

    @Override
    public List<Folder> findSubFoldersWithCursor(Long folderId, Long cursor, int size) {
        var query = queryFactory
                .selectFrom(folder)
                .where(folder.parentFolder.id.eq(folderId));

        // 커서가 있으면 해당 ID 이후부터 조회
        if (cursor != null) {
            query.where(folder.id.gt(cursor));
        }

        return query
                .orderBy(folder.id.asc())
                .limit(size + 1)  // hasNext 판단을 위해 +1개 조회
                .fetch();
    }

    @Override
    public List<Folder> findAllUserFolderThumbnailsWithCursor(Long userId, Long cursor, int size) {
        var query = queryFactory
                .selectFrom(folder)
                .where(folder.userId.eq(userId));

        // 커서가 있으면 해당 ID 이후부터 조회
        if (cursor != null) {
            query.where(folder.id.gt(cursor));
        }

        return query
                .orderBy(folder.id.asc())
                .limit(size + 1)  // hasNext 판단을 위해 +1개 조회
                .fetch();
    }
}
