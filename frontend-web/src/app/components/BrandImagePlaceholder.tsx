"use client";

import React from "react";

interface BrandImagePlaceholderProps {
  compact?: boolean;
  label?: string;
  shopName?: string | null;
  category?: string | null;
}

export interface BrandVisual {
  name: string;
  mark: string;
  rgb: string;
  hex: string;
}

const BRAND_RULES: Array<{ keywords: string[]; visual: BrandVisual }> = [
  {
    keywords: ["네이버", "스마트스토어", "naver"],
    visual: { name: "네이버", mark: "N", rgb: "3, 199, 90", hex: "#03C75A" },
  },
  {
    keywords: ["쿠팡", "coupang"],
    visual: { name: "쿠팡", mark: "C", rgb: "229, 57, 53", hex: "#E53935" },
  },
  {
    keywords: ["11번가", "11st"],
    visual: { name: "11번가", mark: "11", rgb: "255, 43, 85", hex: "#FF2B55" },
  },
  {
    keywords: ["무신사", "29cm", "musinsa"],
    visual: { name: "무신사", mark: "M", rgb: "100, 116, 139", hex: "#64748B" },
  },
  {
    keywords: ["지마켓", "g마켓", "gmarket"],
    visual: { name: "지마켓", mark: "G", rgb: "0, 184, 107", hex: "#00B86B" },
  },
  {
    keywords: ["옥션", "auction"],
    visual: { name: "옥션", mark: "A", rgb: "230, 0, 45", hex: "#E6002D" },
  },
  {
    keywords: ["롯데온", "하이마트", "lotteon", "himart"],
    visual: { name: "롯데온", mark: "L", rgb: "239, 42, 35", hex: "#EF2A23" },
  },
  {
    keywords: ["카카오", "톡딜", "kakao"],
    visual: { name: "카카오쇼핑", mark: "K", rgb: "234, 179, 8", hex: "#EAB308" },
  },
  {
    keywords: ["알리", "aliexpress"],
    visual: { name: "알리익스프레스", mark: "Ali", rgb: "255, 71, 71", hex: "#FF4747" },
  },
  {
    keywords: ["아마존", "amazon"],
    visual: { name: "아마존", mark: "a", rgb: "255, 153, 0", hex: "#FF9900" },
  },
  {
    keywords: ["오늘의집", "ohou"],
    visual: { name: "오늘의집", mark: "집", rgb: "53, 197, 240", hex: "#35C5F0" },
  },
  {
    keywords: ["ssg", "이마트", "emart"],
    visual: { name: "SSG", mark: "SSG", rgb: "245, 158, 11", hex: "#F59E0B" },
  },
  {
    keywords: ["토스", "toss"],
    visual: { name: "토스쇼핑", mark: "T", rgb: "49, 130, 246", hex: "#3182F6" },
  },
  {
    keywords: ["올리브영", "oliveyoung"],
    visual: { name: "올리브영", mark: "OY", rgb: "132, 204, 22", hex: "#84CC16" },
  },
  {
    keywords: ["컬리", "kurly"],
    visual: { name: "컬리", mark: "K", rgb: "124, 58, 237", hex: "#7C3AED" },
  },
  {
    keywords: ["컴퓨존", "compuzone"],
    visual: { name: "컴퓨존", mark: "CZ", rgb: "14, 165, 233", hex: "#0EA5E9" },
  },
  {
    keywords: ["스팀", "steam"],
    visual: { name: "Steam", mark: "ST", rgb: "59, 130, 246", hex: "#3B82F6" },
  },
  {
    keywords: ["뉴발란스", "nbkorea", "newbalance"],
    visual: { name: "뉴발란스", mark: "NB", rgb: "220, 38, 38", hex: "#DC2626" },
  },
];

export function resolveBrandVisual(shopName?: string | null): BrandVisual | null {
  const normalized = String(shopName || "")
    .trim()
    .toLowerCase()
    .replace(/\s+/g, "");
  if (!normalized) return null;
  for (const rule of BRAND_RULES) {
    if (rule.keywords.some((kw) => normalized.includes(kw))) {
      return rule.visual;
    }
  }
  return null;
}

export default function BrandImagePlaceholder({
  compact = false,
  label = "이미지 없음",
  shopName = null,
  category = null,
}: BrandImagePlaceholderProps) {
  const brand = resolveBrandVisual(shopName);

  if (brand) {
    const emblemSize = compact ? 28 : 44;
    const emblemFontSize =
      brand.mark.length >= 3
        ? compact
          ? "0.62rem"
          : "0.78rem"
        : brand.mark.length === 2
          ? compact
            ? "0.72rem"
            : "0.95rem"
          : compact
            ? "0.82rem"
            : "1.15rem";

    return (
      <div
        data-brand={brand.name}
        data-category={category || undefined}
        style={{
          width: "100%",
          height: "100%",
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          gap: compact ? "0px" : "6px",
          backgroundColor: "var(--surface-subtle)",
          backgroundImage: `linear-gradient(145deg, rgba(${brand.rgb}, 0.14) 0%, rgba(${brand.rgb}, 0.04) 100%)`,
          color: "var(--text-main)",
          padding: compact ? "4px" : "12px",
          userSelect: "none",
          textAlign: "center",
        }}
      >
        <span
          aria-hidden="true"
          style={{
            width: `${emblemSize}px`,
            height: `${emblemSize}px`,
            borderRadius: compact ? "9px" : "14px",
            display: "inline-flex",
            alignItems: "center",
            justifyContent: "center",
            background: `rgba(${brand.rgb}, 0.16)`,
            border: `1px solid rgba(${brand.rgb}, 0.32)`,
            color: brand.hex,
            fontSize: emblemFontSize,
            fontWeight: 800,
            letterSpacing: "-0.03em",
            lineHeight: 1,
            flexShrink: 0,
          }}
        >
          {brand.mark}
        </span>
        {compact ? (
          <span className="sr-only">{label}</span>
        ) : (
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              gap: "2px",
              minWidth: 0,
            }}
          >
            <span
              style={{
                fontSize: "0.84rem",
                fontWeight: 800,
                color: "var(--text-main)",
                letterSpacing: "-0.02em",
                lineHeight: 1.2,
              }}
            >
              {brand.name}
            </span>
            <span
              style={{
                fontSize: "0.7rem",
                fontWeight: 600,
                color: "var(--text-muted)",
                lineHeight: 1.2,
              }}
            >
              {label}
            </span>
          </div>
        )}
      </div>
    );
  }

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
