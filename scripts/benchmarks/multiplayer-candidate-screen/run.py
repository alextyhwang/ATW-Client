"""Offline standalone Java 17 screen. Run from any directory; no mod build."""
import argparse
import csv
import hashlib
import json
from pathlib import Path
import re
import statistics
import subprocess
import time

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUT = HERE / "out"
LABEL = "weave-mods/atw-levelhead/src/main/java/com/atw/levelhead/data/LevelTag.java"
MAP = "weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/PlayerMinimapRenderer.java"
SET = "weave-mods/optimal-zone/src/main/java/com/atw/optimalzone/render/ProfileUuidSet.java"


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--forks", type=int, default=4)
    parser.add_argument("--samples", type=int, default=9)
    args = parser.parse_args()
    if not 2 <= args.forks <= 6 or not 5 <= args.samples <= 15:
        raise SystemExit("Use 2..6 forks and 5..15 samples to keep the screen bounded.")
    OUT.mkdir(exist_ok=True)
    src = OUT / "src"
    classes = OUT / "classes"
    classes.mkdir(exist_ok=True)
    originals = {"candidate-label": (ROOT / LABEL).read_bytes(),
                 "candidate-set": (ROOT / SET).read_bytes(),
                 "reference-label": git("show", "HEAD:" + LABEL),
                 "reference-renderer": git("show", "HEAD:" + MAP)}
    head = git("rev-parse", "HEAD").decode().strip()
    renderer = originals["reference-renderer"].decode()
    start = renderer.index("    private static final class ProfileUuidSet {")
    end = start
    depth = 0
    opened = False
    for end in range(start, len(renderer)):
        if renderer[end] == "{":
            depth += 1
            opened = True
        elif renderer[end] == "}":
            depth -= 1
            if opened and depth == 0:
                break
    reference_set = renderer[start:end + 1]
    # Visibility/package adaptation only; original method bodies are preserved.
    reference_set = reference_set.replace("private static final class", "final class", 1)
    reference_set = re.sub(r"private (void|int|boolean) (clear|add|countMissingFrom|copyFrom|contains)\(",
                           r"\1 \2(", reference_set)
    reference_set = reference_set.replace("private int size;", "private int size;\n    int size() { return size; }")
    template = (HERE / "Arms.java.template").read_text(encoding="utf-8")
    adapted = {}
    for arm in ("candidate", "reference"):
        directory = src / "screen" / arm
        directory.mkdir(parents=True, exist_ok=True)
        label = originals[arm + "-label"].decode()
        label = re.sub(r"package [^;]+;", f"package screen.{arm};", label, count=1)
        if arm == "candidate":
            uuid_set = re.sub(r"package [^;]+;", f"package screen.{arm};",
                              originals["candidate-set"].decode(), count=1)
        else:
            uuid_set = f"package screen.{arm};\nimport java.util.UUID;\nimport java.util.Arrays;\n{reference_set}\n"
        for name, content in (("LevelTag.java", label), ("ProfileUuidSet.java", uuid_set),
                              ("Arms.java", template.replace("@ARM@", arm))):
            data = content.encode()
            (directory / name).write_bytes(data)
            adapted[f"{arm}/{name}"] = digest(data)
    screen = src / "screen" / "Screen.java"
    screen.write_bytes((HERE / "Screen.java").read_bytes())
    provenance = {"head": head, "branch": git("branch", "--show-current").decode().strip(),
                  "source_paths": {"candidate-label": LABEL, "candidate-set": SET,
                                   "reference-label": "HEAD:" + LABEL, "reference-renderer": "HEAD:" + MAP},
                  "original_sha256": {name: digest(data) for name, data in originals.items()},
                  "adapted_sha256": adapted,
                  "harness_sha256": {p.name: digest(p.read_bytes()) for p in
                                     (HERE / "Screen.java", HERE / "Arms.java.template", HERE / "run.py")},
                  "java_sha256": digest((ROOT / "runtime/java/bin/java.exe").read_bytes()),
                  "forks": args.forks, "samples": args.samples}
    (OUT / "provenance.json").write_text(json.dumps(provenance, indent=2))
    java = ROOT / "runtime/java/bin/java.exe"
    javac = ROOT / "runtime/java/bin/javac.exe"
    version = subprocess.run([str(java), "-version"], capture_output=True, text=True, check=True)
    (OUT / "java-version.txt").write_text(version.stdout + version.stderr)
    commands = [[str(javac), "--release", "17", "-encoding", "UTF-8", "-d", str(classes),
                 *map(str, sorted(src.rglob("*.java")))]]
    subprocess.run(commands[0], cwd=ROOT, check=True, timeout=60)
    for fork in range(args.forks):
        command = [str(java), "-Xms256m", "-Xmx256m", "-XX:+UseG1GC", "-cp", str(classes),
                   "screen.Screen", str(fork), str(args.samples)]
        commands.append(command)
        (OUT / "commands.json").write_text(json.dumps(commands, indent=2))
        print(f"Fork {fork + 1}/{args.forks} (sequential)", flush=True)
        started = time.monotonic()
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, timeout=150)
        (OUT / f"fork-{fork}.csv").write_text(result.stdout)
        (OUT / f"fork-{fork}.stderr.txt").write_text(result.stderr)
        if result.returncode:
            raise SystemExit(result.stderr)
        print(f"  completed in {time.monotonic() - started:.1f}s", flush=True)
    rows = []
    for fork in range(args.forks):
        rows.extend(csv.DictReader((OUT / f"fork-{fork}.csv").read_text().splitlines()))
    grouped = {}
    for row in rows:
        grouped.setdefault((row["case"], row["arm"], int(row["fork"])), []).append(row)
    summaries = []
    for case in dict.fromkeys(row["case"] for row in rows):
        entry = {"case": case}
        forks = {}
        for arm in ("reference", "candidate"):
            values, allocations = [], []
            for fork in range(args.forks):
                measurements = grouped[(case, arm, fork)]
                values.append(statistics.median(float(r["ns_per_op"]) for r in measurements))
                allocations.append(statistics.median(float(r["bytes_per_op"]) for r in measurements))
            forks[arm] = values
            entry[arm + "_ns"] = statistics.median(values)
            entry[arm + "_min_ns"] = min(values)
            entry[arm + "_max_ns"] = max(values)
            entry[arm + "_bytes"] = statistics.median(allocations)
        entry["delta_ns"] = entry["candidate_ns"] - entry["reference_ns"]
        ratios = [c / r for c, r in zip(forks["candidate"], forks["reference"])]
        entry["ratio"] = statistics.median(ratios)
        entry["ratio_min"] = min(ratios)
        entry["ratio_max"] = max(ratios)
        summaries.append(entry)
    with (OUT / "aggregate.csv").open("w", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=list(summaries[0]))
        writer.writeheader()
        writer.writerows(summaries)
    print(f"Raw forks, checksums, provenance, commands and aggregate: {OUT}", flush=True)


if __name__ == "__main__":
    main()
