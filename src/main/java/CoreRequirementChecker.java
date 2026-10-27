import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Evaluates a loaded core-requirement profile against rich completed-course records. */
public class CoreRequirementChecker {
    private static final Pattern COURSE_CODE = Pattern.compile("^([A-Z]+)(\\d+)$");
    private static final Pattern COUNT = Pattern.compile("(?:^|\\|)\\s*count:\s*(\\d+)");
    private static final Pattern CREDITS = Pattern.compile("(?:^|\\|)\\s*credits:\s*(\\d+(?:\\.\\d+)?)");
    private static final Pattern MIN_SUBJECTS = Pattern.compile("(?:^|\\|)\\s*minSubjects:\s*(\\d+)");

    private static final Map<String, String> DEPARTMENT_ALIASES = departmentAliases();
    private static final Map<String, String> ATTRIBUTE_ALIASES = Map.of("WI", "WRIN");

    private CoreRequirementChecker() { }

    /** Checks every top-level core requirement as an AND. */
    public static RequirementStatus check(CoreRequirementProgram program,
                                          Collection<CompletedCourse> completedCourses) {
        return check(program, completedCourses, "undeclared");
    }

    /** Checks a core profile using the student's saved Jewish-studies track. */
    public static RequirementStatus check(CoreRequirementProgram program,
                                          Collection<CompletedCourse> completedCourses,
                                          String jewishStudiesTrack) {
        return checkNodes(program.getRequirements(), completedCourses, jewishStudiesTrack);
    }

    private static RequirementStatus checkNodes(List<CoreRequirementProgram.RequirementNode> nodes,
                                                Collection<CompletedCourse> completed,
                                                String jewishStudiesTrack) {
        List<RequirementStatus> statuses = new ArrayList<>();
        for (CoreRequirementProgram.RequirementNode node : nodes) {
            statuses.add(checkNode(node, completed, false, jewishStudiesTrack));
        }
        return RequirementStatus.combineAnd(statuses);
    }

    private static RequirementStatus checkNode(CoreRequirementProgram.RequirementNode node,
                                               Collection<CompletedCourse> completed,
                                               boolean manualIsChoice, String jewishStudiesTrack) {
        return switch (node.getType()) {
            case COURSE_LIST -> checkCourseList(node, completed);
            case CHOOSE_N -> checkChooseN(node, completed, jewishStudiesTrack);
            case RANGE -> checkRange(node, completed);
            case TRACK_CHOICE -> checkTrackChoice(node, completed, jewishStudiesTrack);
            case ATTRIBUTE_COUNT -> checkAttributeCount(node, completed);
            case COURSE_COUNT -> checkCourseCount(node, completed);
            case COURSE_COUNT_FROM_DEPARTMENTS -> checkDepartmentCount(node, completed);
            case DISTINCT_SUBJECTS -> checkDistinctSubjects(node, completed);
            case DISTRIBUTION -> checkDistribution(node, completed);
            case CREDIT_COUNT -> checkCreditCount(node, completed);
            case MANUAL_NOTE -> manualStatus(node.getValue(), manualIsChoice);
            case OPTION, TRACK, SUBJECT, COURSE_CODE ->
                    checkNodes(node.getChildren(), completed, jewishStudiesTrack);
        };
    }

    private static RequirementStatus checkCourseList(CoreRequirementProgram.RequirementNode node,
                                                     Collection<CompletedCourse> completed) {
        List<String> remaining = new ArrayList<>();
        for (CoreRequirementProgram.RequirementNode child : node.getChildren()) {
            if (child.getType() == CoreRequirementProgram.NodeType.COURSE_CODE
                    && !hasCourse(completed, child.getValue())) {
                remaining.add(child.getValue());
            }
        }
        return statusForCourses(remaining, label(node, "Required courses"));
    }

    private static RequirementStatus checkChooseN(CoreRequirementProgram.RequirementNode node,
                                                  Collection<CompletedCourse> completed,
                                                  String jewishStudiesTrack) {
        int required = Integer.parseInt(targetSegment(node.getValue()));
        int met = 0;
        List<String> possibilities = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        for (CoreRequirementProgram.RequirementNode option : node.getChildren()) {
            RequirementStatus status;
            if (!option.getChildren().isEmpty()) {
                List<RequirementStatus> optionStatuses = new ArrayList<>();
                for (CoreRequirementProgram.RequirementNode child : option.getChildren()) {
                    // A manual requirement used as one branch of an OR cannot
                    // silently make that OR complete; an advisor must verify it.
                    optionStatuses.add(checkNode(child, completed, true, jewishStudiesTrack));
                }
                status = RequirementStatus.combineAnd(optionStatuses);
            } else if (!option.getValue().isBlank()) {
                status = hasCourse(completed, option.getValue())
                        ? satisfied() : unsatisfied(List.of(option.getValue()), List.of(option.getValue()));
            } else {
                status = unsatisfied(List.of(), List.of("an unspecified option"));
            }
            notes.addAll(status.getManualNotes());
            if (status.isSatisfied()) {
                met++;
            } else {
                possibilities.addAll(status.getRemainingDescriptions());
            }
        }
        if (met >= required) {
            return new RequirementStatus(true, List.of(), List.of(), notes, List.of());
        }
        return new RequirementStatus(false, List.of(),
                List.of("Need " + (required - met) + " more option(s) for " + label(node, "this requirement")
                        + ": " + String.join("; ", possibilities)), notes, List.of());
    }

    private static RequirementStatus checkRange(CoreRequirementProgram.RequirementNode node,
                                                Collection<CompletedCourse> completed) {
        Matcher rangeMatcher = Pattern.compile("([A-Za-z|]+)\\s+(\\d+)-(\\d+)")
                .matcher(targetSegment(node.getValue()));
        if (!rangeMatcher.matches()) {
            throw new IllegalArgumentException("Malformed core RANGE: " + node.getValue());
        }
        int required = optionalInt(node.getValue(), COUNT, 1);
        int start = Integer.parseInt(rangeMatcher.group(2));
        int end = Integer.parseInt(rangeMatcher.group(3));
        Set<String> departments = canonicalDepartments(rangeMatcher.group(1).split("\\|"));
        int matches = 0;
        for (CompletedCourse course : completed) {
            Matcher code = COURSE_CODE.matcher(course.getCourseCode());
            if (code.matches() && departments.contains(canonicalDepartment(code.group(1)))) {
                int number = Integer.parseInt(code.group(2));
                if (number >= start && number <= end) {
                    matches++;
                }
            }
        }
        return countStatus(matches, required, label(node, rangeMatcher.group(1) + " " + start + "-" + end));
    }

    private static RequirementStatus checkTrackChoice(CoreRequirementProgram.RequirementNode node,
                                                      Collection<CompletedCourse> completed,
                                                      String jewishStudiesTrack) {
        if (jewishStudiesTrack == null || jewishStudiesTrack.isBlank()
                || jewishStudiesTrack.equalsIgnoreCase("undeclared")) {
            return new RequirementStatus(true, List.of(), List.of(),
                    List.of(label(node, "Jewish Studies Series")
                            + " is not checked because no Jewish-studies track is saved."), List.of());
        }
        for (CoreRequirementProgram.RequirementNode track : node.getChildren()) {
            if (matchesTrack(track.getValue(), jewishStudiesTrack)) {
                RequirementStatus status = checkNodes(track.getChildren(), completed, jewishStudiesTrack);
                if (status.isSatisfied()) {
                    return status;
                }
                List<String> descriptions = new ArrayList<>();
                for (String description : status.getRemainingDescriptions()) {
                    descriptions.add("[" + track.getValue() + "] " + description);
                }
                return new RequirementStatus(false, status.getRemainingCourseCodes(), descriptions,
                        status.getManualNotes(), status.getChildren());
            }
        }
        return new RequirementStatus(true, List.of(), List.of(),
                List.of("Saved Jewish-studies track " + jewishStudiesTrack
                        + " does not apply to " + label(node, "this core profile") + "."), List.of());
    }

    private static boolean matchesTrack(String requirementTrack, String savedTrack) {
        for (String option : requirementTrack.split("/")) {
            if (option.trim().equalsIgnoreCase(savedTrack.trim())) {
                return true;
            }
        }
        return false;
    }

    private static RequirementStatus checkAttributeCount(CoreRequirementProgram.RequirementNode node,
                                                         Collection<CompletedCourse> completed) {
        String attribute = canonicalAttribute(targetSegment(node.getValue()));
        int required = parseInt(node.getValue(), COUNT, "ATTRIBUTE_COUNT");
        int matches = 0;
        for (CompletedCourse course : completed) {
            for (String courseAttribute : course.getAttributes()) {
                if (canonicalAttribute(courseAttribute).equals(attribute)) {
                    matches++;
                    break;
                }
            }
        }
        return countStatus(matches, required, attribute + " attribute course(s)");
    }

    private static RequirementStatus checkCourseCount(CoreRequirementProgram.RequirementNode node,
                                                      Collection<CompletedCourse> completed) {
        return countStatus(completed.size(), Integer.parseInt(targetSegment(node.getValue())),
                label(node, "course(s)"));
    }

    private static RequirementStatus checkDepartmentCount(CoreRequirementProgram.RequirementNode node,
                                                          Collection<CompletedCourse> completed) {
        Set<String> departments = canonicalDepartments(targetSegment(node.getValue()).split("\\|"));
        int matches = 0;
        for (CompletedCourse course : completed) {
            if (course.getDepartment() != null && departments.contains(canonicalDepartment(course.getDepartment()))) {
                matches++;
            }
        }
        return countStatus(matches, parseInt(node.getValue(), COUNT, "COURSE_COUNT_FROM_DEPARTMENTS"),
                targetSegment(node.getValue()) + " course(s)");
    }

    private static RequirementStatus checkDistinctSubjects(CoreRequirementProgram.RequirementNode node,
                                                           Collection<CompletedCourse> completed) {
        int required = parseInt(node.getValue(), COUNT, "DISTINCT_SUBJECTS");
        int matches = distinctSubjectCount(node, completed);
        return countStatus(matches, required, label(node, "distinct subject area(s)"));
    }

    private static RequirementStatus checkDistribution(CoreRequirementProgram.RequirementNode node,
                                                       Collection<CompletedCourse> completed) {
        int requiredCourses = parseInt(node.getValue(), COUNT, "DISTRIBUTION");
        int minSubjects = parseInt(node.getValue(), MIN_SUBJECTS, "DISTRIBUTION");
        Set<String> allowed = allowedSubjects(node);
        int courseMatches = 0;
        Set<String> subjectMatches = new HashSet<>();
        for (CompletedCourse course : completed) {
            String department = course.getDepartment() == null ? "" : canonicalDepartment(course.getDepartment());
            if (allowed.contains(department)) {
                courseMatches++;
                subjectMatches.add(department);
            }
        }
        boolean satisfied = courseMatches >= requiredCourses && subjectMatches.size() >= minSubjects;
        if (satisfied) {
            return satisfied();
        }
        return unsatisfied(List.of(), List.of(label(node, "Distribution") + " needs "
                + Math.max(0, requiredCourses - courseMatches) + " more course(s) and "
                + Math.max(0, minSubjects - subjectMatches.size()) + " more subject area(s)"));
    }

    private static RequirementStatus checkCreditCount(CoreRequirementProgram.RequirementNode node,
                                                      Collection<CompletedCourse> completed) {
        Set<String> departments = canonicalDepartments(targetSegment(node.getValue()).split("\\|"));
        double required = parseDouble(node.getValue(), CREDITS, "CREDIT_COUNT");
        double credits = 0;
        for (CompletedCourse course : completed) {
            if (course.getCredits() != null && course.getDepartment() != null
                    && departments.contains(canonicalDepartment(course.getDepartment()))) {
                credits += course.getCredits();
            }
        }
        if (credits >= required) {
            return satisfied();
        }
        return unsatisfied(List.of(), List.of(label(node, "Credits") + " needs "
                + formatNumber(required - credits) + " more credit(s)"));
    }

    private static int distinctSubjectCount(CoreRequirementProgram.RequirementNode node,
                                            Collection<CompletedCourse> completed) {
        Set<String> allowed = allowedSubjects(node);
        Set<String> matched = new HashSet<>();
        for (CompletedCourse course : completed) {
            if (course.getDepartment() != null && allowed.contains(canonicalDepartment(course.getDepartment()))) {
                matched.add(canonicalDepartment(course.getDepartment()));
            }
        }
        return matched.size();
    }

    private static Set<String> allowedSubjects(CoreRequirementProgram.RequirementNode node) {
        Set<String> allowed = new HashSet<>();
        for (CoreRequirementProgram.RequirementNode subject : node.getChildren()) {
            if (subject.getType() == CoreRequirementProgram.NodeType.SUBJECT) {
                allowed.addAll(subjectToDepartments(subject.getValue()));
            }
        }
        return allowed;
    }

    private static RequirementStatus manualStatus(String text, boolean manualIsChoice) {
        if (manualIsChoice) {
            return unsatisfied(List.of(), List.of("manual verification required: " + text));
        }
        return new RequirementStatus(true, List.of(), List.of(), List.of(text), List.of());
    }

    private static boolean hasCourse(Collection<CompletedCourse> completed, String code) {
        for (CompletedCourse course : completed) {
            if (course.getCourseCode().equalsIgnoreCase(code)) {
                return true;
            }
        }
        return false;
    }

    private static RequirementStatus statusForCourses(List<String> remaining, String label) {
        return remaining.isEmpty() ? satisfied()
                : unsatisfied(remaining, List.of(label + " still needs: " + String.join(", ", remaining)));
    }

    private static RequirementStatus countStatus(int actual, int required, String label) {
        return actual >= required ? satisfied()
                : unsatisfied(List.of(), List.of(label + " needs " + (required - actual) + " more course(s)"));
    }

    private static RequirementStatus satisfied() {
        return new RequirementStatus(true, List.of(), List.of(), List.of(), List.of());
    }

    private static RequirementStatus unsatisfied(List<String> codes, List<String> descriptions) {
        return new RequirementStatus(false, codes, descriptions, List.of(), List.of());
    }

    /** Returns the requirement target before metadata such as "| count: 2",
     * while preserving pipes that are part of a department OR-list. */
    private static String targetSegment(String value) {
        Matcher metadata = Pattern.compile("\\s+\\|\\s+(?=(?:count|credits|minSubjects|label):)").matcher(value);
        return (metadata.find() ? value.substring(0, metadata.start()) : value).trim();
    }

    private static String label(CoreRequirementProgram.RequirementNode node, String fallback) {
        return node.getValue().contains("label:")
                ? node.getValue().substring(node.getValue().indexOf("label:") + "label:".length()).trim()
                : fallback;
    }

    private static int parseInt(String value, Pattern pattern, String type) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            throw new IllegalArgumentException(type + " is missing a required number: " + value);
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static int optionalInt(String value, Pattern pattern, int defaultValue) {
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : defaultValue;
    }

    private static double parseDouble(String value, Pattern pattern, String type) {
        Matcher matcher = pattern.matcher(value);
        if (!matcher.find()) {
            throw new IllegalArgumentException(type + " is missing a required credit total: " + value);
        }
        return Double.parseDouble(matcher.group(1));
    }

    private static String canonicalAttribute(String attribute) {
        String normalized = attribute.trim().toUpperCase(Locale.ROOT);
        return ATTRIBUTE_ALIASES.getOrDefault(normalized, normalized);
    }

    private static Set<String> subjectToDepartments(String subject) {
        String normalized = subject.trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "ECONOMICS" -> Set.of("ECO");
            case "HISTORY" -> Set.of("HIS");
            case "PHILOSOPHY" -> Set.of("PHI");
            case "POLITICAL SCIENCE" -> Set.of("POL");
            case "PSYCHOLOGY" -> Set.of("PSY");
            case "SOCIOLOGY" -> Set.of("SOC");
            case "ART HISTORY" -> Set.of("ART");
            case "ENGLISH" -> Set.of("ENG");
            case "MUSIC" -> Set.of("MUS");
            case "FOREIGN LANGUAGE" -> Set.of("HEB", "SPA", "FRE", "GER", "YID");
            default -> Set.of(canonicalDepartment(subject));
        };
    }

    private static Set<String> canonicalDepartments(String[] departments) {
        Set<String> result = new HashSet<>();
        for (String department : departments) {
            result.add(canonicalDepartment(department));
        }
        return result;
    }

    private static String canonicalDepartment(String department) {
        String normalized = department.trim().toUpperCase(Locale.ROOT);
        return DEPARTMENT_ALIASES.getOrDefault(normalized, normalized);
    }

    private static Map<String, String> departmentAliases() {
        Map<String, String> aliases = new HashMap<>();
        addAliases(aliases, "ART", "ART", "ARTS");
        addAliases(aliases, "MUS", "MUS", "MUSI");
        addAliases(aliases, "ENG", "ENG", "ENGL");
        addAliases(aliases, "HIS", "HIS", "HIST");
        addAliases(aliases, "PHI", "PHI", "PHIL");
        addAliases(aliases, "JHI", "JHI", "JHIS");
        addAliases(aliases, "JPH", "JPH", "JPHI");
        addAliases(aliases, "ECO", "ECO", "ECON");
        addAliases(aliases, "BIO", "BIO", "BIOL");
        addAliases(aliases, "CHE", "CHE", "CHEM");
        addAliases(aliases, "PHY", "PHY", "PHYS");
        addAliases(aliases, "POL", "POL", "POLI");
        addAliases(aliases, "PSY", "PSY", "PSYC");
        addAliases(aliases, "SCI", "SCI", "SCIE");
        addAliases(aliases, "SOC", "SOC", "SOCI");
        addAliases(aliases, "BIB", "BIB", "BIBL");
        return Collections.unmodifiableMap(aliases);
    }

    private static void addAliases(Map<String, String> aliases, String canonical, String... values) {
        for (String value : values) {
            aliases.put(value, canonical);
        }
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : Double.toString(value);
    }
}
