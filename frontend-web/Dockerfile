# 1. 의존성 캐시 및 빌드에 적합한 Node.js 경량 이미지 사용
FROM node:20-alpine AS deps
RUN apk add --no-cache libc6-compat
WORKDIR /app

# package.json 및 lock 파일 복사
COPY package.json package-lock.json ./
RUN npm ci

# 2. 소스 컴파일 및 빌드
FROM node:20-alpine AS builder
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# Next.js 텔레메트리 전송 해제 (빌드 속도 개선)
ENV NEXT_TELEMETRY_DISABLED 1

# Next.js standalone 빌드 수행
RUN npm run build

# 3. 실제 프로덕션 구동용 초경량 이미지
FROM node:20-alpine AS runner
WORKDIR /app

ENV NODE_ENV production
ENV NEXT_TELEMETRY_DISABLED 1

# 보안 그룹 및 사용자 생성
RUN addgroup --system --gid 1001 nodejs
RUN adduser --system --uid 1002 nextjs

COPY --from=builder /app/public ./public

# standalone 결과물 복사 및 소유권 지정 (1/10 크기로 축소)
COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000
ENV PORT 3000
ENV HOSTNAME "0.0.0.0"

# standalone 모드에서는 server.js를 직접 구동하여 불필요한 dev 서버 메모리 낭비를 제거합니다.
CMD ["node", "server.js"]
