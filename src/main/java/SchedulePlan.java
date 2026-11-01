import java.util.List;

/** One complete, non-mutating proposed schedule. */
public record SchedulePlan(List<Course> fixedCourses, List<Recommendation> selections,
                           double credits, int score, List<String> explanations) {
  /** Kept for simple callers and tests that do not need planning context. */
  public SchedulePlan(List<Course> fixedCourses, List<Recommendation> selections,
                      double credits, int score) {
    this(fixedCourses, selections, credits, score, List.of());
  }
}
