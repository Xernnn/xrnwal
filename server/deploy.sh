#!/bin/sh
# Installs or updates xrnwal-sync on a home server reached over ssh (Termux with runit and
# Tailscale Funnel), next to the other sync services. Safe to run again: an update replaces the
# program and its run script, restarts it and leaves the data alone.
#
#     server/deploy.sh [ssh-host]      (default: homeserver)
#
# Port 5237, Funnel path /xrnwal, data in ~/xrnwal-sync/data, login from the shared
# ~/.config/xrn-sync/users.
#
# The app was called xrnwallet until 0.6.0. On a server still running it as xrnwallet-sync, the
# first run moves it over: it stops that service and removes it, renames ~/xrnwallet-sync, data and
# all, to ~/xrnwal-sync, moves its log, and starts xrnwal-sync on the same port. /xrnwallet stays
# published beside /xrnwal for phones still set up with it, until it is taken off by hand.
set -eu

HOST=${1:-homeserver}
HERE=$(cd "$(dirname "$0")" && pwd)
PORT=5237

echo "Testing the server code first..."
# test_deploy.py runs this script itself, so it isn't one of them.
if ! (cd "$HERE/.." && PYTHONDONTWRITEBYTECODE=1 python3 -m unittest server/test_xrnwal_sync.py >/dev/null 2>&1); then
    echo "The server tests failed, so nothing was changed. Run them for details:" >&2
    echo "    python3 -m unittest server/test_xrnwal_sync.py" >&2
    exit 1
fi

# What it did, said again at the end.
did=""
# Shows the phone's lines as they come back and keeps them for the end.
report() {
    if [ -n "$1" ]; then
        printf '%s\n' "$1" | sed 's/^/  /'
        did="$did$1
"
    fi
}

echo "Looking for the old xrnwallet-sync on $HOST..."
if ! moved=$(ssh "$HOST" '
    set -e
    S=$PREFIX/var/service
    export SVDIR=$S
    OLD=$HOME/xrnwallet-sync
    NEW=$HOME/xrnwal-sync
    GUARD=$HOME/.local/bin/sv-guard
    LOGS=$PREFIX/var/log/sv
    if [ ! -x "$GUARD" ]; then
        echo "$GUARD is missing, and the run script calls it before it starts the program, so nothing" >&2
        echo "was changed. Put it there with phone/install-sv-guard from the home-server repo, then run this again." >&2
        exit 1
    fi
    if [ -d "$OLD" ] && [ -e "$NEW" ]; then
        echo "Both $OLD and $NEW are here, so nothing was changed. Keep the one that holds the wallet" >&2
        echo "(compare their data/users/*/state.json), move the other out of the way, and run this again." >&2
        exit 1
    fi
    # Stopped first, so nothing writes while the data moves. Then under a dot name, which runsvdir
    # lets go of, and told to exit, so no runsv starts it again.
    if [ -d "$S/xrnwallet-sync" ]; then
        sv -v -w 15 down xrnwallet-sync >/dev/null 2>&1 || true
        mv "$S/xrnwallet-sync" "$S/.xrnwallet-sync.old"
    fi
    if [ -d "$S/.xrnwallet-sync.old" ]; then
        sv exit "$S/.xrnwallet-sync.old" >/dev/null 2>&1 || true
        waited=0
        while sv status "$S/.xrnwallet-sync.old" >/dev/null 2>&1; do
            waited=$((waited + 1))
            if [ "$waited" -ge 20 ]; then
                echo "The old xrnwallet-sync service did not stop within 20 s, so its data was not moved." >&2
                echo "Its program is down. Run this again; if it says so again, reboot the phone (adb reboot) first." >&2
                exit 1
            fi
            sleep 1
        done
        rm -rf "$S/.xrnwallet-sync.old"
        echo "stopped the xrnwallet-sync service and removed it"
        # Its svlogd has gone with its runsv, so the log can move: the new service writes on in it.
        if [ -d "$LOGS/xrnwallet-sync" ] && [ ! -e "$LOGS/xrnwal-sync" ]; then
            mv "$LOGS/xrnwallet-sync" "$LOGS/xrnwal-sync"
            echo "moved its log to $LOGS/xrnwal-sync"
        fi
    fi
    if [ -d "$OLD" ]; then
        # A copy of the old program left running after its supervisor died would hold the port, and
        # write into the folder about to move. sv-guard stops it, then checks the port is free.
        if ! "$GUARD" '"$PORT"' "$OLD/xrnwallet-sync.py" >&2; then
            echo "Port '"$PORT"' is still taken, so the data was not moved and nothing serves the wallet now." >&2
            echo "Find what holds it (the homeserver report, or ps -eo pid,ppid,args on the phone), stop it, and run this again." >&2
            exit 1
        fi
        # One rename, so the data is always in exactly one of the two places.
        mv "$OLD" "$NEW"
        rm -rf "$NEW/xrnwallet-sync.py" "$NEW/__pycache__"
        echo "moved $OLD to $NEW, data and all"
    fi
'); then
    report "$moved"
    exit 1
fi
if [ -n "$moved" ]; then report "$moved"; else echo "  not there, so nothing to move"; fi

echo "Copying it to $HOST..."
tar czf - -C "$HERE" xrnwal-sync.py | ssh "$HOST" '
    set -e
    mkdir -p ~/xrnwal-sync/data
    chmod 700 ~/xrnwal-sync ~/xrnwal-sync/data
    cd ~/xrnwal-sync
    tar xzf -
    chmod 755 xrnwal-sync.py
'

echo "Setting up the service..."
report "$(ssh "$HOST" '
    set -e
    S=$PREFIX/var/service
    export SVDIR=$S
    # Written each time, so the service always runs as this script says.
    run() {
        cat <<EOS
#!$PREFIX/bin/sh
# xrnwal sync: the money app data and bill photos (see ~/xrnwal-sync and the home-server README).
# Login is ~/.config/xrn-sync/users, shared with the other xrn sync services.
$HOME/.local/bin/sv-guard '"$PORT"' $HOME/xrnwal-sync/xrnwal-sync.py || exit 1
exec python $HOME/xrnwal-sync/xrnwal-sync.py \\
  --port '"$PORT"' --data $HOME/xrnwal-sync/data \\
  --users $HOME/.config/xrn-sync/users 2>&1
EOS
    }
    if [ -d "$S/xrnwal-sync" ]; then
        run > "$S/xrnwal-sync/run.new"
        chmod 755 "$S/xrnwal-sync/run.new"
        mv "$S/xrnwal-sync/run.new" "$S/xrnwal-sync/run"
        sv restart xrnwal-sync >/dev/null
        echo "restarted xrnwal-sync with this program"
        exit 0
    fi
    # Built under a dot name, which runit ignores, so it never starts half written.
    T=$S/.xrnwal-sync.new
    rm -rf "$T"
    mkdir -p "$T/log"
    run > "$T/run"
    # Written out, not copied from a sibling service: a fresh phone has none to copy.
    printf "#!%s/bin/sh\nmkdir -p \"%s/var/log/sv/xrnwal-sync\"\nexec svlogd -tt \"%s/var/log/sv/xrnwal-sync\"\n" \
        "$PREFIX" "$PREFIX" "$PREFIX" > "$T/log/run"
    chmod 755 "$T/run" "$T/log/run"
    chmod 700 "$T" "$T/log"
    mv "$T" "$S/xrnwal-sync"
    echo "installed the xrnwal-sync service, on port '"$PORT"'"
')"

echo "Waiting for it to answer..."
tries=0
until [ "$(ssh "$HOST" "curl -s -m 3 http://127.0.0.1:$PORT/health" || true)" = ok ]; do
    tries=$((tries + 1))
    if [ "$tries" -ge 8 ]; then
        echo "It did not start. Its log:" >&2
        ssh "$HOST" 'tail -20 $PREFIX/var/log/sv/xrnwal-sync/current' >&2 || true
        exit 1
    fi
    sleep 2
done
report "it answers on 127.0.0.1:$PORT/health"

# `funnel`, never `serve`: serve rewrites the port as tailnet-only and takes the calendar offline.
# /xrnwallet is set again only while it is there, so a run after it was taken off doesn't bring it back.
echo "Publishing /xrnwal..."
report "$(ssh "$HOST" '
    set -e
    tailscale funnel --bg --set-path /xrnwal http://127.0.0.1:'"$PORT"' >/dev/null
    echo "published /xrnwal, to port '"$PORT"'"
    if tailscale funnel status --json 2>/dev/null | grep -q "\"/xrnwallet\""; then
        tailscale funnel --bg --set-path /xrnwallet http://127.0.0.1:'"$PORT"' >/dev/null
        echo "kept /xrnwallet on the same port, for phones still set up with it"
    fi
')"
ssh "$HOST" 'tailscale funnel status'

echo
echo "Done. What it did:"
printf '%s' "$did" | sed 's/^/  - /'
