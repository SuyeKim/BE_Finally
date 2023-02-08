package com.sparta.be_finally.user.repository;

import com.sparta.be_finally.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUserId(String userId);

    boolean existsByUserId(String userId);

    Optional<User> findByKakaoId(Long kakaoId);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByUserIdAndPhoneNumber(String userId, String phoneNumber);

    boolean existsByUserIdAndPhoneNumber(String userId, String phoneNumber);

    Optional<User> findByUserIdAndPassword(String userId, String password);

    boolean existsByPhoneNumber(String phoneNumber);

    void delete(User user);

    @Transactional
    @Modifying
    @Query (
            nativeQuery = true,
            value = "UPDATE users " +
                    "SET openvidu_token = :token " +
                    "WHERE id = :id"
    )
    void update(@Param("id") Long id, @Param("token") String token);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query (value = "UPDATE users u SET u.password = :password WHERE u.user_id = :userId", nativeQuery = true)
    void pwUpdate(@Param("password") String password, @Param("userId") String userId);

    //닉네임 수정
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query (value = "UPDATE users u SET u.nickname = :nickname WHERE u.user_id = :userId", nativeQuery = true)
    void nickNameUpdate(@Param("nickname") String nickname, @Param("userId") String userId);

    // 로그인시 DB에 Access Token 저장
    @Transactional
    @Modifying
    @Query (
            nativeQuery = true,
            value = "UPDATE users " +
                    "SET access_token = :access_token " +
                    "WHERE id = :id"
    )
    void updateAccessToken(@Param("id") Long Id, @Param("access_token") String access_token);
}
