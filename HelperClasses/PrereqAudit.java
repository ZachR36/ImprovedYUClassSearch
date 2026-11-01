import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-time data-quality check for the prerequisite fields in the final course
 * CSV. It flags likely AND/OR encoding mistakes without changing any data.
 *
 * <p>Usage: {@code java PrereqAudit <courses.csv> [report.txt]}</p>
 */
public class PrereqAudit {
    private static final int CRN_INDEX = 0;
    private static final int NUMBER_INDEX = 1;
    private static final int DEPARTMENT_INDEX = 2;
    private static final int PREREQ_INDEX = 17;
    private static final int COREQ_INDEX = 18;
    private static final int FLEXIBLE_PREREQ_INDEX = 19;
    private static final int DESCRIPTION_INDEX = 21;

    private static final Pattern PREREQUISITE_LABEL = Pattern.compile(
            "(?i)pre[- ]?requisites?(?:\\(s\\))?\\s*:");
    private static final Pattern REQUIREMENT_LABEL = Pattern.compile(
            "(?i)(?:pre[- ]?requisites?(?:\\(s\\))?|co[- ]?requisites?(?:\\(s\\))?)\\s*:");
    private static final Pattern REQUIREMENT_NOTE = Pattern.compile(
            "(?i)(?:cross[- ]?listed with|previously offered as|section information text:|"
                    + "not recommended to be taken with|the associated lab)");
    private static final Pattern COURSE_CODE = Pattern.compile(
            "(?i)\\b([A-Z]{2,5})\\s?(\\d{3,4}[A-Z]?)\\b");
    private static final Set<String> NON_DEPARTMENT_PREFIXES = new HashSet<>(Set.of(
            "OR", "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT",
            "NOV", "DEC"));

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.out.println("Usage: java PrereqAudit <courses.csv> [report.txt]");
            return;
        }

        String report = audit(Path.of(args[0]));
        System.out.print(report);
        if (args.length == 2) {
            try (BufferedWriter writer = Files.newBufferedWriter(Path.of(args[1]))) {
                writer.write(report);
            }
            System.out.println("\nReport saved to " + args[1]);
        }
    }

    static String audit(Path csvPath) throws IOException {
        Map<String, CourseRows> rowsByCode = loadCourses(csvPath);
        List<String> findings = new ArrayList<>();
        int sectionCount = 0;

        for (CourseRows courseRows : rowsByCode.values()) {
            sectionCount += courseRows.rows.size();
            findings.addAll(findingsFor(courseRows));
        }

        StringBuilder report = new StringBuilder();
        report.append("Prerequisite data audit\n");
        report.append("Sections scanned: ").append(sectionCount).append('\n');
        report.append("Unique course codes: ").append(rowsByCode.size()).append("\n\n");
        report.append("This report flags likely discrepancies for review; it does not edit course data.\n");
        report.append("A pipe (|) means OR and double semicolons (;;) mean AND.\n\n");

        if (findings.isEmpty()) {
            report.append("No likely prerequisite encoding mismatches were found.\n");
        } else {
            report.append("Findings: ").append(findings.size()).append("\n\n");
            for (String finding : findings) {
                report.append(finding).append("\n\n");
            }
        }
        return report.toString();
    }

    private static Map<String, CourseRows> loadCourses(Path csvPath) throws IOException {
        Map<String, CourseRows> rowsByCode = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                List<String> fields = parseCsvLine(line);
                if (fields.size() <= DESCRIPTION_INDEX) {
                    continue;
                }

                CourseRow row = new CourseRow(fields);
                rowsByCode.computeIfAbsent(row.courseCode, ignored -> new CourseRows()).rows.add(row);
            }
        }
        return rowsByCode;
    }

    private static List<String> findingsFor(CourseRows courseRows) {
        CourseRow representative = courseRows.rows.get(0);
        List<String> findings = new ArrayList<>();

        if (hasInconsistentStructuredData(courseRows.rows)) {
            findings.add(formatFinding("INCONSISTENT_SECTIONS", representative, courseRows,
                    "Different sections with this course code have different structured "
                            + "requirement fields. Confirm whether that is intentional."));
        }

        String prerequisiteText = prerequisiteText(representative.description);
        String allRequirementText = allRequirementText(representative.description);
        Set<String> proseCodes = extractCodes(prerequisiteText);
        Set<String> allProseCodes = extractCodes(allRequirementText);
        Set<String> structuredCodes = new LinkedHashSet<>();
        structuredCodes.addAll(extractCodes(representative.prereqs));
        structuredCodes.addAll(extractCodes(representative.coreqs));
        structuredCodes.addAll(extractCodes(representative.flexiblePrereqs));

        if (!allProseCodes.equals(structuredCodes)
                && (!allProseCodes.isEmpty() || !structuredCodes.isEmpty())) {
            findings.add(formatFinding("CODE_SET_MISMATCH", representative, courseRows,
                    "Codes in the prerequisite/corequisite prose differ from the structured fields.\n"
                            + "  prose codes: " + displayCodes(allProseCodes) + "\n"
                            + "  structured codes: " + displayCodes(structuredCodes)));
        }

        for (CoursePair pair : adjacentCodePairs(prerequisiteText)) {
            if (containsWord(pair.between, "and") && !containsWord(pair.between, "or")
                    && inSameOrGroup(representative.prereqs, pair.first, pair.second)) {
                findings.add(formatFinding("POSSIBLE_AND_STORED_AS_OR", representative, courseRows,
                        pair.first + " and " + pair.second + " appear joined by 'and' in the "
                                + "prerequisite prose but share an OR group (|).\n"
                                + "  pair context: " + pair.context));
            }
            if (containsWord(pair.between, "or") && inDifferentAndGroups(
                    representative.prereqs, pair.first, pair.second)) {
                findings.add(formatFinding("POSSIBLE_OR_STORED_AS_AND", representative, courseRows,
                        pair.first + " and " + pair.second + " appear joined by 'or' in the "
                                + "prerequisite prose but are in separate AND groups (;;).\n"
                                + "  pair context: " + pair.context));
            }
        }

        return new ArrayList<>(new LinkedHashSet<>(findings));
    }

    private static boolean hasInconsistentStructuredData(List<CourseRow> rows) {
        CourseRow first = rows.get(0);
        for (CourseRow row : rows) {
            if (!first.prereqs.equals(row.prereqs) || !first.coreqs.equals(row.coreqs)
                    || !first.flexiblePrereqs.equals(row.flexiblePrereqs)) {
                return true;
            }
        }
        return false;
    }

    private static String prerequisiteText(String description) {
        Matcher label = PREREQUISITE_LABEL.matcher(description);
        if (!label.find()) {
            return "";
        }
        int end = requirementEnd(description, label.end());
        Matcher nextLabel = REQUIREMENT_LABEL.matcher(description);
        if (nextLabel.find(label.end()) && nextLabel.start() < end) {
            end = nextLabel.start();
        }
        return description.substring(label.end(), end);
    }

    private static String allRequirementText(String description) {
        Matcher firstLabel = REQUIREMENT_LABEL.matcher(description);
        if (!firstLabel.find()) {
            return "";
        }
        return description.substring(firstLabel.end(), requirementEnd(description, firstLabel.end()));
    }

    private static int requirementEnd(String description, int start) {
        Matcher note = REQUIREMENT_NOTE.matcher(description);
        return note.find(start) ? note.start() : description.length();
    }

    private static Set<String> extractCodes(String text) {
        Set<String> codes = new LinkedHashSet<>();
        Matcher matcher = COURSE_CODE.matcher(text);
        while (matcher.find()) {
            String code = codeFromMatch(matcher);
            if (code != null) {
                codes.add(code);
            }
        }
        return codes;
    }

    private static List<CoursePair> adjacentCodePairs(String text) {
        List<CodeMatch> matches = new ArrayList<>();
        Matcher matcher = COURSE_CODE.matcher(text);
        while (matcher.find()) {
            String code = codeFromMatch(matcher);
            if (code != null) {
                matches.add(new CodeMatch(code, matcher.start(), matcher.end()));
            }
        }

        List<CoursePair> pairs = new ArrayList<>();
        for (int i = 0; i + 1 < matches.size(); i++) {
            CodeMatch first = matches.get(i);
            CodeMatch second = matches.get(i + 1);
            String between = text.substring(first.end, second.start);
            String context = text.substring(Math.max(0, first.start - 25),
                    Math.min(text.length(), second.end + 25)).replaceAll("\\s+", " ").trim();
            pairs.add(new CoursePair(first.code, second.code, between, context));
        }
        return pairs;
    }

    private static boolean inSameOrGroup(String groups, String firstCode, String secondCode) {
        for (String andGroup : groups.split(";;")) {
            Set<String> codes = extractCodes(andGroup);
            if (codes.contains(firstCode) && codes.contains(secondCode) && andGroup.contains("|")) {
                return true;
            }
        }
        return false;
    }

    private static boolean inDifferentAndGroups(String groups, String firstCode, String secondCode) {
        int firstGroup = groupIndex(groups, firstCode);
        int secondGroup = groupIndex(groups, secondCode);
        return firstGroup >= 0 && secondGroup >= 0 && firstGroup != secondGroup;
    }

    private static int groupIndex(String groups, String code) {
        String[] andGroups = groups.split(";;");
        for (int i = 0; i < andGroups.length; i++) {
            if (extractCodes(andGroups[i]).contains(code)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsWord(String text, String word) {
        return Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b").matcher(text).find();
    }

    private static String formatFinding(String type, CourseRow row, CourseRows courseRows, String details) {
        return "[" + type + "] " + row.courseCode + " (CRNs: " + courseRows.crns() + ")\n"
                + "  structured prereqs: " + displayField(row.prereqs) + "\n"
                + "  structured coreqs: " + displayField(row.coreqs) + "\n"
                + "  flexible prereqs: " + displayField(row.flexiblePrereqs) + "\n"
                + "  " + details + "\n"
                + "  description: " + compact(row.description);
    }

    private static String compact(String text) {
        String compacted = text.replaceAll("\\s+", " ").trim();
        return compacted.length() <= 360 ? compacted : compacted.substring(0, 357) + "...";
    }

    private static String displayCodes(Set<String> codes) {
        return codes.isEmpty() ? "(none)" : String.join(", ", codes);
    }

    private static String displayField(String value) {
        return value.isBlank() ? "(blank)" : value;
    }

    private static String normalizeCode(String code) {
        return code.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private static String codeFromMatch(Matcher matcher) {
        String department = matcher.group(1).toUpperCase(Locale.ROOT);
        if (NON_DEPARTMENT_PREFIXES.contains(department)) {
            return null;
        }
        return normalizeCode(department + matcher.group(2));
    }

    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(current);
            }
        }
        fields.add(field.toString());
        return fields;
    }

    private static class CourseRows {
        private final List<CourseRow> rows = new ArrayList<>();

        private String crns() {
            List<String> crns = new ArrayList<>();
            for (CourseRow row : rows) {
                crns.add(row.crn);
            }
            return String.join(", ", crns);
        }
    }

    private static class CourseRow {
        private final String crn;
        private final String courseCode;
        private final String prereqs;
        private final String coreqs;
        private final String flexiblePrereqs;
        private final String description;

        private CourseRow(List<String> fields) {
            crn = fields.get(CRN_INDEX).trim();
            courseCode = normalizeCode(fields.get(DEPARTMENT_INDEX) + fields.get(NUMBER_INDEX));
            prereqs = fields.get(PREREQ_INDEX).trim();
            coreqs = fields.get(COREQ_INDEX).trim();
            flexiblePrereqs = fields.get(FLEXIBLE_PREREQ_INDEX).trim();
            description = fields.get(DESCRIPTION_INDEX).trim();
        }
    }

    private static class CodeMatch {
        private final String code;
        private final int start;
        private final int end;

        private CodeMatch(String code, int start, int end) {
            this.code = code;
            this.start = start;
            this.end = end;
        }
    }

    private static class CoursePair {
        private final String first;
        private final String second;
        private final String between;
        private final String context;

        private CoursePair(String first, String second, String between, String context) {
            this.first = first;
            this.second = second;
            this.between = between;
            this.context = context;
        }
    }
}
