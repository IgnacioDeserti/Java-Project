import { createContext, useCallback, useContext, useEffect, useState } from "react";

const ThemeContext = createContext(null);
const STORAGE_KEY = "kanban_theme";

/**
 * Three states, not two: "light" and "dark" are explicit user choices, and "system"
 * (the default) follows the OS setting live. The chosen theme is written to a
 * data-theme attribute on <html>, which the CSS in index.css keys off — "system" writes
 * nothing and lets the prefers-color-scheme media query decide.
 */
export function ThemeProvider({ children }) {
  const [theme, setTheme] = useState(() => localStorage.getItem(STORAGE_KEY) || "system");

  useEffect(() => {
    const root = document.documentElement;
    if (theme === "system") {
      root.removeAttribute("data-theme");
      localStorage.removeItem(STORAGE_KEY);
    } else {
      root.setAttribute("data-theme", theme);
      localStorage.setItem(STORAGE_KEY, theme);
    }
  }, [theme]);

  // Cycles light -> dark -> system, so the OS-following option stays reachable without
  // needing a separate menu.
  const cycleTheme = useCallback(() => {
    setTheme((current) => (current === "light" ? "dark" : current === "dark" ? "system" : "light"));
  }, []);

  return (
    <ThemeContext.Provider value={{ theme, setTheme, cycleTheme }}>
      {children}
    </ThemeContext.Provider>
  );
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useTheme must be used within a ThemeProvider");
  return ctx;
}
