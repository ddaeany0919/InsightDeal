
import sys
from core.rule_parser import RuleBasedParser

def test_rule_parser():
    test_cases = [
        {
            "title": "[G마켓] 삼성전자 오디세이 G5 S27AG500 (359,000원/무료)",
            "expected_category": "디지털/가전",
            "expected_price": "359,000원",
            "expect_high_confidence": True
        },
        {
            "title": "농심 신라면 40봉지 (22,900원)",
            "expected_category": "음식/식품",
            "expected_price": "22,900원",
            "expect_high_confidence": True
        },
        {
            "title": "애매한 상품 제목입니다.",
            "expected_category": "기타",
            "expect_high_confidence": False
        }
    ]

    print("Running RuleBasedParser Tests...\n")
    all_passed = True
    for case in test_cases:
        result = RuleBasedParser.parse_deal_info(case['title'])
        print(f"Title: {case['title']}")
        print(f"Result: {result}")
        
        is_success = (
            result['category'] == case['expected_category'] and
            (result['price'] == case['expected_price'] if 'expected_price' in case else True) and
            (result['confidence'] == 'high') == case['expect_high_confidence']
        )
        
        if is_success:
            print("Status: ✅ PASS\n")
        else:
            print("Status: ❌ FAIL\n")
            all_passed = False

    if all_passed:
        print("🎉 All tests passed!")
        sys.exit(0)
    else:
        print("💥 Some tests failed.")
        sys.exit(1)

if __name__ == "__main__":
    test_rule_parser()
