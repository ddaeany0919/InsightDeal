from main import app
from fastapi.testclient import TestClient
import os
import time

def main():
    client = TestClient(app)
    try:
        print("Triggering force-scrape API to populate today's hot deals...")
        response = client.post("/api/community/force-scrape")
        print(f"Status Code: {response.status_code}")
        print(f"Response: {response.json()}")
        
        print("\nWaiting 5 seconds for background scraper to run...")
        time.sleep(5)
    except Exception as e:
        print(f"Failed to trigger force-scrape: {e}")

if __name__ == "__main__":
    try:
        main()
    finally:
        if os.path.exists(__file__):
            os.remove(__file__)
            print("\n[SUCCESS] Temp scraper trigger script cleaned up successfully.")
