"use client";

import React, { useMemo } from "react";

interface HighlightedTitleProps {
  title: string;
  query?: string;
}

function extractHighlightTokens(rawQuery?: string): string[] {
  if (!rawQuery) return [];
  const trimmed = rawQuery.trim();
  if (!trimmed) return [];

  const tokens = trimmed
    .split(/\s+/)
    .map((token) => token.trim())
    .filter((token) => {
      if (!token) return false;
      // 고급 검색 연산자(-제외어, :상한가격)는 제목 하이라이트 대상에서 제외
      if (token.startsWith("-") || token.startsWith(":")) return false;
      return true;
    })
    .map((token) => token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&"));

  // 긴 키워드가 먼저 매칭되도록 내림차순 정렬 및 중복 제거
  return Array.from(new Set(tokens)).sort((a, b) => b.length - a.length);
}

export default function HighlightedTitle({ title, query }: HighlightedTitleProps) {
  const regex = useMemo(() => {
    const tokens = extractHighlightTokens(query);
    if (tokens.length === 0) return null;
    try {
      return new RegExp(`(${tokens.join("|")})`, "gi");
    } catch {
      return null;
    }
  }, [query]);

  if (!regex || !title) {
    return <>{title}</>;
  }

  const parts = title.split(regex);
  if (parts.length <= 1) {
    return <>{title}</>;
  }

  return (
    <>
      {parts.map((part, idx) =>
        idx % 2 === 1 ? (
          <mark key={idx} className="search-highlight">
            {part}
          </mark>
        ) : (
          <React.Fragment key={idx}>{part}</React.Fragment>
        )
      )}
    </>
  );
}
