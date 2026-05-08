from selenium import webdriver
from selenium.webdriver.chrome.options import Options
from selenium_stealth import stealth

def test_fm():
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
    
    url = "https://www.fmkorea.com/hotdeal?listStyle=webzine"
    driver.get(url)
    
    import time
    time.sleep(5)
    
    html = driver.page_source
    if '에펨코리아 보안 시스템' in html:
        print("BLOCKED")
    else:
        print("SUCCESS, length:", len(html))
        
    driver.quit()

test_fm()
