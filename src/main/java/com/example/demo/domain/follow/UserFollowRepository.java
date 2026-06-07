package com.example.demo.domain.follow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    boolean existsByFollowerIdAndFollowingId(Long followerId, Long followingId);

    Optional<UserFollow> findByFollowerIdAndFollowingId(Long followerId, Long followingId);

    @Query("SELECT f FROM UserFollow f JOIN FETCH f.following WHERE f.follower.id = :userId")
    List<UserFollow> findFollowingByUserId(@Param("userId") Long userId);

    @Query("SELECT f FROM UserFollow f JOIN FETCH f.follower WHERE f.following.id = :userId")
    List<UserFollow> findFollowersByUserId(@Param("userId") Long userId);

    long countByFollowerId(Long followerId);

    long countByFollowingId(Long followingId);
}