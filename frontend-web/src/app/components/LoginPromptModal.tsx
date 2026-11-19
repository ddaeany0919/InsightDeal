"use client";

import React from "react";
import DeviceSyncModal from "./DeviceSyncModal";

interface LoginPromptModalProps {
  isOpen: boolean;
  onClose: () => void;
  onActivateGuest?: () => void;
  onSyncComplete?: () => Promise<void> | void;
  onShowToast?: (message: string) => void;
}

export default function LoginPromptModal({
  isOpen,
  onClose,
  onActivateGuest,
  onSyncComplete,
  onShowToast,
}: LoginPromptModalProps) {
  return (
    <DeviceSyncModal
      isOpen={isOpen}
      onClose={onClose}
      onActivateGuest={onActivateGuest}
      onSyncComplete={onSyncComplete}
      onShowToast={onShowToast}
    />
  );
}
