import re
titles = [
    '[알리익스프레스] 레노버 샤오신 100P 빔프로젝터(118.01달러/무료배송)',
    '[amazon]Amazon Basics Thunderbolt4/USB4 Pro Docking Station ($70.08 / 미국내 무배, 직배 $10.84)',
    '[이베이]adidas men Advantage Shoes ($18.40/FS)',
    '[woot](NEW) Apple AirPods Pro 2 Active Noise Cancelling Wireless Earbuds, USB-C Charging ($134.99/FS)'
]
pattern = r'\(\s*\$?\s*((?:[\d,]+|[\d,]*\.[\d]+)(?:원|달러|유로|€)?)(?:\s*/\s*([^\)]*))?\)'
for t in titles:
    m = re.search(pattern, t)
    print(t, '->', m.groups() if m else None)
