
import os
import time
import random
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium_stealth import stealth
from bs4 import BeautifulSoup

def test_fmkorea_super_stealth():
    chrome_options = Options()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--disable-blink-features=AutomationControlled")
    chrome_options.add_experimental_option("excludeSwitches", ["enable-automation"])
    chrome_options.add_experimental_option('useAutomationExtension', False)
    
    # Random realistic window size
    chrome_options.add_argument(f"--window-size={random.randint(1280, 1920)},{random.randint(720, 1080)}")
    
    driver = webdriver.Chrome(options=chrome_options)
    
    stealth(driver,
        languages=["ko-KR", "ko"],
        vendor="Google Inc.",
        platform="Win32",
        webgl_vendor="Intel Inc.",
        renderer="Intel Iris OpenGL Engine",
        fix_hairline=True,
    )

    url = "https://www.fmkorea.com/hotdeal"
    print(f"--- Attempting: {url} ---")
    
    try:
        # Step 1: Visit home page first
        print("1. Visiting Home Page...")
        driver.get("https://www.fmkorea.com/")
        time.sleep(random.uniform(5, 8))
        print(f"Cookies after home: {len(driver.get_cookies())}")
        
        # Step 2: Visit a neutral sub-page
        print("2. Visiting a neutral sub-page (자유게시판)...")
        driver.get("https://www.fmkorea.com/free")
        time.sleep(random.uniform(3, 5))
        
        # Step 3: FINALLY go to hotdeal
        print("3. Navigating to Hot Deal page...")
        driver.get(url)
        
        # Wait for potential challenge resolution
        total_wait = 30
        start_time = time.time()
        while time.time() - start_time < total_wait:
            soup = BeautifulSoup(driver.page_source, 'html.parser')
            if "보안 시스템" not in driver.title and soup.select('tbody tr'):
                print("✅ [SUCCESS] Bypassed challenge!")
                print(f"Rows found: {len(soup.select('tbody tr'))}")
                break
            else:
                print(f"Still challenged... Title: {driver.title}")
                time.sleep(5)
                # Maybe try a refresh or a small scroll
                driver.execute_script("window.scrollTo(0, document.body.scrollHeight/4);")
        
        # Save final result
        with open("/app/fmkorea_super_stealth_result.html", "w", encoding="utf-8") as f:
            f.write(driver.page_source)
        print("Saved fmkorea_super_stealth_result.html")

    except Exception as e:
        print(f"Error: {e}")
    finally:
        driver.quit()

if __name__ == "__main__":
    test_fmkorea_super_stealth()
