package com.aisip.OnO.backend.user.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserRepositoryCustom {
    Page<UserAdminRow> findAdminUsers(Pageable pageable, String sortBy, String direction);
}
