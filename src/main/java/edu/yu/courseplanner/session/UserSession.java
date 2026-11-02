package edu.yu.courseplanner.session;

import edu.yu.courseplanner.catalog.SearchEngine;
import edu.yu.courseplanner.model.Course;
import edu.yu.courseplanner.model.User;
import java.util.ArrayList;
import java.util.*;

/** Holds the current user's transient search, bookmark, and interest-list state. */
public class UserSession {
  public static User currentUser = null;
  public static HashMap<Integer, Course> interested = new HashMap<>();
  public static HashMap<Integer, Course> bookmarked = new HashMap<>();
  public static ArrayList<Course> currentSearchResults = new ArrayList<>();

  public static boolean bookmarkClass(String crn) {
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
    return course.contains("online")
        || (user.contains("wilf") && course.contains("wilf"))
        || (user.contains("beren") && course.contains("beren"));
  }

  /** Adds a prevalidated schedule atomically. Existing bookmarks remain in
   * place; an unexpected conflict or validation failure leaves them untouched. */
  public static boolean bookmarkSchedule(List<Course> schedule) {
    return bookmarkSchedule(schedule, false);
  }

  /** Saves a schedule, optionally replacing rather than retaining old
   * bookmarks when the student deliberately planned without them. */
  public static boolean bookmarkSchedule(List<Course> schedule, boolean replaceExisting) {
    if (currentUser == null || schedule == null || schedule.isEmpty()) return false;
    Map<Integer, Course> proposed = replaceExisting ? new HashMap<>() : new HashMap<>(bookmarked);
    for (Course course : schedule) proposed.put(course.getCRN(), course);
    double credits = proposed.values().stream().mapToDouble(Course::getCredits).sum();
    if (currentUser.getCampus().equalsIgnoreCase("wilf") && credits > 17.5) {
      System.out.println("This schedule exceeds Wilf's 17.5-credit maximum and was not bookmarked.");
      return false;
    }
    Set<String> registering = new HashSet<>(currentUser.getCompletedCourses());
    for (Course course : proposed.values()) registering.add(course.getCourseCode());
    for (Course course : proposed.values()) {
      if (!campusesMatch(currentUser.getCampus(), course.getCampus())
          || !course.prerequisitesSatisfied(currentUser.getCompletedCourses())
          || !course.flexiblePrereqsSatisfied(registering)) {
        System.out.println("The schedule could not be bookmarked because " + course.getCourseCode()
            + " no longer passes validation.");
        return false;
      }
      for (Course other : proposed.values()) {
        if (course.getCRN() != other.getCRN() && hasMeetingConflict(course, other)) {
          System.out.println("The schedule could not be bookmarked because "
              + course.getCourseCode() + " conflicts with " + other.getCourseCode() + ".");
          return false;
        }
      }
    }
    bookmarked = new HashMap<>(proposed);
    System.out.println("Saved the selected schedule to your bookmarked classes.");
    return true;
  }

  /** Read-only eligibility check shared by recommendations.  It deliberately
   * does not reserve the course or print anything. */
  public static boolean canFitRecommendation(Course course) {
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
  public static ArrayList<String[]> missingStrictCorequisites(Course course) {
    Set<String> completedOrBookmarked = new HashSet<>(currentUser.getCompletedCourses());
    for (Course bookmarkedCourse : bookmarked.values()) {
      completedOrBookmarked.add(bookmarkedCourse.getCourseCode());
    }
    return missingCorequisiteGroups(course, completedOrBookmarked);
  }

  /** Reports strict corequisites that are neither completed nor presently
   * bookmarked. This warns without preventing either half of a valid pair
   * from being added first. */
  public static ArrayList<String> getCorequisiteWarnings() {
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
  public static boolean hasMeetingConflict(Course first, Course second) {
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

  public static ArrayList<Course> getBookmarked() {
    return new ArrayList<>(bookmarked.values());
  }

  public static boolean interestedInClass(String crn) {
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

  public static ArrayList<Course> getInterested() {
    return new ArrayList<>(interested.values());
  }
}
