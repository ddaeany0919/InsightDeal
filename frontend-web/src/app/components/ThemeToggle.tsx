"use client";

import React, { useEffect, useSyncExternalStore } from "react";
import { Monitor, Sun, Moon } from "lucide-react";
import type { ThemeMode } from "../lib/types";

export const THEME_STORAGE_KEY = "insightdeal_theme";
const THEME_CHANGE_EVENT = "insightdeal-theme-change";

const THEME_OPTIONS: ReadonlyArray<{
  key: ThemeMode;
  label: string;
  ariaLabel: string;
}> = [
  { key: "system", label: "시스템", ariaLabel: "시스템 테마 자동 적용" },
  { key: "light", label: "라이트", ariaLabel: "라이트 모드 전환" },
  { key: "dark", label: "다크", ariaLabel: "다크 모드 전환" },
];

function applyThemeToDocument(mode: ThemeMode): void {
  if (typeof document === "undefined" || typeof window === "undefined") return;
  const root = document.documentElement;
  if (mode === "light" || mode === "dark") {
    root.dataset.theme = mode;
    root.style.colorScheme = mode;
    return;
  }
  delete root.dataset.theme;
  const prefersDark = window.matchMedia?.("(prefers-color-scheme: dark)")?.matches;
  root.style.colorScheme = prefersDark ? "dark" : "light";
}

function getThemeSnapshot(): ThemeMode {
  if (typeof window === "undefined") return "system";
  try {
    const saved = localStorage.getItem(THEME_STORAGE_KEY);
    if (saved === "light" || saved === "dark" || saved === "system") {
      return saved;
    }
  } catch {}
  return "system";
}

function getServerThemeSnapshot(): ThemeMode {
  return "system";
}

function subscribeTheme(callback: () => void): () => void {
  if (typeof window === "undefined") return () => {};
  const handleStorage = (e: StorageEvent) => {
    if (e.key === null || e.key === THEME_STORAGE_KEY) {
      callback();
    }
  };
  const handleCustom = () => callback();
  window.addEventListener("storage", handleStorage);
  window.addEventListener(THEME_CHANGE_EVENT, handleCustom);
  return () => {
    window.removeEventListener("storage", handleStorage);
    window.removeEventListener(THEME_CHANGE_EVENT, handleCustom);
  };
}

export default function ThemeToggle() {
  const mode = useSyncExternalStore(
    subscribeTheme,
    getThemeSnapshot,
    getServerThemeSnapshot
  );

  useEffect(() => {
    applyThemeToDocument(mode);
    if (mode !== "system" || typeof window === "undefined" || !window.matchMedia) {
      return;
    }
    const mq = window.matchMedia("(prefers-color-scheme: dark)");
    const onSystemChange = () => applyThemeToDocument("system");
    if (typeof mq.addEventListener === "function") {
      mq.addEventListener("change", onSystemChange);
      return () => mq.removeEventListener("change", onSystemChange);
    }
    return undefined;
  }, [mode]);

  const handleSelect = (nextMode: ThemeMode) => {
    try {
      localStorage.setItem(THEME_STORAGE_KEY, nextMode);
    } catch {}
    applyThemeToDocument(nextMode);
    if (typeof window !== "undefined") {
      window.dispatchEvent(new Event(THEME_CHANGE_EVENT));
    }
  };

  return (
    <div
      className="theme-toggle-group"
      role="group"
      aria-label="화면 테마 전환"
    >
      {THEME_OPTIONS.map((opt) => {
        const isActive = mode === opt.key;
        return (
          <button
            key={opt.key}
            type="button"
            className={`theme-toggle-btn bounce-click ${isActive ? "active" : ""}`}
            aria-pressed={isActive}
            aria-label={opt.ariaLabel}
            title={opt.label}
            onClick={() => handleSelect(opt.key)}
          >
            {opt.key === "system" ? (
              <Monitor size={14} aria-hidden="true" />
            ) : opt.key === "light" ? (
              <Sun size={14} aria-hidden="true" />
            ) : (
              <Moon size={14} aria-hidden="true" />
            )}
            <span className="theme-toggle-label">{opt.label}</span>
          </button>
        );
      })}
    </div>
  );
}
