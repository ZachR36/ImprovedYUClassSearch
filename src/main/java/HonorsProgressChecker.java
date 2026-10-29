import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Checks the university-wide five-course requirement for honors students. */
public final class HonorsProgressChecker {
    static final int REQUIRED_HONORS_COURSES = 5;

    private HonorsProgressChecker() {
    }

    /** Returns an honors-progress status for an honors student. */
    public static RequirementStatus check(User user) {
        if (!user.isHonors()) {
            return new RequirementStatus(true, List.of(), List.of(), List.of(), List.of());
        }
        return checkCompletedCourses(user.getCompletedCourseRecords());
    }

    static RequirementStatus checkCompletedCourses(Collection<CompletedCourse> completedCourses) {
        int completedHonorsCourses = 0;
        for (CompletedCourse course : completedCourses) {
            if (hasHonorsAttribute(course)) {
                completedHonorsCourses++;
            }
        }
        if (completedHonorsCourses >= REQUIRED_HONORS_COURSES) {
            return new RequirementStatus(true, List.of(), List.of(), List.of(), List.of());
        }
        int remaining = REQUIRED_HONORS_COURSES - completedHonorsCourses;
        List<String> descriptions = new ArrayList<>();
        descriptions.add("Honors requirement: " + completedHonorsCourses + " of "
                + REQUIRED_HONORS_COURSES + " honors courses completed; needs " + remaining + " more.");
        return new RequirementStatus(false, List.of(), descriptions, List.of(), List.of());
    }

    private static boolean hasHonorsAttribute(CompletedCourse course) {
        for (String attribute : course.getAttributes()) {
            if (attribute.equalsIgnoreCase("HONR") || attribute.equalsIgnoreCase("HONORS")) {
                return true;
            }
        }
        return false;
    }
}
