"use client";

import React, { useEffect, useId, useRef, useState } from "react";
import { ChevronLeft, ChevronRight, Minus, Plus, RotateCcw, X } from "lucide-react";
import { useDialogA11y } from "../lib/useDialogA11y";
import { getFallbackProxyImageUrl, getProxyImageUrl } from "../lib/utils";
import BrandImagePlaceholder from "./BrandImagePlaceholder";

interface ImageLightboxModalProps {
  isOpen: boolean;
  images: string[];
  initialIndex: number;
  title?: string;
  onClose: () => void;
}

function clampScale(val: number): number {
  return Math.min(4, Math.max(1, Math.round(val * 100) / 100));
}

function LightboxViewer({
  images,
  initialIndex,
  title,
  onClose,
}: Omit<ImageLightboxModalProps, "isOpen">) {
  const safeInitial =
    images.length > 0
      ? Math.min(Math.max(0, initialIndex), images.length - 1)
      : 0;
  const [currentIndex, setCurrentIndex] = useState<number>(safeInitial);
  const [scale, setScale] = useState<number>(1);
  const [pan, setPan] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const [isInteracting, setIsInteracting] = useState<boolean>(false);
  const [failedMap, setFailedMap] = useState<Record<string, boolean>>({});

  const dialogRef = useRef<HTMLDivElement>(null);
  const closeBtnRef = useRef<HTMLButtonElement>(null);
  const titleId = useId();
  const swipeStartRef = useRef<{ x: number; y: number } | null>(null);
  const dragPanStartRef = useRef<{
    clientX: number;
    clientY: number;
    originX: number;
    originY: number;
  } | null>(null);
  const pinchStartRef = useRef<{ dist: number; scale: number } | null>(null);

  useDialogA11y({
    isOpen: true,
    onClose,
    containerRef: dialogRef,
    initialFocusRef: closeBtnRef,
  });

  const resetTransform = () => {
    setScale(1);
    setPan({ x: 0, y: 0 });
  };

  const goToIndex = (nextIdx: number) => {
    if (images.length <= 1) return;
    const wrapped = (nextIdx + images.length) % images.length;
    setCurrentIndex(wrapped);
    resetTransform();
  };

  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === "ArrowLeft") {
        e.preventDefault();
        if (images.length > 1) {
          setCurrentIndex((prev) => (prev - 1 + images.length) % images.length);
          setScale(1);
          setPan({ x: 0, y: 0 });
        }
      } else if (e.key === "ArrowRight") {
        e.preventDefault();
        if (images.length > 1) {
          setCurrentIndex((prev) => (prev + 1) % images.length);
          setScale(1);
          setPan({ x: 0, y: 0 });
        }
      } else if (e.key === "+" || e.key === "=") {
        e.preventDefault();
        setScale((s) => clampScale(s + 0.5));
      } else if (e.key === "-") {
        e.preventDefault();
        setScale((s) => {
          const next = clampScale(s - 0.5);
          if (next === 1) setPan({ x: 0, y: 0 });
          return next;
        });
      } else if (e.key === "0") {
        e.preventDefault();
        setScale(1);
        setPan({ x: 0, y: 0 });
      }
    };
    window.addEventListener("keydown", handleKey);
    return () => window.removeEventListener("keydown", handleKey);
  }, [images.length]);

  const activeUrl = images[currentIndex] || "";
  const isFailed = !activeUrl || Boolean(failedMap[activeUrl]);

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        zIndex: 2400,
        background: "rgba(10, 15, 24, 0.92)",
        backdropFilter: "blur(8px)",
        display: "flex",
        flexDirection: "column",
        padding:
          "calc(12px + env(safe-area-inset-top, 0px)) 16px calc(16px + env(safe-area-inset-bottom, 0px)) 16px",
        animation: "fadeIn 0.16s ease",
      }}
      onClick={onClose}
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        style={{
          display: "flex",
          flexDirection: "column",
          width: "100%",
          height: "100%",
          maxWidth: "1080px",
          margin: "0 auto",
          gap: "12px",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 상단 툴바 */}
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: "10px",
            flexWrap: "wrap",
            color: "#F9FAFB",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "10px", minWidth: 0 }}>
            <span
              id={titleId}
              style={{
                fontSize: "0.9rem",
                fontWeight: 700,
                color: "#F9FAFB",
                overflow: "hidden",
                textOverflow: "ellipsis",
                whiteSpace: "nowrap",
                maxWidth: "320px",
              }}
            >
              {title ? `${title} 이미지 보기` : "상품 이미지 확대 보기"}
            </span>
            <span
              aria-live="polite"
              style={{
                fontSize: "0.8rem",
                fontWeight: 800,
                color: "#F9FAFB",
                background: "rgba(255, 255, 255, 0.14)",
                padding: "4px 10px",
                borderRadius: "999px",
              }}
            >
              {currentIndex + 1} / {images.length}
            </span>
          </div>

          <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
            <button
              type="button"
              aria-label="이미지 축소"
              onClick={() =>
                setScale((s) => {
                  const next = clampScale(s - 0.5);
                  if (next === 1) setPan({ x: 0, y: 0 });
                  return next;
                })
              }
              style={{
                minWidth: "44px",
                minHeight: "44px",
                borderRadius: "10px",
                background: "rgba(255, 255, 255, 0.14)",
                color: "#F9FAFB",
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <Minus size={16} aria-hidden="true" />
            </button>
            <button
              type="button"
              aria-label="확대 배율 초기화"
              onClick={resetTransform}
              style={{
                minWidth: "56px",
                minHeight: "44px",
                padding: "0 10px",
                borderRadius: "10px",
                background: "rgba(255, 255, 255, 0.14)",
                color: "#F9FAFB",
                fontSize: "0.8rem",
                fontWeight: 800,
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
                gap: "4px",
              }}
            >
              <RotateCcw size={13} aria-hidden="true" />
              <span>{Math.round(scale * 100)}%</span>
            </button>
            <button
              type="button"
              aria-label="이미지 확대"
              onClick={() => setScale((s) => clampScale(s + 0.5))}
              style={{
                minWidth: "44px",
                minHeight: "44px",
                borderRadius: "10px",
                background: "rgba(255, 255, 255, 0.14)",
                color: "#F9FAFB",
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <Plus size={16} aria-hidden="true" />
            </button>
            <button
              ref={closeBtnRef}
              type="button"
              aria-label="라이트박스 닫기"
              onClick={onClose}
              style={{
                minWidth: "44px",
                minHeight: "44px",
                borderRadius: "999px",
                background: "rgba(255, 255, 255, 0.18)",
                color: "#F9FAFB",
                display: "inline-flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              <X size={18} aria-hidden="true" />
            </button>
          </div>
        </div>

        {/* 이미지 뷰포트 */}
        <div
          style={{
            position: "relative",
            flex: 1,
            minHeight: 0,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            overflow: "hidden",
            borderRadius: "16px",
            background: "rgba(0, 0, 0, 0.35)",
            touchAction: "none",
          }}
          onWheel={(e) => {
            e.preventDefault();
            const delta = e.deltaY < 0 ? 0.25 : -0.25;
            setScale((s) => {
              const next = clampScale(s + delta);
              if (next === 1) setPan({ x: 0, y: 0 });
              return next;
            });
          }}
          onDoubleClick={() => {
            if (scale > 1) {
              resetTransform();
            } else {
              setScale(2);
            }
          }}
          onMouseDown={(e) => {
            if (scale <= 1) return;
            setIsInteracting(true);
            dragPanStartRef.current = {
              clientX: e.clientX,
              clientY: e.clientY,
              originX: pan.x,
              originY: pan.y,
            };
          }}
          onMouseMove={(e) => {
            if (!dragPanStartRef.current || scale <= 1) return;
            setPan({
              x: dragPanStartRef.current.originX + (e.clientX - dragPanStartRef.current.clientX),
              y: dragPanStartRef.current.originY + (e.clientY - dragPanStartRef.current.clientY),
            });
          }}
          onMouseUp={() => {
            dragPanStartRef.current = null;
            setIsInteracting(false);
          }}
          onMouseLeave={() => {
            dragPanStartRef.current = null;
            setIsInteracting(false);
          }}
          onTouchStart={(e) => {
            if (e.touches.length === 2) {
              const dx = e.touches[0].clientX - e.touches[1].clientX;
              const dy = e.touches[0].clientY - e.touches[1].clientY;
              pinchStartRef.current = { dist: Math.hypot(dx, dy), scale };
              swipeStartRef.current = null;
              setIsInteracting(true);
            } else if (e.touches.length === 1) {
              if (scale > 1) {
                setIsInteracting(true);
                dragPanStartRef.current = {
                  clientX: e.touches[0].clientX,
                  clientY: e.touches[0].clientY,
                  originX: pan.x,
                  originY: pan.y,
                };
              } else {
                swipeStartRef.current = {
                  x: e.touches[0].clientX,
                  y: e.touches[0].clientY,
                };
              }
            }
          }}
          onTouchMove={(e) => {
            if (e.touches.length === 2 && pinchStartRef.current) {
              const dx = e.touches[0].clientX - e.touches[1].clientX;
              const dy = e.touches[0].clientY - e.touches[1].clientY;
              const dist = Math.hypot(dx, dy);
              if (pinchStartRef.current.dist > 0) {
                const next = clampScale(
                  pinchStartRef.current.scale * (dist / pinchStartRef.current.dist)
                );
                setScale(next);
                if (next === 1) setPan({ x: 0, y: 0 });
              }
            } else if (e.touches.length === 1 && dragPanStartRef.current && scale > 1) {
              setPan({
                x:
                  dragPanStartRef.current.originX +
                  (e.touches[0].clientX - dragPanStartRef.current.clientX),
                y:
                  dragPanStartRef.current.originY +
                  (e.touches[0].clientY - dragPanStartRef.current.clientY),
              });
            }
          }}
          onTouchEnd={(e) => {
            pinchStartRef.current = null;
            dragPanStartRef.current = null;
            setIsInteracting(false);
            if (swipeStartRef.current && scale === 1 && e.changedTouches.length > 0) {
              const deltaX = e.changedTouches[0].clientX - swipeStartRef.current.x;
              if (Math.abs(deltaX) > 48) {
                goToIndex(deltaX < 0 ? currentIndex + 1 : currentIndex - 1);
              }
            }
            swipeStartRef.current = null;
          }}
        >
          {isFailed ? (
            <div style={{ width: "260px", height: "260px", borderRadius: "16px", overflow: "hidden" }}>
              <BrandImagePlaceholder label="이미지를 불러올 수 없어요" />
            </div>
          ) : (
            // eslint-disable-next-line @next/next/no-img-element
            <img
              key={activeUrl}
              src={getProxyImageUrl(activeUrl)}
              alt={`${title || "상품"} 상세 이미지 ${currentIndex + 1}`}
              referrerPolicy="no-referrer"
              style={{
                maxWidth: "100%",
                maxHeight: "100%",
                objectFit: "contain",
                transform: `translate3d(${pan.x}px, ${pan.y}px, 0) scale(${scale})`,
                transition: isInteracting ? "none" : "transform 0.16s ease",
                cursor: scale > 1 ? "grab" : "zoom-in",
              }}
              onError={(e) => {
                const img = e.currentTarget;
                const primarySrc = getProxyImageUrl(activeUrl);
                const fallback = getFallbackProxyImageUrl(activeUrl);
                if (fallback && fallback !== primarySrc && !img.dataset.fallbackTried) {
                  img.dataset.fallbackTried = "1";
                  img.src = fallback;
                  return;
                }
                img.dataset.fallbackTried = "1";
                setFailedMap((prev) => ({ ...prev, [activeUrl]: true }));
              }}
            />
          )}

          {images.length > 1 && (
            <>
              <button
                type="button"
                aria-label="이전 이미지"
                onClick={() => goToIndex(currentIndex - 1)}
                style={{
                  position: "absolute",
                  left: "12px",
                  top: "50%",
                  transform: "translateY(-50%)",
                  minWidth: "44px",
                  minHeight: "44px",
                  borderRadius: "50%",
                  background: "rgba(15, 23, 42, 0.72)",
                  color: "#F9FAFB",
                  border: "1px solid rgba(255, 255, 255, 0.22)",
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <ChevronLeft size={20} aria-hidden="true" />
              </button>
              <button
                type="button"
                aria-label="다음 이미지"
                onClick={() => goToIndex(currentIndex + 1)}
                style={{
                  position: "absolute",
                  right: "12px",
                  top: "50%",
                  transform: "translateY(-50%)",
                  minWidth: "44px",
                  minHeight: "44px",
                  borderRadius: "50%",
                  background: "rgba(15, 23, 42, 0.72)",
                  color: "#F9FAFB",
                  border: "1px solid rgba(255, 255, 255, 0.22)",
                  display: "inline-flex",
                  alignItems: "center",
                  justifyContent: "center",
                }}
              >
                <ChevronRight size={20} aria-hidden="true" />
              </button>
            </>
          )}
        </div>

        {/* 하단 썸네일 스트립 */}
        {images.length > 1 && (
          <div
            style={{
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "8px",
              overflowX: "auto",
              padding: "4px 0",
            }}
          >
            {images.map((imgUrl, idx) => {
              const isSelected = idx === currentIndex;
              const isThumbFailed = Boolean(failedMap[imgUrl]);
              return (
                <button
                  key={`${imgUrl}-${idx}`}
                  type="button"
                  aria-label={`${idx + 1}번 이미지 보기`}
                  aria-pressed={isSelected}
                  onClick={() => goToIndex(idx)}
                  style={{
                    width: "52px",
                    height: "52px",
                    minWidth: "52px",
                    minHeight: "52px",
                    borderRadius: "10px",
                    overflow: "hidden",
                    padding: 0,
                    border: isSelected
                      ? "2px solid var(--brand-mint)"
                      : "1px solid rgba(255, 255, 255, 0.24)",
                    opacity: isSelected ? 1 : 0.65,
                    background: "#191F28",
                    flexShrink: 0,
                  }}
                >
                  {isThumbFailed ? (
                    <BrandImagePlaceholder compact />
                  ) : (
                    // eslint-disable-next-line @next/next/no-img-element
                    <img
                      src={getProxyImageUrl(imgUrl, { width: 120 })}
                      alt=""
                      style={{ width: "100%", height: "100%", objectFit: "cover" }}
                      referrerPolicy="no-referrer"
                      onError={(e) => {
                        const img = e.currentTarget;
                        const primarySrc = getProxyImageUrl(imgUrl, { width: 120 });
                        const fallback = getFallbackProxyImageUrl(imgUrl);
                        if (fallback && fallback !== primarySrc && !img.dataset.fallbackTried) {
                          img.dataset.fallbackTried = "1";
                          img.src = fallback;
                          return;
                        }
                        img.dataset.fallbackTried = "1";
                        setFailedMap((prev) => ({ ...prev, [imgUrl]: true }));
                      }}
                    />
                  )}
                </button>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}

export default function ImageLightboxModal({
  isOpen,
  images,
  initialIndex,
  title,
  onClose,
}: ImageLightboxModalProps) {
  if (!isOpen || images.length === 0) return null;

  return (
    <LightboxViewer
      key={`${initialIndex}-${images.join("|")}`}
      images={images}
      initialIndex={initialIndex}
      title={title}
      onClose={onClose}
    />
  );
}
