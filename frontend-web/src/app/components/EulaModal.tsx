"use client";

import React, { useId, useRef } from "react";
import { X } from "lucide-react";
import { useDialogA11y } from "../lib/useDialogA11y";

interface EulaModalProps {
  isOpen: boolean;
  onClose: () => void;
}

const sectionCardStyle: React.CSSProperties = {
  padding: "14px 16px",
  borderRadius: "14px",
  background: "var(--surface-subtle)",
  border: "1px solid var(--card-border)",
  display: "flex",
  flexDirection: "column",
  gap: "6px",
};

const sectionHeadingStyle: React.CSSProperties = {
  fontSize: "0.94rem",
  fontWeight: 800,
  color: "var(--text-main)",
  margin: 0,
  lineHeight: 1.45,
};

const paragraphStyle: React.CSSProperties = {
  margin: 0,
  fontSize: "0.86rem",
  lineHeight: 1.6,
  color: "var(--text-sub)",
};

/**
 * 서비스 이용약관 모달:
 * 정보 큐레이션 지위, 가격/재고 변동, 제휴 마케팅 대가성 고지, 외부 링크 면책,
 * 비정상 이용 제한 및 문의처 안내를 6개 조항으로 제공한다.
 */
export default function EulaModal({ isOpen, onClose }: EulaModalProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();

  useDialogA11y({ isOpen, onClose, containerRef: panelRef });

  if (!isOpen) return null;

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(15, 23, 42, 0.55)",
        backdropFilter: "blur(8px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 2000,
        padding:
          "calc(20px + env(safe-area-inset-top, 0px)) 20px calc(20px + env(safe-area-inset-bottom, 0px)) 20px",
        animation: "slideUp 0.2s ease",
      }}
      onClick={onClose}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="glass-panel"
        style={{
          maxWidth: "560px",
          width: "100%",
          borderRadius: "20px",
          padding: "24px",
          maxHeight: "85dvh",
          overflowY: "auto",
          overscrollBehavior: "contain",
          wordBreak: "keep-all",
          overflowWrap: "anywhere",
          display: "flex",
          flexDirection: "column",
          gap: "16px",
          background: "var(--card-bg)",
          border: "1px solid var(--card-border)",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "flex-start",
            gap: "12px",
          }}
        >
          <div>
            <h3
              id={titleId}
              style={{
                fontSize: "1.25rem",
                fontWeight: 800,
                margin: "0 0 4px 0",
                color: "var(--text-main)",
              }}
            >
              서비스 이용약관
            </h3>
            <span
              style={{
                fontSize: "0.82rem",
                color: "var(--text-sub)",
                fontWeight: 600,
              }}
            >
              시행일: 2026년 11월 22일 · 최종 사용자 이용 안내
            </span>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="서비스 이용약관 닫기"
            style={{
              background: "var(--surface-subtle)",
              border: "1px solid var(--card-border)",
              cursor: "pointer",
              width: 44,
              height: 44,
              minWidth: 44,
              minHeight: 44,
              borderRadius: "50%",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "var(--text-sub)",
              flexShrink: 0,
            }}
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: "12px",
          }}
        >
          <section style={sectionCardStyle}>
            <h4 style={sectionHeadingStyle}>1. 서비스 성격 및 당사자 지위</h4>
            <p style={paragraphStyle}>
              얼만데(InsightDeal)는 국내외 주요 커뮤니티에 등록된 특가·할인 정보와
              가격 추이를 모아 보여주는 <strong>정보 큐레이션 및 알림 서비스</strong>
              입니다. 얼만데는 통신판매중개자나 상품의 판매 당사자가 아니며, 실제
              상품의 품질·결제·배송·청약철회·환불 및 A/S에 관한 모든 책임은 각
              외부 쇼핑몰과 판매자에게 있습니다.
            </p>
          </section>

          <section style={sectionCardStyle}>
            <h4 style={sectionHeadingStyle}>
              2. 가격·재고·할인 조건 변동 안내
            </h4>
            <p style={paragraphStyle}>
              게시된 가격과 할인 정보는 수집 시점의 커뮤니티 게시글을 기준으로
              제공됩니다. 판매처 사정, 품절(재고 소진), 카드사·멤버십·쿠폰 조건
              변경 또는 환율 변동에 따라 실제 판매처의 최종 결제 금액과 구매 가능
              여부가 달라질 수 있으므로, 구매 전 판매처 페이지에서 최종 조건을
              반드시 확인해 주세요.
            </p>
          </section>

          <section style={sectionCardStyle}>
            <h4 style={sectionHeadingStyle}>3. 제휴 마케팅 대가성 고지</h4>
            <p style={paragraphStyle}>
              본 서비스에서 제공하는 일부 쇼핑몰 아웃링크는{" "}
              <strong style={{ color: "var(--text-main)" }}>
                쿠팡 파트너스 및 링크프라이스 등 제휴 마케팅 활동의 일환으로 이에
                따른 일정액의 수수료를 제공받을 수 있습니다.
              </strong>{" "}
              제휴 수수료 수취 여부는 이용자의 실제 구매 가격에 어떠한 추가
              부담도 주지 않습니다.
            </p>
          </section>

          <section style={sectionCardStyle}>
            <h4 style={sectionHeadingStyle}>
              4. 외부 링크·커뮤니티 게시물 면책 및 권리 보호
            </h4>
            <p style={paragraphStyle}>
              얼만데가 연결하는 원본 커뮤니티 게시글과 외부 쇼핑몰 페이지의
              내용·정확성·적법성은 각 작성자 및 외부 운영 주체의 책임이며,
              얼만데는 고의 또는 중과실이 없는 한 외부 사이트 거래 및 이용으로
              발생한 손해에 대해 책임을 지지 않습니다. 잘못된 정보·품절 또는 권리
              침해 신고는 서비스 내 <strong>품절 제보</strong> 또는{" "}
              <code>support@insightdeal.com</code>으로 요청하시면 신속히 확인 후
              조치합니다.
            </p>
          </section>

          <section style={sectionCardStyle}>
            <h4 style={sectionHeadingStyle}>5. 비정상 이용 제한</h4>
            <p style={paragraphStyle}>
              비정상적인 자동화 수집(크롤링·매크로), 과도한 트래픽 유발, 타인의
              계정 또는 기기 도용, 투표·제보 기능 악용, 타인의 권리를 침해하거나
              스팸·불법 정보를 게시하는 행위가 확인될 경우 사전 안내 없이 서비스
              이용이 제한될 수 있습니다.
            </p>
          </section>

          <section style={sectionCardStyle}>
            <h4 style={sectionHeadingStyle}>
              6. 서비스 변경·중단, 약관 개정 및 문의처
            </h4>
            <p style={paragraphStyle}>
              운영·기술상 필요에 따라 서비스 일부를 변경하거나 점검·중단할 수
              있으며, 중요한 약관 변경이 있는 경우 서비스 화면 내 공지를 통해
              사전에 안내합니다. 이용 문의 및 오류 제보:{" "}
              <a
                href="mailto:support@insightdeal.com"
                style={{
                  color: "var(--accent)",
                  fontWeight: 700,
                  textDecoration: "underline",
                  textUnderlineOffset: "3px",
                }}
              >
                support@insightdeal.com
              </a>
            </p>
          </section>
        </div>

        <button
          type="button"
          className="btn-primary"
          onClick={onClose}
          style={{
            width: "100%",
            minHeight: "48px",
            justifyContent: "center",
            padding: "14px",
            borderRadius: "12px",
            marginTop: "4px",
          }}
        >
          닫기
        </button>
      </div>
    </div>
  );
}
