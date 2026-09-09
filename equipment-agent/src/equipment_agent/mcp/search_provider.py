from __future__ import annotations

import base64
from typing import Any
from urllib.parse import parse_qs, quote_plus, unquote, urlparse
from xml.etree import ElementTree

import httpx
from bs4 import BeautifulSoup

from equipment_agent.tools.web_search import sanitize_search_query


DEFAULT_PUBLIC_SEARCH_ENDPOINTS = [
    "https://www.bing.com/search?format=rss",
    "https://cn.bing.com/search",
    "https://www.sogou.com/web",
    "https://duckduckgo.com/html/",
    "https://www.bing.com/search",
]

GENERIC_TOPIC_SUFFIXES = (
    "开放平台",
    "小程序",
    "频道",
    "平台",
    "文档",
    "说明",
    "指南",
    "规则",
    "官网",
    "官方",
)


class PublicWebSearchProvider:
    """The only built-in component allowed to make public-web requests.

    Agent workflow code never imports or calls this provider directly. It is
    exposed only through the built-in MCP ``web_search`` tool.
    """

    def __init__(
        self,
        endpoints: list[str] | None = None,
        *,
        timeout: float = 8.0,
    ) -> None:
        self.endpoints = [
            str(endpoint).strip()
            for endpoint in (endpoints or DEFAULT_PUBLIC_SEARCH_ENDPOINTS)
            if str(endpoint).strip()
        ]
        self.timeout = max(1.0, float(timeout or 8.0))

    async def search(
        self,
        query: str,
        *,
        limit: int = 5,
        domains: list[str] | None = None,
    ) -> list[dict[str, Any]]:
        safe_query = sanitize_search_query(query)
        if not safe_query:
            return []
        safe_limit = max(1, min(int(limit or 5), 10))
        safe_domains = [
            str(domain).strip().lower()
            for domain in (domains or [])
            if str(domain).strip()
        ][:8]
        constrained_query = self._with_domain_filters(safe_query, safe_domains)
        errors: list[str] = []
        async with httpx.AsyncClient(timeout=self.timeout, follow_redirects=True) as client:
            for endpoint in self.endpoints:
                variants = self._query_variants(
                    endpoint,
                    constrained_query,
                    safe_query,
                    safe_domains,
                )
                for search_query in variants:
                    url = self._build_url(endpoint, search_query)
                    try:
                        response = await client.get(
                            url,
                            headers={
                                "User-Agent": "Mozilla/5.0 (compatible; EquipmentAgentMCP/1.0; +readonly-search)",
                                "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.7",
                            },
                        )
                        response.raise_for_status()
                    except httpx.HTTPError as exc:
                        host = urlparse(endpoint).netloc or endpoint
                        errors.append(f"{host}: {exc.__class__.__name__}")
                        break
                    results = self._parse_results(response.text, limit=safe_limit, domains=safe_domains)
                    if results:
                        return results
                else:
                    errors.append(f"{urlparse(endpoint).netloc or endpoint}: no results")
        raise RuntimeError("; ".join(errors) or "built-in MCP search provider unavailable")

    def _build_url(self, endpoint: str, query: str) -> str:
        parsed = urlparse(endpoint)
        param = "query" if parsed.netloc.endswith("sogou.com") else "q"
        separator = "&" if "?" in endpoint else "?"
        return f"{endpoint}{separator}{param}={quote_plus(query)}"

    def _query_variants(
        self,
        endpoint: str,
        constrained_query: str,
        safe_query: str,
        domains: list[str],
    ) -> list[str]:
        host = urlparse(endpoint).netloc.lower()
        if not domains or not any(search_host in host for search_host in ["bing.com", "sogou.com"]):
            return [constrained_query]
        variants = [constrained_query, safe_query]
        variants.extend(f"{safe_query} site:{domain}" for domain in domains[:5])
        for broad_query in self._broad_queries(safe_query):
            variants.extend(f"{broad_query} site:{domain}" for domain in domains[:5])
        return list(dict.fromkeys(variants))

    def _broad_queries(self, query: str) -> list[str]:
        terms = [term for term in query.split() if len(term.strip()) >= 2]
        if len(terms) < 2:
            return []
        topic = terms[0]
        variants = [topic]
        for suffix in GENERIC_TOPIC_SUFFIXES:
            if topic.endswith(suffix) and len(topic) > len(suffix) + 1:
                variants.append(topic[:-len(suffix)])
                break
        return list(dict.fromkeys(variants))

    def _with_domain_filters(self, query: str, domains: list[str]) -> str:
        if not domains:
            return query
        domain_filter = " OR ".join(f"site:{domain}" for domain in domains[:5])
        return f"{query} ({domain_filter})"

    def _parse_results(self, document: str, *, limit: int, domains: list[str]) -> list[dict[str, Any]]:
        rss_results = self._parse_rss_results(document, limit=limit, domains=domains)
        if rss_results:
            return rss_results

        soup = BeautifulSoup(document, "html.parser")
        results: list[dict[str, Any]] = []
        selectors = [
            (".result", ".result__a", ".result__snippet"),
            (
                "li.b_algo, .res-list, .vrwrap, .rb",
                "h2 a, h3 a, a",
                ".b_caption p, .c-abstract, .res-desc, .str_info, p",
            ),
        ]
        for item_selector, link_selector, snippet_selector in selectors:
            for item in soup.select(item_selector):
                link = item.select_one(link_selector)
                if link is None:
                    continue
                title = link.get_text(" ", strip=True)
                href = str(link.get("data-mdurl") or link.get("href") or "")
                url = self._normalize_url(href)
                if not title or not url or not self._domain_allowed(url, domains):
                    continue
                snippet_node = item.select_one(snippet_selector)
                snippet = snippet_node.get_text(" ", strip=True) if snippet_node else ""
                results.append(self._result(title, url, snippet))
                if len(results) >= limit:
                    return results
            if results:
                return results

        seen: set[str] = set()
        for link in soup.select("a[href]"):
            title = link.get_text(" ", strip=True)
            href = str(link.get("data-mdurl") or link.get("href") or "")
            url = self._normalize_url(href)
            if not title or not url or url in seen or not self._domain_allowed(url, domains):
                continue
            context = link.find_parent()
            snippet = context.get_text(" ", strip=True) if context is not None else ""
            if len(snippet) <= len(title):
                sibling = context.find_next("p") if context is not None else None
                snippet = sibling.get_text(" ", strip=True) if sibling is not None else snippet
            results.append(self._result(title, url, snippet))
            seen.add(url)
            if len(results) >= limit:
                break
        return results

    def _parse_rss_results(
        self,
        document: str,
        *,
        limit: int,
        domains: list[str],
    ) -> list[dict[str, Any]]:
        if "<rss" not in document[:500].lower():
            return []
        try:
            root = ElementTree.fromstring(document)
        except ElementTree.ParseError:
            return []

        results: list[dict[str, Any]] = []
        for item in root.findall("./channel/item"):
            title = str(item.findtext("title") or "").strip()
            url = self._normalize_url(str(item.findtext("link") or "").strip())
            if not title or not url or not self._domain_allowed(url, domains):
                continue
            snippet = BeautifulSoup(
                str(item.findtext("description") or ""),
                "html.parser",
            ).get_text(" ", strip=True)
            results.append(self._result(title, url, snippet))
            if len(results) >= limit:
                break
        return results

    def _result(self, title: str, url: str, snippet: str) -> dict[str, Any]:
        return {
            "title": title[:200],
            "url": url[:1000],
            "snippet": snippet[:1600],
            "source": urlparse(url).netloc.lower()[:160],
        }

    def _normalize_url(self, href: str) -> str:
        if href.startswith("//"):
            href = "https:" + href
        parsed = urlparse(href)
        if parsed.netloc.lower().endswith("bing.com") and parsed.path.startswith("/ck/a"):
            target = parse_qs(parsed.query).get("u", [""])[0]
            decoded = self._decode_bing_target(target)
            if decoded:
                return decoded
        if parsed.path.startswith("/l/"):
            target = parse_qs(parsed.query).get("uddg", [""])[0]
            if target:
                return unquote(target)
        return href if parsed.scheme in {"http", "https"} else ""

    def _decode_bing_target(self, value: str) -> str:
        encoded = value[2:] if value.startswith("a1") else ""
        if not encoded:
            return ""
        try:
            padding = "=" * (-len(encoded) % 4)
            target = base64.urlsafe_b64decode(encoded + padding).decode("utf-8")
        except (UnicodeDecodeError, ValueError):
            return ""
        return target if urlparse(target).scheme in {"http", "https"} else ""

    def _domain_allowed(self, url: str, domains: list[str]) -> bool:
        if not domains:
            return True
        host = urlparse(url).netloc.lower()
        return any(host == domain or host.endswith(f".{domain}") for domain in domains)
