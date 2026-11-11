#!/usr/bin/env python3
"""xrnwallet sync server: the home server's copy of the wallet, behind HTTP Basic auth.

One JSON document per user (the app's backup format, with each bill's photo named by its file name
and the app lock left out) plus the photos themselves. A phone sends the whole document after a
change, with If-Match, so a phone that never synced (or was reinstalled) can't wipe the copy here
by accident: it has to take this copy, or say on purpose that its own replaces it (If-Match: *).
Made to run on a small home server next to Radicale, published under a path (/xrnwallet) by a
reverse proxy such as Tailscale Funnel, which may or may not strip that prefix. Standard library
only, plus `bcrypt` to read an htpasswd file, so the calendar password is the sync password.

    GET  /state               200, the document; ETag: "<version>" (0 while there is none)
                              If-None-Match: "<version>" -> 304 when unchanged
    PUT  /state               needs If-Match: "<version>" (or "*"); 200 with the new ETag, or
                              412 if the copy here isn't the one the phone last saw
    GET  /versions            the earlier copies kept here, newest first, as a JSON list of
                              {id, version, updated, by, bills}
    GET  /versions/<id>       one earlier copy
    GET  /photos              the names of the photos kept here, as a JSON list
    PUT  /photos/<name>       a photo's bytes
    GET  /photos/<name>       a photo
    GET  /health              200 "ok", no login (for monitoring)

/state answers carry X-Content-SHA256, the hash of the body as it was sent. When an upload's answer
is lost the phone is left a version behind what it sent, and its next write gets a 412. Comparing
this hash with what it tried to send tells that apart from someone else having written here.

They also carry X-Tag, a name each write gets at random, and every copy keeps the tags of the ones
it was written on top of. A version number alone can't tell copies apart: once the data here is
restored from a backup and written to again, the same numbers come back for different copies. So a
phone sends the tag of the copy it last saw as X-Base-Tag, beside If-None-Match or If-Match, and
then a 304 or a write needs that same copy, and a 200 says in X-Follows (1 or 0) whether its copy
was written on top of the phone's. Without a tag on either side, as from an older app or a copy
from before tags, the number decides alone. The ETag stays a bare number for those older apps.

Every write keeps the copy it replaces: the last KEEP_VERSIONS of them in history/, and the first
of each day in daily/ for KEEP_DAYS. A photo goes once none of those name it any more.

    python3 xrnwallet-sync.py --data ~/xrnwallet-sync/data --users ~/.config/xrn-sync/users
"""
import argparse
import base64
import hashlib
import ipaddress
import json
import os
import re
import secrets
import shutil
import sys
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

MAX_BODY = 8 * 1024 * 1024
MAX_PHOTO = 15 * 1024 * 1024
FAIL_DELAY_S = 2  # after a wrong password, as Radicale does
AUTH_CACHE_S = 600  # a bcrypt check costs ~0.3s on a phone; don't pay it per request
FAIL_LIMIT = 10  # wrong logins from one address within FAIL_WINDOW_S, then 429 until they age out
FAIL_WINDOW_S = 15 * 60
KEEP_VERSIONS = 20
KEEP_DAYS = 60
KEEP_TAGS = 1000  # a phone this many writes behind is asked, as it can't be told it's on the same line
PRUNE_EVERY_S = 600  # photos are swept at most this often per user
PHOTO_GRACE_S = 86400  # a photo this new stays even if nothing names it yet
PREFIX = "/xrnwallet"
USER_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
# The same rule as the app's isSafePhotoName: a plain file name, no path, no leading dot.
PHOTO_NAME = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
HISTORY_ID = re.compile(r"\d{1,12}")
DAILY_ID = re.compile(r"\d{4}-\d{2}-\d{2}")
PHOTO_TYPES = {
    ".jpg": "image/jpeg",
    ".jpeg": "image/jpeg",
    ".png": "image/png",
    ".webp": "image/webp",
    ".heic": "image/heic",
    ".heif": "image/heif",
    ".gif": "image/gif",
}
SECURITY_HEADERS = {
    "X-Content-Type-Options": "nosniff",
    "X-Frame-Options": "DENY",
    "Referrer-Policy": "no-referrer",
    "Content-Security-Policy": "default-src 'none'; frame-ancestors 'none'",
}

args = None
lock = threading.Lock()  # state.json, history/ and daily/
photo_lock = threading.Lock()
auth_cache = {}  # sha256(user:password) -> (user, expires, users-file mtime)
fail_lock = threading.Lock()
failures = {}  # client address -> monotonic times of its recent wrong logins
pruned = {}  # user -> time.monotonic() of the last photo sweep


def users_mtime():
    try:
        return os.stat(args.users).st_mtime
    except OSError:
        return 0


def check_login(header):
    """The user name for a valid `Authorization: Basic ...`, else None."""
    if not header or not header.startswith("Basic "):
        return None
    try:
        user, _, password = base64.b64decode(header[6:]).decode("utf-8").partition(":")
    except Exception:
        return None
    if not USER_NAME.fullmatch(user):  # it names a folder here
        return None
    key = hashlib.sha256(f"{user}:{password}".encode()).hexdigest()
    mtime = users_mtime()
    hit = auth_cache.get(key)
    if hit and hit[1] > time.time() and hit[2] == mtime:
        return hit[0]
    import bcrypt  # only needed once someone logs in

    try:
        checked, spare = False, None
        with open(args.users, encoding="utf-8") as f:
            for line in f:
                name, _, hashed = line.strip().partition(":")
                spare = hashed or spare
                if name == user and hashed:
                    checked = True
                    if bcrypt.checkpw(password.encode(), hashed.encode()):
                        auth_cache[key] = (user, time.time() + AUTH_CACHE_S, mtime)
                        return user
        if not checked and spare:
            # A name that isn't there takes as long as a wrong password for one that is, or
            # the time an answer takes says which names exist. The result is never used.
            bcrypt.checkpw(password.encode(), spare.encode())
    except (OSError, ValueError):
        pass
    return None


def client_address(handler):
    """Who a wrong login is counted for. Behind the local proxy (Funnel) that's the last
    X-Forwarded-For hop, the one the proxy added: anything before it came from the caller, who
    could make up a new address for every guess. The header is only trusted from a loopback peer,
    for the same reason. An IPv6 address counts as its /64: a line comes with at least that many,
    so counted one by one its owner had a new address for every guess and never reached the limit."""
    peer = handler.client_address[0]
    forwarded = handler.headers.get("X-Forwarded-For")
    addr = forwarded.split(",")[-1].strip() if forwarded and peer in ("127.0.0.1", "::1") else peer
    try:
        ip = ipaddress.ip_address(addr)
    except ValueError:
        return addr
    if ip.version == 4:
        return addr
    if ip.ipv4_mapped:
        return str(ip.ipv4_mapped)
    return f"{ipaddress.IPv6Address(int(ip) >> 64 << 64)}/64"


def from_another_site(headers):
    """Whether a request came from another site's page. A browser that was once given the password
    in its own sign-in box sends it again with a form any site posts here, and a text/plain form
    body can be JSON. The browser says where the page was in Sec-Fetch-Site; one too old for that
    still sends Origin with a POST, held against this host's name (or the proxy's X-Forwarded-Host).
    The app sends neither."""
    origin = (headers.get("Origin") or "").strip().lower()
    if origin.startswith(("chrome-extension://", "moz-extension://")):
        return False
    site = (headers.get("Sec-Fetch-Site") or "").strip().lower()
    if site:
        return site in ("cross-site", "same-site")
    scheme, _, host = origin.partition("://")
    if scheme not in ("http", "https"):
        return False  # no Origin, or "null"
    ours = [headers.get("Host") or "", *(headers.get("X-Forwarded-Host") or "").split(",")]
    return host_key(host) not in {host_key(h) for h in ours if h.strip()}


def host_key(host):
    """A host as Origin and Host both write it: lower case, without the default port."""
    return host.strip().lower().removesuffix(":443").removesuffix(":80")


def admit(addr, counted):
    """False while addr is locked out. Otherwise a login that is [counted] is taken as a wrong one
    there and then, in the same step as the check, and a right password clears it again. Checked
    first and noted after the slow password check, guesses sent side by side all got through."""
    now = time.monotonic()
    with fail_lock:
        recent = [t for t in failures.get(addr, ()) if now - t < FAIL_WINDOW_S]
        if len(recent) >= FAIL_LIMIT:
            failures[addr] = recent
            return False
        if counted:
            if len(failures) > 10000:  # a flood from many addresses: forget the stale ones
                for a in [a for a, ts in failures.items() if now - ts[-1] >= FAIL_WINDOW_S]:
                    del failures[a]
            recent.append(now)
        if recent:
            failures[addr] = recent
        else:
            failures.pop(addr, None)
        return True


def user_dir(user, *parts):
    return os.path.join(args.data, "users", user, *parts)


def read_doc(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except (OSError, ValueError):
        return None


def read_state(user):
    return read_doc(user_dir(user, "state.json")) or {"version": 0, "data": {}}


def write_json(path, doc):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + ".tmp"
    with open(tmp, "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, path)


def keep_old(user, cur):
    """Files away the copy a write is about to replace, and drops what's past keeping."""
    if not cur.get("version"):
        return
    history = user_dir(user, "history")
    write_json(os.path.join(history, f"{cur['version']}.json"), cur)
    versions = sorted(int(n[:-5]) for n in os.listdir(history) if re.fullmatch(r"\d+\.json", n))
    for v in versions[:-KEEP_VERSIONS]:
        os.remove(os.path.join(history, f"{v}.json"))

    daily = user_dir(user, "daily")
    day = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    if not os.path.exists(os.path.join(daily, f"{day}.json")):
        write_json(os.path.join(daily, f"{day}.json"), cur)
    days = sorted(n for n in os.listdir(daily) if re.fullmatch(r"\d{4}-\d{2}-\d{2}\.json", n))
    for n in days[:-KEEP_DAYS]:
        os.remove(os.path.join(daily, n))


def bills_of(doc):
    data = (doc or {}).get("data")
    bills = data.get("bills") if isinstance(data, dict) else None
    return bills if isinstance(bills, list) else []


def earlier_copies(user):
    """What's kept, newest first. A copy in both folders is listed once, by its history name, since
    that's the one that goes first and its daily twin is still there when it does."""
    found = {}
    for sub, pattern in (("daily", DAILY_ID), ("history", HISTORY_ID)):
        folder = user_dir(user, sub)
        if not os.path.isdir(folder):
            continue
        for n in os.listdir(folder):
            if not n.endswith(".json") or not pattern.fullmatch(n[:-5]):
                continue
            doc = read_doc(os.path.join(folder, n))
            if doc and isinstance(doc.get("version"), int):
                found[doc["version"]] = {
                    "id": n[:-5],
                    "version": doc["version"],
                    "updated": doc.get("updated", ""),
                    "by": doc.get("by", ""),
                    "bills": len(bills_of(doc)),
                }
    return [found[v] for v in sorted(found, reverse=True)]


def earlier_path(user, name):
    if HISTORY_ID.fullmatch(name):
        return user_dir(user, "history", f"{name}.json")
    if DAILY_ID.fullmatch(name):
        return user_dir(user, "daily", f"{name}.json")
    return None


def photos_named(doc):
    return {b["photo"] for b in bills_of(doc) if isinstance(b, dict) and isinstance(b.get("photo"), str)}


def prune_photos(user, force=False):
    """Deletes the photos that no kept copy names (and that aren't brand new)."""
    if not force and time.monotonic() - pruned.get(user, -PRUNE_EVERY_S) < PRUNE_EVERY_S:
        return
    pruned[user] = time.monotonic()
    folder = user_dir(user, "photos")
    if not os.path.isdir(folder):
        return
    with lock:
        named = photos_named(read_state(user))
        for sub in ("history", "daily"):
            d = user_dir(user, sub)
            if os.path.isdir(d):
                for n in os.listdir(d):
                    if n.endswith(".json"):
                        named |= photos_named(read_doc(os.path.join(d, n)))
    now = time.time()
    with photo_lock:
        for n in os.listdir(folder):
            path = os.path.join(folder, n)
            try:
                if n not in named and now - os.stat(path).st_mtime > PHOTO_GRACE_S:
                    os.remove(path)
            except OSError:
                pass


def etag_version(header):
    return (header or "").strip().removeprefix("W/").strip('"')


def doc_headers(doc):
    return {
        "ETag": f'"{doc["version"]}"',
        "X-Content-SHA256": doc.get("sha256", ""),
        "X-Updated": doc.get("updated", ""),
        "X-Tag": doc.get("tag", ""),
    }


def same_copy(doc, tag):
    """Whether the copy a phone names by its X-Base-Tag is this one, as far as the tags can tell."""
    here = doc.get("tag", "")
    return not tag or not here or tag == here


def base_tag(handler):
    return (handler.headers.get("X-Base-Tag") or "").strip()


class Handler(BaseHTTPRequestHandler):
    server_version = "xrnwallet-sync/1"
    timeout = 120  # seconds of silence before a connection is dropped, so a stalled client can't hold a thread

    def log_message(self, fmt, *a):
        who = self.headers.get("X-Forwarded-For", self.client_address[0]) if self.headers else "-"
        sys.stderr.write(f"{who} {fmt % a}\n")

    def parse_request(self):
        if not super().parse_request():
            return False
        # Before the login, which a page elsewhere can have the browser pass for it.
        if self.command not in ("GET", "HEAD", "OPTIONS") and from_another_site(self.headers):
            self.send(403, "refused: sent from another site's page")
            return False
        return True

    def route(self):
        path = self.path.partition("?")[0]
        if path == PREFIX or path.startswith(PREFIX + "/"):
            path = path[len(PREFIX):] or "/"
        return path

    def send(self, code, body=b"", ctype="text/plain; charset=utf-8", headers=None):
        if isinstance(body, str):
            body = body.encode()
        self.send_response(code)
        self.send_header("Cache-Control", "no-store")
        for k, v in SECURITY_HEADERS.items():
            self.send_header(k, v)
        if code != 304:
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(len(body)))
        for k, v in (headers or {}).items():
            self.send_header(k, v)
        self.end_headers()
        if self.command != "HEAD" and code != 304:
            self.wfile.write(body)

    def send_file(self, path, ctype):
        """A file streamed as is; photos are compressed already and run to megabytes."""
        try:
            f = open(path, "rb")
        except OSError:
            return self.send(404, "not found")
        with f:
            # Files are swapped in by rename, so the open file is one whole version.
            size = os.fstat(f.fileno()).st_size
            self.send_response(200)
            self.send_header("Cache-Control", "no-store")
            for k, v in SECURITY_HEADERS.items():
                self.send_header(k, v)
            self.send_header("Content-Type", ctype)
            self.send_header("Content-Length", str(size))
            self.end_headers()
            shutil.copyfileobj(f, self.wfile)

    def length(self, limit):
        """The request body's length, or None once an error has been answered."""
        try:
            length = int(self.headers.get("Content-Length") or 0)
        except ValueError:
            self.send(400, "bad Content-Length")
            return None
        if length <= 0 or length > limit:
            self.send(413, "body missing or too large")
            return None
        return length

    def login(self):
        addr = client_address(self)
        header = self.headers.get("Authorization")
        if not admit(addr, counted=bool(header)):
            if header:
                time.sleep(FAIL_DELAY_S)
            self.send(429, "too many wrong passwords; try again later",
                      headers={"Retry-After": str(FAIL_WINDOW_S)})
            return None
        user = check_login(header)
        if not user:
            if header:  # a request with none guessed nothing, and needn't hold a thread for it
                time.sleep(FAIL_DELAY_S)
            self.send(401, "login required", headers={"WWW-Authenticate": 'Basic realm="xrnwallet"'})
            return None
        with fail_lock:
            failures.pop(addr, None)
        return user

    def do_GET(self):
        path = self.route()
        if path == "/health":
            return self.send(200, "ok")
        known = path in ("/state", "/photos", "/versions")
        if not known and not path.startswith(("/photos/", "/versions/")):
            return self.send(404, "not found")
        user = self.login()
        if not user:
            return
        if path == "/photos":
            folder = user_dir(user, "photos")
            names = sorted(n for n in os.listdir(folder) if PHOTO_NAME.fullmatch(n)) if os.path.isdir(folder) else []
            return self.send(200, json.dumps(names), "application/json; charset=utf-8")
        if path.startswith("/photos/"):
            name = path[len("/photos/"):]
            if not PHOTO_NAME.fullmatch(name) or ".." in name:
                return self.send(404, "not found")
            ctype = PHOTO_TYPES.get(os.path.splitext(name)[1].lower(), "application/octet-stream")
            return self.send_file(user_dir(user, "photos", name), ctype)
        if path == "/versions":
            return self.send(200, json.dumps(earlier_copies(user)), "application/json; charset=utf-8")
        if path.startswith("/versions/"):
            where = earlier_path(user, path[len("/versions/"):])
            doc = read_doc(where) if where else None
            if not doc:
                return self.send(404, "not found")
            body = json.dumps(doc.get("data") or {}, ensure_ascii=False, separators=(",", ":"))
            return self.send(200, body, "application/json; charset=utf-8", doc_headers(doc))
        with lock:
            doc = read_state(user)
        headers = doc_headers(doc)
        tag = base_tag(self)
        if etag_version(self.headers.get("If-None-Match")) == str(doc["version"]) and same_copy(doc, tag):
            return self.send(304, headers=headers)
        if tag and doc.get("tag"):
            # Whether the phone can take this as newer than its own, or must ask first.
            headers["X-Follows"] = "1" if tag == doc["tag"] or tag in doc.get("trail", ()) else "0"
        body = json.dumps(doc.get("data") or {}, ensure_ascii=False, separators=(",", ":"))
        self.send(200, body, "application/json; charset=utf-8", headers)

    def do_PUT(self):
        path = self.route()
        if path != "/state" and not path.startswith("/photos/"):
            return self.send(404, "not found")
        user = self.login()
        if not user:
            return
        if path.startswith("/photos/"):
            return self.put_photo(user, path[len("/photos/"):])
        length = self.length(MAX_BODY)
        if length is None:
            return
        raw = self.rfile.read(length)
        try:
            data = json.loads(raw)
            if not isinstance(data, dict) or not isinstance(data.get("bills"), list):
                raise ValueError
        except ValueError:
            return self.send(400, 'body must be {"bills": [...]}')
        want = (self.headers.get("If-Match") or "").strip()
        if not want:
            return self.send(428, "If-Match required")
        with lock:
            cur = read_state(user)
            stale = etag_version(want) != str(cur["version"]) or not same_copy(cur, base_tag(self))
            if want != "*" and stale:
                return self.send(412, "changed since you read it", headers=doc_headers(cur))
            keep_old(user, cur)
            doc = {
                "version": cur["version"] + 1,
                "tag": secrets.token_hex(8),
                "trail": [t for t in cur.get("trail", []) + [cur.get("tag", "")] if t][-KEEP_TAGS:],
                "updated": datetime.now(timezone.utc).isoformat(),
                "by": (self.headers.get("X-Device") or "")[:64],
                "sha256": hashlib.sha256(raw).hexdigest(),
                "data": data,
            }
            write_json(user_dir(user, "state.json"), doc)
        self.send(200, '{"ok":true}', "application/json", doc_headers(doc))
        prune_photos(user)

    def put_photo(self, user, name):
        if not PHOTO_NAME.fullmatch(name) or ".." in name:
            return self.send(400, "bad photo name")
        length = self.length(MAX_PHOTO)
        if length is None:
            return
        folder = user_dir(user, "photos")
        os.makedirs(folder, exist_ok=True)
        tmp = os.path.join(folder, f".{name}.{threading.get_ident()}.part")
        left = length
        try:
            with open(tmp, "wb") as f:
                while left > 0:
                    chunk = self.rfile.read(min(left, 65536))
                    if not chunk:
                        raise OSError("the upload stopped short")
                    f.write(chunk)
                    left -= len(chunk)
                f.flush()
                os.fsync(f.fileno())
            with photo_lock:
                os.replace(tmp, os.path.join(folder, name))
        except OSError:
            try:
                os.remove(tmp)
            except OSError:
                pass
            return self.send(400, "the upload stopped short")
        self.send(201, '{"ok":true}', "application/json")


def main():
    global args
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=5237)
    p.add_argument("--data", required=True, help="where the documents and photos live")
    p.add_argument("--users", required=True, help="htpasswd file with bcrypt hashes")
    args = p.parse_args()
    args.data = os.path.expanduser(args.data)
    args.users = os.path.expanduser(args.users)
    srv = ThreadingHTTPServer((args.host, args.port), Handler)
    srv.daemon_threads = True
    sys.stderr.write(f"xrnwallet-sync on http://{args.host}:{args.port}, data in {args.data}\n")
    srv.serve_forever()


if __name__ == "__main__":
    main()
