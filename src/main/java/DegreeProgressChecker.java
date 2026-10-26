import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks a student's completed courses against a DegreeProgram's requirement
 * tree. Kept separate from DegreeProgram (which only loads/represents the
 * data) and from SearchEngine (which only handles class search) - this is
 * the evaluation layer the future recommendation engine will build on:
 * recommendations need to know what's left in a program, and this is where
 * that question gets answered.
 *
 * Takes the student's completed-course set as a plain parameter rather than
 * requiring a live reference between DegreeProgram and User, so lookups
 * always go fresh through the catalog (see DegreeProgram.findForStudent) and
 * nothing goes stale if the catalog is reloaded.
 *
 * KNOWN LIMITATION (V1): no cross-slot course reuse optimization. If one
 * completed course could independently satisfy two different requirement
 * slots (e.g. an unused CHOOSE_N alternative that also appears in a RANGE
 * elsewhere), this version counts it toward both independently rather than
 * solving the optimal assignment problem. This can slightly OVERstate
 * progress in rare cases, but should never UNDERstate it. A future version
 * could track which completed courses have already been "spent" on a
 * satisfied slot.
 */
public class DegreeProgressChecker {

    // Splits a canonical course code like "COM1234" into department letters
    // and course number, matching Course.getCourseCode()'s "DEPT" + number
    // format with no separator.
    private static final Pattern COURSE_CODE = Pattern.compile("^([A-Za-z]+)(\\d+)$");

    /** Checks an entire program: all top-level requirements combine as AND. */
    public static RequirementStatus check(DegreeProgram program, Set<String> completedCourses) {
        List<RequirementStatus> childStatuses = new ArrayList<>();
        for (DegreeProgram.RequirementNode node : program.getRequirements()) {
            childStatuses.add(checkNode(node, completedCourses));
        }
        return RequirementStatus.combineAnd(childStatuses);
    }

    private static RequirementStatus checkNode(DegreeProgram.RequirementNode node, Set<String> completed) {
        if (node instanceof DegreeProgram.CourseListRequirement courseList) {
            return checkCourseList(courseList, completed);
        }
        if (node instanceof DegreeProgram.ChooseNRequirement chooseN) {
            return checkChooseN(chooseN, completed);
        }
        if (node instanceof DegreeProgram.RangeRequirement range) {
            return checkRange(range, completed);
        }
        if (node instanceof DegreeProgram.TrackChoiceRequirement trackChoice) {
            return checkTrackChoice(trackChoice, completed);
        }
        if (node instanceof DegreeProgram.ManualNoteRequirement manualNote) {
            return new RequirementStatus(true, List.of(), List.of(), List.of(manualNote.getText()), List.of());
        }
        throw new IllegalStateException("Unknown RequirementNode type: " + node.getClass());
    }

    private static RequirementStatus checkCourseList(DegreeProgram.CourseListRequirement courseList,
                                                       Set<String> completed) {
        List<String> remaining = new ArrayList<>();
        for (String code : courseList.getCourseCodes()) {
            if (!completed.contains(code)) {
                remaining.add(code);
            }
        }
        boolean satisfied = remaining.isEmpty();
        List<String> descriptions = satisfied ? List.of()
                : List.of(describeLabel(courseList.getLabel()) + "still needs: " + String.join(", ", remaining));
        return new RequirementStatus(satisfied, remaining, descriptions, List.of(), List.of());
    }

    private static RequirementStatus checkChooseN(DegreeProgram.ChooseNRequirement chooseN, Set<String> completed) {
        int satisfiedCount = 0;
        List<String> remainingCourseCodes = new ArrayList<>();
        List<String> unsatisfiedOptionDescriptions = new ArrayList<>();
        List<String> manualNotes = new ArrayList<>();
        for (DegreeProgram.Option option : chooseN.getOptions()) {
            if (option.isSimple()) {
                if (completed.contains(option.getCourseCode())) {
                    satisfiedCount++;
                } else {
                    remainingCourseCodes.add(option.getCourseCode());
                    unsatisfiedOptionDescriptions.add(option.getCourseCode());
                }
            } else {
                RequirementStatus nestedStatus = checkNode(option.getNestedContent(), completed);
                manualNotes.addAll(nestedStatus.getManualNotes());
                if (nestedStatus.isSatisfied()) {
                    satisfiedCount++;
                } else {
                    remainingCourseCodes.addAll(nestedStatus.getRemainingCourseCodes());
                    unsatisfiedOptionDescriptions.addAll(nestedStatus.getRemainingDescriptions());
                }
            }
        }
        boolean satisfied = satisfiedCount >= chooseN.getN();
        List<String> descriptions = satisfied ? List.of()
                : List.of("Need " + (chooseN.getN() - satisfiedCount) + " more of: "
                        + String.join(", ", unsatisfiedOptionDescriptions));
        return new RequirementStatus(satisfied, satisfied ? List.of() : remainingCourseCodes, descriptions,
                manualNotes, List.of());
    }

    private static RequirementStatus checkRange(DegreeProgram.RangeRequirement range, Set<String> completed) {
        int matches = 0;
        for (String code : completed) {
            if (matchesRange(code, range)) {
                matches++;
            }
        }
        // A null count means this RANGE is nested inside a ChooseN Option - the
        // enclosing ChooseN's count governs, so this option just needs one match.
        int required = range.getCount() != null ? range.getCount() : 1;
        boolean satisfied = matches >= required;
        String label = String.join("|", range.getDepartments()) + " " + range.getRangeStart() + "-"
                + range.getRangeEnd();
        List<String> descriptions = satisfied ? List.of()
                : List.of("Need " + (required - matches) + " more course(s) in " + label);
        return new RequirementStatus(satisfied, List.of(), descriptions, List.of(), List.of());
    }

    private static boolean matchesRange(String completedCode, DegreeProgram.RangeRequirement range) {
        Matcher m = COURSE_CODE.matcher(completedCode);
        if (!m.matches()) {
            return false;
        }
        String dept = m.group(1);
        int number;
        try {
            number = Integer.parseInt(m.group(2));
        } catch (NumberFormatException e) {
            return false;
        }
        boolean deptMatches = false;
        for (String candidate : range.getDepartments()) {
            if (candidate.equalsIgnoreCase(dept)) {
                deptMatches = true;
                break;
            }
        }
        return deptMatches && number >= range.getRangeStart() && number <= range.getRangeEnd();
    }

    private static RequirementStatus checkTrackChoice(DegreeProgram.TrackChoiceRequirement trackChoice,
                                                         Set<String> completed) {
        RequirementStatus closest = null;
        for (DegreeProgram.Track track : trackChoice.getTracks()) {
            RequirementStatus trackStatus = checkTrack(track, completed);
            if (trackStatus.isSatisfied()) {
                return trackStatus;
            }
            if (closest == null || trackStatus.remainingWeight() < closest.remainingWeight()) {
                closest = trackStatus;
            }
        }
        return closest != null ? closest : new RequirementStatus(true, List.of(), List.of(), List.of(), List.of());
    }

    private static RequirementStatus checkTrack(DegreeProgram.Track track, Set<String> completed) {
        List<RequirementStatus> childStatuses = new ArrayList<>();
        for (DegreeProgram.RequirementNode node : track.getRequirements()) {
            childStatuses.add(checkNode(node, completed));
        }
        RequirementStatus combined = RequirementStatus.combineAnd(childStatuses);
        List<String> descriptions = combined.isSatisfied() ? List.of()
                : prependLabel(track.getName(), combined.getRemainingDescriptions());
        return new RequirementStatus(combined.isSatisfied(), combined.getRemainingCourseCodes(), descriptions,
                combined.getManualNotes(), combined.getChildren());
    }

    private static List<String> prependLabel(String trackName, List<String> descriptions) {
        if (descriptions.isEmpty()) {
            return descriptions;
        }
        List<String> labeled = new ArrayList<>();
        for (String description : descriptions) {
            labeled.add("[" + trackName + "] " + description);
        }
        return Collections.unmodifiableList(labeled);
    }

    private static String describeLabel(String label) {
        return label != null && !label.isBlank() ? label + " " : "";
    }
}
