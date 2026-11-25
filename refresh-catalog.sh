#!/usr/bin/env bash
# Rebuilds a semester catalog CSV from Banner with no login or copied headers.
#
#   ./refresh-catalog.sh <term> <name> [previousFinalCsv]
#   ./refresh-catalog.sh 202701 spring2027
#     -> spring2027CoursesOriginal.csv          (raw sections from Banner)
#        spring2027CoursesWithDescriptions.csv  (+ description column)
#        spring2027CoursesFinal.csv             (+ prereq/coreq columns; load this in the app)
#
# If <name>CoursesFinal.csv already exists (or previousFinalCsv is given), its
# hand-corrected requirement columns are carried over for every course whose
# description is unchanged. Rows that could not be matched are listed for review.
#
# Run `java HelperClasses/CatalogFetcher.java --list-terms` to see term codes.
set -euo pipefail

if [ $# -lt 2 ] || [ $# -gt 3 ]; then
  echo "Usage: $0 <term> <name> [previousFinalCsv]    e.g. $0 202701 spring2027" >&2
  exit 1
fi
term="$1"
name="$2"
cd "$(dirname "$0")"

final="${name}CoursesFinal.csv"
previous="${3:-$final}"
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

java HelperClasses/CatalogFetcher.java "${name}CoursesOriginal.csv" "$term"
java HelperClasses/DescriptionFetcher.java "${name}CoursesOriginal.csv" "${name}CoursesWithDescriptions.csv" "$term"
java HelperClasses/PrereqExtractor.java "${name}CoursesWithDescriptions.csv" "$work/extracted.csv"

if [ -f "$previous" ]; then
  java HelperClasses/RequirementMerger.java "$work/extracted.csv" "$previous" "$work/merged.csv"
  cp "$work/merged.csv" "$final"
else
  cp "$work/extracted.csv" "$final"
fi

echo
echo "Done. Wrote $final. Review flagged requirements with:"
echo "  java HelperClasses/PrereqAudit.java $final"
