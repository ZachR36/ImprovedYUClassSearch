package edu.yu.courseplanner.cli;

import edu.yu.courseplanner.model.Course;
import edu.yu.courseplanner.model.Recommendation;
import edu.yu.courseplanner.model.SchedulePlan;
import edu.yu.courseplanner.progress.RequirementStatus;
import java.util.List;

/** Formats and paginates course lists for the command-line interface. */
public class CoursePrinter {

  /** Prints one program's degree-progress result: satisfied status, concrete
   *  remaining requirements, and any informational (never-blocking) notes. */
  public static void degreeProgressPrinting(String programName, RequirementStatus status) {
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
  public static void recommendationPrinting(List<Recommendation> recommendations) {
    System.out.println("\n**Recommended next courses**");
    if (recommendations.isEmpty()) {
      System.out.println("No current-semester offerings fit your unmet requirements and bookmarked schedule.");
      return;
    }
    for (int i = 0; i < recommendations.size(); i++) {
      Recommendation recommendation = recommendations.get(i);
      Course course = recommendation.course();
      System.out.println((i + 1) + ". " + course.getCourseCode() + " — " + course.getName()
          + " (" + course.getCredits() + " credits)");
      for (String reason : recommendation.reasons()) System.out.println("   Why: " + reason);
      for (String caution : recommendation.cautions()) System.out.println("   Note: " + caution);
      System.out.println("   Available sections:");
      for (Course offering : recommendation.offerings()) {
        System.out.println("   - CRN " + offering.getCRN() + ", section " + offering.getSection()
            + ", " + offering.getCampus() + ", " + offering.getTeacher()
            + " (" + offering.getRemainingEnrollment() + " seats remaining)");
      }
    }
  }

  /** Prints the highest-ranked schedule plans and their requirement impact. */
  public static void schedulePrinting(List<SchedulePlan> plans) {
    System.out.println("\n**Possible semester schedules**");
    if (plans.isEmpty()) {
      System.out.println("No schedule meeting the required credit, timing, prerequisite, and seat constraints was found.");
      return;
    }
    for (int i = 0; i < plans.size(); i++) {
      SchedulePlan plan = plans.get(i);
      System.out.println("\n" + (i + 1) + ". " + plan.credits() + " credits; "
          + plan.fulfilledRequirementCourses() + " requirement-fulfilling course(s) "
          + "(" + plan.majorCourses() + " major, " + plan.minorCourses() + " minor, "
          + plan.coreCourses() + " core)");
      for (Course course : plan.courses()) {
        System.out.println("   - " + course.getCourseCode() + " — " + course.getName()
            + " (CRN " + course.getCRN() + ", " + course.getCredits() + " credits; "
            + meetingSummary(course) + ")");
        for (String reason : plan.courseReasons().getOrDefault(course.getCRN(), List.of())) {
          System.out.println("     Why: " + reason);
        }
      }
    }
  }

  private static String meetingSummary(Course course) {
    StringBuilder result = new StringBuilder();
    for (String[][] meeting : course.getMeetings()) {
      if (meeting.length < 3 || meeting[0].length == 0 || meeting[1].length == 0 || meeting[2].length == 0
          || !meeting[1][0].matches("\\d{4}") || !meeting[2][0].matches("\\d{4}")) continue;
      if (!result.isEmpty()) result.append("; ");
      result.append(String.join("/", meeting[0])).append(" ")
          .append(meeting[1][0]).append("-").append(meeting[2][0]);
    }
    return result.isEmpty() ? "TBA/asynchronous" : result.toString();
  }

  public static final int INCREMENT = 15;

  public static void coursePrinting(List<Course> courses, int startingPoint) {
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
  public static void courseChoicePrinting(String courseCode, List<Course> courses) {
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

  public static boolean hasNextPage(int startingPoint, int courseCount) {
    return startingPoint + INCREMENT <= courseCount;
  }

  public static int lastPrintedCourseIndex(int startingPoint, int courseCount) {
    return Math.min(courseCount, startingPoint + INCREMENT - 1);
  }

  /** Prints the complete details and registration requirements for one course. */
  public static void courseDetailsPrinting(Course course) {
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
