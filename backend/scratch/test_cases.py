import sys
import os

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))
from backend.routers.community import get_normalized_base_name, has_quantity_conflict, has_model_conflict

def test_pair(t1, t2, expected):
    class MockDeal:
        def __init__(self, title):
            self.title = title
    
    n1 = get_normalized_base_name(MockDeal(t1))
    n2 = get_normalized_base_name(MockDeal(t2))
    
    w1 = set(n1.split('_')) if n1 else set()
    w2 = set(n2.split('_')) if n2 else set()
    
    intersection = len(w1.intersection(w2))
    union_len = len(w1.union(w2))
    jaccard = intersection / union_len if union_len > 0 else 0
    
    sc1 = n1.replace('_', '') if n1 else ""
    sc2 = n2.replace('_', '') if n2 else ""
    
    matched = False
    reason = ""
    
    if jaccard >= 0.5:
        matched = True
        reason = "1차 매칭 (Jaccard >= 0.5)"
    elif sc1 == sc2 and sc1 != "":
        matched = True
        reason = "2차 매칭 (띄어쓰기 완전 일치)"
    else:
        if len(w1) > 0 and len(w2) > 0:
            subset_ratio = max(intersection / len(w1), intersection / len(w2))
            if subset_ratio >= 0.8 and intersection >= 3:
                matched = True
                reason = f"3차 매칭 (부분집합 일치율 {subset_ratio*100:.0f}%)"

    if matched:
        if has_quantity_conflict(w1, w2):
            matched = False
            reason = "실패 (용량/수량 충돌)"
        elif has_model_conflict(w1, w2):
            matched = False
            reason = "실패 (모델명 충돌)"
            
    success = matched == expected
    res = "✅ PASS" if success else "❌ FAIL"
    print(f"{res} | '{t1}' VS '{t2}'")
    print(f"   -> 예상: {expected}, 결과: {matched} ({reason})")
    print(f"   -> W1: {w1}")
    print(f"   -> W2: {w2}\n")

test_pair("갤럭시 S26 울트라 512GB", "갤S26 울트라 512gb", True)
test_pair("햇반 210g 36개", "햇반 210g 36팩", True)
test_pair("햇반 210g 36개", "햇반 210g 24개", False)
test_pair("펩시 제로 라임 1.5L 12개", "펩시콜라 제로 라임 1500ml 12개", True)
test_pair("펩시 제로 라임 1.5L 12개", "펩시 제로 라임 500ml 12개", False)
test_pair("스포츠리서치 오메가3 180정 2병(87,720/무료)", "스포츠리서치 오메가3 180정 2병", True)
test_pair("드리미 로봇청소기 L30s Pro Ultra Heat", "드리미 X50s Pro Ultra 올인원 로봇청소기", False)
test_pair("질레트 프로쉴드 옐로우 면도날 12입 + 미니젤 증정", "질레트 스킨텍 면도날 12입", False)
