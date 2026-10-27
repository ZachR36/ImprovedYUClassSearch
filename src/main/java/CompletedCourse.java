import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Immutable record of a course a student has already completed.  Unlike a
 * CRN, its course code remains meaningful across semesters; the other fields
 * preserve the information core-requirement checking needs for credits,
 * department buckets, and attribute requirements.
 */
public final class CompletedCourse {
    private final String courseCode;
    private final Double credits; // null when an older profile did not record it
    private final String department; // null when it cannot be inferred
    private final Set<String> attributes;

    public CompletedCourse(String courseCode, Double credits, String department, Set<String> attributes) {
        if (courseCode == null || courseCode.isBlank()) {
            throw new IllegalArgumentException("A completed course needs a course code.");
        }
        this.courseCode = normalize(courseCode);
        this.credits = credits;
        this.department = department == null || department.isBlank() ? null : normalize(department);
        Set<String> normalizedAttributes = new LinkedHashSet<>();
        if (attributes != null) {
            for (String attribute : attributes) {
                if (attribute != null && !attribute.isBlank()) {
                    normalizedAttributes.add(normalize(attribute));
                }
            }
        }
        this.attributes = Set.copyOf(normalizedAttributes);
    }

    /** Captures the checkable information from a currently loaded course. */
    public CompletedCourse(Course course) {
        this(course.getCourseCode(), course.getCredits(), course.getDepartment(),
                new LinkedHashSet<>(Arrays.asList(course.getAttributes())));
    }

    public String getCourseCode() { return courseCode; }
    public Double getCredits() { return credits; }
    public String getDepartment() { return department; }
    public Set<String> getAttributes() { return attributes; }

    private static String normalize(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }
}
