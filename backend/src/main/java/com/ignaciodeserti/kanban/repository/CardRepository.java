package com.ignaciodeserti.kanban.repository;

import com.ignaciodeserti.kanban.entity.BoardColumn;
import com.ignaciodeserti.kanban.entity.Card;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CardRepository extends JpaRepository<Card, Long> {
    List<Card> findByColumnOrderByPositionAsc(BoardColumn column);
    Long countByColumn(BoardColumn column);
}
