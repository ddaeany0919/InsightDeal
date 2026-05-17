import asyncio
import os
import sys

sys.path.insert(0, os.path.abspath('backend'))
from scrapers.quasarzone_scraper import QuasarzoneScraper
from scrapers.fmkorea_scraper import FmkoreaScraper
from scrapers.ppomppu_scraper import PpomppuScraper
from bs4 import BeautifulSoup

async def check_sites():
    print('==============================')
    print('1. Quasarzone (퀘이사존) Pages 1-3 확인중...')
    try:
        q_scraper = QuasarzoneScraper(8)
        async with q_scraper:
            hot_count = 0
            for page in range(1, 4):
                url = f"https://quasarzone.com/bbs/qb_saleinfo?page={page}"
                q_html = await q_scraper.fetch_html(url)
                q_soup = BeautifulSoup(q_html, 'html.parser')
                q_rows = q_soup.select('div.market-info-type-list table tbody tr')
                for row in q_rows:
                    tangerine = row.select_one('img.tangerine_icon')
                    like_count = 0
                    if tangerine:
                        next_num = tangerine.find_next_sibling('span', class_='num')
                        if next_num:
                            try: like_count = int(next_num.get_text(strip=True))
                            except: pass
                    if like_count >= 20: hot_count += 1
            print(f'=> 퀘이사존 최근 3페이지 핫딜 마크(추천20+) 게시글 수: {hot_count}')
    except Exception as e: print('퀘이사존 에러:', e)

    print('\n==============================')
    print('2. FMKorea (에펨코리아) 인기게시판 Pages 1-3 확인중...')
    try:
        f_scraper = FmkoreaScraper(9)
        async with f_scraper:
            hot_count = 0
            for page in range(1, 4):
                url = f"https://www.fmkorea.com/index.php?mid=hotdeal&sort_index=pop&order_type=desc&page={page}"
                f_html = await f_scraper.fetch_html(url)
                f_soup = BeautifulSoup(f_html, 'html.parser')
                
                # webzine, list 등 여러 형태 대비
                f_rows = f_soup.select('li.li_list')
                if not f_rows:
                    f_rows = f_soup.select('tr.hotdeal_var8')
                if not f_rows:
                    f_rows = f_soup.select('.bd_lst > li')

                for row in f_rows:
                    is_hot = bool(row.select_one('.STAR-BEST-RT, .pc_poten'))
                    if is_hot: hot_count += 1
                print(f'   Page {page}: {len(f_rows)}개 파싱 중 포텐 {hot_count}개 발견')
            print(f'=> 펨코 인기순 최근 3페이지 포텐 게시글 총 수: {hot_count}')
    except Exception as e: print('펨코 에러:', e)

    print('\n==============================')
    print('3. Ppomppu (뽐뿌) Pages 1-3 확인중...')
    try:
        p_scraper = PpomppuScraper(7)
        async with p_scraper:
            hot_count = 0
            for page in range(1, 4):
                url = f"https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu&page={page}"
                p_html = await p_scraper.fetch_html(url)
                p_soup = BeautifulSoup(p_html, 'html.parser')
                p_rows = p_soup.select('tr.baseList, tr.list1, tr.list0')
                for row in p_rows:
                    imgs = [img.get('src') for img in row.select('img') if img.get('src')]
                    if any('hot_icon2' in src for src in imgs): hot_count += 1
            print(f'=> 뽐뿌 최근 3페이지 핫딜(hot_icon2) 게시글 수: {hot_count}')
    except Exception as e: print('뽐뿌 에러:', e)

if __name__ == "__main__":
    asyncio.run(check_sites())
