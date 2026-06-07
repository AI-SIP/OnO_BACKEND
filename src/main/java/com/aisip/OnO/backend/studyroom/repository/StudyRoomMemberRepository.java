package com.aisip.OnO.backend.studyroom.repository;

import com.aisip.OnO.backend.studyroom.entity.StudyRoomMember;
import com.aisip.OnO.backend.studyroom.entity.StudyRoomMemberRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface StudyRoomMemberRepository extends JpaRepository<StudyRoomMember, Long> {

    Optional<StudyRoomMember> findByRoomIdAndUserId(Long roomId, Long userId);

    boolean existsByRoomIdAndUserId(Long roomId, Long userId);

    long countByRoomId(Long roomId);

    long countByUserId(Long userId);

    long countByRoomIdAndRole(Long roomId, StudyRoomMemberRole role);

    List<StudyRoomMember> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    @Query("select m from StudyRoomMember m join fetch m.user where m.room.id = :roomId order by m.createdAt asc")
    List<StudyRoomMember> findAllWithUserByRoomId(@Param("roomId") Long roomId);

    @Query("select m from StudyRoomMember m join fetch m.room join fetch m.user where m.room.id in :roomIds order by m.room.id asc, m.createdAt asc")
    List<StudyRoomMember> findAllWithRoomAndUserByRoomIds(@Param("roomIds") Collection<Long> roomIds);

    @Query("select m from StudyRoomMember m join fetch m.room where m.user.id = :userId order by m.createdAt desc")
    List<StudyRoomMember> findAllWithRoomByUserId(@Param("userId") Long userId);

    @Query("""
            select m.room.id, count(m.id)
            from StudyRoomMember m
            where m.room.id in :roomIds
            group by m.room.id
            """)
    List<Object[]> countMembersByRoomIds(@Param("roomIds") Collection<Long> roomIds);

    @Modifying
    @Query("delete from StudyRoomMember m where m.room.id = :roomId and m.user.id = :userId")
    void deleteByRoomIdAndUserId(@Param("roomId") Long roomId, @Param("userId") Long userId);
}
