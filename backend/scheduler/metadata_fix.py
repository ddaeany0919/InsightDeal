#!/usr/bin/env python3
"""
Python 3.9 호환성을 위한 importlib.metadata 패치
이 파일은 packages_distributions 함수가 없는 문제를 해결합니다.
"""

import sys

def patch_importlib_metadata():
    """Python 3.9에서 importlib.metadata의 packages_distributions 함수 호환성 패치"""
    try:
        # Python 3.10+ 스타일 시도
        from importlib.metadata import packages_distributions
        return packages_distributions
    except (ImportError, AttributeError):
        try:
            # Python 3.9 백포트 패키지 사용
            from importlib_metadata import packages_distributions
            return packages_distributions
        except (ImportError, AttributeError):
            # 완전 대체 함수 제공
            def packages_distributions():
                """packages_distributions 대체 구현"""
                try:
                    from importlib_metadata import distributions
                    pkg_to_dist = {}
                    for dist in distributions():
                        if dist.files:
                            for file in dist.files:
                                if file.suffix == '.py':
                                    pkg_name = str(file).split('/')[0]
                                    if pkg_name not in pkg_to_dist:
                                        pkg_to_dist[pkg_name] = []
                                    pkg_to_dist[pkg_name].append(dist.metadata['Name'])
                    return pkg_to_dist
                except Exception:
                    return {}
            
            return packages_distributions

# 자동 패치 적용
if __name__ == "__main__":
    print("🔧 Applying Python 3.9 importlib.metadata compatibility patch...")
    packages_distributions = patch_importlib_metadata()
    print(f"✅ packages_distributions function available: {callable(packages_distributions)}")
