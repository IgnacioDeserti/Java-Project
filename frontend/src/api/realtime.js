import { Client } from "@stomp/stompjs";
import { TOKEN_KEY } from "./client.js";

function wsBaseUrl() {
  const apiUrl = import.meta.env.VITE_API_URL || "http://localhost:8080/api";
  if (apiUrl.startsWith("http")) {
    // e.g. "http://localhost:8080/api" -> "ws://localhost:8080"
    return apiUrl.replace(/^http/, "ws").replace(/\/api\/?$/, "");
  }
  // Relative API base (Docker/nginx setup): derive from the page's own origin.
  const protocol = window.location.protocol === "https:" ? "wss" : "ws";
  return `${protocol}://${window.location.host}`;
}

/**
 * Subscribes to live updates for one board (see BoardBroadcaster on the backend, which
 * pings this topic after every column/card mutation). Calls onMessage with the parsed
 * event — e.g. { type: "BOARD_UPDATED" } or { type: "BOARD_DELETED" } — whenever one
 * arrives. Returns a cleanup function; call it when the board view unmounts or changes.
 */
export function subscribeToBoard(boardId, onMessage) {
  const client = new Client({
    brokerURL: `${wsBaseUrl()}/ws/websocket`,
    connectHeaders: {
      Authorization: `Bearer ${localStorage.getItem(TOKEN_KEY)}`,
    },
    reconnectDelay: 3000,
    onConnect: () => {
      client.subscribe(`/topic/boards/${boardId}`, (message) => {
        onMessage(JSON.parse(message.body));
      });
    },
  });

  client.activate();

  return () => client.deactivate();
}
