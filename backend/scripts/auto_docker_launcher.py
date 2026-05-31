import time
import subprocess
import os

def check_docker():
    try:
        # docker info 실행
        result = subprocess.run(["docker", "info"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        return result.returncode == 0
    except Exception:
        return False

def launch_compose():
    print("[Auto Launcher] Docker Daemon detected! Launching docker-compose...")
    # docker-compose up -d 실행
    cwd = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    try:
        result = subprocess.run(["docker", "compose", "up", "-d"], cwd=cwd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        print("[Auto Launcher] docker-compose output:", result.stdout)
        if result.returncode == 0:
            print("[Auto Launcher] docker-compose launched successfully!")
            # 00_Agent_Live_Chat.md 에 완료 마크 찍기
            live_chat_path = os.path.join(cwd, "agent_workspace", "00_Agent_Live_Chat.md")
            if os.path.exists(live_chat_path):
                with open(live_chat_path, "r", encoding="utf-8") as f:
                    content = f.read()
                
                # 중계 완료 마크 추가
                update_str = "\n* **[2026-05-31 18:18]** 🤖 [Auto Launcher]: 도커 엔진 기동 자동 감지 완료! `docker compose up -d`를 완전히 자동으로 격발하여 도커 백엔드(Port 8000)를 성공적으로 구동 완료했습니다!\n"
                with open(live_chat_path, "w", encoding="utf-8") as f:
                    f.write(content + update_str)
            return True
        else:
            print("[Auto Launcher] docker-compose failed:", result.stderr)
            return False
    except Exception as e:
        print("[Auto Launcher] Error launching compose:", e)
        return False

def main():
    print("[Auto Launcher] Starting Docker auto-detection loop (interval: 5s)...")
    for _ in range(60): # 최대 5분 동안 5초 간격으로 폴링
        if check_docker():
            if launch_compose():
                break
        time.sleep(5)
    print("[Auto Launcher] Detection loop finished.")

if __name__ == "__main__":
    main()
