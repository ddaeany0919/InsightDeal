FROM node:20-alpine

WORKDIR /app

# 의존성 정의 파일 복사
COPY package.json package-lock.json ./

# 의존성 설치
RUN npm install

# 소스 코드 복사 (볼륨 마운트와 혼용)
COPY . .

EXPOSE 3000

ENV PORT 3000
ENV HOSTNAME "0.0.0.0"

# 로컬 개발 서버 구동 (핫리로드 지원)
CMD ["npm", "run", "dev"]
