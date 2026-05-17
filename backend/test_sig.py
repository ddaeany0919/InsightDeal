from routers.community import get_normalized_base_name
class Deal:
    pass

deal = Deal()
deal.title='[쿠팡] 삼양라면 120g 10개'
print(get_normalized_base_name(deal))

deal.title='삼양라면 120g, 10개, (5개입 X 2팩)'
print(get_normalized_base_name(deal))

deal.title='삼양라면 120g, 10개'
print(get_normalized_base_name(deal))
