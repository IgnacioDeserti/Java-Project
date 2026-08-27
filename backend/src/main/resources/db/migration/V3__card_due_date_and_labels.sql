-- Richer cards: an optional due date, and a set of colour labels per card.
--
-- Labels are modelled as a plain element collection (a set of enum values owned by the
-- card) rather than as first-class Label rows shared across a board. That keeps the
-- surface small — no CRUD for labels themselves, no join entity — at the cost of not
-- being able to rename a label board-wide. Worth revisiting if labels ever need names.

ALTER TABLE cards ADD COLUMN due_date DATE;

CREATE TABLE card_labels (
    card_id BIGINT      NOT NULL,
    label   VARCHAR(20) NOT NULL,
    CONSTRAINT pk_card_labels PRIMARY KEY (card_id, label),
    CONSTRAINT fk_card_labels_card FOREIGN KEY (card_id) REFERENCES cards (id) ON DELETE CASCADE
);
CREATE INDEX idx_card_labels_card_id ON card_labels (card_id);
