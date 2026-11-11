"""Tests for xrnwallet-sync.py: the If-Match rule, the upload hash, earlier copies, photos, logins.

    python3 -m unittest server/test_xrnwallet_sync.py     (from the repo root; needs bcrypt)
"""
import base64
import hashlib
import importlib.util
import json
import os
import socket
import tempfile
import threading
import time
import unittest
import unittest.mock
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from http.server import ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("xrnwallet_sync", os.path.join(HERE, "xrnwallet-sync.py"))
srv = importlib.util.module_from_spec(spec)
spec.loader.exec_module(srv)

RIGHT = "secret"


class LoopbackServer(ThreadingHTTPServer):
    request_queue_size = 64  # room for requests sent side by side, so none waits to connect


def document(*notes, photo=None):
    return {"version": 1, "settings": "{}",
            "bills": [{"id": i, "note": n, "photo": photo} for i, n in enumerate(notes)]}


class SyncServerTest(unittest.TestCase):
    def setUp(self):
        import bcrypt

        self.tmp = tempfile.TemporaryDirectory()
        users = os.path.join(self.tmp.name, "users")
        with open(users, "w") as f:
            for name in ("owner", "other"):
                f.write(f"{name}:" + bcrypt.hashpw(RIGHT.encode(), bcrypt.gensalt(4)).decode() + "\n")
        self.data = os.path.join(self.tmp.name, "data")
        srv.args = type("Args", (), {"data": self.data, "users": users})()
        srv.FAIL_DELAY_S = 0
        srv.failures.clear()
        srv.pruned.clear()
        srv.auth_cache.clear()
        self.httpd = LoopbackServer(("127.0.0.1", 0), srv.Handler)
        self.httpd.daemon_threads = True
        self.base = f"http://127.0.0.1:{self.httpd.server_address[1]}"
        threading.Thread(target=self.httpd.serve_forever, daemon=True).start()

    def tearDown(self):
        self.httpd.shutdown()
        self.httpd.server_close()
        self.tmp.cleanup()

    def call(self, method, path, body=None, headers=None, password=RIGHT, raw=False, user="owner"):
        data = body if raw else (json.dumps(body).encode() if body is not None else None)
        req = urllib.request.Request(self.base + path, method=method, data=data)
        if password is not None:
            req.add_header("Authorization", "Basic " + base64.b64encode(f"{user}:{password}".encode()).decode())
        for k, v in (headers or {}).items():
            req.add_header(k, v)
        try:
            with urllib.request.urlopen(req, timeout=10) as r:
                return r.status, r.read(), r.headers
        except urllib.error.HTTPError as e:
            with e:
                return e.code, e.read(), e.headers

    def put(self, doc, base):
        return self.call("PUT", "/state", doc, {"If-Match": base})

    def test_an_empty_server_takes_a_first_sync(self):
        status, body, headers = self.call("GET", "/state")
        self.assertEqual((status, json.loads(body), headers["ETag"]), (200, {}, '"0"'))
        status, _, headers = self.put(document("Lunch"), '"0"')
        self.assertEqual((status, headers["ETag"]), (200, '"1"'))
        status, body, _ = self.call("GET", "/state")
        self.assertEqual(json.loads(body)["bills"][0]["note"], "Lunch")

    def test_the_path_works_with_and_without_the_prefix(self):
        self.assertEqual(self.call("GET", "/xrnwallet/health", password=None)[1], b"ok")
        self.assertEqual(self.call("GET", "/health", password=None)[1], b"ok")
        self.assertEqual(self.call("PUT", "/xrnwallet/state", document("a"), {"If-Match": '"0"'})[0], 200)

    def test_a_stale_write_is_refused_and_changes_nothing(self):
        self.put(document("first"), '"0"')
        self.put(document("second"), '"1"')
        status, _, headers = self.put(document("from a phone a version behind"), '"1"')
        self.assertEqual((status, headers["ETag"]), (412, '"2"'))
        self.assertEqual(json.loads(self.call("GET", "/state")[1])["bills"][0]["note"], "second")

    def test_a_write_without_if_match_is_refused(self):
        self.assertEqual(self.call("PUT", "/state", document("a"))[0], 428)

    def test_a_phone_that_never_synced_cannot_replace_a_copy_by_accident(self):
        self.put(document("kept"), '"0"')
        self.assertEqual(self.put(document("fresh install"), '"0"')[0], 412)

    def test_saying_so_on_purpose_replaces_it_and_keeps_the_old_one(self):
        self.put(document("old"), '"0"')
        status, _, headers = self.put(document("new"), "*")
        self.assertEqual((status, headers["ETag"]), (200, '"2"'))
        earlier = json.loads(self.call("GET", "/versions")[1])
        self.assertEqual([e["version"] for e in earlier], [1])

    def test_a_page_on_another_site_cant_write_with_the_browsers_password(self):
        # A browser once given the password in its own box sends it with whatever another
        # site's page sends here.
        for elsewhere in ({"Sec-Fetch-Site": "cross-site", "Origin": "https://evil.example"},
                          {"Sec-Fetch-Site": "same-site", "Origin": "https://other.homeserver.example"},
                          {"Origin": "https://evil.example"}):  # a browser from before Sec-Fetch-Site
            self.assertEqual(self.call("PUT", "/state", document("Junk"), {"If-Match": "*", **elsewhere})[0], 403)
            self.assertEqual(self.call("PUT", "/photos/junk.jpg", b"x", elsewhere, raw=True)[0], 403)
        self.assertEqual(json.loads(self.call("GET", "/state")[1]), {})
        self.assertEqual(json.loads(self.call("GET", "/photos")[1]), [])
        # Reads and the phone (it sends neither header) still do.
        self.assertEqual(self.call("GET", "/state", headers={"Sec-Fetch-Site": "cross-site"})[0], 200)
        port = self.httpd.server_address[1]
        for ours in ({"Sec-Fetch-Site": "same-origin", "Origin": "https://homeserver.example"},
                     {"Origin": f"http://127.0.0.1:{port}"},
                     {"Origin": "https://HomeServer.example", "X-Forwarded-Host": "homeserver.example:443"},
                     {}):
            self.assertEqual(self.call("PUT", "/state", document("Lunch"), {"If-Match": "*", **ours})[0], 200, ours)

    def test_unchanged_answers_304(self):
        self.put(document("a"), '"0"')
        self.assertEqual(self.call("GET", "/state", headers={"If-None-Match": '"1"'})[0], 304)
        self.assertEqual(self.call("GET", "/state", headers={"If-None-Match": '"0"'})[0], 200)

    def state_file(self):
        return os.path.join(self.data, "users", "owner", "state.json")

    def test_every_write_gets_a_tag_of_its_own(self):
        _, _, first = self.put(document("a"), '"0"')
        _, _, second = self.put(document("b"), '"1"')
        self.assertTrue(first["X-Tag"])
        self.assertNotEqual(first["X-Tag"], second["X-Tag"])
        self.assertEqual(self.call("GET", "/state")[2]["X-Tag"], second["X-Tag"])

    def test_a_copy_written_on_top_of_the_phones_follows_it(self):
        _, _, first = self.put(document("one"), '"0"')
        seen = {"If-None-Match": '"1"', "X-Base-Tag": first["X-Tag"]}
        self.assertEqual(self.call("GET", "/state", headers=seen)[0], 304)
        self.put(document("two"), '"1"')
        status, _, headers = self.call("GET", "/state", headers=seen)
        self.assertEqual((status, headers["X-Follows"]), (200, "1"))

    def test_after_a_restore_here_the_same_version_is_not_taken_for_the_phones_copy(self):
        self.put(document("one"), '"0"')
        with open(self.state_file()) as f:
            backup = f.read()
        _, _, mine = self.put(document("two, from this phone"), '"1"')
        with open(self.state_file(), "w") as f:
            f.write(backup)  # the data here restored from a backup
        self.put(document("two, from the other phone"), '"1"')
        status, _, headers = self.call("GET", "/state", headers={"If-None-Match": '"2"', "X-Base-Tag": mine["X-Tag"]})
        self.assertEqual((status, headers["X-Follows"]), (200, "0"))
        status = self.call("PUT", "/state", document("three"), {"If-Match": '"2"', "X-Base-Tag": mine["X-Tag"]})[0]
        self.assertEqual(status, 412)
        self.assertEqual(json.loads(self.call("GET", "/state")[1])["bills"][0]["note"], "two, from the other phone")

    def test_a_phone_that_sends_no_tag_is_answered_by_version_as_before(self):
        self.put(document("one"), '"0"')
        self.assertEqual(self.call("GET", "/state", headers={"If-None-Match": '"1"'})[0], 304)
        self.put(document("two"), '"1"')
        _, _, headers = self.call("GET", "/state", headers={"If-None-Match": '"1"'})
        self.assertIsNone(headers["X-Follows"])
        self.assertEqual(self.put(document("three"), '"2"')[0], 200)

    def test_a_copy_from_before_tags_is_matched_by_version_alone(self):
        os.makedirs(os.path.dirname(self.state_file()))
        with open(self.state_file(), "w") as f:
            json.dump({"version": 3, "updated": "", "sha256": "", "data": document("from the old server")}, f)
        tagged = {"If-None-Match": '"3"', "X-Base-Tag": "from-a-later-copy"}
        self.assertEqual(self.call("GET", "/state", headers=tagged)[0], 304)
        status, _, headers = self.call("PUT", "/state", document("next"), {"If-Match": '"3"', "X-Base-Tag": "from-a-later-copy"})
        self.assertEqual(status, 200)
        self.assertTrue(headers["X-Tag"])

    def test_only_a_document_with_bills_is_taken(self):
        self.assertEqual(self.put({"items": []}, '"0"')[0], 400)
        self.assertEqual(self.put([], '"0"')[0], 400)
        self.assertEqual(self.call("PUT", "/state", b"not json", {"If-Match": '"0"'}, raw=True)[0], 400)

    def test_a_write_reports_the_hash_of_what_it_was_sent(self):
        raw = json.dumps(document("a"), separators=(",", ":")).encode()
        want = hashlib.sha256(raw).hexdigest()
        _, _, put = self.call("PUT", "/state", raw, {"If-Match": '"0"'}, raw=True)
        self.assertEqual(put["X-Content-SHA256"], want)
        self.assertEqual(self.call("GET", "/state")[2]["X-Content-SHA256"], want)

    def test_a_refused_write_says_what_is_here_so_a_lost_answer_can_be_spotted(self):
        raw = json.dumps(document("landed, answer lost"), separators=(",", ":")).encode()
        self.call("PUT", "/state", raw, {"If-Match": '"0"'}, raw=True)
        status, _, headers = self.put(document("next change"), '"0"')
        self.assertEqual((status, headers["X-Content-SHA256"], headers["ETag"]),
                         (412, hashlib.sha256(raw).hexdigest(), '"1"'))

    def test_the_hash_is_of_the_bytes_sent_whatever_they_look_like_read_back(self):
        # Python and Kotlin write floats differently, so the hash can't come from a re-encoding.
        raw = b'{"bills":[{"rate":1.0E-5}]}'
        self.call("PUT", "/state", raw, {"If-Match": '"0"'}, raw=True)
        _, body, headers = self.call("GET", "/state")
        self.assertEqual(headers["X-Content-SHA256"], hashlib.sha256(raw).hexdigest())
        self.assertAlmostEqual(json.loads(body)["bills"][0]["rate"], 1e-5)

    def test_earlier_copies_can_be_listed_and_fetched(self):
        self.put(document("one"), '"0"')
        self.put(document("two", "bills"), '"1"')
        self.put(document("three"), '"2"')
        earlier = json.loads(self.call("GET", "/versions")[1])
        self.assertEqual([(e["version"], e["bills"]) for e in earlier], [(2, 2), (1, 1)])
        self.assertTrue(earlier[0]["updated"])
        status, body, headers = self.call("GET", f"/versions/{earlier[1]['id']}")
        self.assertEqual((status, json.loads(body)["bills"][0]["note"]), (200, "one"))
        self.assertTrue(headers["X-Content-SHA256"])

    def test_a_copy_past_the_history_is_still_there_in_the_daily_folder(self):
        self.put(document("first of the day"), '"0"')
        for i in range(1, srv.KEEP_VERSIONS + 3):
            self.put(document(f"change {i}"), f'"{i}"')
        earlier = json.loads(self.call("GET", "/versions")[1])
        oldest = earlier[-1]
        self.assertEqual(oldest["version"], 1)
        self.assertRegex(oldest["id"], r"\d{4}-\d{2}-\d{2}")
        self.assertEqual(len(earlier), srv.KEEP_VERSIONS + 1)
        body = json.loads(self.call("GET", f"/versions/{oldest['id']}")[1])
        self.assertEqual(body["bills"][0]["note"], "first of the day")

    def test_no_earlier_copies_is_an_empty_list(self):
        status, body, _ = self.call("GET", "/versions")
        self.assertEqual((status, json.loads(body)), (200, []))

    def test_an_unknown_or_unsafe_version_is_not_found(self):
        self.put(document("a"), '"0"')
        for name in ("99", "2020-01-01", "..", "../state", "%2e%2e%2fstate", "state"):
            self.assertEqual(self.call("GET", f"/versions/{name}")[0], 404, name)

    def test_a_photo_goes_up_and_comes_back(self):
        self.assertEqual(self.call("PUT", "/photos/a.jpg", b"\xff\xd8bytes", raw=True)[0], 201)
        self.assertEqual(json.loads(self.call("GET", "/photos")[1]), ["a.jpg"])
        status, body, headers = self.call("GET", "/photos/a.jpg")
        self.assertEqual((status, body, headers["Content-Type"]), (200, b"\xff\xd8bytes", "image/jpeg"))

    def test_a_bad_photo_name_is_refused(self):
        for name in (".hidden", "a..jpg", "..%2f..%2fetc"):
            self.assertIn(self.call("PUT", f"/photos/{name}", b"x", raw=True)[0], (400, 404), name)
        self.assertEqual(self.call("GET", "/photos/.hidden")[0], 404)

    def age(self, name):
        path = os.path.join(self.data, "users", "owner", "photos", name)
        then = time.time() - srv.PHOTO_GRACE_S - 60
        os.utime(path, (then, then))

    def test_a_photo_goes_once_nothing_kept_names_it(self):
        self.call("PUT", "/photos/old.jpg", b"x", raw=True)
        self.put(document("with photo", photo="old.jpg"), '"0"')
        self.age("old.jpg")
        for i in range(1, srv.KEEP_VERSIONS + 2):
            self.put(document("no photo"), f'"{i}"')
        srv.prune_photos("owner", force=True)
        self.assertIn(b"old.jpg", self.call("GET", "/photos")[1], "the daily copy still names it")
        user = os.path.join(self.data, "users", "owner")
        for name in os.listdir(os.path.join(user, "daily")):
            os.remove(os.path.join(user, "daily", name))
        srv.prune_photos("owner", force=True)
        self.assertEqual(json.loads(self.call("GET", "/photos")[1]), [])

    def test_a_new_photo_nothing_names_yet_stays(self):
        self.call("PUT", "/photos/new.jpg", b"x", raw=True)
        self.put(document("a"), '"0"')
        srv.prune_photos("owner", force=True)
        self.assertEqual(json.loads(self.call("GET", "/photos")[1]), ["new.jpg"])

    def test_an_unparseable_request_gets_an_answer(self):
        with socket.create_connection(("127.0.0.1", self.httpd.server_address[1]), timeout=10) as s:
            s.sendall(b"GARBAGE\r\n\r\n")
            self.assertIn(b"400", s.recv(200))

    def test_everything_needs_the_login_except_health(self):
        for method, path in (("GET", "/state"), ("GET", "/versions"), ("GET", "/versions/1"),
                             ("GET", "/photos"), ("GET", "/photos/a.jpg")):
            self.assertEqual(self.call(method, path, password=None)[0], 401, path)
            self.assertEqual(self.call(method, path, password="wrong")[0], 401, path)
        self.assertEqual(self.put_as("wrong")[0], 401)
        self.assertEqual(self.call("GET", "/health", password=None)[0], 200)

    def put_as(self, password):
        return self.call("PUT", "/state", document("a"), {"If-Match": '"0"'}, password=password)

    def guess(self, forwarded, n=0):
        return self.call("GET", "/state", password=f"guess{n}", headers={"X-Forwarded-For": forwarded})[0]

    def test_a_made_up_forwarded_hop_is_not_a_new_address(self):
        for i in range(10):  # what a proxy that appends would pass on: the caller's own header first
            self.assertEqual(401, self.guess(f"198.51.100.{i}, 203.0.113.20", i))
        self.assertEqual(429, self.guess("198.51.100.200, 203.0.113.20", 10))
        self.assertEqual(401, self.guess("203.0.113.20, 203.0.113.21"))  # another caller isn't held

    def test_an_ipv6_guesser_is_its_64_not_each_address_in_it(self):
        for i in range(10):
            self.assertEqual(401, self.guess(f"2001:db8:1:2::{i + 1:x}", i))
        self.assertEqual(429, self.guess("2001:db8:1:2:aaaa::1", 10))
        self.assertEqual(401, self.guess("2001:db8:1:3::1"))  # the /64 next door isn't held
        self.assertEqual(401, self.guess("::ffff:203.0.113.40"))  # an IPv4 caller written as IPv6
        self.assertIn("203.0.113.40", srv.failures)  # ...is counted as that IPv4 address

    def test_a_name_that_isnt_there_costs_a_check_like_one_that_is(self):
        import bcrypt

        def checks(login):
            header = "Basic " + base64.b64encode(login.encode()).decode()
            with unittest.mock.patch.object(bcrypt, "checkpw", wraps=bcrypt.checkpw) as checkpw:
                self.assertIsNone(srv.check_login(header))
            return checkpw.call_count
        self.assertEqual(checks("owner:nope"), 1)
        self.assertEqual(checks("nobody:nope"), 1)  # none: the answer came sooner, and said so
        self.assertIsNone(srv.check_login("Basic " + base64.b64encode(b"nobody:secret").decode()))

    def test_a_request_with_no_password_isnt_held(self):
        # The hold after a wrong password slows a guesser down. One that sent no password guessed
        # nothing, and each held a thread for the hold all the same.
        def timed(addr, password):
            start = time.monotonic()
            status = self.call("GET", "/state", password=password, headers={"X-Forwarded-For": addr})[0]
            return status, time.monotonic() - start
        srv.failures["203.0.113.61"] = [time.monotonic()] * srv.FAIL_LIMIT  # locked out
        srv.FAIL_DELAY_S = 0.5
        try:
            for addr, status in (("203.0.113.60", 401), ("203.0.113.61", 429)):
                got, took = timed(addr, None)
                self.assertEqual(got, status)
                self.assertLess(took, 0.4, addr)
                got, took = timed(addr, "nope")
                self.assertEqual(got, status)
                self.assertGreaterEqual(took, 0.5, addr)
        finally:
            srv.FAIL_DELAY_S = 0

    def test_too_many_wrong_passwords_lock_the_address_out(self):
        for _ in range(srv.FAIL_LIMIT):
            self.assertEqual(self.call("GET", "/state", password="wrong")[0], 401)
        status, _, headers = self.call("GET", "/state", password=RIGHT)
        self.assertEqual(status, 429)
        self.assertTrue(headers["Retry-After"])

    def test_wrong_passwords_sent_side_by_side_get_no_more_tries(self):
        real = srv.check_login

        def slow(header):  # as bcrypt is, which is when the others arrive
            time.sleep(0.5)
            return real(header)

        srv.check_login = slow
        try:
            tries = srv.FAIL_LIMIT * 3
            with ThreadPoolExecutor(tries) as pool:
                codes = list(pool.map(lambda _: self.call("GET", "/state", password="wrong")[0], range(tries)))
        finally:
            srv.check_login = real
        self.assertEqual((codes.count(401), codes.count(429)), (srv.FAIL_LIMIT, tries - srv.FAIL_LIMIT))
        self.assertEqual(self.call("GET", "/state", password=RIGHT)[0], 429)

    def test_users_do_not_see_each_others_copies(self):
        self.put(document("mine"), '"0"')
        self.put(document("newer"), '"1"')
        self.call("PUT", "/photos/mine.jpg", b"x", raw=True)
        status, body, headers = self.call("GET", "/state", user="other")
        self.assertEqual((status, json.loads(body), headers["ETag"]), (200, {}, '"0"'))
        self.assertEqual(json.loads(self.call("GET", "/versions", user="other")[1]), [])
        self.assertEqual(self.call("GET", "/versions/1", user="other")[0], 404)
        self.assertEqual(json.loads(self.call("GET", "/photos", user="other")[1]), [])
        self.assertEqual(self.call("GET", "/photos/mine.jpg", user="other")[0], 404)


if __name__ == "__main__":
    unittest.main()
