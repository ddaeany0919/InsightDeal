import sys, os
from datetime import datetime
sys.path.append(os.path.abspath('.'))

from backend.database.session import SessionLocal
from backend.database.models import Deal

db = SessionLocal()

# Delete any existing
deals = db.query(Deal).filter(Deal.title.like('%태국망고 대과 2.5kg%')).all()
for d in deals: db.delete(d)
deals2 = db.query(Deal).filter(Deal.title.like('%제스프리 골드키위 슈퍼점보과 3.4kg%')).all()
for d in deals2: db.delete(d)

d1 = Deal(
    source_community_id=12, # 알리뽐뿌 id
    title='남독마이 태국망고 대과 2.5kg (18,429원) 제스프리 골드키위 슈퍼점보과 3.4kg (27,206원) / 무료] 남독마이 태국망고 대과 2.5kg',
    price='18429',
    currency='KRW',
    post_link='https://m.ppomppu.co.kr/new/bbs_view.php?id=ppomppu8&page=1&divpage=15&no=87695&extref=1',
    ecommerce_link='https://m.ppomppu.co.kr/new/bbs_view.php?id=ppomppu8&page=1&divpage=15&no=87695&extref=1',
    shop_name='알리뽐뿌',
    shipping_fee='무료배송',
    is_closed=False,
    category='일반',
    base_product_name='남독마이 태국망고 대과 2.5kg',
    image_url='https://image.ppomppu.co.kr/mango_box.jpg',
    ai_summary='✅ 18,429원! AI가 자동 분리해낸 핫딜입니다.\n✅ 분할된 옵션 상품으로 정확한 내용은 본문을 참고하세요.\n✅ 세부 스펙은 상품 페이지를 확인해주세요.',
    honey_score=100,
    view_count=1500,
    like_count=30,
    comment_count=10,
    indexed_at=datetime.now()
)

d2 = Deal(
    source_community_id=12, # 알리뽐뿌 id
    title='남독마이 태국망고 대과 2.5kg (18,429원) 제스프리 골드키위 슈퍼점보과 3.4kg (27,206원) / 무료] 제스프리 골드키위 슈퍼점보과 3.4kg',
    price='27206',
    currency='KRW',
    post_link='https://m.ppomppu.co.kr/new/bbs_view.php?id=ppomppu8&page=1&divpage=15&no=87695&extref=1',
    ecommerce_link='https://m.ppomppu.co.kr/new/bbs_view.php?id=ppomppu8&page=1&divpage=15&no=87695&extref=1',
    shop_name='알리뽐뿌',
    shipping_fee='무료배송',
    is_closed=False,
    category='일반',
    base_product_name='제스프리 골드키위 슈퍼점보과 3.4kg',
    image_url='https://image.ppomppu.co.kr/kiwi_box.jpg',
    ai_summary='✅ 27,206원! AI가 자동 분리해낸 핫딜입니다.\n✅ 분할된 옵션 상품으로 정확한 내용은 본문을 참고하세요.\n✅ 세부 스펙은 상품 페이지를 확인해주세요.',
    honey_score=100,
    view_count=1500,
    like_count=30,
    comment_count=10,
    indexed_at=datetime.now()
)

db.add(d1)
db.add(d2)
db.commit()

print("Manual insertion complete!")
