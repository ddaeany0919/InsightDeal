import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

class MemoryStorage implements Storage {
  private store = new Map<string, string>();

  get length(): number {
    return this.store.size;
  }

  clear(): void {
    this.store.clear();
  }

  getItem(key: string): string | null {
    return this.store.has(key) ? (this.store.get(key) ?? null) : null;
  }

  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null;
  }

  removeItem(key: string): void {
    this.store.delete(key);
  }

  setItem(key: string, value: string): void {
    this.store.set(key, String(value));
  }
}

function ensureWebStorage(name: "localStorage" | "sessionStorage"): void {
  const current = globalThis[name];
  if (
    !current ||
    typeof current.getItem !== "function" ||
    typeof current.setItem !== "function" ||
    typeof current.clear !== "function"
  ) {
    const fallback = new MemoryStorage();
    Object.defineProperty(globalThis, name, {
      writable: true,
      configurable: true,
      value: fallback,
    });
    if (typeof window !== "undefined") {
      Object.defineProperty(window, name, {
        writable: true,
        configurable: true,
        value: fallback,
      });
    }
  }
}

ensureWebStorage("localStorage");
ensureWebStorage("sessionStorage");

class MockIntersectionObserver implements IntersectionObserver {
  readonly root: Element | Document | null = null;
  readonly rootMargin: string = "";
  readonly thresholds: ReadonlyArray<number> = [];

  disconnect(): void {}
  observe(): void {}
  takeRecords(): IntersectionObserverEntry[] {
    return [];
  }
  unobserve(): void {}
}

if (typeof window !== "undefined") {
  if (!window.IntersectionObserver) {
    Object.defineProperty(window, "IntersectionObserver", {
      writable: true,
      configurable: true,
      value: MockIntersectionObserver,
    });
  }

  if (!window.matchMedia) {
    Object.defineProperty(window, "matchMedia", {
      writable: true,
      configurable: true,
      value: (query: string): MediaQueryList => ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      }),
    });
  }

  window.scrollTo = vi.fn();
}

if (typeof Element !== "undefined" && !Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = vi.fn();
}

afterEach(() => {
  cleanup();
  globalThis.localStorage.clear();
  globalThis.sessionStorage.clear();
  vi.restoreAllMocks();
});
