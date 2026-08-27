/**
 * Placeholder shapes shown while data is in flight. They mirror the real layout closely
 * enough that nothing jumps when the content arrives — the point is to avoid a blank
 * screen and a layout shift, not to be decorative.
 */

export function BoardListSkeleton({ count = 4 }) {
  return (
    <ul className="board-grid" aria-hidden="true">
      {Array.from({ length: count }, (_, i) => (
        <li key={i}>
          <div className="skeleton skeleton-board-card" />
        </li>
      ))}
    </ul>
  );
}

export function BoardSkeleton({ columns = 3 }) {
  return (
    <div className="columns-row" aria-hidden="true">
      {Array.from({ length: columns }, (_, columnIndex) => (
        <div className="skeleton-column" key={columnIndex}>
          <div className="skeleton skeleton-line" style={{ width: "45%" }} />
          {Array.from({ length: 3 - (columnIndex % 2) }, (_, cardIndex) => (
            <div className="skeleton skeleton-card" key={cardIndex} />
          ))}
        </div>
      ))}
    </div>
  );
}
