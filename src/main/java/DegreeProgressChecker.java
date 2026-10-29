import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
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
 * Takes the student's completed-course records as a plain parameter rather than
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

    /** One independent major/minor audit plus the individual course records it
     * assigned to that program.  Callers can union assignments across all
     * degree programs before evaluating non-double-countable core work. */
    public record AllocationResult(RequirementStatus status, List<CompletedCourse> usedCourses) { }

    /** Checks an entire program: all top-level requirements combine as AND. */
    public static RequirementStatus check(DegreeProgram program, Set<String> completedCourses) {
        return check(program, recordsForCodes(completedCourses), null);
    }

    /** Checks a program against its explicitly selected track, if it has one. */
    public static RequirementStatus check(DegreeProgram program, Set<String> completedCourses,
                                          String selectedTrack) {
        return check(program, recordsForCodes(completedCourses), selectedTrack);
    }

    /** Uses full records so repeatable courses can count separately in ranges
     * and other count-based requirements. */
    public static RequirementStatus check(DegreeProgram program, Collection<CompletedCourse> completedCourses,
                                          String selectedTrack) {
        Set<String> completedCodes = completedCodes(completedCourses);
        List<RequirementStatus> childStatuses = new ArrayList<>();
        for (DegreeProgram.RequirementNode node : program.getRequirements()) {
            childStatuses.add(checkNode(node, completedCodes, completedCourses, selectedTrack));
        }
        return RequirementStatus.combineAnd(childStatuses);
    }

    /** Performs the normal degree audit and also identifies records used by
     * it. Degree programs are deliberately evaluated independently: a course
     * may count toward a major and a minor (or two majors), but any record
     * claimed by at least one program is unavailable to core requirements. */
    public static AllocationResult allocate(DegreeProgram program, Collection<CompletedCourse> completedCourses,
                                            String selectedTrack) {
        List<CompletedCourse> available = new ArrayList<>(completedCourses);
        List<CompletedCourse> used = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        List<DegreeProgram.RequirementNode> nodes = new ArrayList<>(program.getRequirements());
        nodes.sort((left, right) -> Integer.compare(allocationPriority(left), allocationPriority(right)));
        List<RequirementStatus> statuses = new ArrayList<>();
        for (DegreeProgram.RequirementNode node : nodes) {
            statuses.add(checkNode(node, completedCodes(available), available, selectedTrack));
            allocateNode(node, available, used, notes, selectedTrack);
        }
        RequirementStatus raw = RequirementStatus.combineAnd(statuses);
        RequirementStatus status = new RequirementStatus(raw.isSatisfied(), raw.getRemainingCourseCodes(),
                raw.getRemainingDescriptions(), raw.getManualNotes(), notes, raw.getChildren());
        return new AllocationResult(status, List.copyOf(used));
    }

    private static int allocationPriority(DegreeProgram.RequirementNode node) {
        if (node instanceof DegreeProgram.CourseListRequirement) return 0;
        if (node instanceof DegreeProgram.ChooseNRequirement || node instanceof DegreeProgram.TrackChoiceRequirement) return 1;
        if (node instanceof DegreeProgram.RangeRequirement) return 2;
        return 3;
    }

    private static void allocateNode(DegreeProgram.RequirementNode node, List<CompletedCourse> available,
                                     List<CompletedCourse> used, List<String> notes, String selectedTrack) {
        if (node instanceof DegreeProgram.CourseListRequirement list) {
            for (String code : list.getCourseCodes()) takeCode(available, used, code);
        } else if (node instanceof DegreeProgram.RangeRequirement range) {
            int count = range.getCount() == null ? 1 : range.getCount();
            takeRange(available, used, range, count);
        } else if (node instanceof DegreeProgram.ChooseNRequirement choose) {
            allocateChoose(choose, available, used, notes, selectedTrack);
        } else if (node instanceof DegreeProgram.TrackChoiceRequirement choice) {
            if (selectedTrack != null) {
                for (DegreeProgram.Track track : choice.getTracks()) {
                    if (track.getName().equalsIgnoreCase(selectedTrack.trim())) {
                        List<DegreeProgram.RequirementNode> nodes = new ArrayList<>(track.getRequirements());
                        nodes.sort((left, right) -> Integer.compare(allocationPriority(left), allocationPriority(right)));
                        for (DegreeProgram.RequirementNode child : nodes) {
                            allocateNode(child, available, used, notes, selectedTrack);
                        }
                        return;
                    }
                }
            }
        }
    }

    private static void allocateChoose(DegreeProgram.ChooseNRequirement choose, List<CompletedCourse> available,
                                       List<CompletedCourse> used, List<String> notes, String selectedTrack) {
        List<DegreeProgram.Option> valid = new ArrayList<>();
        for (DegreeProgram.Option option : choose.getOptions()) {
            if (optionSatisfied(option, available, selectedTrack)) valid.add(option);
        }
        valid.sort((left, right) -> Integer.compare(optionFlexibility(left, available), optionFlexibility(right, available)));
        List<String> selected = new ArrayList<>();
        for (DegreeProgram.Option option : valid) {
            if (selected.size() == choose.getN()) break;
            if (optionSatisfied(option, available, selectedTrack)) {
                if (option.isSimple()) takeCode(available, used, option.getCourseCode());
                else allocateNode(option.getNestedContent(), available, used, notes, selectedTrack);
                selected.add(option.isSimple() ? option.getCourseCode() : "a grouped option");
            }
        }
        if (valid.size() > choose.getN()) {
            notes.add("A degree-program choice had " + valid.size() + " valid options; used "
                    + String.join(", ", selected) + " because it was the most constrained available allocation.");
        }
    }

    private static boolean optionSatisfied(DegreeProgram.Option option, Collection<CompletedCourse> available,
                                           String selectedTrack) {
        if (option.isSimple()) return completedCodes(available).contains(option.getCourseCode());
        return checkNode(option.getNestedContent(), completedCodes(available), available, selectedTrack).isSatisfied();
    }

    private static int optionFlexibility(DegreeProgram.Option option, Collection<CompletedCourse> available) {
        if (option.isSimple()) return 1;
        if (option.getNestedContent() instanceof DegreeProgram.RangeRequirement range) {
            int matches = 0;
            for (CompletedCourse course : available) if (matchesRange(course.getCourseCode(), range)) matches++;
            return matches;
        }
        return 2;
    }

    private static void takeCode(List<CompletedCourse> available, List<CompletedCourse> used, String code) {
        for (CompletedCourse course : available) {
            if (course.getCourseCode().equalsIgnoreCase(code)) {
                available.remove(course);
                used.add(course);
                return;
            }
        }
    }

    private static void takeRange(List<CompletedCourse> available, List<CompletedCourse> used,
                                  DegreeProgram.RangeRequirement range, int count) {
        for (CompletedCourse course : new ArrayList<>(available)) {
            if (matchesRange(course.getCourseCode(), range)) {
                available.remove(course);
                used.add(course);
                if (--count == 0) return;
            }
        }
    }

    private static RequirementStatus checkNode(DegreeProgram.RequirementNode node, Set<String> completedCodes,
                                               Collection<CompletedCourse> completedRecords,
                                               String selectedTrack) {
        if (node instanceof DegreeProgram.CourseListRequirement courseList) {
            return checkCourseList(courseList, completedCodes);
        }
        if (node instanceof DegreeProgram.ChooseNRequirement chooseN) {
            return checkChooseN(chooseN, completedCodes, completedRecords, selectedTrack);
        }
        if (node instanceof DegreeProgram.RangeRequirement range) {
            return checkRange(range, completedRecords);
        }
        if (node instanceof DegreeProgram.TrackChoiceRequirement trackChoice) {
            return checkTrackChoice(trackChoice, completedCodes, completedRecords, selectedTrack);
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

    private static RequirementStatus checkChooseN(DegreeProgram.ChooseNRequirement chooseN, Set<String> completedCodes,
                                                  Collection<CompletedCourse> completedRecords,
                                                  String selectedTrack) {
        int satisfiedCount = 0;
        List<String> remainingCourseCodes = new ArrayList<>();
        List<String> unsatisfiedOptionDescriptions = new ArrayList<>();
        List<String> manualNotes = new ArrayList<>();
        for (DegreeProgram.Option option : chooseN.getOptions()) {
            if (option.isSimple()) {
                if (completedCodes.contains(option.getCourseCode())) {
                    satisfiedCount++;
                } else {
                    remainingCourseCodes.add(option.getCourseCode());
                    unsatisfiedOptionDescriptions.add(option.getCourseCode());
                }
            } else {
                RequirementStatus nestedStatus = checkNode(option.getNestedContent(), completedCodes,
                        completedRecords, selectedTrack);
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

    private static RequirementStatus checkRange(DegreeProgram.RangeRequirement range,
                                                Collection<CompletedCourse> completed) {
        int matches = 0;
        for (CompletedCourse course : completed) {
            if (matchesRange(course.getCourseCode(), range)) {
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
                                                         Set<String> completedCodes,
                                                         Collection<CompletedCourse> completedRecords,
                                                         String selectedTrack) {
        if (selectedTrack == null || selectedTrack.isBlank()) {
            return new RequirementStatus(true, List.of(), List.of(),
                    List.of(describeLabel(trackChoice.getLabel())
                            + "is not checked because no track is saved for this program."), List.of());
        }
        for (DegreeProgram.Track track : trackChoice.getTracks()) {
            if (track.getName().equalsIgnoreCase(selectedTrack.trim())) {
                return checkTrack(track, completedCodes, completedRecords, selectedTrack);
            }
        }
        return new RequirementStatus(true, List.of(), List.of(),
                List.of("Saved track \"" + selectedTrack + "\" does not match a track in "
                        + describeLabel(trackChoice.getLabel()) + "."), List.of());
    }

    private static RequirementStatus checkTrack(DegreeProgram.Track track, Set<String> completedCodes,
                                                Collection<CompletedCourse> completedRecords,
                                                String selectedTrack) {
        List<RequirementStatus> childStatuses = new ArrayList<>();
        for (DegreeProgram.RequirementNode node : track.getRequirements()) {
            childStatuses.add(checkNode(node, completedCodes, completedRecords, selectedTrack));
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

    private static Set<String> completedCodes(Collection<CompletedCourse> completedCourses) {
        Set<String> codes = new HashSet<>();
        for (CompletedCourse course : completedCourses) {
            codes.add(course.getCourseCode());
        }
        return codes;
    }

    private static List<CompletedCourse> recordsForCodes(Set<String> completedCourseCodes) {
        List<CompletedCourse> records = new ArrayList<>();
        for (String code : completedCourseCodes) {
            records.add(new CompletedCourse(code, null, null, Set.of()));
        }
        return records;
    }
}
