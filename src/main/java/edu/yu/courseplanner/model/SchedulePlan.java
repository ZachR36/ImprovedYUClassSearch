package edu.yu.courseplanner.model;

import java.util.List;
import java.util.Map;

/** One fully validated proposed semester schedule. */
public record SchedulePlan(List<Course> courses, double credits, int majorCourses,
                           int minorCourses, int coreCourses,
                           Map<Integer, List<String>> courseReasons) {
  public SchedulePlan {
    courses = List.copyOf(courses);
    courseReasons = Map.copyOf(courseReasons);
  }

  /** Requirement-fulfilling course count used as the primary schedule rank. */
  public int fulfilledRequirementCourses() {
    return majorCourses + minorCourses + coreCourses;
  }

  /** Secondary rank: degree-program courses outrank core-only courses. */
  public int degreeProgramCourses() {
    return majorCourses + minorCourses;
  }
}
