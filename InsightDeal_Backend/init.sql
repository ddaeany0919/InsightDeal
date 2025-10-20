-- 커뮤니티 테이블에 기본 데이터 삽입

INSERT INTO communities (id, name, base_url) VALUES 
(1, '알리뽐뿌', 'https://www.ppomppu.co.kr'),
(2, '루리웹', 'https://bbs.ruliweb.com'),
(3, '뽐뿌', 'https://www.ppomppu.co.kr'),
(4, '뽐뿌해외', 'https://www.ppomppu.co.kr'),
(5, '빠삭국내', 'https://bbasak.com'),
(6, '빠삭해외', 'https://bbasak.com'),
(7, '클리앙', 'https://www.clien.net'),
(8, '퀘이사존', 'https://quasarzone.com'),
(9, '펨코', 'https://www.fmkorea.com')
ON CONFLICT (id) DO NOTHING;