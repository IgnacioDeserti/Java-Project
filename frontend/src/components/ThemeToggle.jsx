import { Monitor, Moon, Sun } from "lucide-react";
import { useTheme } from "../context/ThemeContext.jsx";

const NEXT_LABEL = { light: "dark", dark: "system", system: "light" };

export default function ThemeToggle() {
  const { theme, cycleTheme } = useTheme();
  const Icon = theme === "light" ? Sun : theme === "dark" ? Moon : Monitor;

  return (
    <button
      className="theme-toggle"
      onClick={cycleTheme}
      title={`Theme: ${theme} (click for ${NEXT_LABEL[theme]})`}
      aria-label={`Theme: ${theme}. Switch to ${NEXT_LABEL[theme]}.`}
    >
      <Icon size={16} />
    </button>
  );
}
