"use client";

import React from "react";

interface BrandImagePlaceholderProps {
  compact?: boolean;
  label?: string;
}

export default function BrandImagePlaceholder({
  compact = false,
  label = "이미지 없음",
}: BrandImagePlaceholderProps) {
  return (
    <div
      style={{
        width: "100%",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        gap: compact ? "0px" : "6px",
        background: "var(--surface-subtle)",
        color: "var(--text-muted)",
        padding: compact ? "4px" : "12px",
        userSelect: "none",
      }}
    >
      <svg
        width={compact ? 24 : 36}
        height={compact ? 24 : 36}
        viewBox="0 0 36 36"
        fill="none"
        aria-hidden="true"
      >
        <path
          d="M6.5 11.2C6.5 8.88 8.38 7 10.7 7H18.4C19.52 7 20.6 7.45 21.39 8.24L27.76 14.61C29.4 16.25 29.4 18.91 27.76 20.55L20.55 27.76C18.91 29.4 16.25 29.4 14.61 27.76L8.24 21.39C7.45 20.6 7 19.52 7 18.4V11.2H6.5Z"
          stroke="var(--brand-mint)"
          strokeWidth="2.2"
          strokeLinejoin="round"
        />
        <circle cx="12.5" cy="12.5" r="2" fill="var(--brand-mint)" />
        <path
          d="M14.5 19.5L17.2 22.2L22.5 16.8"
          stroke="var(--accent)"
          strokeWidth="2.2"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
      </svg>
      {compact ? (
        <span className="sr-only">{label}</span>
      ) : (
        <span
          style={{
            fontSize: "0.78rem",
            fontWeight: 700,
            color: "var(--text-muted)",
          }}
        >
          {label}
        </span>
      )}
    </div>
  );
}
