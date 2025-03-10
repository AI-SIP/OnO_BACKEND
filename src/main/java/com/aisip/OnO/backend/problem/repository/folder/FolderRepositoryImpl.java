package com.aisip.OnO.backend.problem.repository.folder;

import com.aisip.OnO.backend.problem.entity.Folder;
import com.aisip.OnO.backend.problem.entity.QFolder;
import com.aisip.OnO.backend.problem.entity.QProblem;
import com.aisip.OnO.backend.problem.entity.QProblemImageData;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.util.Optional;

import static com.aisip.OnO.backend.problem.entity.QFolder.folder;

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
    public Optional<Folder> findWithAllData(Long folderId) {
        Folder folder = queryFactory.selectFrom(QFolder.folder)
                .leftJoin(QFolder.folder.problemList, QProblem.problem).fetchJoin()
                .leftJoin(QProblem.problem.problemImageDataList, QProblemImageData.problemImageData).fetchJoin()
                .leftJoin(QFolder.folder.subFolderList, new QFolder("subFolder")).fetchJoin()
                .leftJoin(QFolder.folder.parentFolder, new QFolder("parentFolder")).fetchJoin()
                .where(QFolder.folder.id.eq(folderId))
                .fetchOne();

        return Optional.ofNullable(folder);
    }
}
