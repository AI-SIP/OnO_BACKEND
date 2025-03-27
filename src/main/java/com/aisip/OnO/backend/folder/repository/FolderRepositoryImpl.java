package com.aisip.OnO.backend.folder.repository;

import com.aisip.OnO.backend.folder.entity.Folder;
import com.aisip.OnO.backend.folder.entity.QFolder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.util.List;
import java.util.Optional;

import static com.aisip.OnO.backend.folder.entity.QFolder.folder;

public class FolderRepositoryImpl implements FolderRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public FolderRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Optional<Folder> findRootFolder(Long userId) {
        Folder rootFolder = queryFactory
                    .select(folder)
                    .from(folder)
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
                .fetch();
    }
}
