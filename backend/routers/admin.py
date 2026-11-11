from fastapi import APIRouter, Depends, Header, HTTPException, Query, Request
from fastapi.responses import HTMLResponse, JSONResponse
from sqlalchemy.orm import Session
from typing import Optional
import hmac
import os
import json
import re

from backend.database.session import get_db_session

router = APIRouter()

_LOOPBACK_HOSTS = {"127.0.0.1", "::1", "localhost", "testclient"}


def _verify_admin_key(
    request: Request,
    x_admin_key: Optional[str] = Header(default=None, alias="X-Admin-Key"),
    api_key: Optional[str] = Query(default=None),
):
    expected_key = os.getenv("ADMIN_API_KEY", "").strip()
    if expected_key:
        provided_key = (x_admin_key or api_key or "").strip()
        if not hmac.compare_digest(provided_key, expected_key):
            raise HTTPException(status_code=403, detail="관리자 인증 키가 유효하지 않습니다.")
    else:
        forwarded_for = (request.headers.get("x-forwarded-for") or "").split(",")[0].strip().lower() if request else ""
        real_ip = (request.headers.get("x-real-ip") or "").strip().lower() if request else ""
        client_host = ((request.client.host if request and request.client else "") or "").strip().lower()
        is_loopback = (
            client_host in _LOOPBACK_HOSTS
            and (not forwarded_for or forwarded_for in _LOOPBACK_HOSTS)
            and (not real_ip or real_ip in _LOOPBACK_HOSTS)
        )
        if not is_loopback:
            raise HTTPException(status_code=503, detail="ADMIN_API_KEY is not configured")


HTML_TEMPLATE = """
<!DOCTYPE html>
<html lang="ko">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>InsightDeal 관리자</title>
    <style>
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f4f7f6; margin: 0; padding: 20px; display: flex; flex-direction: column; align-items: center; }
        .container { background-color: white; padding: 30px; border-radius: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); width: 100%; max-width: 800px; }
        h1 { color: #333; margin-top: 0; font-size: 24px; }
        p { color: #666; margin-bottom: 20px; font-size: 15px; }
        .status-box { background-color: #eef2f5; padding: 15px; border-radius: 8px; margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; }
        .status-title { font-size: 14px; color: #666; font-weight: bold; }
        .status-time { font-size: 18px; color: #007bff; font-weight: bold; }
        .btn { background-color: #007bff; color: white; border: none; padding: 12px 24px; font-size: 15px; border-radius: 6px; cursor: pointer; transition: background-color 0.3s; font-weight: bold; }
        .btn:hover { background-color: #0056b3; }
        .btn:disabled { background-color: #ccc; cursor: not-allowed; }
        .key-input { border: 1px solid #d0d7de; border-radius: 6px; padding: 8px 10px; font-size: 13px; width: 200px; }
        
        .progress-container { margin-bottom: 20px; border: 1px solid #e0e0e0; border-radius: 8px; padding: 15px; background-color: #fafafa; }
        .progress-item { display: flex; justify-content: space-between; align-items: center; padding: 10px 0; border-bottom: 1px solid #eee; }
        .progress-item:last-child { border-bottom: none; padding-bottom: 0; }
        .progress-name { font-weight: bold; width: 100px; color: #444; }
        .progress-bar-bg { flex-grow: 1; height: 10px; background-color: #e0e0e0; border-radius: 5px; margin: 0 15px; overflow: hidden; position: relative; }
        .progress-bar-fill { height: 100%; background-color: #007bff; width: 0%; transition: width 0.5s ease; }
        .progress-bar-fill.done { background-color: #28a745; width: 100%; }
        .progress-bar-fill.running { width: 50%; background-color: #ffc107; animation: pulse 1.5s infinite; }
        @keyframes pulse { 0% { opacity: 0.6; } 50% { opacity: 1; } 100% { opacity: 0.6; } }
        .progress-info { font-size: 13px; color: #666; width: 280px; text-align: right; }
        
        .log-container { background-color: #1e1e1e; color: #00ff00; padding: 15px; border-radius: 8px; font-family: 'Courier New', Courier, monospace; font-size: 13px; height: 250px; overflow-y: auto; margin-top: 10px; white-space: pre-wrap; word-wrap: break-word; }
    </style>
</head>
<body>
    <div class="container">
        <div style="display:flex; justify-content:space-between; align-items:center; flex-wrap:wrap; gap:8px;">
            <h1 style="margin-bottom:0;">InsightDeal 관리자</h1>
            <input type="password" id="admin-key-input" class="key-input" placeholder="Admin API Key (선택)" onchange="saveAdminKey(this.value)" />
        </div>
        <p>전체 스크래퍼 실행 및 진행 상태를 확인합니다.</p>
        
        <div class="status-box">
            <div>
                <div class="status-title">마지막 데이터 업데이트 완료</div>
                <div class="status-time" id="last-update">{last_updated}</div>
            </div>
            <button class="btn" id="update-btn" onclick="triggerUpdate()">스크래퍼 전체 실행</button>
        </div>

        <div class="status-title" style="margin-bottom: 10px;">제휴 수익화 및 아웃링크 클릭 현황</div>
        <div class="progress-container" id="monetization-box" style="margin-bottom: 20px;">
            <div style="text-align:center; color:#999; font-size:14px;">수익화 통계를 불러오는 중...</div>
        </div>

        <div class="status-title" style="margin-bottom: 10px;">커뮤니티별 업데이트 현황</div>
        <div class="progress-container" id="progress-list">
            <div style="text-align:center; color:#999; font-size:14px;">데이터를 불러오는 중...</div>
        </div>

        <div class="status-title" style="margin-bottom: 10px;">상세 실시간 로그</div>
        <div class="log-container" id="log-box">로그를 불러오는 중...</div>
    </div>

    <script>
        const communities = ["알리뽐뿌", "펨코", "퀘이사존", "루리웹", "클리앙", "뽐뿌", "빠삭국내", "빠삭해외", "빠삭육아"];

        function getAdminKey() {
            const urlKey = new URLSearchParams(window.location.search).get('api_key');
            if (urlKey) {
                sessionStorage.setItem('admin_api_key', urlKey);
                return urlKey;
            }
            const inputEl = document.getElementById('admin-key-input');
            if (inputEl && inputEl.value.trim()) {
                return inputEl.value.trim();
            }
            return sessionStorage.getItem('admin_api_key') || '';
        }

        function saveAdminKey(val) {
            const trimmed = (val || '').trim();
            if (trimmed) {
                sessionStorage.setItem('admin_api_key', trimmed);
            } else {
                sessionStorage.removeItem('admin_api_key');
            }
            fetchLogs();
        }

        function getAdminHeaders() {
            const key = getAdminKey();
            return key ? { 'X-Admin-Key': key } : {};
        }

        (function initKeyInput() {
            const existing = getAdminKey();
            const inputEl = document.getElementById('admin-key-input');
            if (inputEl && existing) {
                inputEl.value = existing;
            }
        })();
        
        function renderProgress(statusData) {
            const container = document.getElementById('progress-list');
            container.innerHTML = '';
            
            communities.forEach(comm => {
                const data = statusData[comm] || { status: '대기 중', time: '-', count: '' };
                
                let barClass = '';
                let statusText = data.status;
                if (data.status === '업데이트 완료') {
                    barClass = 'done';
                    statusText = `${data.time} 완료 (${data.count})`;
                } else if (data.status === '진행 중') {
                    barClass = 'running';
                    statusText = `수집 중 (시작: ${data.time})`;
                }

                const itemHTML = `
                    <div class="progress-item">
                        <div class="progress-name">${comm}</div>
                        <div class="progress-bar-bg">
                            <div class="progress-bar-fill ${barClass}"></div>
                        </div>
                        <div class="progress-info">${statusText}</div>
                    </div>
                `;
                container.innerHTML += itemHTML;
            });
        }

        async function triggerUpdate() {
            const btn = document.getElementById('update-btn');
            btn.disabled = true;
            btn.innerText = "업데이트 실행 중...";
            document.getElementById('log-box').innerText = `스크래퍼 작업 시작 대기 중...\\n`;
            
            try {
                const response = await fetch('/admin/trigger-update', {
                    method: 'POST',
                    headers: getAdminHeaders()
                });
                if (!response.ok) {
                    const result = await response.json();
                    alert('오류 발생: ' + result.detail);
                    btn.disabled = false;
                    btn.innerText = "스크래퍼 전체 실행";
                }
            } catch (error) {
                alert('요청 중 오류가 발생했습니다.');
                btn.disabled = false;
                btn.innerText = "스크래퍼 전체 실행";
            }
        }

        async function fetchLogs() {
            try {
                const response = await fetch('/admin/logs?t=' + Date.now(), {
                    headers: getAdminHeaders()
                });
                if (!response.ok) {
                    throw new Error(`서버 응답 오류 (상태 코드: ${response.status})`);
                }
                const data = await response.json();
                
                renderProgress(data.community_status || {});
                
                const logBox = document.getElementById('log-box');
                const isScrolledToBottom = logBox.scrollHeight - logBox.clientHeight <= logBox.scrollTop + 10;
                
                logBox.innerText = data.logs || "아직 기록된 로그가 없습니다.";
                
                if (data.is_running) {
                    document.getElementById('update-btn').disabled = true;
                    document.getElementById('update-btn').innerText = "업데이트 실행 중 (상단 현황 확인)";
                } else {
                    document.getElementById('update-btn').disabled = false;
                    document.getElementById('update-btn').innerText = "스크래퍼 전체 실행";
                }
                
                if (data.last_updated && data.last_updated !== "기록 없음") {
                    document.getElementById('last-update').innerText = data.last_updated;
                }

                if (isScrolledToBottom) {
                    logBox.scrollTop = logBox.scrollHeight;
                }
            } catch (e) {
                const logBox = document.getElementById('log-box');
                logBox.innerText = `로그를 가져오는 데 실패했습니다: ${e.message}\\n\\n(인증 키를 확인하거나 잠시 후 다시 시도해주세요.)`;
                console.error("로그 조회 실패", e);
            }
        }

        async function fetchMonetizationStats() {
            try {
                const res = await fetch('/api/coupang/stats?t=' + Date.now());
                if (!res.ok) return;
                const d = await res.json();
                const box = document.getElementById('monetization-box');
                if (!box || !d.networks) return;
                const nets = Object.values(d.networks).map(n => {
                    const badge = n.configured
                        ? '<span style="color:#16a34a;font-weight:bold;">활성</span>'
                        : '<span style="color:#d97706;font-weight:bold;">미설정 (' + n.env_key + ')</span>';
                    return `<div class="progress-item"><div style="flex:1;font-weight:bold;color:#333;">${n.name} <span style="font-size:12px;color:#666;font-weight:normal;">(수수료 ${n.commission_rate})</span></div><div>${badge}</div></div>`;
                }).join('');
                const cov = d.db_coverage || {};
                const cs = d.click_stats || {};
                box.innerHTML = `
                    <div style="display:flex;justify-content:space-between;background:#f0f7ff;padding:10px 14px;border-radius:6px;margin-bottom:10px;font-size:13px;">
                        <span>최근 ${cov.sampled_deals || 0}개 딜 중 제휴 전환 가능 비율: <strong style="color:#007bff;">${cov.monetizable_ratio_pct || 0}% (${cov.monetizable_deals || 0}건)</strong></span>
                        <span>누적 구매 클릭: <strong>${cs.total_clicks || 0}회</strong> (예상 누적 수수료: <strong style="color:#16a34a;">₩${(cs.estimated_commission_krw || 0).toLocaleString()}</strong>)</span>
                    </div>
                    ${nets}
                `;
            } catch (e) {}
        }

        fetchLogs();
        fetchMonetizationStats();
        setInterval(fetchLogs, 2000);
        setInterval(fetchMonetizationStats, 10000);
    </script>
</body>
</html>
"""

@router.get("/", response_class=HTMLResponse, dependencies=[Depends(_verify_admin_key)])
async def get_admin_dashboard():
    last_updated = "기록 없음"
    log_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), "logs", "last_update.json")
    if os.path.exists(log_file):
        try:
            with open(log_file, "r", encoding="utf-8") as f:
                data = json.load(f)
                last_updated = data.get("last_updated", "기록 없음")
        except Exception:
            pass
            
    return HTMLResponse(content=HTML_TEMPLATE.replace("{last_updated}", last_updated))

@router.get("/logs", dependencies=[Depends(_verify_admin_key)])
def get_logs():
    log_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "logs")
    scraper_log = os.path.join(log_dir, "scraper_output.log")
    last_update_json = os.path.join(log_dir, "last_update.json")
    
    logs = ""
    community_status = {}
    
    if os.path.exists(scraper_log):
        try:
            with open(scraper_log, "r", encoding="utf-8") as f:
                all_lines = f.readlines()
                
                for line in all_lines:
                    match_start = re.search(r'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}).*?▶ \[([^\]]+)\] 큐 기반 스크래핑', line)
                    if match_start:
                        time_str = match_start.group(1)
                        comm = match_start.group(2)
                        community_status[comm] = {"status": "진행 중", "time": time_str, "count": ""}
                    
                    match_succ = re.search(r'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}).*?✅ \[([^\]]+)\] 스크래핑 성공 \((.*)\)', line)
                    if match_succ:
                        time_str = match_succ.group(1)
                        comm = match_succ.group(2)
                        count_str = match_succ.group(3)
                        community_status[comm] = {"status": "업데이트 완료", "time": time_str, "count": count_str}
                
                import time
                try:
                    mtime = os.path.getmtime(scraper_log)
                    if time.time() - mtime >= 300:
                        for c in community_status:
                            if community_status[c]["status"] == "진행 중":
                                community_status[c]["status"] = "비정상 종료"
                except Exception:
                    pass
                
                logs = "".join(all_lines[-200:])
        except Exception:
            logs = "로그 파일을 읽을 수 없습니다."
            
    last_updated = "기록 없음"
    if os.path.exists(last_update_json):
        try:
            with open(last_update_json, "r", encoding="utf-8") as f:
                data = json.load(f)
                last_updated = data.get("last_updated", "기록 없음")
        except Exception:
            pass
            
    import time
    is_running = False
    if logs and "모든 커뮤니티 데이터 파싱 및 업데이트 완료" not in logs and "AI Batch 요약 처리 완료" not in logs:
        try:
            mtime = os.path.getmtime(scraper_log)
            if time.time() - mtime < 300:
                is_running = True
            else:
                is_running = False
        except Exception:
            is_running = False
        
    if logs and ("AI Batch 요약 처리 완료" in logs or "모든 커뮤니티 데이터 파싱 및 업데이트 완료" in logs):
        is_running = False

    return JSONResponse(content={"logs": logs, "last_updated": last_updated, "is_running": is_running, "community_status": community_status})

_LAST_ADMIN_TRIGGER_TS: float = 0.0

@router.post("/backup-db", dependencies=[Depends(_verify_admin_key)])
async def trigger_db_backup():
    from backend.database.session import db_manager
    result = db_manager.perform_maintenance_and_backup(max_backups=3, slim_days=180)
    return {"status": "ok", **result}


@router.post("/renormalize-deals", dependencies=[Depends(_verify_admin_key)])
def renormalize_deals_endpoint(
    dry_run: bool = Query(default=False),
    db: Session = Depends(get_db_session),
):
    from backend.core.deal_normalizer import renormalize_existing_deals
    from backend.routers.community import invalidate_feed_api_cache

    stats = renormalize_existing_deals(db, dry_run=dry_run)
    if not dry_run and stats.get("updated", 0) > 0:
        invalidate_feed_api_cache()
    return {"status": "ok", "stats": stats, **stats}

@router.post("/trigger-update", dependencies=[Depends(_verify_admin_key)])
async def trigger_update_script():
    global _LAST_ADMIN_TRIGGER_TS
    import subprocess
    import sys
    import time
    
    now = time.time()
    if now - _LAST_ADMIN_TRIGGER_TS < 60:
        return {"message": "이미 최근 1분 내에 스크래퍼 작업이 시작되었습니다. 잠시 후 다시 시도해주세요."}
    _LAST_ADMIN_TRIGGER_TS = now

    root_dir = os.path.dirname(os.path.dirname(os.path.dirname(__file__)))
    script_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "scheduler", "main.py")
    log_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "logs")
    os.makedirs(log_dir, exist_ok=True)
    scraper_log = os.path.join(log_dir, "scraper_output.log")
    
    try:
        python_executable = sys.executable
        env = dict(os.environ, PYTHONUTF8="1", PYTHONUNBUFFERED="1")
        
        with open(scraper_log, "w", encoding="utf-8") as log_file_handle:
            log_file_handle.write("스크래퍼 실행이 예약되었습니다. 곧 로그가 표시됩니다...\n")
            log_file_handle.flush()
            subprocess.Popen(
                [python_executable, script_path, "--one-shot"], 
                cwd=root_dir,
                stdout=log_file_handle, 
                stderr=subprocess.STDOUT,
                env=env
            )
        return {"message": "스크래퍼 작업이 시작되었습니다."}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
