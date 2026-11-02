package edu.yu.courseplanner.model;

import java.util.List;

/** One fully validated proposed semester schedule. */
public record SchedulePlan(List<Course> courses, double credits, int majorCourses,
                           int minorCourses, int coreCourses) {
  public SchedulePlan {
    courses = List.copyOf(courses);
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
