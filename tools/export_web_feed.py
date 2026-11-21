#!/usr/bin/env python3
"""
Builds the data of Gideon Web (and the iOS app): fetches every feed of
app/src/main/assets/sources.json, extracts the newest articles and writes
  <out>/data/feed.json        compact list of stories
  <out>/data/a/<id>.json      full text of an article (reader)
"""
import calendar
import concurrent.futures as cf
import hashlib
import html
import json
import os
import re
import sys
import time
import xml.etree.ElementTree as ET

import feedparser
import requests
import trafilatura

OUT = sys.argv[1] if len(sys.argv) > 1 else "site"
SOURCES = "app/src/main/assets/sources.json"
UA = ("Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) AppleWebKit/605.1.15 "
      "(KHTML, like Gecko) Version/18.6 Mobile/15E148 Safari/604.1")
MAX_ITEMS = 900
MAX_ARTICLES = int(os.environ.get("GIDEON_MAX_ARTICLES", "160"))
NOW = time.time()

session = requests.Session()
session.headers.update({"User-Agent": UA, "Accept-Language": "fr-FR,fr;q=0.9,en;q=0.5"})


def get(url, timeout=15):
    r = session.get(url, timeout=timeout)
    r.raise_for_status()
    return r


def clean(text):
    if not text:
        return ""
    text = re.sub(r"<[^>]+>", " ", text)
    text = html.unescape(text).replace(" ", " ")
    return re.sub(r"\s+", " ", text).strip()


def web_url(u):
    """The URL if it is an absolute http(s) one (protocol-relative ones become https), else None."""
    u = (u or "").strip()
    if u.startswith("//"):
        u = "https:" + u
    return u if re.match(r"https?://", u, re.I) else None


def md5(s):
    return hashlib.md5(s.encode("utf-8")).hexdigest()


def first_img(fragment):
    m = re.search(r"<img[^>]+src=[\"']([^\"']+)", fragment or "", re.I)
    return m.group(1) if m else None


def image_of(e):
    best, best_w = None, -1
    for m in (e.get("media_content") or []) + (e.get("media_thumbnail") or []):
        url = m.get("url")
        if not url:
            continue
        medium, typ = m.get("medium", ""), m.get("type", "")
        if (medium and medium != "image") or (typ and not typ.startswith("image")):
            continue
        w = int(m.get("width") or 0) if str(m.get("width") or "0").isdigit() else 0
        if best is None or w > best_w:
            best, best_w = url, w
    if best:
        return best
    for enc in e.get("enclosures") or []:
        if enc.get("type", "").startswith("image") and enc.get("href"):
            return enc["href"]
    content = " ".join(c.get("value", "") for c in (e.get("content") or []))
    return first_img(content) or first_img(e.get("summary", ""))


def stamp(e):
    for key in ("published_parsed", "updated_parsed"):
        t = e.get(key)
        if t:
            return calendar.timegm(t)
    return NOW


def parse_feed(feed, pub):
    r = get(feed["url"])
    d = feedparser.parse(r.content)
    out = []
    for e in d.entries[: 15 if feed["kind"] == "youtube" else 25]:
        link = web_url(e.get("link"))
        title = clean(e.get("title"))
        if not link or not title:
            continue
        vid = e.get("yt_videoid")
        if vid:
            summary = (e.get("summary") or "").strip().split("\n")[0]
            image = f"https://i.ytimg.com/vi/{vid}/hqdefault.jpg"
        else:
            summary = clean(e.get("summary") or "")
            image = image_of(e)
        if len(summary) > 320:
            summary = summary[:317].rstrip() + "…"
        if summary.lower() == title.lower():
            summary = ""
        out.append({
            "id": f"yt:{vid}" if vid else "a:" + md5(link.split("#")[0]),
            "t": title,
            "s": clean(summary),
            "l": link,
            "i": web_url(image.replace("&amp;", "&")) if image else None,
            "p": pub["id"],
            "c": feed["category"],
            "k": feed["kind"],
            "d": int(stamp(e)),
            "v": vid,
            "sh": bool(vid) and "/shorts/" in link,
        })
    return out


def norm_title(t):
    return re.sub(r"[^\w]+", " ", t.lower()).strip()


PAYWALL = re.compile(r"(réservée? aux abonnés|il vous reste \d|la suite est réservée|pour lire la suite|"
                     r"abonnez-vous pour|contenu réservé|déjà abonné|cet article est réservé)", re.I)
JUNK = re.compile(r"^(lire aussi|à lire aussi|a lire aussi|lire également|à voir aussi|voir aussi|newsletter|"
                  r"abonnez-vous|inscrivez-vous|suivez-nous|publicité|tous droits réservés|votre navigateur|©)", re.I)


OG_IMAGE = re.compile(r'<meta[^>]+(?:property|name)=["\'](?:og:image|twitter:image)["\'][^>]*content=["\']([^"\']+)', re.I)
OG_IMAGE_REV = re.compile(r'<meta[^>]+content=["\']([^"\']+)["\'][^>]*(?:property|name)=["\'](?:og:image|twitter:image)["\']', re.I)


def extract(item):
    """Full text of an article as reading blocks (via trafilatura)."""
    try:
        page = get(item["l"], timeout=20).text
    except Exception:
        return None
    xml = trafilatura.extract(page, url=item["l"], output_format="xml", include_images=True,
                              include_comments=False, include_tables=False, with_metadata=True,
                              favor_precision=True)
    if not xml:
        return None
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return None
    blocks, truncated = [], False
    main = root.find("main")
    if main is None:
        main = root
    parent = {c: p for p in main.iter() for c in p}
    for el in main.iter():
        tag = el.tag
        text = clean("".join(el.itertext())) if tag in ("p", "head", "quote", "item") else ""
        if tag == "p" and parent.get(el) is not None and parent[el].tag in ("quote", "item", "cell"):
            continue
        if tag == "p" and text:
            if PAYWALL.search(text) and len(text) < 260:
                truncated = True
                continue
            if JUNK.search(text):
                continue
            blocks.append({"t": "p", "x": text})
        elif tag == "head" and text:
            if el.get("rend") == "h1" or norm_title(text) == norm_title(item["t"]):
                continue
            blocks.append({"t": "h", "x": text})
        elif tag == "quote" and text:
            blocks.append({"t": "q", "x": text})
        elif tag == "item" and text:
            blocks.append({"t": "li", "x": text})
        elif tag == "graphic" and el.get("src", "").startswith("http"):
            blocks.append({"t": "img", "u": el.get("src"), "x": clean(el.get("alt") or "")})
    # quotes/items also contain <p>: drop duplicated consecutive texts
    dedup, seen = [], set()
    for b in blocks:
        key = (b["t"], b.get("x") or b.get("u"))
        if key in seen:
            continue
        seen.add(key)
        dedup.append(b)
    blocks = dedup[:400]
    chars = sum(len(b.get("x", "")) for b in blocks if b["t"] != "img")
    if chars < 200:
        return None
    if re.search(r'"isAccessibleForFree"\s*:\s*"?false', page, re.I) or chars < 700:
        truncated = True
    m = OG_IMAGE.search(page) or OG_IMAGE_REV.search(page)
    hero = web_url(html.unescape(m.group(1))) if m else None
    words = sum(len(b.get("x", "").split()) for b in blocks if b["t"] != "img")
    return {
        "title": root.get("title") or item["t"],
        "byline": root.get("author"),
        "hero": hero,
        "standfirst": clean(root.get("description") or ""),
        "blocks": [b for b in blocks if not (b["t"] == "img" and b.get("u") == hero)],
        "truncated": truncated,
        "minutes": max(1, round(words / 230)),
    }


def main():
    src = json.load(open(SOURCES, encoding="utf-8"))
    pubs = {p["id"]: p for p in src["publishers"]}
    items, ok = [], 0
    with cf.ThreadPoolExecutor(12) as pool:
        jobs = {pool.submit(parse_feed, f, pubs[f["publisher"]]): f for f in src["feeds"]}
        for job in cf.as_completed(jobs):
            try:
                got = job.result()
                items += got
                ok += 1 if got else 0
            except Exception as ex:
                print(f"feed failed: {jobs[job]['url']} ({ex})", file=sys.stderr)
    print(f"feeds ok: {ok}/{len(src['feeds'])}, raw items: {len(items)}")

    cutoff = NOW - 3 * 86400
    merged = {}
    for it in sorted(items, key=lambda x: x["d"], reverse=True):
        if not (cutoff <= it["d"] <= NOW + 3600):
            continue
        prev = merged.get(it["id"])
        if prev is None or (prev["c"] == "UNE" and it["c"] != "UNE"):
            merged[it["id"]] = it
    seen_titles, final = set(), []
    for it in sorted(merged.values(), key=lambda x: x["d"], reverse=True):
        key = norm_title(it["t"])
        if key in seen_titles:
            continue
        seen_titles.add(key)
        final.append(it)
    final = final[:MAX_ITEMS]

    os.makedirs(f"{OUT}/data/a", exist_ok=True)
    articles = [it for it in final if not it["v"]][:MAX_ARTICLES]
    done = 0
    with cf.ThreadPoolExecutor(10) as pool:
        for it, art in zip(articles, pool.map(extract, articles)):
            if not art:
                continue
            safe = it["id"].replace(":", "_")
            with open(f"{OUT}/data/a/{safe}.json", "w", encoding="utf-8") as f:
                json.dump(art, f, ensure_ascii=False, separators=(",", ":"))
            it["a"] = 1
            it["m"] = art["minutes"]
            if not it.get("i") and art.get("hero"):
                it["i"] = art["hero"]
            lead = next((b["x"] for b in art["blocks"] if b["t"] == "p" and len(b["x"]) > 60), None)
            if lead:
                it["lead"] = lead[:300]
            done += 1
    print(f"articles extracted: {done}/{len(articles)}")

    feed = {
        "updatedAt": int(NOW),
        "publishers": [{"id": p["id"], "name": p["name"], "domain": p["domain"]} for p in src["publishers"]],
        "items": final,
    }
    with open(f"{OUT}/data/feed.json", "w", encoding="utf-8") as f:
        json.dump(feed, f, ensure_ascii=False, separators=(",", ":"))
    print(f"wrote {len(final)} items")


if __name__ == "__main__":
    main()
