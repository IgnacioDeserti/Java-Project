package com.ignaciodeserti.kanban.repository;

import com.ignaciodeserti.kanban.entity.Board;
import com.ignaciodeserti.kanban.entity.BoardColumn;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoardColumnRepository extends JpaRepository<BoardColumn, Long> {
    List<BoardColumn> findByBoardOrderByPositionAsc(Board board);
}
