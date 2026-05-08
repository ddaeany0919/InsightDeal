from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium_stealth import stealth
import time

def test_selenium():
    options = Options()
    options.add_argument('--headless')
    options.add_argument('--disable-gpu')
    
    driver = webdriver.Chrome(options=options)
    
    stealth(driver,
            languages=["ko-KR", "ko"],
            vendor="Google Inc.",
            platform="Win32",
            webgl_vendor="Intel Inc.",
            renderer="Intel Iris OpenGL Engine",
            fix_hairline=True,
            )
            
    try:
        driver.get('https://www.fmkorea.com/hotdeal')
        time.sleep(3)
        print("Page Title:", driver.title)
        if "에펨코리아" in driver.title and "보안 시스템" not in driver.title:
            print("Success!")
        else:
            print("Blocked or failed.")
            print(driver.page_source[:500])
    finally:
        driver.quit()

if __name__ == "__main__":
    test_selenium()
