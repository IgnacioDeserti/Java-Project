package com.ignaciodeserti.kanban.repository;

import com.ignaciodeserti.kanban.entity.Board;
import com.ignaciodeserti.kanban.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoardRepository extends JpaRepository<Board, Long> {
    List<Board> findByOwnerOrderByCreatedAtAsc(User owner);

    Optional<Board> findByIdAndOwner(Long id, User owner);
}
