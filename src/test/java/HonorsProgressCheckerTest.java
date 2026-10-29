import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HonorsProgressCheckerTest {

    @Test
    void honorsStudentProgressCountsOnlyCoursesWithTheHonorsAttribute() {
        User user = new User("Ari", true, "Wilf", "YC", Set.of());
        user.addCompletedCourse(new CompletedCourse("COMP1000", 3.0, "COMP", Set.of("HONR")));
        user.addCompletedCourse(new CompletedCourse("MATH1000", 3.0, "MATH", Set.of("HONORS")));
        user.addCompletedCourse(new CompletedCourse("ENG1000", 3.0, "ENG", Set.of("WRIN")));

        RequirementStatus status = HonorsProgressChecker.check(user);

        assertFalse(status.isSatisfied());
        assertTrue(status.getRemainingDescriptions().getFirst().contains("2 of 5"));
        assertTrue(status.getRemainingDescriptions().getFirst().contains("needs 3 more"));
    }

    @Test
    void honorsRequirementIsSatisfiedAfterFiveHonorsCourses() {
        List<CompletedCourse> completed = List.of(
                new CompletedCourse("A1000", 3.0, "A", Set.of("HONR")),
                new CompletedCourse("B1000", 3.0, "B", Set.of("HONR")),
                new CompletedCourse("C1000", 3.0, "C", Set.of("HONR")),
                new CompletedCourse("D1000", 3.0, "D", Set.of("HONR")),
                new CompletedCourse("E1000", 3.0, "E", Set.of("HONR")));

        assertTrue(HonorsProgressChecker.checkCompletedCourses(completed).isSatisfied());
    }
}
