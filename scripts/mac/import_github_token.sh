#!/bin/bash
# Provisions the updater's GitHub credential on a Mac from a remote shell.
# Over SSH the login keychain is locked (SecItemAdd fails with -25308), so the
# import runs in the logged-in desktop session through a one-shot LaunchAgent.
# The token arrives on this script's stdin, sits in a 0600 file for a few
# seconds, and is never in an argument, a log or the output:
#   gh auth token | ssh user@mac 'bash /path/to/import_github_token.sh'
# Prefer a fine-grained token limited to alextyhwang/ATW-Client-Portable with
# Contents: read; the GitHub CLI token usually has write access to every repo.
# Copy this script to the Mac first and run it as a command, not on stdin.
# Runs on the Mac. Reads the token from this script's stdin, imports it from the GUI session, removes all traces.
set -u
U=$(id -u); WORK="$HOME/ATW-import-$$"; LABEL=com.alextyhwang.atw-token-import
mkdir -m 700 "$WORK" || exit 90
cat > "$WORK/token" ; chmod 600 "$WORK/token"
cat > "$WORK/job.plist" <<PL
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
<key>Label</key><string>$LABEL</string>
<key>ProgramArguments</key><array><string>/Applications/ATW Client.app/Contents/MacOS/atw-update</string><string>--import-github-token-stdin</string></array>
<key>StandardInPath</key><string>$WORK/token</string>
<key>StandardOutPath</key><string>$WORK/out</string>
<key>StandardErrorPath</key><string>/dev/null</string>
<key>RunAtLoad</key><true/><key>LimitLoadToSessionType</key><string>Aqua</string>
</dict></plist>
PL
launchctl bootout gui/$U/$LABEL 2>/dev/null
launchctl bootstrap gui/$U "$WORK/job.plist"; rc=$?
for i in $(seq 1 20); do [ -s "$WORK/out" ] && break; sleep 1; done
echo "bootstrap=$rc"; cat "$WORK/out" 2>/dev/null
launchctl bootout gui/$U/$LABEL 2>/dev/null
rm -P "$WORK/token" 2>/dev/null; rm -rf "$WORK"
[ -e "$WORK" ] && echo "cleanup incomplete" || echo "token file removed"
