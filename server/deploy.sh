#!/bin/sh
# Installs or updates xrnwallet-sync on a home server reached over ssh (Termux with runit and
# Tailscale Funnel), next to the other sync services. Safe to run again: an update replaces the
# program and restarts it, and leaves the data alone.
#
#     server/deploy.sh [ssh-host]      (default: homeserver)
set -eu

HOST=${1:-homeserver}
HERE=$(cd "$(dirname "$0")" && pwd)

echo "Testing the server code first..."
if ! (cd "$HERE/.." && PYTHONDONTWRITEBYTECODE=1 python3 -m unittest server/test_xrnwallet_sync.py >/dev/null 2>&1); then
    echo "The server tests failed, so nothing was changed. Run them for details:" >&2
    echo "    python3 -m unittest server/test_xrnwallet_sync.py" >&2
    exit 1
fi

echo "Copying it to $HOST..."
tar czf - -C "$HERE" xrnwallet-sync.py | ssh "$HOST" '
    set -e
    mkdir -p ~/xrnwallet-sync/data
    chmod 700 ~/xrnwallet-sync ~/xrnwallet-sync/data
    cd ~/xrnwallet-sync
    tar xzf -
    chmod 755 xrnwallet-sync.py
'

echo "Setting up the service..."
ssh "$HOST" '
    set -e
    S=$PREFIX/var/service
    export SVDIR=$S
    if [ -d "$S/xrnwallet-sync" ]; then
        sv restart xrnwallet-sync
        exit 0
    fi
    # Built under a dot name, which runit ignores, so it never starts half written.
    T=$S/.xrnwallet-sync.new
    rm -rf "$T"
    mkdir -p "$T/log"
    cat > "$T/run" <<EOS
#!$PREFIX/bin/sh
# xrnwallet sync: the money app data and bill photos (see ~/xrnwallet-sync and the home-server README).
# Login is ~/.config/xrn-sync/users, shared with xrnloops-sync, xrnrule-sync and xrnhome-sync.
exec python $HOME/xrnwallet-sync/xrnwallet-sync.py \\
  --port 5237 --data $HOME/xrnwallet-sync/data \\
  --users $HOME/.config/xrn-sync/users 2>&1
EOS
    cp "$S/xrnhome-sync/log/run" "$T/log/run"
    chmod 755 "$T/run" "$T/log/run"
    chmod 700 "$T" "$T/log"
    mv "$T" "$S/xrnwallet-sync"
'

echo "Waiting for it to answer..."
tries=0
until [ "$(ssh "$HOST" 'curl -s -m 3 http://127.0.0.1:5237/health' || true)" = ok ]; do
    tries=$((tries + 1))
    if [ "$tries" -ge 8 ]; then
        echo "It did not start. Its log:" >&2
        ssh "$HOST" 'tail -20 $PREFIX/var/log/sv/xrnwallet-sync/current' >&2 || true
        exit 1
    fi
    sleep 2
done

# `funnel`, never `serve`: serve rewrites the port as tailnet-only and takes the calendar offline.
echo "Publishing /xrnwallet..."
ssh "$HOST" 'tailscale funnel --bg --set-path /xrnwallet http://127.0.0.1:5237 >/dev/null; tailscale funnel status'

echo "Done."
