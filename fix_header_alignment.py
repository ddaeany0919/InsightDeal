# -*- coding: utf-8 -*-
file_path = r"c:\Users\kth00\StudioProjects\InsightDeal\app\src\main\java\com\ddaeany0919\insightdeal\presentation\mypage\MyPageScreen.kt"

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

idx_logout = content.find("AuthManager.logout")
if idx_logout != -1:
    print("Found AuthManager.logout! Snippet:")
    snippet = content[idx_logout-250:idx_logout+200]
    print(repr(snippet))
