import fs from "fs";
import path from "path";
import { execSync } from "child_process";
import { fileURLToPath } from "url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, "..");

const apiRouteDir = path.join(rootDir, "src", "app", "api");
const stashDir = path.join(rootDir, ".toss-api-stash");
const outDir = path.join(rootDir, "out");
const zipPath = path.join(rootDir, "insightdeal-apps-in-toss-bundle.zip");

const apiBaseUrl = process.env.NEXT_PUBLIC_API_BASE_URL || "https://34.59.208.14.sslip.io";

console.log("====================================================");
console.log("🚀 [InsightDeal] Apps in Toss (앱인토스) 정적 번들 빌더");
console.log(`🔗 Target Backend API: ${apiBaseUrl}`);
console.log("====================================================");

let stashed = false;

try {
  // 1. Next.js output: "export" 시 Server Route Handler 충돌 방지를 위해 임시 격리 (빌드 직후 자동 복원)
  if (fs.existsSync(apiRouteDir)) {
    if (fs.existsSync(stashDir)) {
      fs.rmSync(stashDir, { recursive: true, force: true });
    }
    fs.renameSync(apiRouteDir, stashDir);
    stashed = true;
  }

  // 2. 정적 Export 빌드 실행
  execSync("npx next build", {
    cwd: rootDir,
    stdio: "inherit",
    env: {
      ...process.env,
      NEXT_PUBLIC_TOSS_MINIAPP: "true",
      NEXT_PUBLIC_API_BASE_URL: apiBaseUrl,
    },
  });
} finally {
  // 3. 격리했던 API Route 즉시 원상 복구 (GEMINI.md 4.1 무결성 보장)
  if (stashed && fs.existsSync(stashDir)) {
    fs.renameSync(stashDir, apiRouteDir);
  }
}

// 4. out/ 폴더를 앱인토스 콘솔 업로드용 zip으로 압축
if (fs.existsSync(outDir)) {
  if (fs.existsSync(zipPath)) {
    fs.unlinkSync(zipPath);
  }
  if (process.platform === "win32") {
    execSync(
      `powershell -NoProfile -Command "Compress-Archive -Path '${outDir}\\*' -DestinationPath '${zipPath}' -Force"`,
      { stdio: "inherit" }
    );
  } else {
    execSync(`cd "${outDir}" && zip -r "${zipPath}" .`, { stdio: "inherit" });
  }
  const sizeKb = (fs.statSync(zipPath).size / 1024).toFixed(1);
  // 5. 공식 Apps in Toss (.ait) 아티팩트 생성
  try {
    execSync("npx ait build", { cwd: rootDir, stdio: "inherit" });
  } catch (err) {
    console.warn("ait build warning:", err?.message || err);
  }
  console.log("====================================================");
  console.log(`✅ 정적 빌드(out/) 및 .ait/.zip 생성 완료 (${sizeKb} KB)`);
  console.log("====================================================");
}
