import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * PrereqExtractor
 * ================
 * Reads the course CSV that already has descriptions (from DescriptionFetcher)
 * and produces a NEW csv with the same course info, plus two new columns for
 * structured prerequisite/corequisite data, plus the description at the very end.
 *
 * FINAL COLUMN ORDER:
 *   [all 17 original columns] , prereqGroups , coreqGroups , description
 *
 * Every row keeps its own copy of prereq/coreq/description info (even though
 * many rows are just different sections of the same course and therefore
 * share identical values) so the file stays a single flat table, matching
 * how the rest of this project's data is structured. The DESCRIPTION ITSELF
 * is only ever parsed ONCE per unique course, though - not once per row -
 * so this stays fast even though the output has one row per section.
 *
 * PREREQ/COREQ MINI-SYNTAX (matches the style already used elsewhere in this
 * project's CSV, e.g. the meetings column):
 *   - Different REQUIRED groups (all must be satisfied - an "AND") are
 *     separated by ";;"
 *   - Within one group, alternative courses that each individually satisfy
 *     it (an "OR") are separated by "|"
 *
 *   Example: "ACC1001;;IDS1015|IDS1020;;IDS1010"
 *   means: ACC1001 AND (IDS1015 OR IDS1020) AND IDS1010
 *
 * A course with no prerequisites (or no corequisites) just gets an empty
 * string in that column.
 *
 * KNOWN LIMITATION - "needsReview" courses:
 * A handful of courses phrase their requirement as something that isn't a
 * course code at all (e.g. "permission of instructor", "two years of high
 * school mathematics", "graduate standing"). Those can't be turned into a
 * course code automatically. This script does NOT silently drop them - it
 * prints every one of them to the console while running, so you can see
 * exactly which courses need a manual look, and their raw description text
 * is preserved in the final "description" column either way.
 *
 * Usage: java PrereqExtractor <inputCsv> <outputCsv>
 * Example: java PrereqExtractor fall2026CoursesWDesc.csv fall2026CoursesFinal.csv
 */
public class PrereqExtractor {

    // Matches "Prerequisite(s):" / "Corequisite(s):" / "Pre-requisite:" etc.
    // Requires a colon right after the word, so we don't false-match on the
    // word "prerequisite" just appearing casually inside a sentence.
    private static final Pattern LABEL =
            Pattern.compile("(pre-?requisites?\\(?s?\\)?|co-?requisites?\\(?s?\\)?)\\s*:", Pattern.CASE_INSENSITIVE);

    // Matches a department + course number, e.g. "ACC 1001", "IDS1010"
    private static final Pattern COURSE_CODE =
            Pattern.compile("\\b([A-Z]{2,5})\\s?(\\d{3,4}[A-Z]?)\\b");

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java PrereqExtractor <inputCsv> <outputCsv>");
            return;
        }
        Path inputPath = Path.of(args[0]);
        Path outputPath = Path.of(args[1]);

        List<String> lines = Files.readAllLines(inputPath);

        // Cache of courseId -> already-computed [prereqGroups, coreqGroups], so
        // that a description shared by many sections only gets parsed once.
        Map<String, String[]> cache = new HashMap<>();

        List<String> outputLines = new ArrayList<>();
        int needsReviewCount = 0;

        for (String line : lines) {
            if (line.isBlank()) continue;

            String[] fields = parseQuotedCsvLine(line);
            if (fields.length < 18) {
                System.out.println("Skipping malformed line (expected 18 fields, got " + fields.length + "): "
                        + line.substring(0, Math.min(80, line.length())));
                continue;
            }

            String courseId = fields[1];
            String description = fields[17];

            String[] cached = cache.get(courseId);
            if (cached == null) {
                ExtractionResult result = extractRequirements(description);
                if (result.needsReview) {
                    needsReviewCount++;
                    System.out.println("NEEDS REVIEW - " + fields[2] + " " + fields[3]
                            + " (courseId " + courseId + "): " + result.rawClause);
                }
                cached = new String[] { result.prereqGroups, result.coreqGroups };
                cache.put(courseId, cached);
            }
            String prereqGroups = cached[0];
            String coreqGroups = cached[1];

            // Rebuild the line: original 17 fields (unchanged), then prereqs, then
            // coreqs, then the description moved to the very end.
            StringBuilder rebuilt = new StringBuilder();
            for (int i = 0; i < 17; i++) {
                if (i > 0) rebuilt.append(",");
                rebuilt.append(quote(fields[i]));
            }
            rebuilt.append(",").append(quote(prereqGroups));
            rebuilt.append(",").append(quote(coreqGroups));
            rebuilt.append(",").append(quote(description));

            outputLines.add(rebuilt.toString());
        }

        Files.write(outputPath, outputLines);
        System.out.println();
        System.out.println("Wrote " + outputPath + " (" + outputLines.size() + " rows).");
        System.out.println(needsReviewCount + " unique course(s) flagged as needsReview (printed above) - "
                + "search the output file by dept/number to find and fix those by hand.");
    }

    /** Holds the parsed result for a single course's description. */
    private static class ExtractionResult {
        String prereqGroups = "";
        String coreqGroups = "";
        boolean needsReview = false;
        String rawClause = "";
    }

    private static ExtractionResult extractRequirements(String description) {
        ExtractionResult result = new ExtractionResult();

        Matcher firstLabel = LABEL.matcher(description);
        if (!firstLabel.find()) {
            return result; // no prereq/coreq language at all - nothing to do
        }

        String clauseText = description.substring(firstLabel.start());
        result.rawClause = clauseText;

        // Walk through every "Prerequisite(s):"/"Corequisite(s):" label found in
        // the clause, and treat the text between one label and the next (or the
        // end of the string) as that label's requirement text.
        Matcher labelMatcher = LABEL.matcher(clauseText);
        List<int[]> labelPositions = new ArrayList<>(); // [start, end]
        List<String> labelTypes = new ArrayList<>();     // "prereq" or "coreq"
        while (labelMatcher.find()) {
            labelPositions.add(new int[] { labelMatcher.start(), labelMatcher.end() });
            labelTypes.add(labelMatcher.group(1).toLowerCase().startsWith("co") ? "coreq" : "prereq");
        }

        List<String> prereqGroupList = new ArrayList<>();
        List<String> coreqGroupList = new ArrayList<>();

        for (int i = 0; i < labelPositions.size(); i++) {
            int segmentStart = labelPositions.get(i)[1];
            int segmentEnd = (i + 1 < labelPositions.size()) ? labelPositions.get(i + 1)[0] : clauseText.length();
            String segmentText = clauseText.substring(segmentStart, segmentEnd).trim();
            if (segmentText.endsWith(".")) {
                segmentText = segmentText.substring(0, segmentText.length() - 1).trim();
            }

            List<String> groups = extractGroups(segmentText);
            if (groups.isEmpty()) {
                // Labeled as a requirement, but no course code could be found in it
                // at all (e.g. "permission of instructor") - flag for a human to look at.
                result.needsReview = true;
                continue;
            }

            if (labelTypes.get(i).equals("prereq")) {
                prereqGroupList.addAll(groups);
            } else {
                coreqGroupList.addAll(groups);
            }
        }

        result.prereqGroups = String.join(";;", prereqGroupList);
        result.coreqGroups = String.join(";;", coreqGroupList);
        return result;
    }

    /**
     * Splits one label's requirement text into AND-groups (separated by ";"
     * in the original text), and within each group extracts course codes as
     * OR-alternatives. Groups where NO course code is found are dropped here
     * (the caller marks the course as needsReview when this happens) since a
     * plain-text note like "or equivalent" can't be safely turned into a
     * course code automatically.
     */
    private static List<String> extractGroups(String segmentText) {
        List<String> result = new ArrayList<>();
        for (String rawGroup : segmentText.split(";")) {
            Matcher codeMatcher = COURSE_CODE.matcher(rawGroup);
            List<String> codesInGroup = new ArrayList<>();
            while (codeMatcher.find()) {
                codesInGroup.add(codeMatcher.group(1).toUpperCase() + codeMatcher.group(2).toUpperCase());
            }
            if (!codesInGroup.isEmpty()) {
                result.add(String.join("|", codesInGroup));
            }
        }
        return result;
    }

    /** Wraps a value in quotes for CSV output, matching the existing file's format. */
    private static String quote(String value) {
        if (value == null) value = "";
        return "\"" + value.replace("\"", "'") + "\"";
    }

    /**
     * Splits a CSV line on commas, respecting the fact that every field in this
     * project's CSVs is individually quoted (e.g. "field1","field2",...) and
     * strips the surrounding quotes off each field.
     */
    private static String[] parseQuotedCsvLine(String line) {
        String[] rawFields = line.split(",", -1);
        String[] result = new String[rawFields.length];
        for (int i = 0; i < rawFields.length; i++) {
            String f = rawFields[i];
            if (f.length() >= 2 && f.startsWith("\"") && f.endsWith("\"")) {
                f = f.substring(1, f.length() - 1);
            }
            result[i] = f;
        }
        return result;
    }
}