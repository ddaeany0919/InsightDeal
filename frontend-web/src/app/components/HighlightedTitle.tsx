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

  const seenLower = new Set<string>();
  const uniqueRawTokens = trimmed
    .split(/\s+/)
    .map((token) => token.trim())
    .filter((token) => {
      if (!token) return false;
      // 고급 검색 연산자(-제외어, :상한가격)는 제목 하이라이트 대상에서 제외
      if (token.startsWith("-") || token.startsWith(":")) return false;
      const lower = token.toLowerCase();
      if (seenLower.has(lower)) return false;
      seenLower.add(lower);
      return true;
    })
    // 이스케이프 문자(\)로 인한 길이 왜곡을 막기 위해 원본 길이 기준으로 내림차순 정렬
    .sort((a, b) => b.length - a.length);

  return uniqueRawTokens.map((token) =>
    token.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
  );
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
