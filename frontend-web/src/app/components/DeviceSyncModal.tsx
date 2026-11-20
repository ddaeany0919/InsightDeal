"use client";

import React, { useEffect, useId, useRef, useState } from "react";
import {
  Check,
  Copy,
  KeyRound,
  LogOut,
  RefreshCw,
  Smartphone,
  UserCheck,
  X,
} from "lucide-react";
import type {
  AccountAuthResponse,
  ClaimPairingCodeResponse,
  PairingCodeResponse,
} from "../lib/types";
import {
  authFetch,
  clearStoredAccountAuth,
  ensureWebDeviceAuth,
  getStoredAccountToken,
  getStoredAccountUser,
  saveStoredAccountAuth,
  type StoredAccountUser,
} from "../lib/utils";
import { copyTextSafe } from "../lib/tossBridge";
import { useDialogA11y } from "../lib/useDialogA11y";

interface DeviceSyncModalProps {
  isOpen: boolean;
  onClose: () => void;
  onActivateGuest?: () => void;
  onSyncComplete?: () => Promise<void> | void;
  onShowToast?: (message: string) => void;
}

type SyncTab = "pairing" | "account";
type AccountFormMode = "login" | "signup";

function DeviceSyncModalContent({
  onClose,
  onActivateGuest,
  onSyncComplete,
  onShowToast,
}: Omit<DeviceSyncModalProps, "isOpen">) {
  const [activeTab, setActiveTab] = useState<SyncTab>("pairing");
  const [pairingCode, setPairingCode] = useState<string>("");
  const [remainingSec, setRemainingSec] = useState<number>(0);
  const [issuingCode, setIssuingCode] = useState<boolean>(false);
  const [copiedCode, setCopiedCode] = useState<boolean>(false);

  const [inputCode, setInputCode] = useState<string>("");
  const [claimingCode, setClaimingCode] = useState<boolean>(false);
  const [pairingStatusMsg, setPairingStatusMsg] = useState<string | null>(null);
  const [pairingErrorMsg, setPairingErrorMsg] = useState<string | null>(null);

  const [accountMode, setAccountMode] = useState<AccountFormMode>("login");
  const [username, setUsername] = useState<string>("");
  const [password, setPassword] = useState<string>("");
  const [nickname, setNickname] = useState<string>("");
  const [accountLoading, setAccountLoading] = useState<boolean>(false);
  const [accountError, setAccountError] = useState<string | null>(null);
  const [accountSuccess, setAccountSuccess] = useState<string | null>(null);
  const [connectedUser, setConnectedUser] = useState<StoredAccountUser | null>(() =>
    getStoredAccountUser()
  );

  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();

  useDialogA11y({ isOpen: true, onClose, containerRef: panelRef });

  useEffect(() => {
    if (remainingSec <= 0) return;
    const timer = window.setInterval(() => {
      setRemainingSec((prev) => (prev > 1 ? prev - 1 : 0));
    }, 1000);
    return () => window.clearInterval(timer);
  }, [remainingSec]);

  const formatCountdown = (sec: number): string => {
    const mins = Math.floor(sec / 60);
    const secs = sec % 60;
    return `${String(mins).padStart(2, "0")}:${String(secs).padStart(2, "0")}`;
  };

  const handleIssuePairingCode = async () => {
    if (issuingCode) return;
    setIssuingCode(true);
    setPairingErrorMsg(null);
    setPairingStatusMsg(null);
    setCopiedCode(false);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch("/api/auth/pairing-code", {
        method: "POST",
      });
      const data = (await res.json().catch(() => null)) as
        | (PairingCodeResponse & { detail?: string })
        | null;
      if (!res.ok || !data?.code) {
        if (res.status === 429) {
          setPairingErrorMsg("요청이 너무 많아요. 잠시 후 다시 시도해 주세요.");
        } else {
          setPairingErrorMsg(
            data?.detail || "동기화 코드를 발급하지 못했어요. 잠시 후 다시 시도해 주세요."
          );
        }
        return;
      }
      setPairingCode(String(data.code));
      setRemainingSec(Number(data.expires_in_sec) > 0 ? Number(data.expires_in_sec) : 600);
      setPairingStatusMsg("10분 안에 다른 기기에서 이 6자리 코드를 입력해 주세요.");
    } catch {
      setPairingErrorMsg("네트워크 오류로 동기화 코드를 발급하지 못했어요.");
    } finally {
      setIssuingCode(false);
    }
  };

  const handleCopyPairingCode = async () => {
    if (!pairingCode) return;
    const copied = await copyTextSafe(pairingCode);
    if (copied) {
      setCopiedCode(true);
      onShowToast?.("6자리 동기화 코드를 복사했어요");
      window.setTimeout(() => setCopiedCode(false), 2000);
    } else {
      onShowToast?.("코드를 복사하지 못했어요. 숫자를 직접 입력해 주세요");
    }
  };

  const handleClaimPairingCode = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleaned = inputCode.replace(/[^0-9]/g, "");
    if (cleaned.length !== 6) {
      setPairingErrorMsg("6자리 숫자 코드를 정확히 입력해 주세요.");
      return;
    }
    if (claimingCode) return;
    setClaimingCode(true);
    setPairingErrorMsg(null);
    setPairingStatusMsg(null);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch("/api/auth/claim-pairing-code", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ code: cleaned }),
      });
      const data = (await res.json().catch(() => null)) as ClaimPairingCodeResponse | null;
      if (!res.ok) {
        if (res.status === 400) {
          setPairingErrorMsg("코드 형식이 올바르지 않거나 내 기기 코드입니다.");
        } else if (res.status === 404) {
          setPairingErrorMsg("존재하지 않거나 이미 만료된 6자리 코드예요.");
        } else if (res.status === 409) {
          setPairingErrorMsg("이미 사용된 동기화 코드예요. 새 코드를 발급해 주세요.");
        } else if (res.status === 429) {
          setPairingErrorMsg("입력 시도가 너무 많아요. 잠시 후 다시 시도해 주세요.");
        } else {
          setPairingErrorMsg(
            data?.detail || "기기 동기화에 실패했어요. 잠시 후 다시 시도해 주세요."
          );
        }
        return;
      }

      const wishCnt = Number(data?.merged_wishlists ?? data?.mergedWishlists ?? 0);
      const kwCnt = Number(data?.merged_keywords ?? data?.mergedKeywords ?? 0);
      const alertCnt = Number(data?.merged_alerts ?? data?.mergedAlerts ?? 0);
      const summary = `동기화 완료! 찜 ${wishCnt}건 · 키워드 ${kwCnt}건 · 알림 ${alertCnt}건을 불러왔어요.`;
      setPairingStatusMsg(summary);
      setInputCode("");
      onShowToast?.(summary);
      await onSyncComplete?.();
    } catch {
      setPairingErrorMsg("네트워크 오류로 기기 동기화에 실패했어요.");
    } finally {
      setClaimingCode(false);
    }
  };

  const handleAccountSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanUsername = username.trim();
    const cleanPassword = password;
    const cleanNickname = nickname.trim();
    if (!cleanUsername || !cleanPassword) {
      setAccountError("아이디와 비밀번호를 모두 입력해 주세요.");
      return;
    }
    if (accountMode === "signup" && !cleanNickname) {
      setAccountError("표시할 닉네임을 입력해 주세요.");
      return;
    }
    if (accountLoading) return;
    setAccountLoading(true);
    setAccountError(null);
    setAccountSuccess(null);

    try {
      await ensureWebDeviceAuth();
      const endpoint =
        accountMode === "login" ? "/api/auth/login" : "/api/auth/signup";
      const payload =
        accountMode === "login"
          ? { username: cleanUsername, password: cleanPassword }
          : {
              username: cleanUsername,
              password: cleanPassword,
              nickname: cleanNickname,
            };

      const res = await authFetch(endpoint, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      const data = (await res.json().catch(() => null)) as AccountAuthResponse | null;
      if (!res.ok || !data?.username) {
        setAccountError(
          data?.detail ||
            (accountMode === "login"
              ? "아이디 또는 비밀번호를 확인해 주세요."
              : "회원가입을 완료하지 못했어요. 다른 아이디로 시도해 주세요.")
        );
        return;
      }

      const token = data.access_token || "";
      const userObj: StoredAccountUser = {
        username: data.username,
        nickname: data.nickname || data.username,
        honey_points: data.honey_points,
      };
      if (token) {
        const linkRes = await authFetch("/api/auth/link-device", {
          method: "POST",
          headers: { Authorization: `Bearer ${token}` },
        });
        if (!linkRes.ok) {
          setAccountError("계정은 인증됐지만 현재 기기 연결에 실패했어요.");
          return;
        }
        saveStoredAccountAuth(token, userObj);
      }

      setConnectedUser(userObj);
      setPassword("");
      const doneMsg = `${userObj.nickname}님 계정과 현재 기기를 연결했어요.`;
      setAccountSuccess(doneMsg);
      onShowToast?.(doneMsg);
      await onSyncComplete?.();
    } catch {
      setAccountError("네트워크 오류로 계정 연결에 실패했어요.");
    } finally {
      setAccountLoading(false);
    }
  };

  const handleLogoutAccount = () => {
    clearStoredAccountAuth();
    setConnectedUser(null);
    setAccountSuccess("계정 연결을 해제했어요. 게스트 기기 상태는 그대로 유지돼요.");
    onShowToast?.("계정 연결을 해제했어요");
  };

  const storedTokenExists = Boolean(getStoredAccountToken());

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
          "max(20px, env(safe-area-inset-top, 0px)) 20px max(20px, env(safe-area-inset-bottom, 0px)) 20px",
        animation: "slideUp 0.22s ease",
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
          maxWidth: "480px",
          width: "100%",
          borderRadius: "22px",
          padding: "24px",
          maxHeight: "88dvh",
          overflowY: "auto",
          display: "flex",
          flexDirection: "column",
          gap: "16px",
          background: "var(--card-bg)",
          border: "1px solid var(--card-border)",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "12px" }}>
          <div>
            <h3
              id={titleId}
              style={{
                fontSize: "1.22rem",
                fontWeight: 900,
                marginBottom: "4px",
                color: "var(--text-main)",
              }}
            >
              다기기 동기화 및 연동
            </h3>
            <p style={{ fontSize: "0.84rem", color: "var(--text-sub)", lineHeight: 1.45 }}>
              로그인 없이 6자리 코드로 찜·키워드·알림을 웹, 안드로이드 앱, 토스 미니앱에 즉시 이어받으세요.
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="모달 닫기"
            title="다기기 동기화 모달 닫기"
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

        {/* 2단 탭 전환 */}
        <div
          role="group"
          aria-label="동기화 방식 선택"
          style={{
            display: "grid",
            gridTemplateColumns: "1fr 1fr",
            gap: "6px",
            background: "var(--surface-subtle)",
            padding: "4px",
            borderRadius: "12px",
            border: "1px solid var(--card-border)",
          }}
        >
          <button
            type="button"
            aria-pressed={activeTab === "pairing"}
            onClick={() => setActiveTab("pairing")}
            style={{
              minHeight: "44px",
              borderRadius: "9px",
              fontSize: "0.84rem",
              fontWeight: 800,
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "6px",
              background: activeTab === "pairing" ? "var(--accent)" : "transparent",
              color: activeTab === "pairing" ? "var(--on-accent)" : "var(--text-sub)",
            }}
          >
            <Smartphone size={15} aria-hidden="true" />
            <span>6자리 코드 동기화</span>
          </button>
          <button
            type="button"
            aria-pressed={activeTab === "account"}
            onClick={() => setActiveTab("account")}
            style={{
              minHeight: "44px",
              borderRadius: "9px",
              fontSize: "0.84rem",
              fontWeight: 800,
              display: "inline-flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "6px",
              background: activeTab === "account" ? "var(--accent)" : "transparent",
              color: activeTab === "account" ? "var(--on-accent)" : "var(--text-sub)",
            }}
          >
            <KeyRound size={15} aria-hidden="true" />
            <span>선택형 계정 연결</span>
          </button>
        </div>

        {activeTab === "pairing" ? (
          <div style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
            {pairingErrorMsg && (
              <div
                role="alert"
                style={{
                  padding: "10px 12px",
                  borderRadius: "12px",
                  background: "var(--danger-bg)",
                  border: "1px solid var(--danger-border)",
                  color: "var(--danger-text)",
                  fontSize: "0.82rem",
                  fontWeight: 700,
                }}
              >
                {pairingErrorMsg}
              </div>
            )}
            {pairingStatusMsg && (
              <div
                role="status"
                aria-live="polite"
                style={{
                  padding: "10px 12px",
                  borderRadius: "12px",
                  background: "var(--success-bg)",
                  border: "1px solid var(--success-border)",
                  color: "var(--success-text)",
                  fontSize: "0.82rem",
                  fontWeight: 700,
                }}
              >
                {pairingStatusMsg}
              </div>
            )}

            {/* 1) 내 기기 코드 발급 */}
            <div
              style={{
                padding: "14px",
                borderRadius: "14px",
                background: "var(--surface-subtle)",
                border: "1px solid var(--card-border)",
                display: "flex",
                flexDirection: "column",
                gap: "10px",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <span style={{ fontSize: "0.88rem", fontWeight: 800, color: "var(--text-main)" }}>
                  1. 내 기기 동기화 코드 발급
                </span>
                {remainingSec > 0 && (
                  <span style={{ fontSize: "0.78rem", fontWeight: 800, color: "var(--accent)" }}>
                    남은 시간 {formatCountdown(remainingSec)}
                  </span>
                )}
              </div>

              {pairingCode ? (
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    gap: "10px",
                    padding: "12px 14px",
                    borderRadius: "12px",
                    background: "var(--card-bg)",
                    border: "1px solid var(--accent-soft-border)",
                  }}
                >
                  <span
                    aria-label="발급된 6자리 동기화 코드"
                    style={{
                      fontSize: "1.45rem",
                      fontWeight: 900,
                      letterSpacing: "0.18em",
                      fontVariantNumeric: "tabular-nums",
                      color: "var(--accent)",
                    }}
                  >
                    {pairingCode}
                  </span>
                  <div style={{ display: "flex", gap: "6px" }}>
                    <button
                      type="button"
                      onClick={() => void handleCopyPairingCode()}
                      className="btn-secondary"
                      style={{ minHeight: "44px", padding: "8px 12px", fontSize: "0.8rem" }}
                    >
                      {copiedCode ? (
                        <Check size={14} aria-hidden="true" />
                      ) : (
                        <Copy size={14} aria-hidden="true" />
                      )}
                      <span>{copiedCode ? "복사됨" : "복사"}</span>
                    </button>
                    <button
                      type="button"
                      onClick={() => void handleIssuePairingCode()}
                      aria-label="동기화 코드 재발급"
                      style={{
                        minWidth: "44px",
                        minHeight: "44px",
                        borderRadius: "10px",
                        background: "var(--surface-subtle)",
                        border: "1px solid var(--card-border)",
                        color: "var(--text-main)",
                        display: "inline-flex",
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      <RefreshCw size={15} aria-hidden="true" />
                    </button>
                  </div>
                </div>
              ) : (
                <button
                  type="button"
                  onClick={() => void handleIssuePairingCode()}
                  disabled={issuingCode}
                  className="btn-primary"
                  style={{ width: "100%", minHeight: "44px" }}
                >
                  {issuingCode ? "코드 발급 중..." : "6자리 동기화 코드 발급하기"}
                </button>
              )}
            </div>

            {/* 2) 다른 기기 코드 입력 */}
            <form
              onSubmit={handleClaimPairingCode}
              style={{
                padding: "14px",
                borderRadius: "14px",
                background: "var(--surface-subtle)",
                border: "1px solid var(--card-border)",
                display: "flex",
                flexDirection: "column",
                gap: "10px",
              }}
            >
              <span style={{ fontSize: "0.88rem", fontWeight: 800, color: "var(--text-main)" }}>
                2. 다른 기기의 6자리 코드 입력
              </span>
              <div style={{ display: "flex", gap: "8px" }}>
                <input
                  type="text"
                  inputMode="numeric"
                  value={inputCode}
                  onChange={(e) => setInputCode(e.target.value.replace(/[^0-9]/g, ""))}
                  placeholder="예: 482910"
                  aria-label="6자리 동기화 코드 입력"
                  style={{
                    flex: 1,
                    minWidth: 0,
                    minHeight: "44px",
                    padding: "8px 12px",
                    borderRadius: "10px",
                    border: "1px solid var(--card-border)",
                    background: "var(--card-bg)",
                    color: "var(--text-main)",
                    fontSize: "1rem",
                    fontWeight: 800,
                    letterSpacing: "0.14em",
                    fontVariantNumeric: "tabular-nums",
                  }}
                />
                <button
                  type="submit"
                  disabled={claimingCode}
                  className="btn-primary"
                  style={{ minHeight: "44px", padding: "0 16px", whiteSpace: "nowrap" }}
                >
                  {claimingCode ? "연동 중..." : "내 기기와 합치기"}
                </button>
              </div>
            </form>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            {accountError && (
              <div
                role="alert"
                style={{
                  padding: "10px 12px",
                  borderRadius: "12px",
                  background: "var(--danger-bg)",
                  border: "1px solid var(--danger-border)",
                  color: "var(--danger-text)",
                  fontSize: "0.82rem",
                  fontWeight: 700,
                }}
              >
                {accountError}
              </div>
            )}
            {accountSuccess && (
              <div
                role="status"
                aria-live="polite"
                style={{
                  padding: "10px 12px",
                  borderRadius: "12px",
                  background: "var(--success-bg)",
                  border: "1px solid var(--success-border)",
                  color: "var(--success-text)",
                  fontSize: "0.82rem",
                  fontWeight: 700,
                }}
              >
                {accountSuccess}
              </div>
            )}

            {connectedUser && storedTokenExists ? (
              <div
                style={{
                  padding: "16px",
                  borderRadius: "14px",
                  background: "var(--surface-subtle)",
                  border: "1px solid var(--card-border)",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: "12px",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                  <UserCheck size={20} color="var(--accent)" aria-hidden="true" />
                  <div>
                    <div style={{ fontSize: "0.94rem", fontWeight: 800, color: "var(--text-main)" }}>
                      {connectedUser.nickname} ({connectedUser.username})
                    </div>
                    <div style={{ fontSize: "0.78rem", color: "var(--text-sub)", fontWeight: 600 }}>
                      현재 기기와 계정이 연결되어 있어요
                    </div>
                  </div>
                </div>
                <button
                  type="button"
                  onClick={handleLogoutAccount}
                  style={{
                    minHeight: "44px",
                    padding: "8px 12px",
                    borderRadius: "10px",
                    background: "var(--danger-bg)",
                    color: "var(--danger-text)",
                    border: "1px solid var(--danger-border)",
                    fontSize: "0.8rem",
                    fontWeight: 700,
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "4px",
                  }}
                >
                  <LogOut size={14} aria-hidden="true" />
                  <span>연결 해제</span>
                </button>
              </div>
            ) : (
              <form
                onSubmit={handleAccountSubmit}
                style={{
                  padding: "14px",
                  borderRadius: "14px",
                  background: "var(--surface-subtle)",
                  border: "1px solid var(--card-border)",
                  display: "flex",
                  flexDirection: "column",
                  gap: "10px",
                }}
              >
                <div style={{ display: "flex", gap: "6px" }}>
                  <button
                    type="button"
                    aria-pressed={accountMode === "login"}
                    onClick={() => {
                      setAccountMode("login");
                      setAccountError(null);
                    }}
                    style={{
                      flex: 1,
                      minHeight: "44px",
                      borderRadius: "10px",
                      fontSize: "0.82rem",
                      fontWeight: 700,
                      background: accountMode === "login" ? "var(--card-bg)" : "transparent",
                      color: accountMode === "login" ? "var(--accent)" : "var(--text-sub)",
                      border:
                        accountMode === "login"
                          ? "1px solid var(--accent)"
                          : "1px solid var(--card-border)",
                    }}
                  >
                    기존 계정 로그인
                  </button>
                  <button
                    type="button"
                    aria-pressed={accountMode === "signup"}
                    onClick={() => {
                      setAccountMode("signup");
                      setAccountError(null);
                    }}
                    style={{
                      flex: 1,
                      minHeight: "44px",
                      borderRadius: "10px",
                      fontSize: "0.82rem",
                      fontWeight: 700,
                      background: accountMode === "signup" ? "var(--card-bg)" : "transparent",
                      color: accountMode === "signup" ? "var(--accent)" : "var(--text-sub)",
                      border:
                        accountMode === "signup"
                          ? "1px solid var(--accent)"
                          : "1px solid var(--card-border)",
                    }}
                  >
                    새 계정 만들기
                  </button>
                </div>

                <input
                  type="text"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="아이디"
                  aria-label="계정 아이디"
                  autoComplete="username"
                  style={{
                    minHeight: "44px",
                    padding: "8px 12px",
                    borderRadius: "10px",
                    border: "1px solid var(--card-border)",
                    background: "var(--card-bg)",
                    color: "var(--text-main)",
                    fontSize: "0.9rem",
                  }}
                />
                {accountMode === "signup" && (
                  <input
                    type="text"
                    value={nickname}
                    onChange={(e) => setNickname(e.target.value)}
                    placeholder="닉네임"
                    aria-label="닉네임"
                    style={{
                      minHeight: "44px",
                      padding: "8px 12px",
                      borderRadius: "10px",
                      border: "1px solid var(--card-border)",
                      background: "var(--card-bg)",
                      color: "var(--text-main)",
                      fontSize: "0.9rem",
                    }}
                  />
                )}
                <input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="비밀번호"
                  aria-label="계정 비밀번호"
                  autoComplete={accountMode === "login" ? "current-password" : "new-password"}
                  style={{
                    minHeight: "44px",
                    padding: "8px 12px",
                    borderRadius: "10px",
                    border: "1px solid var(--card-border)",
                    background: "var(--card-bg)",
                    color: "var(--text-main)",
                    fontSize: "0.9rem",
                  }}
                />
                <button
                  type="submit"
                  disabled={accountLoading}
                  className="btn-primary"
                  style={{ width: "100%", minHeight: "44px" }}
                >
                  {accountLoading
                    ? "처리 중..."
                    : accountMode === "login"
                      ? "로그인 후 이 기기와 연결하기"
                      : "가입 후 이 기기와 연결하기"}
                </button>
              </form>
            )}
          </div>
        )}

        {onActivateGuest && (
          <button
            type="button"
            className="btn-secondary"
            onClick={() => {
              onActivateGuest();
              onClose();
            }}
            style={{ width: "100%", minHeight: "44px", justifyContent: "center" }}
          >
            이 기기에서 바로 사용하기
          </button>
        )}

        <button
          type="button"
          onClick={onClose}
          style={{
            width: "100%",
            minHeight: "44px",
            justifyContent: "center",
            padding: "10px 14px",
            borderRadius: "12px",
            background: "var(--surface-subtle)",
            border: "1px solid var(--card-border)",
            color: "var(--text-main)",
            fontWeight: 700,
            fontSize: "0.88rem",
            display: "inline-flex",
            alignItems: "center",
          }}
        >
          닫기
        </button>
      </div>
    </div>
  );
}

export default function DeviceSyncModal({
  isOpen,
  onClose,
  onActivateGuest,
  onSyncComplete,
  onShowToast,
}: DeviceSyncModalProps) {
  if (!isOpen) return null;

  return (
    <DeviceSyncModalContent
      onClose={onClose}
      onActivateGuest={onActivateGuest}
      onSyncComplete={onSyncComplete}
      onShowToast={onShowToast}
    />
  );
}
