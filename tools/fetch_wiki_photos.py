#!/usr/bin/env python3
"""
生存指南配图抓取脚本 —— Wikimedia Commons 公有领域 / CC0 真实照片。

流程:
1. REST 搜索接口(与 action API 独立限流)按章节关键词找文件
2. 标题关键词白名单过滤,保证与章节主题相关
3. 打开文件描述页 HTML,确认授权为 Public domain / CC0(免署名)
4. 下载 1024px 缩略图(把 60px 换成 1024px),PIL 校验压缩为 JPEG
5. 输出到 app/src/main/res/drawable-nodpi/,随 APK 离线打包

无命中章节保留内置矢量插图兜底。幂等:已存在文件跳过。
用法:python3 fetch_wiki_photos.py
"""
import io
import json
import re
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

UA = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) DeauthCtrl-wiki/1.0"
REST = "https://commons.wikimedia.org/w/rest.php/v1/search/page"
PAGE = "https://commons.wikimedia.org/wiki/"
OUT = Path(__file__).resolve().parents[1] / "app/src/main/res/drawable-nodpi"

# 标题拒词表:相关性兜底(误匹配防护)
REJECT_WORDS = ["nude", "naked", "love", "station", "pdf", "logo", "icon"]

# 章节 → (搜索词, 标题必须命中的主题词列表)
CHAPTERS = {
    1: ("emergency whistle", ["whistle"]),
    2: ("water filter", ["water", "filter"]),
    3: ("lean-to shelter", ["shelter", "lean", "debris"]),
    4: ("campfire", ["campfire", "bonfire", "fire"]),
    5: ("first aid kit", ["first aid", "first-aid", "bandage", "medic"]),
    6: ("rucksack", ["rucksack", "backpack"]),
    7: ("computer keyboard dark", ["keyboard", "laptop"]),
    8: ("hard disk drive", ["hard drive", "hard disk", "hdd"]),
    9: ("magnifying glass", ["magnify", "magnifier", "lens"]),
    10: ("padlock security", ["padlock", "lock", "security"]),
    11: ("fishing hook", ["hook"]),
    12: ("external hard drive", ["hard drive", "hard disk", "hdd", "backup"]),
    13: ("tourniquet", ["tourniquet"]),
    14: ("gas mask", ["gas mask", "gasmask", "respirator"]),
    15: ("camouflage soldier", ["camoufl", "ghillie", "netting"]),
    16: ("canned food", ["canned", "tin can", "can food"]),
    17: ("compass", ["compass"]),
    18: ("smoke signal", ["smoke", "signal"]),
    19: ("two-way radio", ["radio", "walkie", "transceiver"]),
    20: ("map navigation", ["map", "navigation", "gps"]),
    21: ("solar panel", ["solar", "photovoltaic"]),
    22: ("meditation breathing", ["meditat", "breath", "yoga"]),
    23: ("military dog tag", ["dog tag", "identification tag", "medical id"]),
    24: ("camping gear", ["camping", "backpack", "gear"]),
}


def rest_search(keyword: str, limit: int = 20):
    q = urllib.parse.urlencode({"q": keyword, "limit": limit})
    req = urllib.request.Request(f"{REST}?{q}", headers={"User-Agent": UA})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                return json.load(r).get("pages", [])
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt < 3:
                time.sleep(15 * (attempt + 1))
                continue
            raise


def page_html(title: str) -> str:
    name = urllib.parse.quote(title.replace(" ", "_"), safe="/:_-()")
    req = urllib.request.Request(f"{PAGE}{name}", headers={"User-Agent": UA})
    for attempt in range(4):
        try:
            with urllib.request.urlopen(req, timeout=25) as r:
                return r.read().decode("utf-8", "ignore")
        except urllib.error.HTTPError as e:
            if e.code == 429 and attempt < 3:
                time.sleep(30 * (attempt + 1))
                continue
            raise


def is_free_license(html: str) -> bool:
    low = html.lower()
    return ("public domain" in low) or ("cc0" in low)


def download(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=60) as r:
                return r.read()
        except Exception:
            if attempt == 2:
                raise
            time.sleep(10)


def main():
    from PIL import Image
    OUT.mkdir(parents=True, exist_ok=True)
    report = []
    for ch, (keyword, words) in CHAPTERS.items():
        target = OUT / f"wiki_photo_ch{ch:02d}.jpg"
        if target.exists():
            report.append((ch, "exists", target.name, "", ""))
            continue
        try:
            pages = rest_search(keyword)
            chosen = None
            for page in pages:
                title = page.get("title", "")
                if not title.startswith("File:"):
                    continue
                if not re.search(r"\.(jpg|jpeg|png|gif|tif|tiff|webp)$", title.lower()):
                    continue  # 跳过 PDF / SVG / 音视频
                tlow = title.lower()
                if any(r in tlow for r in REJECT_WORDS):
                    continue
                if not any(w in tlow for w in words):
                    continue
                thumb = (page.get("thumbnail") or {}).get("url", "")
                if not thumb:
                    continue
                if not is_free_license(page_html(title)):
                    continue
                chosen = (title, thumb)
                break
            if not chosen:
                report.append((ch, "NO-HIT", "", keyword, ""))
                continue
            title, thumb = chosen
            # 官方尺寸通道:Special:FilePath?width= → 302 到合法缩略图
            file_url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + \
                urllib.parse.quote(title.replace(" ", "_").removeprefix("File:"), safe="")
            raw = download(file_url + "?width=1024")
            img = Image.open(io.BytesIO(raw))
            img.load()
            img = img.convert("RGB")
            img.thumbnail((1024, 1024))
            img.save(target, "JPEG", quality=80)
            report.append((ch, "OK", target.name, "PD/CC0", title))
        except Exception as e:
            report.append((ch, "FAIL", str(e)[:50], keyword, ""))
        time.sleep(8)
    print(f"{'ch':>3} {'status':8} {'file':22} {'license':8} source")
    for ch, status, fname, lic, title in report:
        print(f"{ch:>3} {status:8} {fname:22} {lic:8} {title[:58]}")


if __name__ == "__main__":
    main()
