import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))
from backend.routers.community import get_normalized_base_name

class MockDeal:
    def __init__(self, t):
        self.title = t
        self.category = None

deal1 = MockDeal("[11번가] 갤럭시 S26 256GB, 자급제 (989,880원)")
deal2 = MockDeal("갤럭시 S26 256GB 자급제 삼카4만")

n1 = get_normalized_base_name(deal1)
n2 = get_normalized_base_name(deal2)
print("n1:", n1)
print("n2:", n2)

set1 = set(n1.split('_')) if n1 else set()
set2 = set(n2.split('_')) if n2 else set()

intersection = len(set1.intersection(set2))
union = len(set1.union(set2))
print("Intersection:", intersection)
print("Union:", union)
print("Ratio:", intersection / union if union > 0 else 0)
