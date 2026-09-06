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
  void resetSearchState() throws ReflectiveOperationException {
    setStaticField("mapByCRN", new HashMap<Integer, Course>());
    setStaticField("mapByCredits", new HashMap<Double, ArrayList<Course>>());
    setStaticField("bookmarked", new HashMap<Integer, Course>());
    setStaticField("currentSearchResults", new ArrayList<Course>());
    setStaticField("currentUser", new User("Test User", false, "beren", "beren", new java.util.HashSet<>()));
  }

  @Test
  void fractionalCreditSearchReturnsMatchingCourses() throws ReflectiveOperationException {
    Course halfCreditCourse = course("10001", "A", "M}0900}1000}SCW}101", "0.5");
    HashMap<Double, ArrayList<Course>> credits = new HashMap<>();
    credits.put(0.5, new ArrayList<>(java.util.List.of(halfCreditCourse)));
    setStaticField("mapByCredits", credits);

    assertTrue(chooseSearchOption("credits\n0.5\n"));
    assertEquals(java.util.List.of(halfCreditCourse), getStaticField("currentSearchResults"));
  }

  @Test
  void paginationRecognizesTheLastPageAtRelevantBoundaries() {
    assertTrue(Search.hasNextPage(1, 20));
    assertFalse(Search.hasNextPage(16, 20));
    assertTrue(Search.hasNextPage(1, 21));
    assertFalse(Search.hasNextPage(16, 21));
    assertTrue(Search.hasNextPage(16, 40));
    assertFalse(Search.hasNextPage(31, 40));
    assertTrue(Search.hasNextPage(16, 41));
    assertFalse(Search.hasNextPage(31, 41));
    assertEquals(15, Search.lastPrintedCourseIndex(1, 20));
    assertEquals(20, Search.lastPrintedCourseIndex(16, 20));
    assertEquals(41, Search.lastPrintedCourseIndex(31, 41));
  }

  @Test
  void berenCoursesWithOverlappingSectionLettersButDifferentMeetingTimesCanBothBeBookmarked()
      throws ReflectiveOperationException {
    Course first = course("10001", "AB", "M}0900}1000}SCW}101", "3");
    Course second = course("10002", "BC", "T}0900}1000}SCW}102", "3");
    addToCourseMap(first, second);

    assertTrue(Search.bookmarkClass("10001"));
    assertTrue(Search.bookmarkClass("10002"));
  }

  @Test
  void coursesWithOverlappingMeetingsCannotBothBeBookmarked() throws ReflectiveOperationException {
    Course first = course("10001", "A", "M}0900}1000}SCW}101", "3");
    Course second = course("10002", "B", "M}0930}1030}SCW}102", "3");
    addToCourseMap(first, second);

    assertTrue(Search.bookmarkClass("10001"));
    assertFalse(Search.bookmarkClass("10002"));
  }

  @Test
  void nonNumericBookmarkCrnIsRejectedWithoutThrowing() {
    assertFalse(Search.bookmarkClass("not-a-crn"));
  }

  private Course course(String crn, String section, String meeting, String credits) {
    return new Course("\"" + crn + "\",\"1001\",\"TEST\",\"" + section
        + "\",\"Beren\",\"Test Course " + crn + "\"," + credits
        + ",\"Professor\",0,20,20,0,0,0,\"" + meeting + "\",\"\",\"\"");
  }

  @SuppressWarnings("unchecked")
  private void addToCourseMap(Course... courses) throws ReflectiveOperationException {
    HashMap<Integer, Course> byCrn = (HashMap<Integer, Course>) getStaticField("mapByCRN");
    for (Course course : courses) {
      byCrn.put(course.getCRN(), course);
    }
  }

  private Object getStaticField(String name) throws ReflectiveOperationException {
    Field field = Search.class.getDeclaredField(name);
    field.setAccessible(true);
    return field.get(null);
  }

  private void setStaticField(String name, Object value) throws ReflectiveOperationException {
    Field field = Search.class.getDeclaredField(name);
    field.setAccessible(true);
    field.set(null, value);
  }

  private boolean chooseSearchOption(String input) throws ReflectiveOperationException {
    Method method = Search.class.getDeclaredMethod("chooseSearchOption", Scanner.class, boolean.class);
    method.setAccessible(true);
    return (boolean) method.invoke(null, new Scanner(input), false);
  }
}
