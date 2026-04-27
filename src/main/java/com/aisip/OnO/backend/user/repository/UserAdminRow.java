package com.aisip.OnO.backend.user.repository;

import com.aisip.OnO.backend.user.entity.User;

public record UserAdminRow(
        User user,
        Long problemCount
) {
}
