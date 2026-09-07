import java.util.List;

/** Formats and paginates course lists for the command-line interface. */
public class CoursePrinter {
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

  static boolean hasNextPage(int startingPoint, int courseCount) {
    return startingPoint + INCREMENT <= courseCount;
  }

  static int lastPrintedCourseIndex(int startingPoint, int courseCount) {
    return Math.min(courseCount, startingPoint + INCREMENT - 1);
  }
}
