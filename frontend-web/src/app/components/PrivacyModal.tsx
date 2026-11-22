"use client";

import React, { useId, useRef } from "react";
import { X } from "lucide-react";
import { useDialogA11y } from "../lib/useDialogA11y";

interface PrivacyModalProps {
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
  gap: "8px",
};

const sectionHeadingStyle: React.CSSProperties = {
  fontSize: "0.94rem",
  fontWeight: 800,
  color: "var(--text-main)",
  margin: 0,
  lineHeight: 1.45,
};

const listStyle: React.CSSProperties = {
  margin: 0,
  paddingLeft: "18px",
  display: "flex",
  flexDirection: "column",
  gap: "6px",
  fontSize: "0.86rem",
  lineHeight: 1.6,
  color: "var(--text-sub)",
};

const paragraphStyle: React.CSSProperties = {
  margin: 0,
  fontSize: "0.86rem",
  lineHeight: 1.6,
  color: "var(--text-sub)",
};

/**
 * 개인정보 처리방침 모달:
 * 실제 웹·앱·토스 미니앱 데이터 흐름과 100% 일치하는 5대 법정 고지 영역을 제공한다.
 */
export default function PrivacyModal({ isOpen, onClose }: PrivacyModalProps) {
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
              개인정보 처리방침
            </h3>
            <span
              style={{
                fontSize: "0.82rem",
                color: "var(--text-sub)",
                fontWeight: 600,
              }}
            >
              시행일: 2026년 11월 22일 · 서비스: 얼만데(InsightDeal)
            </span>
          </div>

          <button
            type="button"
            onClick={onClose}
            aria-label="개인정보 처리방침 닫기"
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
            <h4 style={sectionHeadingStyle}>
              1. 수집하는 항목 및 이용 목적 (필수·선택·로컬 저장 구분)
            </h4>
            <ul style={listStyle}>
              <li>
                <strong style={{ color: "var(--text-main)" }}>
                  게스트 이용(기기 식별·알림):
                </strong>{" "}
                무작위 기기 식별자(<code>device_uuid</code>), 푸시 수신 정보(웹
                브라우저 Web Push 구독 정보 및 Android 앱 FCM 토큰), 키워드·목표가
                알림 및 방해금지 시간대 설정, 알림 수신 내역, 커뮤니티 댓글·투표·품절
                제보 식별자를 수집해 실시간 핫딜 알림 발송, 6자리 코드 다기기
                동기화, 중복 제보 및 부정 참여 방지에 이용합니다.
              </li>
              <li>
                <strong style={{ color: "var(--text-main)" }}>
                  선택형 계정 연결(선택):
                </strong>{" "}
                계정 연동을 직접 선택한 경우에만 아이디, 닉네임, 비밀번호(서버에서{" "}
                <code>PBKDF2-HMAC-SHA256</code> 솔트 암호화 단방향 해시 저장)를
                수집하여 기기 간 찜·키워드·알림 설정 연동 및 계정 복구에 이용합니다.
                (전화번호·주민등록번호·결제정보는 수집하지 않습니다.)
              </li>
              <li>
                <strong style={{ color: "var(--text-main)" }}>
                  자동 수집 로그 및 비식별 통계:
                </strong>{" "}
                서비스 접속 IP(일시적 요청 제한 및 서버 보안 로그), 브라우저·OS
                정보(User-Agent), 요청 시각·오류 로그, 비식별 쇼핑몰 아웃링크 클릭
                통계(쇼핑몰명·도메인·플랫폼 구분)를 수집해 서비스 안정성 확보와
                어뷰징 차단에 이용합니다.
              </li>
              <li>
                <strong style={{ color: "var(--text-main)" }}>
                  서버 전송 없는 기기 내 로컬 저장(<code>localStorage</code>) 및
                  모바일 앱 전용 생체 인증:
                </strong>{" "}
                테마, 카드·컴팩트 보기 모드, 최근 본 핫딜·검색어, 숨김 딜·뮤트
                키워드, 알림 읽음 상태, 인앱 배너 닫기 상태는 서버로 전송하지 않고
                이용자 기기 내부에만 저장됩니다. 지문 등 생체 인증 잠금은 모바일
                앱(Android/iOS) 전용 기능으로 기기 내 보안 영역(
                <code>Android KeyStore</code> / <code>iOS Secure Enclave</code>)에서만
                처리되며 서버로 전송·저장하지 않습니다.
              </li>
            </ul>
          </section>

          <section style={sectionCardStyle}>
            <h4 style={sectionHeadingStyle}>2. 보유 및 이용기간과 파기 방법</h4>
            <ul style={listStyle}>
              <li>
                알림 설정 및 푸시 구독 정보는 알림 해제 또는 브라우저·앱 알림 권한
                철회 시 지체 없이 삭제합니다.
              </li>
              <li>
                선택형 계정은 <strong>다기기 동기화 &gt; 계정 탈퇴</strong>{" "}
                요청 시 계정 정보와 기기 연결 매핑을 즉시 파기합니다. 게스트
                상태의 브라우저 로컬 데이터(<code>localStorage</code>)와 푸시
                구독은 브라우저 사이트 데이터 삭제 또는 알림 해제로 직접 초기화할
                수 있습니다.
              </li>
              <li>
                단, 관계 법령에 따라 보존이 필요한 경우 해당 법령이 정한 기간 동안
                분리 보관 후 파기합니다(「통신비밀보호법」 서비스 접속 로그 3개월,
                「전자상거래 등에서의 소비자보호에 관한 법률」 소비자 불만·분쟁 처리
                기록 3년 등 해당 사유 발생 시).
              </li>
            </ul>
          </section>

          <section style={sectionCardStyle}>
            <h4 style={sectionHeadingStyle}>
              3. 개인정보 처리위탁 및 국외 이전
            </h4>
            <p style={paragraphStyle}>
              원활한 서비스 운영과 푸시 알림 발송을 위해 아래 클라우드 인프라 및
              메시징 플랫폼을 이용하며, 서비스 특성상 데이터가 국외 서버(미국 등)에
              저장·처리되거나 경유할 수 있습니다.
            </p>
            <ul style={listStyle}>
              <li>
                <strong style={{ color: "var(--text-main)" }}>
                  Google Cloud Platform (Google LLC):
                </strong>{" "}
                백엔드 API 및 데이터베이스 호스팅 운영
              </li>
              <li>
                <strong style={{ color: "var(--text-main)" }}>Vercel Inc.:</strong>{" "}
                웹 프론트엔드 호스팅 및 콘텐츠 전송 네트워크(CDN)
              </li>
              <li>
                <strong style={{ color: "var(--text-main)" }}>
                  Google Firebase Cloud Messaging (FCM) 및 브라우저 푸시 서비스:
                </strong>{" "}
                모바일 앱 및 Web Push 알림 메시지 전송
              </li>
              <li>
                <strong style={{ color: "var(--text-main)" }}>
                  주식회사 비바리퍼블리카(앱인토스):
                </strong>{" "}
                토스 미니앱 실행 시 실행 환경 제공 및 비식별 화면·클릭 이용 통계
                처리
              </li>
            </ul>
          </section>

          <section style={sectionCardStyle}>
            <h4 style={sectionHeadingStyle}>
              4. 제휴 마케팅(쿠팡 파트너스·링크프라이스) 및 외부 사이트 이동 안내
            </h4>
            <ul style={listStyle}>
              <li>
                일부 쇼핑몰 이동 링크에는 <strong>쿠팡 파트너스</strong> 및{" "}
                <strong>링크프라이스</strong> 등 제휴 마케팅 식별 파라미터가 포함될
                수 있으나, 비식별 아웃링크 통계로만 활용되며 이용자의 개인식별정보를
                외부 쇼핑몰에 제공하지 않고 구매 가격에도 영향을 미치지 않습니다.
              </li>
              <li>
                상품 보기 링크나 원본 글 링크를 통해 외부 쇼핑몰 또는 커뮤니티로
                이동한 이후의 개인정보 수집·처리는 각 외부 사업자 및 커뮤니티의
                개인정보 처리방침을 따릅니다.
              </li>
            </ul>
          </section>

          <section style={sectionCardStyle}>
            <h4 style={sectionHeadingStyle}>
              5. 정보주체의 권리·행사 방법, 개인정보 보호책임자 및 구제기관
            </h4>
            <ul style={listStyle}>
              <li>
                이용자는 언제든지 알림 설정 해제, 찜·키워드 삭제, 계정 연결 해제 및
                계정 탈퇴를 통해 개인정보 열람·정정·삭제·처리정지를 직접 행사할 수
                있습니다.
              </li>
              <li>
                <strong style={{ color: "var(--text-main)" }}>
                  개인정보 보호책임자 및 문의처:
                </strong>{" "}
                얼만데 서비스 운영팀 ·{" "}
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
              </li>
              <li>
                <strong style={{ color: "var(--text-main)" }}>
                  권익침해 구제기관:
                </strong>{" "}
                개인정보분쟁조정위원회(www.kopico.go.kr, 1833-6972),
                개인정보침해신고센터(privacy.kisa.or.kr, 국번없이 118),
                대검찰청(www.spo.go.kr, 1301), 경찰청(ecrm.police.go.kr, 182)
              </li>
            </ul>
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
