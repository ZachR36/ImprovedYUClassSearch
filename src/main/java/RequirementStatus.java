import java.util.ArrayList;
import java.util.List;

/**
 * Result of checking one RequirementNode (or a whole DegreeProgram) against a
 * student's completed courses. Carries enough detail to both drive a later
 * recommendation engine (remainingCourseCodes - concrete courses that would
 * help) and show a human a readable breakdown (remainingDescriptions).
 *
 * ManualNoteRequirement never blocks: its status is always satisfied=true,
 * with its text surfaced only in manualNotes, never counted as unsatisfied.
 */
public class RequirementStatus {
    private final boolean satisfied;
    private final List<String> remainingCourseCodes;
    private final List<String> remainingDescriptions;
    private final List<String> manualNotes;
    private final List<RequirementStatus> children;

    public RequirementStatus(boolean satisfied, List<String> remainingCourseCodes,
                              List<String> remainingDescriptions, List<String> manualNotes,
                              List<RequirementStatus> children) {
        this.satisfied = satisfied;
        this.remainingCourseCodes = remainingCourseCodes;
        this.remainingDescriptions = remainingDescriptions;
        this.manualNotes = manualNotes;
        this.children = children;
    }

    public boolean isSatisfied() { return satisfied; }
    public List<String> getRemainingCourseCodes() { return remainingCourseCodes; }
    public List<String> getRemainingDescriptions() { return remainingDescriptions; }
    public List<String> getManualNotes() { return manualNotes; }
    public List<RequirementStatus> getChildren() { return children; }

    /** Combines several child statuses as an AND: satisfied iff all are,
     *  with remaining/manual-note lists concatenated. Used for a program's
     *  top-level requirement list and for a Track's requirement list. */
    static RequirementStatus combineAnd(List<RequirementStatus> childStatuses) {
        boolean satisfied = true;
        List<String> remainingCourseCodes = new ArrayList<>();
        List<String> remainingDescriptions = new ArrayList<>();
        List<String> manualNotes = new ArrayList<>();
        for (RequirementStatus child : childStatuses) {
            if (!child.isSatisfied()) {
                satisfied = false;
            }
            remainingCourseCodes.addAll(child.getRemainingCourseCodes());
            remainingDescriptions.addAll(child.getRemainingDescriptions());
            manualNotes.addAll(child.getManualNotes());
        }
        return new RequirementStatus(satisfied, remainingCourseCodes, remainingDescriptions, manualNotes,
                childStatuses);
    }

    /** Rough measure of how much work is left, used to pick the
     *  closest-to-complete track in a TrackChoiceRequirement. */
    int remainingWeight() {
        return remainingCourseCodes.size() + remainingDescriptions.size();
    }
}
