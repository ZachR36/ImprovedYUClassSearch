import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SearchRunTests {
  @BeforeEach
  void resetSearchState() {
    SearchEngine.mapByCRN = new HashMap<>();
    SearchEngine.mapByCredits = new HashMap<>();
    UserSession.bookmarked = new HashMap<>();
    UserSession.currentSearchResults = new ArrayList<>();
    UserSession.currentUser = new User("Test User", false, "beren", "beren", new java.util.HashSet<>());
  }

  @Test
  void fractionalCreditSearchReturnsMatchingCourses() throws ReflectiveOperationException {
    Course halfCreditCourse = course("10001", "A", "M}0900}1000}SCW}101", "0.5");
    HashMap<Double, ArrayList<Course>> credits = new HashMap<>();
    credits.put(0.5, new ArrayList<>(java.util.List.of(halfCreditCourse)));
    SearchEngine.mapByCredits = credits;

    assertTrue(chooseSearchOption("credits\n0.5\n"));
    assertEquals(java.util.List.of(halfCreditCourse), UserSession.currentSearchResults);
  }

  @Test
  void expandingASearchAddsNewMatchesWithoutDuplicatingExistingOnes() throws ReflectiveOperationException {
    Course existingCourse = course("10001", "A", "M}0900}1000}SCW}101", "3");
    Course additionalCourse = course("10002", "B", "T}0900}1000}SCW}102", "0.5");
    SearchEngine.mapByCredits.put(0.5, new ArrayList<>(java.util.List.of(existingCourse, additionalCourse)));
    UserSession.currentSearchResults.add(existingCourse);

    assertTrue(chooseSearchOption("credits\n0.5\n", false, true));
    assertEquals(java.util.List.of(existingCourse, additionalCourse), UserSession.currentSearchResults);
  }

  @Test
  void paginationRecognizesTheLastPageAtRelevantBoundaries() {
    assertTrue(CoursePrinter.hasNextPage(1, 20));
    assertFalse(CoursePrinter.hasNextPage(16, 20));
    assertTrue(CoursePrinter.hasNextPage(1, 21));
    assertFalse(CoursePrinter.hasNextPage(16, 21));
    assertTrue(CoursePrinter.hasNextPage(16, 40));
    assertFalse(CoursePrinter.hasNextPage(31, 40));
    assertTrue(CoursePrinter.hasNextPage(16, 41));
    assertFalse(CoursePrinter.hasNextPage(31, 41));
    assertEquals(15, CoursePrinter.lastPrintedCourseIndex(1, 20));
    assertEquals(20, CoursePrinter.lastPrintedCourseIndex(16, 20));
    assertEquals(41, CoursePrinter.lastPrintedCourseIndex(31, 41));
  }

  @Test
  void berenCoursesWithOverlappingSectionLettersButDifferentMeetingTimesCanBothBeBookmarked()
      throws ReflectiveOperationException {
    Course first = course("10001", "AB", "M}0900}1000}SCW}101", "3");
    Course second = course("10002", "BC", "T}0900}1000}SCW}102", "3");
    addToCourseMap(first, second);

    assertTrue(UserSession.bookmarkClass("10001"));
    assertTrue(UserSession.bookmarkClass("10002"));
  }

  @Test
  void coursesWithOverlappingMeetingsCannotBothBeBookmarked() throws ReflectiveOperationException {
    Course first = course("10001", "A", "M}0900}1000}SCW}101", "3");
    Course second = course("10002", "B", "M}0930}1030}SCW}102", "3");
    addToCourseMap(first, second);

    assertTrue(UserSession.bookmarkClass("10001"));
    assertFalse(UserSession.bookmarkClass("10002"));
  }

  @Test
  void nonNumericBookmarkCrnIsRejectedWithoutThrowing() {
    assertFalse(UserSession.bookmarkClass("not-a-crn"));
  }

  private Course course(String crn, String section, String meeting, String credits) {
    return new Course("\"" + crn + "\",\"1001\",\"TEST\",\"" + section
        + "\",\"Beren\",\"Test Course " + crn + "\"," + credits
        + ",\"Professor\",0,20,20,0,0,0,\"" + meeting + "\",\"\",\"\"");
  }

  private void addToCourseMap(Course... courses) {
    for (Course course : courses) {
      SearchEngine.mapByCRN.put(course.getCRN(), course);
    }
  }

  private boolean chooseSearchOption(String input) throws ReflectiveOperationException {
    Method method = Runner.class.getDeclaredMethod("chooseSearchOption", Scanner.class, boolean.class);
    method.setAccessible(true);
    return (boolean) method.invoke(null, new Scanner(input), false);
  }

  private boolean chooseSearchOption(String input, boolean refine, boolean expand) throws ReflectiveOperationException {
    Method method = Runner.class.getDeclaredMethod("chooseSearchOption", Scanner.class, boolean.class, boolean.class);
    method.setAccessible(true);
    return (boolean) method.invoke(null, new Scanner(input), refine, expand);
  }
}
