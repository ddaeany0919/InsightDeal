from fastapi import APIRouter, Request
from fastapi.responses import HTMLResponse, JSONResponse
import os
import json
import re

router = APIRouter()

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
        .status-box { background-color: #eef2f5; padding: 15px; border-radius: 8px; margin-bottom: 20px; display: flex; justify-content: space-between; align-items: center; }
        .status-title { font-size: 14px; color: #666; font-weight: bold; }
        .status-time { font-size: 18px; color: #007bff; font-weight: bold; }
        .btn { background-color: #007bff; color: white; border: none; padding: 12px 24px; font-size: 15px; border-radius: 6px; cursor: pointer; transition: background-color 0.3s; font-weight: bold; }
        .btn:hover { background-color: #0056b3; }
        .btn:disabled { background-color: #ccc; cursor: not-allowed; }
        
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
        <h1>🛠️ InsightDeal 관리자</h1>
        <p>전체 스크래퍼 실행 및 실시간 진행 상황을 모니터링합니다.</p>
        
        <div class="status-box">
            <div>
                <div class="status-title">마지막 데이터 업데이트 완료</div>
                <div class="status-time" id="last-update">{last_updated}</div>
            </div>
            <button class="btn" id="update-btn" onclick="triggerUpdate()">▶ 스크래퍼 전체 강제 실행</button>
        </div>

        <div class="status-title" style="margin-bottom: 10px;">커뮤니티별 업데이트 현황</div>
        <div class="progress-container" id="progress-list">
            <!-- 자바스크립트로 동적 렌더링 -->
            <div style="text-align:center; color:#999; font-size:14px;">데이터를 불러오는 중...</div>
        </div>

        <div class="status-title" style="margin-bottom: 10px;">상세 실시간 로그 (터미널)</div>
        <div class="log-container" id="log-box">로그를 불러오는 중...</div>
    </div>

    <script>
        const communities = ["알리뽐뿌", "펨코", "퀘이사존", "루리웹", "클리앙", "뽐뿌", "빠삭국내", "빠삭해외", "빠삭육아"];
        
        function renderProgress(statusData) {
            const container = document.getElementById('progress-list');
            container.innerHTML = '';
            
            communities.forEach(comm => {
                const data = statusData[comm] || { status: '대기 중', time: '-', count: '' };
                
                let barClass = '';
                let statusText = data.status;
                if (data.status === '업데이트 완료 ✅') {
                    barClass = 'done';
                    statusText = `${data.time} 완료 (${data.count})`;
                } else if (data.status === '진행 중 ⏳') {
                    barClass = 'running';
                    statusText = `수집 중... (시작: ${data.time})`;
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
                const response = await fetch('/admin/trigger-update', { method: 'POST' });
                if (!response.ok) {
                    const result = await response.json();
                    alert('오류 발생: ' + result.detail);
                    btn.disabled = false;
                    btn.innerText = "▶ 스크래퍼 전체 강제 실행";
                }
            } catch (error) {
                alert('요청 중 오류가 발생했습니다.');
                btn.disabled = false;
                btn.innerText = "▶ 스크래퍼 전체 강제 실행";
            }
        }

        async function fetchLogs() {
            try {
                const response = await fetch('/admin/logs?t=' + Date.now());
                if (!response.ok) {
                    throw new Error(`서버 응답 오류 (상태 코드: ${response.status})`);
                }
                const data = await response.json();
                
                // 막대바 UI 렌더링
                renderProgress(data.community_status || {});
                
                const logBox = document.getElementById('log-box');
                const isScrolledToBottom = logBox.scrollHeight - logBox.clientHeight <= logBox.scrollTop + 10;
                
                logBox.innerText = data.logs || "아직 기록된 로그가 없습니다.";
                
                if (data.is_running) {
                    document.getElementById('update-btn').disabled = true;
                    document.getElementById('update-btn').innerText = "업데이트 실행 중... (상단 현황 확인)";
                } else {
                    document.getElementById('update-btn').disabled = false;
                    document.getElementById('update-btn').innerText = "▶ 스크래퍼 전체 강제 실행";
                }
                
                // 마지막 업데이트 시간 갱신
                if (data.last_updated && data.last_updated !== "기록 없음") {
                    document.getElementById('last-update').innerText = data.last_updated;
                }

                if (isScrolledToBottom) {
                    logBox.scrollTop = logBox.scrollHeight;
                }
            } catch (e) {
                const logBox = document.getElementById('log-box');
                logBox.innerText = `로그를 가져오는 데 실패했습니다: ${e.message}\\n\\n(백엔드 서버가 재시작 중이거나 아직 준비되지 않았을 수 있습니다. 잠시 후 다시 시도됩니다.)`;
                console.error("로그 가져오기 실패", e);
            }
        }

        // 초기 한 번 실행하고 2초마다 갱신
        fetchLogs();
        setInterval(fetchLogs, 2000);
    </script>
</body>
</html>
"""

@router.get("/", response_class=HTMLResponse)
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

@router.get("/logs")
async def get_logs():
    log_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "logs")
    scraper_log = os.path.join(log_dir, "scraper_output.log")
    last_update_json = os.path.join(log_dir, "last_update.json")
    
    logs = ""
    community_status = {}
    
    if os.path.exists(scraper_log):
        try:
            with open(scraper_log, "r", encoding="utf-8") as f:
                all_lines = f.readlines()
                
                # 전체 로그를 파싱하여 커뮤니티별 상태 추출
                for line in all_lines:
                    # 시작 감지: 2026-05-09 18:43:34,704 - backend.scheduler.main - INFO - ▶ [퀘이사존] 큐 기반 스크래핑 워커 가동
                    match_start = re.search(r'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}).*?▶ \[([^\]]+)\] 큐 기반 스크래핑', line)
                    if match_start:
                        time_str = match_start.group(1)
                        comm = match_start.group(2)
                        community_status[comm] = {"status": "진행 중 ⏳", "time": time_str, "count": ""}
                    
                    # 완료 감지: 2026-05-09 18:43:34,704 - backend.scheduler.main - INFO - ✅ [퀘이사존] 스크래핑 성공 (총 수집/갱신건수: 90건)
                    match_succ = re.search(r'(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}).*?✅ \[([^\]]+)\] 스크래핑 성공 \((.*?)\)', line)
                    if match_succ:
                        time_str = match_succ.group(1)
                        comm = match_succ.group(2)
                        count_str = match_succ.group(3) # "총 수집/갱신건수: 90건"
                        community_status[comm] = {"status": "업데이트 완료 ✅", "time": time_str, "count": count_str}
                
                # 로그 전송량 최적화 (마지막 200줄만)
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
            
    # 스크립트 실행 중 여부를 판단
    is_running = False
    if logs and "모든 커뮤니티 데이터 파싱 및 업데이트 완료" not in logs and "AI Batch 요약 처리 완료" not in logs:
        is_running = True
        
    if logs and ("AI Batch 요약 처리 완료" in logs or "모든 커뮤니티 데이터 파싱 및 업데이트 완료" in logs):
        is_running = False

    return JSONResponse(content={"logs": logs, "last_updated": last_updated, "is_running": is_running, "community_status": community_status})

@router.post("/trigger-update")
async def trigger_update_script():
    import subprocess
    import sys
    
    script_path = os.path.join(os.path.dirname(os.path.dirname(__file__)), "scripts", "update_all_deals.py")
    log_dir = os.path.join(os.path.dirname(os.path.dirname(__file__)), "logs")
    os.makedirs(log_dir, exist_ok=True)
    scraper_log = os.path.join(log_dir, "scraper_output.log")
    
    try:
        # 기존 로그 삭제
        with open(scraper_log, "w", encoding="utf-8") as f:
            f.write("🚀 스크래퍼 실행이 예약되었습니다. 곧 로그가 표시됩니다...\n")
            
        python_executable = sys.executable
        # 파이썬 출력 버퍼링을 꺼서 실시간으로 로그 파일에 기록되도록 설정 (PYTHONUNBUFFERED=1)
        # 덮어쓰기 모드('w')로 로그 저장
        log_file_handle = open(scraper_log, "w", encoding="utf-8")
        
        env = dict(os.environ, PYTHONUTF8="1", PYTHONUNBUFFERED="1")
        
        subprocess.Popen(
            [python_executable, script_path], 
            stdout=log_file_handle, 
            stderr=subprocess.STDOUT,
            env=env
        )
        return {"message": "스크래퍼 작업이 시작되었습니다."}
    except Exception as e:
        from fastapi import HTTPException
        raise HTTPException(status_code=500, detail=str(e))
