#!/usr/bin/env python3
"""Collect word frequencies from official Django/FastAPI prose for vocabulary curation."""
import collections
import json
import re
import urllib.request
from bs4 import BeautifulSoup

DJANGO = [
    "intro/overview/", "intro/tutorial01/", "intro/tutorial02/", "intro/tutorial03/",
    "intro/tutorial04/", "intro/tutorial05/", "intro/tutorial06/", "intro/tutorial07/",
    "topics/db/models/", "topics/db/queries/", "topics/db/aggregation/",
    "topics/http/urls/", "topics/http/views/", "topics/http/sessions/",
    "topics/forms/", "topics/forms/modelforms/", "topics/testing/overview/",
    "topics/auth/default/", "topics/security/", "topics/cache/", "topics/migrations/",
    "topics/files/", "topics/email/", "ref/request-response/", "ref/models/querysets/",
]
FASTAPI = [
    "tutorial/first-steps/", "tutorial/path-params/", "tutorial/query-params/",
    "tutorial/body/", "tutorial/response-model/", "tutorial/dependencies/",
    "tutorial/security/first-steps/", "tutorial/handling-errors/",
    "tutorial/testing/", "tutorial/background-tasks/", "tutorial/middleware/",
    "tutorial/cors/", "tutorial/sql-databases/", "tutorial/metadata/",
    "advanced/response-directly/", "advanced/custom-response/", "advanced/testing-dependencies/",
]

pages = {}
for path in DJANGO + FASTAPI:
    url = ("https://docs.djangoproject.com/en/5.2/" if path in DJANGO else "https://fastapi.tiangolo.com/") + path
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "EnglishCards vocabulary builder/1.0"})
        with urllib.request.urlopen(req, timeout=25) as response:
            soup = BeautifulSoup(response.read(), "html.parser")
        body = soup.select_one("#main-content") or soup.select_one("article") or soup.select_one("main")
        if not body:
            raise ValueError("main prose missing")
        for node in body.select("script,style,pre,code,nav,footer,aside,.highlight,.highlight-default"):
            node.decompose()
        text = " ".join(body.stripped_strings)
        words = re.findall(r"\b[a-zA-Z][a-zA-Z'-]*\b", text.lower())
        pages[url] = {"word_count": len(words), "frequencies": dict(collections.Counter(words))}
        print(len(pages), len(words), url, flush=True)
    except Exception as exc:
        print("SKIP", url, repr(exc), flush=True)

with open("/tmp/english_docs_corpus.json", "w", encoding="utf-8") as out:
    json.dump(pages, out, ensure_ascii=False)
print("SAVED", len(pages), "pages")
