import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
    SearchEngine.mapByCourseCode = new HashMap<>();
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
  void scheduleTimeWindowsAcceptMultipleCommaSeparatedEntries() throws ReflectiveOperationException {
    Method method = Runner.class.getDeclaredMethod("readTimeRanges", Scanner.class, String.class);
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    java.util.List<TimeRange> ranges = (java.util.List<TimeRange>) method.invoke(null,
        new Scanner("0900-1100, 1300-1500\n"), "test: ");

    assertEquals(java.util.List.of(new TimeRange(900, 1100), new TimeRange(1300, 1500)), ranges);
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
  void departmentAndCourseNumberSearchReturnsEveryOfferingOfThatCourse() throws ReflectiveOperationException {
    Course first = course("10001", "A", "M}0900}1000}SCW}101", "3");
    Course second = course("10002", "B", "T}0900}1000}SCW}102", "3");
    SearchEngine.mapByCourseCode.put("TEST1001", new ArrayList<>(java.util.List.of(first, second)));

    assertTrue(chooseSearchOption("dept + course number\nTEST\n1001\n"));
    assertEquals(java.util.List.of(first, second), UserSession.currentSearchResults);
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
  void bookmarkingOneHalfOfAStrictCorequisitePairWarnsButDoesNotBlockIt() throws ReflectiveOperationException {
    Course lecture = courseWithCorequisite("10001", "CHEM", "1215", "CHEM1213");
    Course companion = courseWithCorequisite("10002", "CHEM", "1213", "");
    addToCourseMap(lecture, companion);

    assertTrue(UserSession.bookmarkClass("10001"));
    assertTrue(UserSession.getCorequisiteWarnings().getFirst().contains("CHEM1215 requires strict corequisite CHEM1213"));

    assertTrue(UserSession.bookmarkClass("10002"));
    assertTrue(UserSession.getCorequisiteWarnings().isEmpty());
  }

  @Test
  void acceptingAScheduleIsAtomicWhenAProposedCourseConflicts() {
    Course saved = course("10001", "A", "M}0900}1000}SCW}101", "3");
    Course conflict = course("10002", "B", "M}0930}1030}SCW}102", "3");
    UserSession.bookmarked.put(saved.getCRN(), saved);

    assertFalse(UserSession.acceptScheduleAtomically(java.util.List.of(conflict)));
    assertEquals(java.util.List.of(saved), UserSession.getBookmarked());
  }

  @Test
  void acceptingAScheduleRequiresAllStrictCorequisitesTogether() {
    Course lecture = courseWithCorequisite("10001", "CHEM", "1215", "CHEM1213");
    Course companion = courseWithCorequisite("10002", "CHEM", "1213", "");

    assertFalse(UserSession.acceptScheduleAtomically(java.util.List.of(lecture)));
    assertTrue(UserSession.getBookmarked().isEmpty());
    assertTrue(UserSession.acceptScheduleAtomically(java.util.List.of(lecture, companion)));
    assertEquals(2, UserSession.getBookmarked().size());
  }

  @Test
  void replacingASelectionKeepsFixedBookmarkCreditsInTheSelectedPlan() {
    Course fixed = course("10001", "A", "M}0900}1000}SCW}101", "3");
    Course original = course("10002", "B", "T}0900}1000}SCW}102", "3");
    Course replacementCourse = course("10003", "C", "W}0900}1000}SCW}103", "4");
    Recommendation originalRecommendation = new Recommendation(original, java.util.List.of(original),
        java.util.List.of("Advances test major"), java.util.List.of());
    Recommendation replacement = new Recommendation(replacementCourse, java.util.List.of(replacementCourse),
        java.util.List.of("Advances test major"), java.util.List.of());
    ScheduleSession.selectedPlan = new SchedulePlan(java.util.List.of(fixed),
        java.util.List.of(originalRecommendation), 6, 0);

    ScheduleSession.replace(original.getCRN(), new ScheduleSubstitution(replacement, java.util.List.of()));

    assertEquals(7, ScheduleSession.selectedPlan.credits());
    assertEquals(java.util.List.of(fixed), ScheduleSession.selectedPlan.fixedCourses());
  }

  @Test
  void selectedPlanCanBeRedisplayedWithItsFixedBookmarks() {
    Course fixed = course("10001", "A", "M}0900}1000}SCW}101", "3");
    Course selection = course("10002", "B", "T}0900}1000}SCW}102", "3");
    Recommendation recommendation = new Recommendation(selection, java.util.List.of(selection),
        java.util.List.of("Advances test major"), java.util.List.of());
    SchedulePlan plan = new SchedulePlan(java.util.List.of(fixed), java.util.List.of(recommendation), 6, 0,
        java.util.List.of("Strategy: major-first."));
    ScheduleSession.setPlans(java.util.List.of(plan), SchedulePreferences.defaults());
    assertTrue(ScheduleSession.select(1));

    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream originalOutput = System.out;
    try {
      System.setOut(new PrintStream(output));
      CoursePrinter.schedulePlanPrinting(java.util.List.of(ScheduleSession.selectedPlan));
    } finally {
      System.setOut(originalOutput);
    }

    assertTrue(output.toString().contains("Already bookmarked (kept in this schedule)"));
    assertTrue(output.toString().contains("Why this schedule:"));
    assertTrue(output.toString().contains("Strategy: major-first."));
    assertTrue(output.toString().contains("CRN 10001"));
    assertTrue(output.toString().contains("CRN 10002"));
  }

  @Test
  void nonNumericBookmarkCrnIsRejectedWithoutThrowing() {
    assertFalse(UserSession.bookmarkClass("not-a-crn"));
  }

  @Test
  void guestUserCanBookmarkAnOnlineOfferingFromEitherCampus() {
    UserSession.currentUser = new User();
    Course berenOnline = new Course("\"10001\",\"1001\",\"TEST\",\"A\",\"Beren (Online)\",\"Test Course\",3,"
        + "\"Professor\",0,20,20,0,0,0,\"M}0900}1000}SCW}101\",\"\",\"\"");
    addToCourseMap(berenOnline);

    assertTrue(UserSession.bookmarkClass("10001"));
  }

  @Test
  void courseDetailsIncludeAllRequirementColumnsAndDescription() {
    Course course = courseWithRequirements();
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    PrintStream originalOutput = System.out;
    try {
      System.setOut(new PrintStream(output));
      CoursePrinter.courseDetailsPrinting(course);
    } finally {
      System.setOut(originalOutput);
    }

    String details = output.toString();
    assertTrue(details.contains("Name: Test Course 10001"));
    assertTrue(details.contains("Course code: TEST1001"));
    assertTrue(details.contains("CRN: 10001"));
    assertTrue(details.contains("Instructor: Professor"));
    assertTrue(details.contains("Prerequisites: TEST1000 AND (ALT1000 OR ALT2000)"));
    assertTrue(details.contains("Corequisites: CORE1000"));
    assertTrue(details.contains("Flexible prerequisites/corequisites: FLEX1000"));
    assertTrue(details.contains("Manual requirement notes: Permission of instructor"));
    assertTrue(details.contains("Description: A detailed course description."));
  }

  private Course course(String crn, String section, String meeting, String credits) {
    return new Course("\"" + crn + "\",\"1001\",\"TEST\",\"" + section
        + "\",\"Beren\",\"Test Course " + crn + "\"," + credits
        + ",\"Professor\",0,20,20,0,0,0,\"" + meeting + "\",\"\",\"\"");
  }

  private Course courseWithRequirements() {
    return new Course("\"10001\",\"1001\",\"TEST\",\"A\",\"Beren\",\"Test Course 10001\",3,"
        + "\"Professor\",0,20,20,0,0,0,\"M}0900}1000}SCW}101\",\"\",\"\","
        + "\"TEST1000;;ALT1000|ALT2000\",\"CORE1000\",\"FLEX1000\","
        + "\"Permission of instructor\",\"A detailed course description.\"");
  }

  private Course courseWithCorequisite(String crn, String department, String number, String corequisite) {
    String meeting = crn.equals("10002") ? "T}0900}1000}SCW}101" : "M}0900}1000}SCW}101";
    return new Course("\"" + crn + "\",\"" + number + "\",\"" + department + "\",\"A\",\"Beren\",\"Test Course\",3,"
        + "\"Professor\",0,20,20,0,0,0,\"" + meeting + "\",\"\",\"\",\"\",\""
        + corequisite + "\",\"\",\"\",\"\"");
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
