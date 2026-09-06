import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * PrereqExtractor
 * ================
 * Reads the course CSV that already has descriptions (from DescriptionFetcher)
 * and produces a NEW csv with the same course info, plus new columns for
 * structured prerequisite/corequisite data, plus the description at the very end.
 *
 * FINAL COLUMN ORDER:
 *   [all 17 original columns] , prereqGroups , coreqGroups , flexiblePrereqs ,
 *   requirementNotes , description
 *   (columns 18, 19, 20, 21, 22 if you're counting 1-indexed, spreadsheet-style)
 *
 * IMPORTANT FIELD NOTE (this tripped us up once already - worth keeping here):
 * Column index 1 is the course's real number within its department (e.g. "1001",
 * "4930") - Course.java calls this "deptNumber". Column index 3 is the SECTION
 * code (e.g. "231", "B", "TBA"). A course's real unique identity is
 * (department + deptNumber), NOT (department + section) - two totally
 * unrelated courses in different departments can and do reuse the same
 * deptNumber by coincidence, so the cache key below MUST combine both fields.
 *
 * FOUR KINDS OF REQUIREMENT DATA, AND WHY THEY'RE SEPARATE COLUMNS:
 *   - prereqGroups     - STRICT, MACHINE-READABLE: must be completed before
 *                          taking this course. Only ever contains real course
 *                          codes in the mini-syntax below - safe for any
 *                          later logic (e.g. eligibility checks) to parse.
 *   - coreqGroups       - STRICT, MACHINE-READABLE: must be taken at the
 *                          exact same time as this course (e.g. a lecture/lab
 *                          pair). Also only ever contains real course codes.
 *   - flexiblePrereqs   - LENIENT, MACHINE-READABLE: the description says
 *                          this course "may be taken as a prerequisite or
 *                          corequisite" - satisfied whether completed
 *                          beforehand or taken alongside. Also only ever
 *                          contains real course codes.
 *   - requirementNotes  - FREE TEXT, HUMAN-READABLE ONLY: anything that was
 *                          clearly labeled as a requirement but ISN'T
 *                          expressible as a course code (e.g. "permission of
 *                          instructor", "graduate standing", "placement
 *                          exam"). Deliberately kept OUT of the three columns
 *                          above so nothing that expects a real course code
 *                          there ever chokes on a sentence - eligibility
 *                          logic should just treat this column as informational,
 *                          not something to check off automatically. IMPORTANT:
 *                          a course can have BOTH real codes in the columns
 *                          above AND a note here at the same time (e.g. a
 *                          description might list one real prerequisite course
 *                          AND separately mention "or permission of instructor").
 *
 * MINI-SYNTAX (applies to prereqGroups/coreqGroups/flexiblePrereqs only -
 * requirementNotes is always just plain text):
 *   - Different REQUIRED groups (all must be satisfied - an "AND") are
 *     separated by ";;"
 *   - Within one group, alternative courses that each individually satisfy
 *     it (an "OR") are separated by "|"
 *   Example: "ACC1001;;IDS1015|IDS1020;;IDS1010"
 *   means: ACC1001 AND (IDS1015 OR IDS1020) AND IDS1010
 *
 * A course with none of a given requirement type just gets an empty string
 * in that column.
 *
 * Usage: java PrereqExtractor <inputCsv> <outputCsv>
 * Example: java PrereqExtractor fall2026CoursesWDesc.csv fall2026CoursesFinal.csv
 */
public class PrereqExtractor {

    private static final Pattern LABEL =
            Pattern.compile("(pre-?requisites?\\(?s?\\)?|co-?requisites?\\(?s?\\)?)\\s*:", Pattern.CASE_INSENSITIVE);

    private static final Pattern COURSE_CODE =
            Pattern.compile("\\b([A-Z]{2,5})\\s?(\\d{3,4}[A-Z]?)\\b");

    private static final Pattern FLEXIBLE_PHRASE =
            Pattern.compile("may be taken as.*(pre|co)requisite", Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java PrereqExtractor <inputCsv> <outputCsv>");
            return;
        }
        Path inputPath = Path.of(args[0]);
        Path outputPath = Path.of(args[1]);

        List<String> lines = Files.readAllLines(inputPath);
        Map<String, ExtractionResult> cache = new HashMap<>();
        List<String> outputLines = new ArrayList<>();
        int notesCount = 0;

        for (String line : lines) {
            if (line.isBlank()) continue;

            String[] fields = parseQuotedCsvLine(line);
            if (fields.length < 18) {
                System.out.println("Skipping malformed line (expected 18 fields, got " + fields.length + "): "
                        + line.substring(0, Math.min(80, line.length())));
                continue;
            }

            String crn = fields[0];
            String department = fields[2];
            String deptNumber = fields[1];
            String description = fields[17];
            String cacheKey = department + "_" + deptNumber;

            ExtractionResult result = cache.get(cacheKey);
            if (result == null) {
                result = extractRequirements(description);
                if (!result.requirementNotes.isEmpty()) {
                    notesCount++;
                    System.out.println("HAS FREE-TEXT NOTE - " + department + " " + deptNumber
                            + " (CRN " + crn + "): " + result.requirementNotes);
                }
                cache.put(cacheKey, result);
            }

            StringBuilder rebuilt = new StringBuilder();
            for (int i = 0; i < 17; i++) {
                if (i > 0) rebuilt.append(",");
                rebuilt.append(quote(fields[i]));
            }
            rebuilt.append(",").append(quote(result.prereqGroups));
            rebuilt.append(",").append(quote(result.coreqGroups));
            rebuilt.append(",").append(quote(result.flexiblePrereqs));
            rebuilt.append(",").append(quote(result.requirementNotes));
            rebuilt.append(",").append(quote(description));

            outputLines.add(rebuilt.toString());
        }

        Files.write(outputPath, outputLines);
        System.out.println();
        System.out.println("Wrote " + outputPath + " (" + outputLines.size() + " rows, "
                + cache.size() + " unique courses).");
        System.out.println(notesCount + " course(s) have a requirementNotes entry (printed above) - "
                + "these are informational only and were never written into prereqGroups/coreqGroups/flexiblePrereqs.");
    }

    private static class ExtractionResult {
        String prereqGroups = "";
        String coreqGroups = "";
        String flexiblePrereqs = "";
        String requirementNotes = "";
    }

    private static ExtractionResult extractRequirements(String description) {
        ExtractionResult result = new ExtractionResult();

        Matcher firstLabel = LABEL.matcher(description);
        if (!firstLabel.find()) {
            return result;
        }

        String clauseText = description.substring(firstLabel.start());

        Matcher labelMatcher = LABEL.matcher(clauseText);
        List<int[]> labelPositions = new ArrayList<>();
        List<String> labelTypes = new ArrayList<>();
        while (labelMatcher.find()) {
            labelPositions.add(new int[] { labelMatcher.start(), labelMatcher.end() });
            labelTypes.add(labelMatcher.group(1).toLowerCase().startsWith("co") ? "coreq" : "prereq");
        }

        List<String> prereqGroupList = new ArrayList<>();
        List<String> coreqGroupList = new ArrayList<>();
        List<String> flexibleGroupList = new ArrayList<>();
        List<String> notesList = new ArrayList<>();

        for (int i = 0; i < labelPositions.size(); i++) {
            int segmentStart = labelPositions.get(i)[1];
            int segmentEnd = (i + 1 < labelPositions.size()) ? labelPositions.get(i + 1)[0] : clauseText.length();
            String segmentText = clauseText.substring(segmentStart, segmentEnd).trim();
            if (segmentText.endsWith(".")) {
                segmentText = segmentText.substring(0, segmentText.length() - 1).trim();
            }

            List<String> groups = extractGroups(segmentText);
            if (groups.isEmpty()) {
                // No real course code in this segment - keep the raw text as an
                // informational note instead of silently dropping it OR forcing it
                // into a column that's supposed to only ever hold course codes.
                notesList.add(segmentText);
                continue;
            }

            boolean isFlexible = FLEXIBLE_PHRASE.matcher(segmentText).find();
            if (isFlexible) {
                flexibleGroupList.addAll(groups);
            } else if (labelTypes.get(i).equals("prereq")) {
                prereqGroupList.addAll(groups);
            } else {
                coreqGroupList.addAll(groups);
            }
        }

        result.prereqGroups = String.join(";;", prereqGroupList);
        result.coreqGroups = String.join(";;", coreqGroupList);
        result.flexiblePrereqs = String.join(";;", flexibleGroupList);
        result.requirementNotes = String.join(" | ", notesList);
        return result;
    }

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

    private static String quote(String value) {
        if (value == null) value = "";
        return "\"" + value.replace("\"", "'") + "\"";
    }

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