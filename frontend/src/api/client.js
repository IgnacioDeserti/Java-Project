import axios from "axios";

export const TOKEN_KEY = "kanban_token";
export const REFRESH_TOKEN_KEY = "kanban_refresh_token";

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || "http://localhost:8080/api",
});

function storeSession(session) {
  localStorage.setItem(TOKEN_KEY, session.token);
  localStorage.setItem(REFRESH_TOKEN_KEY, session.refreshToken);
}

function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
}

/** The backend's own origin, derived from VITE_API_URL, for links that aren't under /api
 *  (Google's OAuth redirect flow lands on plain backend paths like /oauth2/...). */
function backendBaseUrl() {
  const apiUrl = import.meta.env.VITE_API_URL || "http://localhost:8080/api";
  if (apiUrl.startsWith("http")) {
    return apiUrl.replace(/\/api\/?$/, "");
  }
  return ""; // relative API base (Docker/nginx): "" + "/oauth2/..." resolves against the page's own origin
}

/** Sends the browser to Google's consent screen; the backend redirects back to
 *  /oauth-callback with a session once Google confirms the user's identity. */
export function googleLoginUrl() {
  return `${backendBaseUrl()}/oauth2/authorization/google`;
}

api.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// A 401 on a protected route means the access token expired (they live 15 minutes).
// Try once to trade the refresh token for a new session before giving up and sending
// the user back to login — this is what makes "stay logged in" actually work.
let onSessionExpired = () => {};
export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler;
}

let refreshInFlight = null;

function refreshSession() {
  const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  if (!refreshToken) return Promise.reject(new Error("No refresh token"));

  // Multiple requests can 401 at once; share one refresh call instead of racing several.
  if (!refreshInFlight) {
    refreshInFlight = api
      .post("/auth/refresh", { refreshToken })
      .then((r) => {
        storeSession(r.data);
        return r.data;
      })
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const isAuthCall = error.config?.url?.startsWith("/auth/");
    const alreadyRetried = error.config?._retried;

    if (error.response?.status === 401 && !isAuthCall && !alreadyRetried) {
      try {
        await refreshSession();
        error.config._retried = true;
        return api.request(error.config);
      } catch {
        clearSession();
        onSessionExpired();
        return Promise.reject(error);
      }
    }

    if (error.response?.status === 401 && !isAuthCall && alreadyRetried) {
      clearSession();
      onSessionExpired();
    }

    return Promise.reject(error);
  }
);

/** Pulls the backend's `{ "error": "..." }` body out of an Axios failure. */
export function errorMessage(err, fallback = "Something went wrong") {
  return err?.response?.data?.error || err?.message || fallback;
}

export function isEmailNotVerified(err) {
  return err?.response?.data?.code === "EMAIL_NOT_VERIFIED";
}

export const auth = {
  register: (payload) =>
    api.post("/auth/register", payload).then((r) => {
      storeSession(r.data);
      return r.data;
    }),
  login: (payload) =>
    api.post("/auth/login", payload).then((r) => {
      storeSession(r.data);
      return r.data;
    }),
  logout: () => {
    const refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
    clearSession();
    if (!refreshToken) return Promise.resolve();
    // Best-effort: the user is logged out locally regardless of whether this succeeds.
    return api.post("/auth/logout", { refreshToken }).catch(() => {});
  },
  me: () => api.get("/auth/me").then((r) => r.data),
  /** Persists the token pair handed back by the Google OAuth redirect — no API call
   *  needed, the backend already minted a real session before redirecting here. */
  completeOAuthLogin: (token, refreshToken) => storeSession({ token, refreshToken }),
  updateProfile: (displayName) => api.put("/auth/me", { displayName }).then((r) => r.data),
  changePassword: (currentPassword, newPassword) =>
    api.post("/auth/change-password", { currentPassword, newPassword }).then((r) => r.data),
  deleteAccount: (password) =>
    // Only clear the local session once the delete actually succeeds — clearing first
    // would strip the Authorization header this very request needs.
    api.delete("/auth/me", { data: { password } }).then((r) => {
      clearSession();
      return r.data;
    }),
  verifyEmail: (token) => api.post("/auth/verify-email", { token }).then((r) => r.data),
  resendVerification: (email) =>
    api.post("/auth/resend-verification", { email }).then((r) => r.data),
  forgotPassword: (email) => api.post("/auth/forgot-password", { email }).then((r) => r.data),
  resetPassword: (token, newPassword) =>
    api.post("/auth/reset-password", { token, newPassword }).then((r) => r.data),
};

export const boards = {
  list: () => api.get("/boards").then((r) => r.data),
  create: (payload) => api.post("/boards", payload).then((r) => r.data),
  get: (boardId) => api.get(`/boards/${boardId}`).then((r) => r.data),
  update: (boardId, payload) => api.put(`/boards/${boardId}`, payload).then((r) => r.data),
  remove: (boardId) => api.delete(`/boards/${boardId}`).then((r) => r.data),

  createColumn: (boardId, payload) =>
    api.post(`/boards/${boardId}/columns`, payload).then((r) => r.data),
  updateColumn: (boardId, columnId, payload) =>
    api.put(`/boards/${boardId}/columns/${columnId}`, payload).then((r) => r.data),
  removeColumn: (boardId, columnId) =>
    api.delete(`/boards/${boardId}/columns/${columnId}`).then((r) => r.data),
  moveColumn: (boardId, columnId, payload) =>
    api.patch(`/boards/${boardId}/columns/${columnId}/move`, payload).then((r) => r.data),

  createCard: (boardId, columnId, payload) =>
    api.post(`/boards/${boardId}/columns/${columnId}/cards`, payload).then((r) => r.data),
  updateCard: (boardId, cardId, payload) =>
    api.put(`/boards/${boardId}/cards/${cardId}`, payload).then((r) => r.data),
  removeCard: (boardId, cardId) =>
    api.delete(`/boards/${boardId}/cards/${cardId}`).then((r) => r.data),
  moveCard: (boardId, cardId, payload) =>
    api.patch(`/boards/${boardId}/cards/${cardId}/move`, payload).then((r) => r.data),
};

export default api;
