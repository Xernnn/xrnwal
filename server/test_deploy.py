"""deploy.sh against a made-up server: ssh, sv, curl, tailscale and sv-guard are stand-ins that run here.

    python3 -m unittest server/test_deploy.py

It installs a fresh service, moves one still named xrnwallet-sync over, and is run again on top of
both, reading back what it wrote on the "phone". The server's own tests, which deploy.sh runs
first, are left to test_xrnwal_sync.py: here they pass, or fail when a test says so.
"""
import os
import shutil
import stat
import subprocess
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
DEPLOY = os.path.join(HERE, "deploy.sh")
PYTHON3 = shutil.which("python3")

FAKES = {
    # ssh HOST SCRIPT: the script runs here, in the made-up home, as it would on the phone.
    "ssh": '#!/bin/sh\nshift\nexec sh -c "$*"\n',
    # runit's sv, with no runsv running: `status` fails as it does once one has exited, and a
    # service it is told about must be there.
    "sv": """#!/bin/sh
echo "sv $*" >> "$HOME/calls"
while [ "${1#-}" != "$1" ]; do
    if [ "$1" = -w ]; then shift; fi
    shift
done
command=$1
shift
[ "$command" = status ] && exit 1
for service; do
    case $service in
        /*) [ -d "$service" ] || exit 1 ;;
        *) [ -d "$SVDIR/$service" ] || exit 1 ;;
    esac
done
exit 0
""",
    "curl": "#!/bin/sh\nprintf ok\n",
    # Funnel's paths, one "<path> <target>" a line in $HOME/funnel.
    "tailscale": """#!/bin/sh
echo "tailscale $*" >> "$HOME/calls"
paths=$HOME/funnel
touch "$paths"
case "$*" in
    "funnel status --json")
        echo '{"Web": {"host.example:443": {"Handlers": {'
        sed 's/^\\([^ ]*\\) \\(.*\\)$/  "\\1": {"Proxy": "\\2"},/' "$paths"
        echo '}}}}' ;;
    "funnel status") echo "Funnel on"; cat "$paths" ;;
    "funnel --bg --set-path "*)
        grep -v "^$4 " "$paths" > "$paths.new" || true
        echo "$4 $5" >> "$paths.new"
        mv "$paths.new" "$paths" ;;
    *) exit 1 ;;
esac
""",
    "python": '#!/bin/sh\nexec python3 "$@"\n',
    # The server tests deploy.sh runs first pass, unless the test has made them fail.
    "python3": f"""#!/bin/sh
if [ "$*" = "-m unittest server/test_xrnwal_sync.py" ]; then
    [ -e "$HOME/tests-fail" ] && exit 1
    exit 0
fi
exec {PYTHON3} "$@"
""",
}

# The home-server's sv-guard: says what it was asked, and fails as one that found the port taken
# when $HOME/port-taken is there.
GUARD = '#!/bin/sh\necho "sv-guard $*" >> "$HOME/calls"\n[ -e "$HOME/port-taken" ] && exit 1\nexit 0\n'


def write(path, text, mode=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)
    if mode is not None:
        os.chmod(path, mode)


def read(path):
    with open(path) as f:
        return f.read()


@unittest.skipUnless(shutil.which("sh") and shutil.which("tar") and PYTHON3, "needs sh, tar and python3")
class Deploy(unittest.TestCase):
    def setUp(self):
        self.root = tempfile.mkdtemp(prefix="xrnwal-deploy-")
        self.home = os.path.join(self.root, "home")
        self.prefix = os.path.join(self.root, "usr")
        self.bin = os.path.join(self.root, "bin")
        self.services = os.path.join(self.prefix, "var", "service")
        self.logs = os.path.join(self.prefix, "var", "log", "sv")
        for d in (self.home, self.services, self.bin):
            os.makedirs(d)
        for name, body in FAKES.items():
            write(os.path.join(self.bin, name), body, stat.S_IRWXU)
        write(self.guard, GUARD, stat.S_IRWXU)

    def tearDown(self):
        shutil.rmtree(self.root)

    guard = property(lambda self: os.path.join(self.home, ".local", "bin", "sv-guard"))
    old = property(lambda self: os.path.join(self.home, "xrnwallet-sync"))
    new = property(lambda self: os.path.join(self.home, "xrnwal-sync"))

    def deploy(self):
        open(os.path.join(self.home, "calls"), "w").close()
        env = {**os.environ, "HOME": self.home, "PREFIX": self.prefix, "PATH": f"{self.bin}:{os.environ['PATH']}",
               "PYTHONDONTWRITEBYTECODE": "1"}
        return subprocess.run(["sh", DEPLOY, "phone"], env=env, capture_output=True, text=True, timeout=120)

    def calls(self):
        return read(os.path.join(self.home, "calls")).splitlines()

    def funnel(self):
        path = os.path.join(self.home, "funnel")
        return read(path).splitlines() if os.path.exists(path) else []

    def old_server(self):
        """A phone as xrnwallet's deploy.sh left it: the service, its data, its log and its Funnel path."""
        write(os.path.join(self.old, "xrnwallet-sync.py"), "# the old program\n", 0o755)
        write(os.path.join(self.old, "data", "users", "alex", "state.json"), '{"version": 3, "bills": [1, 2, 3]}')
        write(os.path.join(self.old, "data", "users", "alex", "history", "7.json"), '{"version": 3}')
        write(os.path.join(self.old, "data", "users", "alex", "photos", "receipt.jpg"), "jpeg")
        write(os.path.join(self.services, "xrnwallet-sync", "run"), "#!/bin/sh\nexec python xrnwallet-sync.py\n", 0o755)
        write(os.path.join(self.services, "xrnwallet-sync", "log", "run"), "#!/bin/sh\nexec svlogd .\n", 0o755)
        write(os.path.join(self.logs, "xrnwallet-sync", "current"), "the old log\n")
        write(os.path.join(self.home, "funnel"), "/ http://127.0.0.1:5232\n/xrnwallet http://127.0.0.1:5237\n")

    def assert_no_half_built_service(self):
        self.assertEqual([n for n in os.listdir(self.services) if n.startswith(".")], [])

    def test_a_fresh_install_writes_the_service_and_publishes_the_path(self):
        done = self.deploy()
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertTrue(os.path.isfile(os.path.join(self.new, "xrnwal-sync.py")))
        self.assertEqual(stat.S_IMODE(os.stat(os.path.join(self.new, "data")).st_mode), 0o700)
        service = os.path.join(self.services, "xrnwal-sync")
        run = read(os.path.join(service, "run"))
        self.assertTrue(run.startswith(f"#!{self.prefix}/bin/sh\n"))
        self.assertIn(f"\n{self.home}/.local/bin/sv-guard 5237 {self.home}/xrnwal-sync/xrnwal-sync.py || exit 1\n"
                      f"exec python {self.home}/xrnwal-sync/xrnwal-sync.py \\\n  --port 5237 "
                      f"--data {self.home}/xrnwal-sync/data \\\n  --users {self.home}/.config/xrn-sync/users 2>&1\n", run)
        self.assertEqual(read(os.path.join(service, "log", "run")),
                         f'#!{self.prefix}/bin/sh\nmkdir -p "{self.prefix}/var/log/sv/xrnwal-sync"\n'
                         f'exec svlogd -tt "{self.prefix}/var/log/sv/xrnwal-sync"\n')
        self.assert_no_half_built_service()
        self.assertEqual(self.funnel(), ["/xrnwal http://127.0.0.1:5237"])
        self.assertNotIn("serve", " ".join(self.calls()))
        self.assertFalse(os.path.exists(self.old))
        self.assertIn("installed the xrnwal-sync service", done.stdout.split("What it did:")[1])

    def test_the_old_service_moves_over_with_its_data_and_its_path(self):
        self.old_server()
        done = self.deploy()
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertFalse(os.path.exists(self.old))
        alex = os.path.join(self.new, "data", "users", "alex")
        self.assertEqual(read(os.path.join(alex, "state.json")), '{"version": 3, "bills": [1, 2, 3]}')
        self.assertEqual(read(os.path.join(alex, "history", "7.json")), '{"version": 3}')
        self.assertEqual(read(os.path.join(alex, "photos", "receipt.jpg")), "jpeg")
        self.assertEqual(sorted(os.listdir(self.new)), ["data", "xrnwal-sync.py"])
        # The old service is gone from runit's sight and stopped before anything moved.
        self.assertEqual(sorted(os.listdir(self.services)), ["xrnwal-sync"])
        calls = self.calls()
        down = calls.index("sv -v -w 15 down xrnwallet-sync")
        retired = calls.index(f"sv exit {self.services}/.xrnwallet-sync.old")
        guarded = calls.index(f"sv-guard 5237 {self.home}/xrnwallet-sync/xrnwallet-sync.py")
        self.assertLess(down, retired)
        self.assertLess(retired, guarded)
        self.assertLess(guarded, calls.index("tailscale funnel --bg --set-path /xrnwal http://127.0.0.1:5237"))
        self.assertEqual(read(os.path.join(self.logs, "xrnwal-sync", "current")), "the old log\n")
        self.assertFalse(os.path.exists(os.path.join(self.logs, "xrnwallet-sync")))
        self.assertIn(f"\n{self.home}/.local/bin/sv-guard 5237 {self.home}/xrnwal-sync/xrnwal-sync.py || exit 1\n",
                      read(os.path.join(self.services, "xrnwal-sync", "run")))
        self.assertEqual(sorted(self.funnel()),
                         ["/ http://127.0.0.1:5232", "/xrnwal http://127.0.0.1:5237", "/xrnwallet http://127.0.0.1:5237"])
        said = done.stdout.split("What it did:")[1]
        for line in ("stopped the xrnwallet-sync service and removed it", f"moved {self.old} to {self.new}, data and all",
                     "installed the xrnwal-sync service", "published /xrnwal", "kept /xrnwallet on the same port"):
            self.assertIn(line, said)

    def test_running_it_again_moves_nothing_and_keeps_the_data(self):
        self.old_server()
        self.assertEqual(self.deploy().returncode, 0)
        write(os.path.join(self.new, "data", "users", "alex", "state.json"), '{"version": 3, "bills": [1, 2, 3, 4]}')
        run = os.path.join(self.services, "xrnwal-sync", "run")
        write(run, "#!/bin/sh\nexec python an-old-run-script.py\n", 0o755)
        done = self.deploy()
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertEqual(read(os.path.join(self.new, "data", "users", "alex", "state.json")),
                         '{"version": 3, "bills": [1, 2, 3, 4]}')
        self.assertIn("sv restart xrnwal-sync", self.calls())
        self.assertFalse([c for c in self.calls() if "xrnwallet-sync" in c])
        self.assertIn("sv-guard 5237", read(run))
        self.assert_no_half_built_service()
        self.assertIn("/xrnwallet http://127.0.0.1:5237", self.funnel())
        self.assertIn("nothing to move", done.stdout)
        self.assertNotIn("data and all", done.stdout)

    def test_a_path_taken_off_by_hand_stays_off(self):
        self.old_server()
        self.assertEqual(self.deploy().returncode, 0)
        write(os.path.join(self.home, "funnel"), "/ http://127.0.0.1:5232\n/xrnwal http://127.0.0.1:5237\n")
        done = self.deploy()
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertEqual(sorted(self.funnel()), ["/ http://127.0.0.1:5232", "/xrnwal http://127.0.0.1:5237"])

    def assert_nothing_changed(self):
        self.assertEqual(read(os.path.join(self.old, "data", "users", "alex", "state.json")), '{"version": 3, "bills": [1, 2, 3]}')
        self.assertTrue(os.path.isfile(os.path.join(self.services, "xrnwallet-sync", "run")))
        self.assertFalse(os.path.exists(os.path.join(self.services, "xrnwal-sync")))
        self.assertFalse([c for c in self.calls() if c.startswith(("sv ", "tailscale "))])

    def test_both_folders_there_changes_nothing(self):
        self.old_server()
        write(os.path.join(self.new, "data", "users", "alex", "state.json"), "{}")
        done = self.deploy()
        self.assertEqual(done.returncode, 1)
        self.assertIn("so nothing was changed", done.stderr)
        self.assert_nothing_changed()
        self.assertEqual(read(os.path.join(self.new, "data", "users", "alex", "state.json")), "{}")

    def test_without_sv_guard_nothing_changes(self):
        self.old_server()
        os.remove(self.guard)
        done = self.deploy()
        self.assertEqual(done.returncode, 1)
        self.assertIn("sv-guard is missing", done.stderr)
        self.assert_nothing_changed()
        self.assertFalse(os.path.exists(self.new))

    def test_failing_server_tests_change_nothing(self):
        self.old_server()
        write(os.path.join(self.home, "tests-fail"), "")
        done = self.deploy()
        self.assertEqual(done.returncode, 1)
        self.assertIn("The server tests failed", done.stderr)
        self.assert_nothing_changed()
        self.assertFalse(os.path.exists(self.new))

    def test_a_port_still_taken_leaves_the_data_where_it_was_until_run_again(self):
        self.old_server()
        write(os.path.join(self.home, "port-taken"), "")
        done = self.deploy()
        self.assertEqual(done.returncode, 1)
        self.assertIn("the data was not moved", done.stderr)
        self.assertEqual(read(os.path.join(self.old, "data", "users", "alex", "state.json")), '{"version": 3, "bills": [1, 2, 3]}')
        self.assertFalse(os.path.exists(self.new))
        self.assertFalse(os.path.exists(os.path.join(self.services, "xrnwal-sync")))
        # Whatever held it has gone: the same command carries on from where it stopped.
        os.remove(os.path.join(self.home, "port-taken"))
        done = self.deploy()
        self.assertEqual(done.returncode, 0, done.stderr)
        self.assertEqual(read(os.path.join(self.new, "data", "users", "alex", "state.json")), '{"version": 3, "bills": [1, 2, 3]}')
        self.assertFalse(os.path.exists(self.old))
        self.assertEqual(sorted(os.listdir(self.services)), ["xrnwal-sync"])


if __name__ == "__main__":
    unittest.main()
