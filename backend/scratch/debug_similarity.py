import sys
import os

sys.path.append(os.path.abspath(os.path.join(os.path.dirname(__file__), '../..')))
from backend.routers.community import get_normalized_base_name, is_similar_deal, get_model_codes

def main():
    t1 = "COLORFUL iGame 5080 ULTRA OC 합본팩+사운드바(체감가 173만)"
    t2 = "COLORFUL iGame 5080 Vulcan OC 합본팩 (체감가 200 만)"
    
    n1 = get_normalized_base_name(t1)
    n2 = get_normalized_base_name(t2)
    
    print(f"N1: {n1}")
    print(f"N2: {n2}")
    
    tokens1 = set(n1.split())
    tokens2 = set(n2.split())
    
    print(f"Tokens1: {tokens1}")
    print(f"Tokens2: {tokens2}")
    
    inter = len(tokens1.intersection(tokens2))
    union = len(tokens1.union(tokens2))
    
    print(f"Intersection: {inter}")
    print(f"Union: {union}")
    print(f"Jaccard: {inter / union}")
    print(f"is_similar: {is_similar_deal(t1, t2)}")
    
    print(f"Codes1: {get_model_codes(n1)}")
    print(f"Codes2: {get_model_codes(n2)}")

if __name__ == "__main__":
    main()
