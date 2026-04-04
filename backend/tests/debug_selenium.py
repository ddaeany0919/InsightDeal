
import sys
import os
import time
from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium_stealth import stealth

def debug_selenium():
    chrome_options = Options()
    chrome_options.add_argument("--headless")
    chrome_options.add_argument("--no-sandbox")
    chrome_options.add_argument("--disable-dev-shm-usage")
    chrome_options.add_argument("--disable-gpu")
    chrome_options.add_argument("--window-size=1920,1080")
    chrome_options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

    driver = webdriver.Chrome(options=chrome_options)
    stealth(driver,
            languages=["ko-KR", "ko"],
            vendor="Google Inc.",
            platform="Win32",
            webgl_vendor="Intel Inc.",
            renderer="Intel Iris OpenGL Engine",
            fix_hairline=True)

    urls = {
        "quasarzone": "https://quasarzone.com/bbs/qb_saleinfo",
        "fmkorea": "https://www.fmkorea.com/hotdeal"
    }

    for name, url in urls.items():
        print(f"--- Fetching {name}: {url} ---")
        try:
            driver.get(url)
            time.sleep(10) # Wait for potential challenges/JS
            with open(f"/app/{name}_source.html", "w", encoding="utf-8") as f:
                f.write(driver.page_source)
            print(f"Saved {name}_source.html")
        except Exception as e:
            print(f"Error fetching {name}: {e}")

    driver.quit()

if __name__ == "__main__":
    debug_selenium()
