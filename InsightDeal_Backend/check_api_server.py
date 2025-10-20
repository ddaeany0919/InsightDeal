import sys
import os
import subprocess

def check_api_server():
    print("=== API 서버 상태 확인 ===\n")
    
    # 1. main.py 또는 start_server.py 파일 확인
    api_files = ["main.py", "start_server.py", "app.py"]
    found_file = None
    
    for filename in api_files:
        if os.path.exists(filename):
            found_file = filename
            break
    
    if found_file:
        print(f"✅ API 서버 파일 발견: {found_file}")
        
        # 실행 중인지 확인
        try:
            import requests
            response = requests.get("http://localhost:8000/docs", timeout=2)
            if response.status_code == 200:
                print("✅ API 서버 실행 중 (http://localhost:8000/docs)")
            else:
                print(f"⚠️ API 서버 응답 이상: {response.status_code}")
        except requests.exceptions.ConnectionError:
            print("❌ API 서버 실행되지 않음")
            print(f"💡 실행 방법: python {found_file}")
        except ImportError:
            print("⚠️ requests 모듈 없음. pip install requests")
        except Exception as e:
            print(f"❌ 확인 실패: {e}")
    else:
        print("❌ API 서버 파일을 찾을 수 없습니다.")
        print("💡 찾는 파일: main.py, start_server.py, app.py")
    
    # 2. 포트 사용 확인
    print(f"\n🔍 포트 8000 사용 확인:")
    if os.name == 'nt':  # Windows
        try:
            result = subprocess.run(["netstat", "-an"], capture_output=True, text=True)
            if ":8000" in result.stdout:
                print("✅ 포트 8000 사용 중")
            else:
                print("❌ 포트 8000 사용하지 않음")
        except:
            print("⚠️ netstat 확인 실패")

if __name__ == "__main__":
    check_api_server()
