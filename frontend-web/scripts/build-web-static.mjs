import fs from "fs";
import path from "path";
import { execSync } from "child_process";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, "..");

const apiRouteDir = path.join(rootDir, "src", "app", "api");
const stashDir = path.join(rootDir, ".web-static-api-stash");
const outDir = path.join(rootDir, "out");

const apiBaseUrl = (
  process.env.NEXT_PUBLIC_API_BASE_URL || "https://34.59.208.14.sslip.io"
).replace(/\/+$/, "");

console.log("====================================================");
console.log("🌐 [InsightDeal] 무료 정적 웹 (GCP Caddy / Cloudflare Pages) 빌더");
console.log(`🔗 Target Backend API: ${apiBaseUrl}`);
console.log("====================================================");

let stashed = false;

try {
  const nextDevDir = path.join(rootDir, ".next", "dev");
  fs.rmSync(nextDevDir, { recursive: true, force: true });

  if (fs.existsSync(apiRouteDir)) {
    if (fs.existsSync(stashDir)) {
      fs.rmSync(stashDir, { recursive: true, force: true });
    }
    fs.renameSync(apiRouteDir, stashDir);
    stashed = true;
  } else if (fs.existsSync(stashDir)) {
    stashed = true;
  }

  execSync("npx next build --webpack", {
    cwd: rootDir,
    stdio: "inherit",
    env: {
      ...process.env,
      NEXT_PUBLIC_STATIC_EXPORT: "true",
      NEXT_PUBLIC_TOSS_MINIAPP: "false",
      NEXT_PUBLIC_API_BASE_URL: apiBaseUrl,
    },
  });
} finally {
  if (stashed && fs.existsSync(stashDir)) {
    fs.renameSync(stashDir, apiRouteDir);
  }
}

const indexHtmlPath = path.join(outDir, "index.html");
if (!fs.existsSync(indexHtmlPath)) {
  throw new Error(`정적 빌드 실패: ${indexHtmlPath}가 생성되지 않았습니다.`);
}

console.log("====================================================");
console.log(`✅ 정적 웹 빌드(out/) 완료: ${indexHtmlPath}`);
console.log("====================================================");
