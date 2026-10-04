"""Stand-in for the n8n + Apify scraper, so ReachFlow can be demoed with no outside accounts.

It speaks the same contract as the real workflow's webhook:
  POST  {cities: [...], keywords: [...], maxResults: N}
  ->    {results: [{title, city, phone, address, website, email, allEmails}]}

Businesses are generated deterministically (same input -> same output) from word lists,
use reserved example domains, and a few carry the kind of junk "emails" that real scrapers
produce (image file names), so the email-audit feature has something to show.
"""
import hashlib
import json
import os
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

DELAY = float(os.environ.get("MOCK_DELAY_SECONDS", "3"))
PORT = int(os.environ.get("PORT", "5679"))

KINDS = ["Metallbau", "Elektrotechnik", "Softwarehaus", "Hotel", "Bäckerei", "Logistik",
         "Sanitär", "Autohaus", "Pflegedienst", "Druckerei", "Gartenbau", "Steuerberatung"]
SUFFIX = ["GmbH", "AG", "& Söhne", "KG", "e.K."]
NAMES = ["Schneider", "Fischer", "Weber", "Meyer", "Wagner", "Becker", "Hoffmann", "Koch",
         "Richter", "Klein", "Wolf", "Neumann", "Schwarz", "Braun", "Zimmermann"]
STREETS = ["Hauptstraße", "Bahnhofstraße", "Gartenweg", "Industrieallee", "Lindenstraße"]


def names(value):
    """Accept strings or objects like {name|value|label: ...}, as the real workflow does."""
    out = []
    for item in value or []:
        if isinstance(item, dict):
            item = item.get("name") or item.get("value") or item.get("label")
        if isinstance(item, str) and item.strip():
            out.append(item.strip())
    return out


def slug(text):
    text = text.lower().replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
    return re.sub(r"[^a-z0-9]+", "-", text).strip("-")


def business(city, keyword, n):
    h = int(hashlib.sha256(f"{city}|{keyword}|{n}".encode()).hexdigest(), 16)
    name = NAMES[h % len(NAMES)]
    kind = KINDS[(h >> 8) % len(KINDS)]
    suffix = SUFFIX[(h >> 16) % len(SUFFIX)]
    title = f"{name} {kind} {suffix}"
    domain = f"{slug(name + '-' + kind)}-{slug(city)}.example"
    emails = [f"kontakt@{domain}", f"bewerbung@{domain}"]
    if (h >> 24) % 5 == 0:
        emails.append(f"logo@2x-{slug(name)}.png")  # scraper artifact: an image file name
    return {
        "title": title,
        "city": city,
        "phone": f"+49 {30 + (h >> 4) % 60} {1000000 + (h >> 12) % 8999999}",
        "address": f"{STREETS[(h >> 20) % len(STREETS)]} {1 + (h >> 28) % 90}, {city}",
        "website": f"https://www.{domain}",
        "email": emails[0],
        "allEmails": emails,
    }


class Handler(BaseHTTPRequestHandler):
    def read_body(self):
        """Clients differ: Content-Length (curl, n8n) or chunked (Java's HttpURLConnection)."""
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            chunks = []
            while True:
                size = int(self.rfile.readline().split(b";")[0].strip() or b"0", 16)
                if size == 0:
                    self.rfile.readline()  # trailing CRLF after the last chunk
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.readline()
            return b"".join(chunks)
        return self.rfile.read(int(self.headers.get("Content-Length") or 0))

    def do_POST(self):
        body = self.read_body() or b"{}"
        try:
            data = json.loads(body)
        except ValueError:
            data = {}
        cities, keywords = names(data.get("cities")), names(data.get("keywords"))
        limit = int(data.get("maxResults") or 20)
        time.sleep(DELAY)  # the real scrape takes minutes; this keeps the progress UI visible
        results, seen = [], set()
        n = 0
        while len(results) < limit and cities and keywords and n < limit * 4:
            city, keyword = cities[n % len(cities)], keywords[(n // len(cities)) % len(keywords)]
            item = business(city, keyword, n)
            n += 1
            if item["website"] in seen:
                continue
            seen.add(item["website"])
            results.append(item)
        payload = json.dumps({"results": results}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def do_GET(self):  # health check
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"mock scraper ok")

    def log_message(self, fmt, *args):
        print("mock-scraper:", fmt % args, flush=True)


if __name__ == "__main__":
    print(f"mock scraper on :{PORT}, delay {DELAY}s", flush=True)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
