import re

def fix_community():
    with open('backend/routers/community.py', 'r', encoding='utf-8') as f:
        content = f.read()

    # 1. Add get_deal_sources helper function after imports
    helper_code = """
import json

def get_deal_sources(deal, comp_name, parsed_price_int):
    deal_sources = []
    currency = getattr(deal, 'currency', 'KRW') or 'KRW'
    if hasattr(deal, 'options_data') and deal.options_data:
        try:
            options = json.loads(deal.options_data)
            for opt in options:
                deal_sources.append({
                    "site_name": f"{comp_name} - {opt.get('name', '옵션')}",
                    "post_url": deal.post_link or "",
                    "ecommerce_url": deal.ecommerce_link or deal.post_link or "",
                    "price": int(opt.get("price", parsed_price_int)),
                    "currency": currency
                })
        except Exception:
            deal_sources.append({
                "site_name": comp_name, 
                "post_url": deal.post_link or "",
                "ecommerce_url": deal.ecommerce_link or deal.post_link or "",
                "price": parsed_price_int,
                "currency": currency
            })
    else:
        deal_sources.append({
            "site_name": comp_name, 
            "post_url": deal.post_link or "",
            "ecommerce_url": deal.ecommerce_link or deal.post_link or "",
            "price": parsed_price_int,
            "currency": currency
        })
    return deal_sources
"""
    if "def get_deal_sources" not in content:
        content = content.replace("router = APIRouter()", helper_code + "\nrouter = APIRouter()")

    # 2. Fix the first place (around line 438)
    replace1 = """                    # 출처 추가 (post_url 기준 중복 방지)
                    if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                        if comp_name not in existing.setdefault("site_names", []):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                        existing["sources"].append({
                            "site_name": comp_name, 
                            "post_url": deal.post_link or "",
                            "ecommerce_url": deal.ecommerce_link or deal.post_link or "",
                            "price": parsed_price_int
                        })"""
    
    with1 = """                    # 출처 추가 (post_url 기준 중복 방지)
                    if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                        if comp_name not in existing.setdefault("site_names", []):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                        existing["sources"].extend(get_deal_sources(deal, comp_name, parsed_price_int))"""
    
    content = content.replace(replace1, with1)

    # 3. Fix the second place (around line 450)
    replace2 = """                    if existing.get("total_price", 0) == 0:
                        if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                            if comp_name not in existing.setdefault("site_names", []):
                                existing["site_names"].append(comp_name)
                                existing["site_name"] = ", ".join(existing["site_names"])
                            existing["sources"].append({
                                "site_name": comp_name, 
                                "post_url": deal.post_link or "",
                                "ecommerce_url": deal.ecommerce_link or deal.post_link or "",
                                "price": parsed_price_int
                            })"""
    
    with2 = """                    if existing.get("total_price", 0) == 0:
                        if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                            if comp_name not in existing.setdefault("site_names", []):
                                existing["site_names"].append(comp_name)
                                existing["site_name"] = ", ".join(existing["site_names"])
                            existing["sources"].extend(get_deal_sources(deal, comp_name, parsed_price_int))"""
    
    content = content.replace(replace2, with2)

    # 4. Fix the third place (around line 491)
    replace3 = """"sources": [{"site_name": comp_name, "post_url": deal.post_link or "", "ecommerce_url": deal.ecommerce_link or deal.post_link or "", "price": parsed_price_int}],"""
    with3 = """"sources": get_deal_sources(deal, comp_name, parsed_price_int),"""
    content = content.replace(replace3, with3)

    # 5. Fix the fourth place (/api/community/search)
    replace4 = """                    if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                        if comp_name not in existing.setdefault("site_names", []):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                        existing["sources"].append({
                            "site_name": comp_name, 
                            "post_url": deal.post_link or "",
                            "ecommerce_url": deal.ecommerce_link or deal.post_link or "",
                            "price": parsed_price_int
                        })"""
    
    with4 = """                    if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                        if comp_name not in existing.setdefault("site_names", []):
                            existing["site_names"].append(comp_name)
                            existing["site_name"] = ", ".join(existing["site_names"])
                        existing["sources"].extend(get_deal_sources(deal, comp_name, parsed_price_int))"""
    content = content.replace(replace4, with4)

    replace5 = """                        if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                            if comp_name not in existing.setdefault("site_names", []):
                                existing["site_names"].append(comp_name)
                                existing["site_name"] = ", ".join(existing["site_names"])
                            existing["sources"].append({
                                "site_name": comp_name, 
                                "post_url": deal.post_link or "",
                                "ecommerce_url": deal.ecommerce_link or deal.post_link or "",
                                "price": parsed_price_int
                            })"""
    with5 = """                        if not any(s.get('post_url') == (deal.post_link or "") for s in existing.setdefault("sources", [])):
                            if comp_name not in existing.setdefault("site_names", []):
                                existing["site_names"].append(comp_name)
                                existing["site_name"] = ", ".join(existing["site_names"])
                            existing["sources"].extend(get_deal_sources(deal, comp_name, parsed_price_int))"""
    content = content.replace(replace5, with5)

    # 6. Fix the sixth place (/api/community/deals/{deal_id}) line 877
    replace6 = """            if not any(s.get('post_url') == (d.post_link or "") for s in sources):
                if c_name not in site_names:
                    site_names.append(c_name)
                sources.append({
                    "site_name": c_name,
                    "post_url": d.post_link or "",
                    "ecommerce_url": d.ecommerce_link or d.post_link or "",
                    "price": p_val
                })"""
    
    with6 = """            if not any(s.get('post_url') == (d.post_link or "") for s in sources):
                if c_name not in site_names:
                    site_names.append(c_name)
                sources.extend(get_deal_sources(d, c_name, p_val))"""
    content = content.replace(replace6, with6)

    # 7. Fix the seventh place (/api/community/deals/{deal_id}) line 890
    replace7 = """        sources = [{"site_name": c_name, "post_url": deal.post_link or "", "ecommerce_url": deal.ecommerce_link or deal.post_link or "", "price": extract_price(deal.price) if isinstance(deal.price, str) else (deal.price or 0)}]"""
    with7 = """        p_val = extract_price(deal.price) if isinstance(deal.price, str) else (deal.price or 0)
        sources = get_deal_sources(deal, c_name, p_val)"""
    content = content.replace(replace7, with7)

    # 8. ADD currency to the deals/{deal_id} response
    replace8 = """        "sources": sources,
        "category": getattr(best_deal, "category", "기타"),"""
    with8 = """        "currency": getattr(best_deal, 'currency', 'KRW') or 'KRW',
        "sources": sources,
        "category": getattr(best_deal, "category", "기타"),"""
    content = content.replace(replace8, with8)
    
    with open('backend/routers/community.py', 'w', encoding='utf-8') as f:
        f.write(content)

if __name__ == "__main__":
    fix_community()
    print("Done")
