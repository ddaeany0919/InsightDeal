"use client";

import React, { useEffect, useId, useRef, useState, useSyncExternalStore } from "react";
import Link from "next/link";
import { Download, MoreHorizontal, Settings, Smartphone } from "lucide-react";
import ThemeToggle from "./ThemeToggle";
import { appendTossDebugQuery } from "../lib/tossBridge";

const subscribeNoop = () => () => {};

export interface HeaderMoreMenuProps {
  isTossMiniApp: boolean;
  onOpenSyncModal: () => void;
  onInstallApp: () => void;
}

export default function HeaderMoreMenu({
  isTossMiniApp,
  onOpenSyncModal,
  onInstallApp,
}: HeaderMoreMenuProps) {
  const [isOpen, setIsOpen] = useState(false);
  const menuWrapRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const menuId = useId();
  const settingsHref = useSyncExternalStore(
    subscribeNoop,
    () => appendTossDebugQuery("/settings/notifications"),
    () => "/settings/notifications"
  );

  useEffect(() => {
    if (!isOpen) return;

    const handlePointerDown = (e: MouseEvent) => {
      if (menuWrapRef.current && !menuWrapRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.preventDefault();
        setIsOpen(false);
        triggerRef.current?.focus();
      }
    };

    document.addEventListener("mousedown", handlePointerDown);
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isOpen]);

  return (
    <div ref={menuWrapRef} className="header-more-wrap">
      <button
        ref={triggerRef}
        type="button"
        className={`counter-item header-more-trigger bounce-click ${isOpen ? "is-open" : ""}`}
        aria-label="더보기 메뉴"
        aria-haspopup="true"
        aria-expanded={isOpen}
        aria-controls={menuId}
        onClick={() => setIsOpen((prev) => !prev)}
      >
        <MoreHorizontal size={18} color="var(--text-main)" aria-hidden="true" />
      </button>

      {isOpen && (
        <div
          id={menuId}
          role="region"
          aria-label="더보기 메뉴"
          className="header-more-popover glass-panel"
        >
          {/* 정적 export 환경에서 존재하지 않는 RSC payload(__next.*.txt) 404 방지 */}
          <Link
            href={settingsHref}
            prefetch={false}
            className="header-more-item"
            aria-label="키워드 알림 설정"
            onClick={() => setIsOpen(false)}
          >
            <Settings size={16} color="var(--text-sub)" aria-hidden="true" />
            <span>키워드 알림 설정</span>
          </Link>

          <button
            type="button"
            className="header-more-item"
            aria-label="기기 동기화 및 계정 연결"
            onClick={() => {
              setIsOpen(false);
              onOpenSyncModal();
            }}
          >
            <Smartphone size={16} color="var(--text-sub)" aria-hidden="true" />
            <span>기기 동기화 및 계정 연결</span>
          </button>

          <div className="header-more-divider" aria-hidden="true" />

          <div className="header-more-theme-row">
            <span className="header-more-theme-label">화면 테마</span>
            <ThemeToggle />
          </div>

          {!isTossMiniApp && (
            <>
              <div className="header-more-divider" aria-hidden="true" />
              <button
                type="button"
                className="btn-primary header-more-install-btn"
                aria-label="앱 설치"
                onClick={() => {
                  setIsOpen(false);
                  onInstallApp();
                }}
              >
                <Download size={16} aria-hidden="true" />
                <span>앱 설치</span>
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
}
