"""Summarize the existing raw forks without starting another benchmark."""
import csv
import json
from pathlib import Path
import statistics

OUT = Path(__file__).resolve().parent / "out"


def percentile(values, p):
    ordered = sorted(values)
    index = (len(ordered) - 1) * p
    low = int(index)
    high = min(low + 1, len(ordered) - 1)
    return ordered[low] + (ordered[high] - ordered[low]) * (index - low)


def main():
    provenance = json.loads((OUT / "provenance.json").read_text())
    rows = []
    for fork in range(provenance["forks"]):
        rows.extend(csv.DictReader((OUT / f"fork-{fork}.csv").read_text().splitlines()))
    distributions = {}
    for case in dict.fromkeys(r["case"] for r in rows):
        result = {}
        for arm in ("reference", "candidate"):
            selected = [r for r in rows if r["case"] == case and r["arm"] == arm]
            values = [float(r["ns_per_op"]) for r in selected]
            medians = [statistics.median(float(r["ns_per_op"]) for r in selected if int(r["fork"]) == f)
                       for f in range(provenance["forks"])]
            result[arm] = {"fork_median_ns": medians,
                           "sample_p10_ns": percentile(values, .1), "sample_p90_ns": percentile(values, .9),
                           "sample_min_ns": min(values), "sample_max_ns": max(values)}
        distributions[case] = result
    (OUT / "distributions.json").write_text(json.dumps(distributions, indent=2))
    aggregate = list(csv.DictReader((OUT / "aggregate.csv").read_text().splitlines()))
    lines = ["| Case | HEAD ns/op [fork median range] | Candidate ns/op [fork median range] | Delta ns/op | C/H ratio [fork range] | HEAD / candidate allocated B/op |",
             "| --- | ---: | ---: | ---: | ---: | ---: |"]
    for r in aggregate:
        lines.append(f"| {r['case']} | {float(r['reference_ns']):.1f} [{float(r['reference_min_ns']):.1f}–{float(r['reference_max_ns']):.1f}]"
                     f" | {float(r['candidate_ns']):.1f} [{float(r['candidate_min_ns']):.1f}–{float(r['candidate_max_ns']):.1f}]"
                     f" | {float(r['delta_ns']):+.1f} | {float(r['ratio']):.2f} [{float(r['ratio_min']):.2f}–{float(r['ratio_max']):.2f}]"
                     f" | {float(r['reference_bytes']):.1f} / {float(r['candidate_bytes']):.1f} |")
    (OUT / "aggregate.md").write_text("\n".join(lines) + "\n", encoding="utf-8")
    print("\n".join(lines))


if __name__ == "__main__":
    main()
