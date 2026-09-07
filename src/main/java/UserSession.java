import java.util.ArrayList;
import java.util.HashMap;

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
    float credits = 0;
    for (Course course : bookmarked.values()) {
      credits += course.getCredits();
    }
    if ((currentUser.getCampus().equals("wilf") && credits + newCourse.getCredits() > 17.5)
        || credits + newCourse.getCredits() > 21) {
      System.out.println("Over credit maximum");
      return false;
    }
    if (!currentUser.getCampus().contains(newCourse.getCampus().toLowerCase())) {
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
    return true;
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
