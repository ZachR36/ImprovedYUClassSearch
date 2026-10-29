import java.util.List;

/** One explained, schedulable course suggestion. */
public record Recommendation(Course course, List<String> reasons, List<String> cautions) { }
