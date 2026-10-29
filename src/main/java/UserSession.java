import java.util.ArrayList;
import java.util.*;

/** Holds the current user's transient search, bookmark, and interest-list state. */
public class UserSession {
  static User currentUser = null;
  static HashMap<Integer, Course> interested = new HashMap<>();
  static HashMap<Integer, Course> bookmarked = new HashMap<>();
  static ArrayList<Course> currentSearchResults = new ArrayList<>();

  static boolean bookmarkClass(String crn) {
    int courseCrn;
    try {
      courseCrn = Integer.parseInt(crn);
    } catch (NumberFormatException e) {
      System.out.println("CRN must be a number");
      return false;
    }
    if (!SearchEngine.mapByCRN.containsKey(courseCrn)) {
      System.out.println("Course is not in database");
      return false;
    }
    if (bookmarked.containsKey(courseCrn)) {
      bookmarked.remove(courseCrn);
      System.out.println("Removed " + crn + " from bookmarked");
      return true;
    }
    Course newCourse = SearchEngine.mapByCRN.get(courseCrn);

    if (!newCourse.prerequisitesSatisfied(currentUser.getCompletedCourses())) {
      System.out.println("Prerequisites not satisfied for " + newCourse.getCourseCode());
      return false;
    }

    Set<String> completedOrBookmarked = new HashSet<>(currentUser.getCompletedCourses());
    for (Course bookmarkedCourse : bookmarked.values()) {
      completedOrBookmarked.add(bookmarkedCourse.getCourseCode());
    }
    if (!newCourse.flexiblePrereqsSatisfied(completedOrBookmarked)) {
      System.out.println("Flexible prerequisites not satisfied for " + newCourse.getCourseCode()
              + " (must be completed or bookmarked alongside this course)");
      return false;
    }
    float credits = 0;
    for (Course course : bookmarked.values()) {
      credits += course.getCredits();
    }
    if ((currentUser.getCampus().equals("wilf") && credits + newCourse.getCredits() > 17.5)
        || credits + newCourse.getCredits() > 21) {
      System.out.println("Over credit maximum");
      return false;
    }
    if (!campusesMatch(currentUser.getCampus(), newCourse.getCampus())) {
      System.out.println("Course and User campuses do not match");
      return false;
    }
    for (Course course : bookmarked.values()) {
      if (hasMeetingConflict(newCourse, course)) {
        System.out.println("Course has a meeting-time conflict with " + course.getName());
        return false;
      }
    }
    bookmarked.put(courseCrn, newCourse);
    printCorequisiteWarning(newCourse);
    return true;
  }

  /** Matches campus labels by their Wilf/Beren identity, so a guest user with
   * both names can also use offerings labelled "Beren (Online)" or "Wilf (Online)". */
  private static boolean campusesMatch(String userCampus, String courseCampus) {
    String user = userCampus == null ? "" : userCampus.toLowerCase();
    String course = courseCampus == null ? "" : courseCampus.toLowerCase();
    return (user.contains("wilf") && course.contains("wilf"))
        || (user.contains("beren") && course.contains("beren"));
  }

  /** Read-only eligibility check shared by recommendations.  It deliberately
   * does not reserve the course or print anything. */
  static boolean canFitRecommendation(Course course) {
    if (currentUser == null || !campusesMatch(currentUser.getCampus(), course.getCampus())
        || course.getRemainingEnrollment() <= 0
        || !course.prerequisitesSatisfied(currentUser.getCompletedCourses())) {
      return false;
    }
    Set<String> completedOrBookmarked = new HashSet<>(currentUser.getCompletedCourses());
    for (Course bookmarkedCourse : bookmarked.values()) {
      completedOrBookmarked.add(bookmarkedCourse.getCourseCode());
      if (hasMeetingConflict(course, bookmarkedCourse)) {
        return false;
      }
    }
    return course.flexiblePrereqsSatisfied(completedOrBookmarked);
  }

  /** A strict corequisite is a warning rather than a rejection: students may
   * need to select the lecture and lab one at a time. */
  static ArrayList<String[]> missingStrictCorequisites(Course course) {
    Set<String> completedOrBookmarked = new HashSet<>(currentUser.getCompletedCourses());
    for (Course bookmarkedCourse : bookmarked.values()) {
      completedOrBookmarked.add(bookmarkedCourse.getCourseCode());
    }
    return missingCorequisiteGroups(course, completedOrBookmarked);
  }

  /** Reports strict corequisites that are neither completed nor presently
   * bookmarked. This warns without preventing either half of a valid pair
   * from being added first. */
  static ArrayList<String> getCorequisiteWarnings() {
    Set<String> completedOrBookmarked = new HashSet<>(currentUser.getCompletedCourses());
    for (Course course : bookmarked.values()) {
      completedOrBookmarked.add(course.getCourseCode());
    }
    ArrayList<String> warnings = new ArrayList<>();
    for (Course course : bookmarked.values()) {
      for (String[] group : missingCorequisiteGroups(course, completedOrBookmarked)) {
        warnings.add(course.getCourseCode() + " requires strict corequisite "
            + String.join(" OR ", group) + " to be completed or bookmarked alongside it.");
      }
    }
    return warnings;
  }

  private static void printCorequisiteWarning(Course course) {
    Set<String> completedOrBookmarked = new HashSet<>(currentUser.getCompletedCourses());
    for (Course bookmarkedCourse : bookmarked.values()) {
      completedOrBookmarked.add(bookmarkedCourse.getCourseCode());
    }
    for (String[] group : missingCorequisiteGroups(course, completedOrBookmarked)) {
      System.out.println("Warning: " + course.getCourseCode() + " requires strict corequisite "
          + String.join(" OR ", group) + " to be completed or bookmarked alongside it.");
    }
  }

  private static ArrayList<String[]> missingCorequisiteGroups(Course course, Set<String> completedOrBookmarked) {
    ArrayList<String[]> missing = new ArrayList<>();
    for (String[] group : course.getCorequisites()) {
      boolean satisfied = false;
      for (String alternative : group) {
        if (completedOrBookmarked.contains(alternative)) {
          satisfied = true;
          break;
        }
      }
      if (!satisfied) {
        missing.add(group);
      }
    }
    return missing;
  }

  /** Returns whether two courses meet on a shared day during overlapping times. */
  static boolean hasMeetingConflict(Course first, Course second) {
    for (String[][] firstMeeting : first.getMeetings()) {
      if (!hasUsableTime(firstMeeting)) {
        continue;
      }
      for (String[][] secondMeeting : second.getMeetings()) {
        if (!hasUsableTime(secondMeeting) || !sharesDay(firstMeeting[0], secondMeeting[0])) {
          continue;
        }
        int firstStart = Integer.parseInt(firstMeeting[1][0]);
        int firstEnd = Integer.parseInt(firstMeeting[2][0]);
        int secondStart = Integer.parseInt(secondMeeting[1][0]);
        int secondEnd = Integer.parseInt(secondMeeting[2][0]);
        if (firstStart < secondEnd && secondStart < firstEnd) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasUsableTime(String[][] meeting) {
    return meeting.length >= 3
        && meeting[0].length > 0
        && meeting[1].length > 0
        && meeting[2].length > 0
        && meeting[1][0].matches("\\d{4}")
        && meeting[2][0].matches("\\d{4}");
  }

  private static boolean sharesDay(String[] firstDays, String[] secondDays) {
    for (String firstDay : firstDays) {
      for (String secondDay : secondDays) {
        if (firstDay.equalsIgnoreCase(secondDay)) {
          return true;
        }
      }
    }
    return false;
  }

  static ArrayList<Course> getBookmarked() {
    return new ArrayList<>(bookmarked.values());
  }

  static boolean interestedInClass(String crn) {
    int intcrn = Integer.parseInt(crn);
    if (!SearchEngine.mapByCRN.containsKey(intcrn)) {
      System.out.println("CRN is not in database");
      return false;
    }
    if (interested.containsKey(intcrn)) {
      interested.remove(intcrn);
      return true;
    }
    interested.put(intcrn, SearchEngine.mapByCRN.get(intcrn));
    return true;
  }

  static ArrayList<Course> getInterested() {
    return new ArrayList<>(interested.values());
  }
}
