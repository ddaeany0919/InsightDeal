from fastapi import APIRouter, Body, Depends, Header, HTTPException, Query, Request
from fastapi.responses import HTMLResponse, JSONResponse
from starlette.concurrency import run_in_threadpool
from sqlalchemy.orm import Session
from typing import Optional
import hmac
import html
import os
import json
import re

from backend.database.session import get_db_session

router = APIRouter()

_LOOPBACK_HOSTS = {"127.0.0.1", "::1", "localhost", "testclient"}


def _verify_admin_key(
    request: Request,
    x_admin_key: Optional[str] = Header(default=None, alias="X-Admin-Key"),
):
    expected_key = os.getenv("ADMIN_API_KEY", "").strip()
    if expected_key:
        provided_key = (x_admin_key or "").strip()
        if not hmac.compare_digest(provided_key, expected_key):
            raise HTTPException(status_code=403, detail="관리자 인증 키가 유효하지 않습니다.")
    else:
        env = os.getenv("ENV", "development").strip().lower()
        if env in ("production", "prod") or env.startswith("prod"):
            raise HTTPException(status_code=503, detail="ADMIN_API_KEY is not configured")
        from backend.core.device_auth import extract_client_ip
        real_client_ip = extract_client_ip(request).strip().lower() if request else ""
        real_ip = (request.headers.get("x-real-ip") or "").strip().lower() if request else ""
        client_host = ((request.client.host if request and request.client else "") or "").strip().lower()
        is_loopback = (
            client_host in _LOOPBACK_HOSTS
            and real_client_ip in _LOOPBACK_HOSTS
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
    <title>얼만데 관리자</title>
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
            <h1 style="margin-bottom:0;">얼만데 관리자</h1>
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

        <div class="status-title" style="margin-bottom: 10px;">검색 결과 0건 키워드 (Top 20 · 동의어 사전 보강 후보)</div>
        <div class="progress-container" id="zero-search-box" style="margin-bottom: 20px;">
            <div style="text-align:center; color:#999; font-size:14px;">0건 검색어 집계를 불러오는 중...</div>
        </div>

        <div class="status-title" style="margin-bottom: 10px;">사용자 피드백(VOC) 수신함</div>
        <div class="progress-container" id="voc-feedback-box" style="margin-bottom: 20px;">
            <div style="text-align:center; color:#999; font-size:14px;">사용자 피드백을 불러오는 중...</div>
        </div>

        <div class="status-title" style="margin-bottom: 10px;">상세 실시간 로그</div>
        <div class="log-container" id="log-box">로그를 불러오는 중...</div>
    </div>

    <script>
        const communities = ["알리뽐뿌", "펨코", "퀘이사존", "루리웹", "클리앙", "뽐뿌", "빠삭국내", "빠삭해외", "빠삭육아"];

        function getAdminKey() {
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
            container.textContent = '';

            communities.forEach(comm => {
                const data = statusData[comm] || { status: '대기 중', time: '-', count: '' };

                let barClass = '';
                let statusText = data.status || '대기 중';
                if (data.status === '업데이트 완료') {
                    barClass = 'done';
                    statusText = `${data.time || '-'} 완료 (${data.count || ''})`;
                } else if (data.status === '진행 중') {
                    barClass = 'running';
                    statusText = `수집 중 (시작: ${data.time || '-'})`;
                }

                const item = document.createElement('div');
                item.className = 'progress-item';

                const nameEl = document.createElement('div');
                nameEl.className = 'progress-name';
                nameEl.textContent = String(comm);

                const barBg = document.createElement('div');
                barBg.className = 'progress-bar-bg';

                const barFill = document.createElement('div');
                barFill.className = barClass ? `progress-bar-fill ${barClass}` : 'progress-bar-fill';
                barBg.appendChild(barFill);

                const infoEl = document.createElement('div');
                infoEl.className = 'progress-info';
                infoEl.textContent = String(statusText);

                item.appendChild(nameEl);
                item.appendChild(barBg);
                item.appendChild(infoEl);
                container.appendChild(item);
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
                const cov = d.db_coverage || {};
                const cs = d.click_stats || {};

                box.textContent = '';

                const summaryDiv = document.createElement('div');
                summaryDiv.style.cssText = 'display:flex;justify-content:space-between;background:#f0f7ff;padding:10px 14px;border-radius:6px;margin-bottom:10px;font-size:13px;';

                const leftSpan = document.createElement('span');
                leftSpan.appendChild(document.createTextNode(`최근 ${Number(cov.sampled_deals || 0)}개 딜 중 제휴 전환 가능 비율: `));
                const ratioStrong = document.createElement('strong');
                ratioStrong.style.color = '#007bff';
                ratioStrong.textContent = `${Number(cov.monetizable_ratio_pct || 0)}% (${Number(cov.monetizable_deals || 0)}건)`;
                leftSpan.appendChild(ratioStrong);

                const rightSpan = document.createElement('span');
                rightSpan.appendChild(document.createTextNode('누적 구매 클릭: '));
                const clicksStrong = document.createElement('strong');
                clicksStrong.textContent = `${Number(cs.total_clicks || 0)}회`;
                rightSpan.appendChild(clicksStrong);
                rightSpan.appendChild(document.createTextNode(' (예상 누적 수수료: '));
                const commStrong = document.createElement('strong');
                commStrong.style.color = '#16a34a';
                commStrong.textContent = `₩${Number(cs.estimated_commission_krw || 0).toLocaleString()}`;
                rightSpan.appendChild(commStrong);
                rightSpan.appendChild(document.createTextNode(')'));

                summaryDiv.appendChild(leftSpan);
                summaryDiv.appendChild(rightSpan);
                box.appendChild(summaryDiv);

                Object.values(d.networks).forEach(n => {
                    const row = document.createElement('div');
                    row.className = 'progress-item';

                    const left = document.createElement('div');
                    left.style.cssText = 'flex:1;font-weight:bold;color:#333;';
                    left.textContent = `${n.name || ''} `;

                    const rateSpan = document.createElement('span');
                    rateSpan.style.cssText = 'font-size:12px;color:#666;font-weight:normal;';
                    rateSpan.textContent = `(수수료 ${n.commission_rate || ''})`;
                    left.appendChild(rateSpan);

                    const right = document.createElement('div');
                    const badge = document.createElement('span');
                    badge.style.fontWeight = 'bold';
                    if (n.configured) {
                        badge.style.color = '#16a34a';
                        badge.textContent = '활성';
                    } else {
                        badge.style.color = '#d97706';
                        badge.textContent = `미설정 (${n.env_key || ''})`;
                    }
                    right.appendChild(badge);

                    row.appendChild(left);
                    row.appendChild(right);
                    box.appendChild(row);
                });
            } catch (e) {}
        }

        async function fetchZeroSearchQueries() {
            const box = document.getElementById('zero-search-box');
            if (!box) return;
            try {
                const res = await fetch('/admin/zero-search-queries?limit=20&t=' + Date.now(), {
                    headers: getAdminHeaders()
                });
                if (!res.ok) return;
                const d = await res.json();
                const items = Array.isArray(d.items) ? d.items : [];
                box.textContent = '';
                if (!items.length) {
                    const emptyEl = document.createElement('div');
                    emptyEl.style.cssText = 'text-align:center;color:#999;font-size:13px;';
                    emptyEl.textContent = '아직 누적된 0건 검색어가 없습니다.';
                    box.appendChild(emptyEl);
                    return;
                }
                items.forEach(item => {
                    const row = document.createElement('div');
                    row.className = 'progress-item';
                    const left = document.createElement('div');
                    left.style.cssText = 'flex:1;font-weight:bold;color:#333;';
                    left.textContent = String(item.query || '');
                    const right = document.createElement('div');
                    right.style.cssText = 'font-size:12px;color:#666;';
                    right.textContent = `${Number(item.hit_count || 0)}회${item.last_category ? ' · ' + String(item.last_category) : ''}`;
                    row.appendChild(left);
                    row.appendChild(right);
                    box.appendChild(row);
                });
            } catch (e) {}
        }

        async function resolveFeedback(feedbackId, nextStatus) {
            try {
                const res = await fetch(`/admin/feedback/${Number(feedbackId)}`, {
                    method: 'PATCH',
                    headers: { 'Content-Type': 'application/json', ...getAdminHeaders() },
                    body: JSON.stringify({ status: nextStatus })
                });
                if (res.ok) {
                    fetchFeedbacks();
                }
            } catch (e) {}
        }

        async function fetchFeedbacks() {
            const box = document.getElementById('voc-feedback-box');
            if (!box) return;
            try {
                const res = await fetch('/admin/feedback?status=all&limit=20&t=' + Date.now(), {
                    headers: getAdminHeaders()
                });
                if (!res.ok) return;
                const d = await res.json();
                const items = Array.isArray(d.items) ? d.items : [];
                box.textContent = '';
                if (!items.length) {
                    const emptyEl = document.createElement('div');
                    emptyEl.style.cssText = 'text-align:center;color:#999;font-size:13px;';
                    emptyEl.textContent = '접수된 사용자 피드백이 없습니다.';
                    box.appendChild(emptyEl);
                    return;
                }
                items.forEach(item => {
                    const row = document.createElement('div');
                    row.className = 'progress-item';
                    const left = document.createElement('div');
                    left.style.cssText = 'flex:1;font-size:13px;color:#333;';
                    const meta = document.createElement('div');
                    meta.style.cssText = 'font-weight:bold;margin-bottom:4px;';
                    meta.textContent = `[${String(item.category || 'general')} · ${String(item.platform || 'web')}] ${item.contact ? String(item.contact) : '익명'}`;
                    const msg = document.createElement('div');
                    msg.style.cssText = 'color:#555;white-space:pre-wrap;word-break:break-word;';
                    msg.textContent = String(item.message || '');
                    left.appendChild(meta);
                    left.appendChild(msg);

                    const right = document.createElement('div');
                    right.style.cssText = 'margin-left:12px;';
                    const btn = document.createElement('button');
                    btn.className = 'btn';
                    btn.style.cssText = 'padding:6px 10px;font-size:12px;';
                    const isResolved = item.status === 'resolved';
                    btn.textContent = isResolved ? '완료됨 (재오픈)' : '처리완료';
                    btn.onclick = () => resolveFeedback(item.id, isResolved ? 'open' : 'resolved');
                    right.appendChild(btn);

                    row.appendChild(left);
                    row.appendChild(right);
                    box.appendChild(row);
                });
            } catch (e) {}
        }

        fetchLogs();
        fetchMonetizationStats();
        fetchZeroSearchQueries();
        fetchFeedbacks();
        setInterval(fetchLogs, 2000);
        setInterval(fetchMonetizationStats, 10000);
        setInterval(fetchZeroSearchQueries, 15000);
        setInterval(fetchFeedbacks, 15000);
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
            
    return HTMLResponse(content=HTML_TEMPLATE.replace("{last_updated}", html.escape(str(last_updated))))

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
    result = await run_in_threadpool(db_manager.perform_maintenance_and_backup, max_backups=3, slim_days=180)
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


@router.get("/zero-search-queries", dependencies=[Depends(_verify_admin_key)])
def get_zero_search_queries(
    limit: int = Query(default=50, ge=1, le=200),
    db: Session = Depends(get_db_session),
):
    from backend.database import models
    from backend.core.utils import to_utc_iso

    rows = (
        db.query(models.ZeroSearchQuery)
        .order_by(models.ZeroSearchQuery.hit_count.desc(), models.ZeroSearchQuery.last_searched_at.desc())
        .limit(limit)
        .all()
    )
    items = [
        {
            "id": r.id,
            "query": r.query,
            "hit_count": int(r.hit_count or 0),
            "last_category": r.last_category,
            "last_platform": r.last_platform,
            "last_searched_at": to_utc_iso(r.last_searched_at),
            "created_at": to_utc_iso(r.created_at),
        }
        for r in rows
    ]
    return {"items": items, "total": len(items)}


@router.get("/feedback", dependencies=[Depends(_verify_admin_key)])
def get_user_feedback_list(
    status: str = Query(default="open", description="open | resolved | all"),
    limit: int = Query(default=50, ge=1, le=200),
    db: Session = Depends(get_db_session),
):
    from backend.database import models
    from backend.core.utils import to_utc_iso

    norm_status = (status or "open").strip().lower()
    if norm_status not in ("open", "resolved", "all"):
        raise HTTPException(status_code=400, detail="Invalid status filter (open | resolved | all)")

    q = db.query(models.UserFeedback)
    if norm_status != "all":
        q = q.filter(models.UserFeedback.status == norm_status)
    rows = q.order_by(models.UserFeedback.created_at.desc(), models.UserFeedback.id.desc()).limit(limit).all()
    items = [
        {
            "id": r.id,
            "device_uuid": r.device_uuid,
            "category": r.category,
            "message": r.message,
            "contact": r.contact,
            "platform": r.platform,
            "app_version": r.app_version,
            "status": r.status,
            "created_at": to_utc_iso(r.created_at),
        }
        for r in rows
    ]
    return {"items": items, "total": len(items)}


@router.patch("/feedback/{feedback_id}", dependencies=[Depends(_verify_admin_key)])
def update_user_feedback_status(
    feedback_id: int,
    payload: dict,
    db: Session = Depends(get_db_session),
):
    from backend.database import models

    next_status = str((payload or {}).get("status") or "").strip().lower()
    if next_status not in ("open", "resolved"):
        raise HTTPException(status_code=400, detail="status must be 'open' or 'resolved'")

    row = db.query(models.UserFeedback).filter(models.UserFeedback.id == feedback_id).first()
    if not row:
        raise HTTPException(status_code=404, detail="Feedback not found")

    row.status = next_status
    db.commit()
    return {"success": True, "id": row.id, "status": row.status}


@router.get("/toss-push/status", dependencies=[Depends(_verify_admin_key)])
def get_toss_push_admin_status(db: Session = Depends(get_db_session)):
    from backend.database import models
    from backend.services.toss_push_service import get_toss_push_status

    registered_count = (
        db.query(models.DeviceToken)
        .filter(
            models.DeviceToken.is_active == True,
            models.DeviceToken.toss_anon_key.isnot(None),
            models.DeviceToken.toss_anon_key != "",
        )
        .count()
    )
    return {
        **get_toss_push_status(),
        "registered_toss_devices": int(registered_count),
    }


@router.get("/telegram/status", dependencies=[Depends(_verify_admin_key)])
def get_telegram_broadcast_status():
    from backend.services.telegram_channel import get_telegram_status

    return get_telegram_status()


@router.post("/telegram/test", dependencies=[Depends(_verify_admin_key)])
def test_telegram_broadcast(
    payload: Optional[dict] = Body(default=None),
    db: Session = Depends(get_db_session),
):
    from backend.database import models
    from backend.services.telegram_channel import send_telegram_deal

    data = payload or {}
    dry_run = bool(data.get("dry_run", False))
    deal_id = data.get("deal_id")
    deal = None

    if deal_id is not None:
        try:
            deal = db.query(models.Deal).filter(models.Deal.id == int(deal_id)).first()
        except (TypeError, ValueError):
            raise HTTPException(status_code=400, detail="Invalid deal_id")
    else:
        deal = (
            db.query(models.Deal)
            .filter(models.Deal.is_closed == False, models.Deal.is_super_hotdeal == True)
            .order_by(models.Deal.indexed_at.desc(), models.Deal.id.desc())
            .first()
        )
        if not deal:
            deal = (
                db.query(models.Deal)
                .filter(models.Deal.is_closed == False)
                .order_by(models.Deal.indexed_at.desc(), models.Deal.id.desc())
                .first()
            )

    if not deal:
        raise HTTPException(status_code=404, detail="Deal not found")

    res = send_telegram_deal(deal, dry_run=dry_run, force=True)
    return {"deal_id": deal.id, "title": deal.title, **res}

