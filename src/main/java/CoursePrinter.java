import java.util.List;

/** Formats and paginates course lists for the command-line interface. */
public class CoursePrinter {

  /** Prints one program's degree-progress result: satisfied status, concrete
   *  remaining requirements, and any informational (never-blocking) notes. */
  static void degreeProgressPrinting(String programName, RequirementStatus status) {
    System.out.println("\n**Degree Progress: " + programName + "**");
    System.out.println(status.isSatisfied() ? "All checkable requirements satisfied!" : "Not yet complete.");
    if (!status.getRemainingDescriptions().isEmpty()) {
      System.out.println("Remaining requirements:");
      for (String description : status.getRemainingDescriptions()) {
        System.out.println("  - " + description);
      }
    }
    if (!status.getManualNotes().isEmpty()) {
      System.out.println("Notes (not automatically checked):");
      for (String note : status.getManualNotes()) {
        System.out.println("  - " + note);
      }
    }
    if (!status.getAllocationNotes().isEmpty()) {
      System.out.println("Course-allocation notes:");
      for (String note : status.getAllocationNotes()) {
        System.out.println("  - " + note);
      }
    }
  }

  /** Prints a compact, explained list of course suggestions. */
  static void recommendationPrinting(List<Recommendation> recommendations) {
    System.out.println("\n**Recommended next courses**");
    if (recommendations.isEmpty()) {
      System.out.println("No current-semester offerings fit your unmet requirements and bookmarked schedule.");
      return;
    }
    for (int i = 0; i < recommendations.size(); i++) {
      Recommendation recommendation = recommendations.get(i);
      Course course = recommendation.course();
      System.out.println((i + 1) + ". " + course.getCourseCode() + " — " + course.getName()
          + " (CRN " + course.getCRN() + ", " + course.getCampus() + ", " + course.getCredits() + " credits)");
      for (String reason : recommendation.reasons()) System.out.println("   Why: " + reason);
      for (String caution : recommendation.cautions()) System.out.println("   Note: " + caution);
    }
  }
  static final int INCREMENT = 15;

  static void coursePrinting(List<Course> courses, int startingPoint) {
    System.out.printf("%-30s %-10s %-6s %-10s %-10s %-7s %-8s %-20s %-7s %-11s %-15s%n%s%n",
        "Title", "Code", "Dpt.", "Course #", "Section", "Hours", "CRN", "Instructor", "Seats", "Seats Rem.", "Attributes",
        "-".repeat(142));
    for (int i = startingPoint; i < startingPoint + INCREMENT; i++) {
      if (i > courses.size()) {
        break;
      }
      Course course = courses.get(i - 1);
      System.out.printf("%-30.30s %-10s %-6s %-10s %-10s %-7s %-8s %-20.20s %-7s %-11s %-15s%n%s%n",
          course.getName(), course.getCourseCode(), course.getDepartment(), course.getDeptNumber(), course.getSection(), course.getCredits(),
          course.getCRN(), course.getTeacher(), course.getMaxEnrolled(), course.getRemainingEnrollment(),
          String.join(", ", course.getAttributes()), "-".repeat(142));
    }
    int endSpot = lastPrintedCourseIndex(startingPoint, courses.size());
    System.out.println("Classes " + startingPoint + " to " + endSpot + ". Out of " + courses.size());
  }

  /** Prints current-semester offerings plus a custom option for a prior
   * semester, whose attributes may differ from the current catalog. */
  static void courseChoicePrinting(String courseCode, List<Course> courses) {
    System.out.println("Current-semester offerings matching " + courseCode + ". Choose the one you completed,"
        + " or choose the custom option for a prior-semester offering:");
    for (int i = 0; i < courses.size(); i++) {
      Course course = courses.get(i);
      System.out.println("  " + (i + 1) + ") CRN " + course.getCRN() + ", section " + course.getSection()
          + ", " + course.getCampus() + ", " + course.getCredits() + " credits, "
          + course.getTeacher() + ", attributes: " + String.join(", ", course.getAttributes()));
    }
    System.out.println("  " + (courses.size() + 1)
        + ") Custom/prior-semester offering (enter credits, department, and attributes manually)");
    System.out.println("Enter a number, or type exit to cancel: ");
  }

  static boolean hasNextPage(int startingPoint, int courseCount) {
    return startingPoint + INCREMENT <= courseCount;
  }

  static int lastPrintedCourseIndex(int startingPoint, int courseCount) {
    return Math.min(courseCount, startingPoint + INCREMENT - 1);
  }

  /** Prints the complete details and registration requirements for one course. */
  static void courseDetailsPrinting(Course course) {
    System.out.println("\n**Course Details**");
    System.out.println("Name: " + course.getName());
    System.out.println("Course code: " + course.getCourseCode());
    System.out.println("CRN: " + course.getCRN());
    System.out.println("Instructor: " + course.getTeacher());
    System.out.println("Prerequisites: " + formatRequirementGroups(course.getPrerequisites()));
    System.out.println("Corequisites: " + formatRequirementGroups(course.getCorequisites()));
    System.out.println("Flexible prerequisites/corequisites: "
        + formatRequirementGroups(course.getFlexiblePrereqs()));
    System.out.println("Manual requirement notes: " + course.getManualRequirementNotes());
    System.out.println("Description: " + course.getCourseDescription());
  }

  /** Formats AND requirement groups containing OR alternatives in readable form. */
  private static String formatRequirementGroups(String[][] groups) {
    if (groups.length == 0) {
      return "";
    }
    StringBuilder formatted = new StringBuilder();
    for (int i = 0; i < groups.length; i++) {
      if (i > 0) {
        formatted.append(" AND ");
      }
      if (groups[i].length > 1) {
        formatted.append('(').append(String.join(" OR ", groups[i])).append(')');
      } else {
        formatted.append(groups[i][0]);
      }
    }
    return formatted.toString();
  }
}
