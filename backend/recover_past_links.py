# -*- coding: utf-8 -*-
import sys
import os
import re
import base64
import urllib.parse
import urllib.request
from bs4 import BeautifulSoup

# Add backend directory to sys.path
backend_dir = os.path.dirname(os.path.abspath(__file__))
sys.path.append(os.path.dirname(backend_dir))

from backend.database.session import create_db_session
from backend.database.models import Deal

def fetch_html(url):
    try:
        req = urllib.request.Request(
            url, 
            headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'}
        )
        with urllib.request.urlopen(req, timeout=8) as response:
            return response.read().decode('utf-8', errors='ignore')
    except Exception as e:
        print(f"Failed to fetch {url}: {e}")
        return ""

def extract_quasarzone(html):
    soup = BeautifulSoup(html, 'html.parser')
    ecommerce_link = ""
    for a in soup.select('table.market-info-view-table a'):
        href = a.get('href', '') or ''
        onclick = a.get('onclick', '') or ''
        combined = href + " " + onclick
        if 'goToLink' in combined:
            match = re.search(r"goToLink\('([^']+)'\)", combined)
            if match:
                try:
                    b64_str = match.group(1)
                    b64_str += "=" * ((4 - len(b64_str) % 4) % 4)
                    decoded = base64.b64decode(b64_str).decode('utf-8')
                    if decoded.startswith('http'):
                        return decoded
                except Exception:
                    pass
        elif href.startswith('http') and 'quasarzone.com' not in href:
            return href
            
    if not ecommerce_link:
        for a in soup.select('.view-content a'):
            href = a.get('href', '') or ''
            if href.startswith('http') and 'clickBanner' not in href:
                return href
    return ""

def extract_ppomppu(html):
    soup = BeautifulSoup(html, 'html.parser')
    ecommerce_link = ""
    link_tag = soup.select_one('.word, .word_break a')
    if link_tag and link_tag.get('href') and 'http' in link_tag['href']:
        ecommerce_link = link_tag['href']
        
    if not ecommerce_link:
        for a in soup.find_all('a'):
            href = a.get('href', '') or ''
            if not href:
                continue
            if 's.ppomppu.co.kr' in href:
                ecommerce_link = href
                break
            elif href.startswith('http') and 'ppomppu.co.kr' not in href and 'ppomppu4.co.kr' not in href and 'ppomppu8.co.kr' not in href:
                ecommerce_link = href
                break
                
    if "s.ppomppu.co.kr" in ecommerce_link:
        try:
            parsed_url = urllib.parse.urlparse(ecommerce_link)
            if 'target' in parsed_url.query:
                query_params = urllib.parse.parse_qs(parsed_url.query)
                encoded_target = query_params.get("target", [None])[0]
                if encoded_target:
                    encoded_target += '=' * (-len(encoded_target) % 4)
                    ecommerce_link = base64.b64decode(encoded_target).decode('utf-8')
        except Exception:
            pass
    return ecommerce_link

def extract_fmkorea(html):
    soup = BeautifulSoup(html, 'html.parser')
    ecommerce_link = ""
    for a in soup.select('.hotdeal_info a, a'):
        href = a.get('href', '') or ''
        if not href:
            continue
        if 'link.fmkorea.org' in href or 'link.fmkorea.com' in href:
            try:
                parsed_url = urllib.parse.urlparse(href)
                query_params = urllib.parse.parse_qs(parsed_url.query)
                target_url = query_params.get('url', [''])[0] or query_params.get('target', [''])[0]
                if target_url:
                    return target_url
            except:
                pass
        elif href.startswith('http') and 'fmkorea.com' not in href and 'fmkorea.org' not in href and 'saedu.naver.com' not in href and 'ader.naver.com' not in href:
            return href
            
    if not ecommerce_link:
        content_area = soup.select_one('.xe_content') or soup.select_one('.rd_body')
        if content_area:
            text_content = content_area.get_text(separator=' ')
            url_matches = re.findall(r'(https?://[^\s]+)', text_content)
            for m in url_matches:
                if 'fmkorea.com' not in m and 'fmkorea.org' not in m and 'saedu.naver.com' not in m:
                    return m
            url_matches2 = re.findall(r'(m\.smartstore\.naver\.com/[^\s]+|smartstore\.naver\.com/[^\s]+|brand\.naver\.com/[^\s]+|coupang\.com/[^\s]+)', text_content)
            if url_matches2:
                return "https://" + url_matches2[0]
    return ""

def main():
    print("[INFO] Start past outlink recovery script.")
    session = create_db_session()
    
    # 뽐뿌, 퀘이사존, 펨코리아 핫딜 중 최근 150개 추출
    # sqlite 데이터베이스에서 post_link 와 ecommerce_link 가 같거나, ecommerce_link가 비어있거나 '#'인 딜들 필터링
    deals_to_recover = session.query(Deal).filter(
        Deal.is_closed == False
    ).order_by(Deal.id.desc()).limit(150).all()
    
    print(f"Targeting {len(deals_to_recover)} recent deals for link recovery audit...")
    
    recovered_count = 0
    
    for deal in deals_to_recover:
        # 뽐뿌, 퀘이사존, 펨코리아 딜인지 판단
        post_url = deal.post_link or ""
        ecommerce_url = deal.ecommerce_link or ""
        
        is_quasarzone = "quasarzone.com" in post_url
        is_ppomppu = "ppomppu.co.kr" in post_url
        is_fmkorea = "fmkorea.com" in post_url
        
        if not (is_quasarzone or is_ppomppu or is_fmkorea):
            continue
            
        # 복원이 필요한 대상인지 확인 (복원 링크가 유실되었거나 커뮤니티 주소와 판박이인 경우)
        needs_recovery = (not ecommerce_url or ecommerce_url == "#" or ecommerce_url.strip() == "" or (post_url.split('?')[0] in ecommerce_url))
        
        if not needs_recovery:
            continue
            
        print(f"\n[AUDIT] Deal ID: {deal.id} | Title: {deal.title}")
        print(f"  Current post_link: {post_url}")
        print(f"  Current ecommerce_link: {ecommerce_url}")
        
        html = fetch_html(post_url)
        if not html:
            continue
            
        real_link = ""
        if is_quasarzone:
            real_link = extract_quasarzone(html)
        elif is_ppomppu:
            real_link = extract_ppomppu(html)
        elif is_fmkorea:
            real_link = extract_fmkorea(html)
            
        if real_link and real_link.strip() != "" and real_link != post_url:
            print(f"  [SUCCESS] Recovered real outlink: {real_link}")
            deal.ecommerce_link = real_link
            recovered_count += 1
        else:
            print(f"  [SKIPPED] Could not resolve a better link. (Extracted: {real_link})")
            
    if recovered_count > 0:
        session.commit()
        print(f"\n[INFO] Finished! Recovered {recovered_count} past links.")
    else:
        print("\nℹ️ 추가적으로 복원할 수 있는 링크가 존재하지 않거나 이미 정화가 완료되었습니다.")
        
    session.close()

if __name__ == "__main__":
    main()
