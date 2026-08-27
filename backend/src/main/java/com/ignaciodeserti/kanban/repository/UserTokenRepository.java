package com.ignaciodeserti.kanban.repository;

import com.ignaciodeserti.kanban.entity.User;
import com.ignaciodeserti.kanban.entity.UserToken;
import com.ignaciodeserti.kanban.entity.UserToken.Type;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {
    Optional<UserToken> findByTokenHashAndType(String tokenHash, Type type);

    @Modifying
    @Transactional
    void deleteByUserAndType(User user, Type type);
}
