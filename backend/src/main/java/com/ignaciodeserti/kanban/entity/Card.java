package com.ignaciodeserti.kanban.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "cards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private Integer position = 0; // for ordering cards within a column

    @Enumerated(EnumType.STRING)
    private Priority priority = Priority.MEDIUM;

    /** A calendar day, not an instant — "due Friday" means the same in every timezone. */
    @Column(name = "due_date")
    private LocalDate dueDate;

    // Left lazy on purpose: rendering a board walks every card, and eager-loading this
    // would mean one extra query per card. @BatchSize makes Hibernate fetch the labels
    // for up to 100 cards in a single query instead, so a whole board costs one or two
    // extra round trips rather than N.
    @ElementCollection
    @CollectionTable(name = "card_labels", joinColumns = @JoinColumn(name = "card_id"))
    @Column(name = "label", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    @BatchSize(size = 100)
    private Set<Label> labels = new LinkedHashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "column_id", nullable = false)
    @JsonIgnore
    private BoardColumn column;

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH
    }

    public enum Label {
        GREEN,
        BLUE,
        PURPLE,
        ORANGE,
        RED,
        GRAY
    }
}
