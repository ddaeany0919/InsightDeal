#!/usr/bin/env bash
# ==============================================================================
# 🚀 InsightDeal — Oracle Cloud Always Free (평생 무료 티어) 원클릭 구축 스크립트
# ==============================================================================
# 사용법 (오라클 클라우드 Ubuntu 서버 접속 후 실행):
#   git clone https://github.com/ddaeany0919/InsightDeal.git
#   cd InsightDeal
#   sudo bash deploy/oracle_free_setup.sh
# ==============================================================================

set -e

PUBLIC_IP="${1:-$(curl -s ifconfig.me)}"
DOMAIN="${2:-${PUBLIC_IP}.sslip.io}"
APP_DIR="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND_DIR="${APP_DIR}/backend"

echo "=================================================================="
echo "🚀 InsightDeal 오라클 클라우드 평생 무료 서버 자동 구축 시작"
echo "📌 감지된 공인 IP : ${PUBLIC_IP}"
echo "🔒 무료 HTTPS 주소: https://${DOMAIN}"
echo "📂 프로젝트 경로  : ${APP_DIR}"
echo "=================================================================="

# 1. 메모리 부족 방지용 2GB Swap 메모리 자동 생성 (1GB RAM 마이크로 인스턴스 보호)
if [ ! -f /swapfile ]; then
    echo "💾 [1/5] 2GB Swap 메모리 생성 중..."
    fallocate -l 2G /swapfile || dd if=/dev/zero of=/swapfile bs=1M count=2048
    chmod 600 /swapfile
    mkswap /swapfile
    swapon /swapfile
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
else
    echo "💾 [1/5] 기존 Swap 메모리 확인 완료"
fi

# 2. 오라클 클라우드 Ubuntu 기본 iptables 방화벽 포트(80, 443, 8080) 개방
echo "🛡️ [2/5] 오라클 클라우드 Ubuntu 내부 방화벽(iptables) 80/443/8080 포트 개방 중..."
iptables -I INPUT 6 -m state --state NEW -p tcp --dport 80 -j ACCEPT || true
iptables -I INPUT 6 -m state --state NEW -p tcp --dport 443 -j ACCEPT || true
iptables -I INPUT 6 -m state --state NEW -p tcp --dport 8080 -j ACCEPT || true
apt-get update -y
DEBIAN_FRONTEND=noninteractive apt-get install -y iptables-persistent python3-venv python3-pip curl debian-keyring debian-archive-keyring apt-transport-https
netfilter-persistent save || true

# 3. Python 가상환경 및 백엔드 패키지 설치
echo "🐍 [3/5] Python 가상환경 및 FastAPI 의존성 설치 중..."
cd "${BACKEND_DIR}"
python3 -m venv venv
./venv/bin/pip install --upgrade pip
./venv/bin/pip install -r requirements.txt

# 4. Systemd 24시간 무중단 백엔드 + 크롤러 서비스 등록 (포트 8080)
echo "⚙️ [4/5] 24시간 자동 재시작 Systemd 데몬(insightdeal-backend) 등록 중..."
cat >/etc/systemd/system/insightdeal-backend.service <<EOF
[Unit]
Description=InsightDeal FastAPI Backend & HotDeal Scraper (Port 8080)
After=network.target

[Service]
Type=simple
User=root
WorkingDirectory=${BACKEND_DIR}
Environment="PYTHONPATH=${APP_DIR}:${BACKEND_DIR}"
Environment="ENABLE_SCHEDULER=true"
Environment="HEADLESS=true"
ExecStart=${BACKEND_DIR}/venv/bin/python -m uvicorn main:app --host 0.0.0.0 --port 8080
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable insightdeal-backend
systemctl restart insightdeal-backend

# 5. Caddy 웹서버 설치 및 자동 무료 HTTPS(Let's Encrypt) 리버스 프록시 적용
echo "🔒 [5/5] Caddy 설치 및 무료 HTTPS(https://${DOMAIN}) 인증서 자동 발급 설정 중..."
if ! command -v caddy >/dev/null 2>&1; then
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg --yes
    curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | tee /etc/apt/sources.list.d/caddy-stable.list
    apt-get update -y
    apt-get install -y caddy
fi

cat >/etc/caddy/Caddyfile <<EOF
${DOMAIN} {
    reverse_proxy localhost:8080
}
EOF

systemctl enable caddy
systemctl restart caddy

echo "=================================================================="
echo "🎉 구축 완료! 아래 주소에서 백엔드 상태를 확인할 수 있습니다:"
echo "   👉 HTTP  직접 연결: http://${PUBLIC_IP}:8080/api/health"
echo "   👉 HTTPS 보안 연결: https://${DOMAIN}/api/health"
echo ""
echo "💡 앱인토스(Apps in Toss) 프론트엔드 빌드 시 사용할 환경변수:"
echo "   NEXT_PUBLIC_API_BASE_URL=https://${DOMAIN}"
echo "=================================================================="
