package com.aisip.OnO.backend.repository.Folder;

import com.aisip.OnO.backend.entity.Folder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;

import java.util.Optional;

import static com.aisip.OnO.backend.entity.QFolder.folder;

public class FolderRepositoryImpl implements FolderRepositoryCustom{

    private final JPAQueryFactory queryFactory;

    public FolderRepositoryImpl(EntityManager entityManager) {
        this.queryFactory = new JPAQueryFactory(entityManager);
    }

    @Override
    public Optional<Folder> findRootFolder(Long userId) {
        Folder rootFolder = queryFactory
                    .select(folder)
                    .from(folder)
                    .where(folder.user.id.eq(userId).and(folder.parentFolder.isNull()))
                    .fetchOne();

        return Optional.ofNullable(rootFolder);
    }
}
