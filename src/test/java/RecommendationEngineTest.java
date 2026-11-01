import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecommendationEngineTest {
  @TempDir Path tempDir;

  @Test
  void recommendsOnlyTheStudentsSavedDegreeTrackAndSkipsConflictingOfferings() throws Exception {
    DegreeProgram.setup(write("programs.txt", """
        === Test Major ===
        name: Test Major
        school: YC
        degreeType: BA

        TRACK_CHOICE: Concentration
          TRACK: Systems
            COURSE_LIST: Required
              COM1000
          TRACK: Theory
            COURSE_LIST: Required
              COM2000
        """));
    CoreRequirementProgram.setup(write("cores.txt", """
        === Test Core ===
        name: Test Core
        school: YC
        degreeType: BA
        major: Test Major
        programType: Core
        """));
    resetCourseIndexes();
    addCourse(course(10001, "COM", "1000", "M}0900}1000}SCW}101"));
    addCourse(course(10002, "COM", "2000", "M}0900}1000}SCW}101"));
    User user = new User("Ari", false, "wilf", "yc", "test major@yc", Set.of(), Set.of());
    user.setSelectedProgramTrack("test major@yc", "Theory");
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();

    assertEquals("COM2000", RecommendationEngine.recommend(user).getFirst().course().getCourseCode());

    UserSession.bookmarked.put(99999, course(99999, "OTHER", "1000", "M}0900}1000}SCW}101"));
    assertTrue(RecommendationEngine.recommend(user).isEmpty());
  }

  @Test
  void recommendsAnApplicableCoreCourseAfterDegreeRequirementsAreComplete() throws Exception {
    DegreeProgram.setup(write("programs.txt", """
        === Test Major ===
        name: Test Major
        school: YC
        degreeType: BA

        COURSE_LIST: Required
          MAJ1000
        """));
    CoreRequirementProgram.setup(write("cores.txt", """
        === Test Core ===
        name: Test Core
        school: YC
        degreeType: BA
        major: Test Major
        programType: Core

        COURSE_LIST: Writing
          CORE1000
        """));
    resetCourseIndexes();
    addCourse(course(10003, "CORE", "1000", "T}0900}1000}SCW}101"));
    User user = new User("Ari", false, "wilf", "yc", "test major@yc", Set.of(), Set.of("MAJ1000"));
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();

    Recommendation recommendation = RecommendationEngine.recommend(user).getFirst();
    assertEquals("CORE1000", recommendation.course().getCourseCode());
    assertTrue(recommendation.reasons().stream().anyMatch(reason -> reason.contains("Test Core")));
  }

  @Test
  void includesEveryViableSectionForOneRecommendedCourse() throws Exception {
    DegreeProgram.setup(write("programs.txt", """
        === Test Major ===
        name: Test Major
        school: YC
        degreeType: BA

        COURSE_LIST: Required
          COM2000
        """));
    CoreRequirementProgram.setup(write("cores.txt", """
        === Test Core ===
        name: Test Core
        school: YC
        degreeType: BA
        major: Test Major
        programType: Core
        """));
    resetCourseIndexes();
    addCourse(course(10005, "COM", "2000", "M}0900}1000}SCW}101"));
    addCourse(course(10006, "COM", "2000", "T}0900}1000}SCW}101"));
    addCourse(course(10007, "COM", "2000", "W}0900}1000}SCW}101"));
    User user = new User("Ari", false, "wilf", "yc", "test major@yc", Set.of(), Set.of());
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();

    Recommendation recommendation = RecommendationEngine.recommend(user).getFirst();
    assertEquals(3, recommendation.offerings().size());
    assertEquals(10005, recommendation.course().getCRN());

    UserSession.bookmarked.put(99999, course(99999, "OTHER", "1000", "T}0900}1000}SCW}101"));
    recommendation = RecommendationEngine.recommend(user).getFirst();
    assertEquals(2, recommendation.offerings().size());
    assertTrue(recommendation.offerings().stream().noneMatch(course -> course.getCRN() == 10006));
  }

  @Test
  void schedulePlannerSoftlyFavorsRequestedCourseAttributes() throws Exception {
    DegreeProgram.setup(write("programs.txt", """
        === Test Major ===
        name: Test Major
        school: YC
        degreeType: BA

        COURSE_LIST: Required
          COM1000
        """));
    CoreRequirementProgram.setup(write("cores.txt", """
        === Test Core ===
        name: Test Core
        school: YC
        degreeType: BA
        major: Test Major
        programType: Core
        """));
    resetCourseIndexes();
    addCourse(course(10008, "COM", "1000", "M}0900}1000}SCW}101"));
    addCourse(courseWithAttributes(10009, "COM", "1000", "T}0900}1000}SCW}102", "HONR"));
    User user = new User("Ari", false, "wilf", "yc", "test major@yc", Set.of(), Set.of());
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();
    SchedulePreferences preferences = new SchedulePreferences(3, 3, 1, 0,
        SchedulePreferences.Strategy.MAJOR_FIRST, java.util.List.of(), java.util.List.of(), Set.of(), false,
        Set.of(), Set.of("HONR"), Set.of(), SchedulePreferences.SeatPreference.NO_PREFERENCE);

    SchedulePlan plan = SchedulePlanner.plan(user, preferences).getFirst();

    assertEquals(10009, plan.selections().getFirst().course().getCRN());
    assertTrue(plan.explanations().stream().anyMatch(note -> note.contains("HONR attributes")));
  }

  @Test
  void substituteBundlesAnAvailableStrictCorequisite() throws Exception {
    DegreeProgram.setup(write("programs.txt", """
        === Test Major ===
        name: Test Major
        school: YC
        degreeType: BA

        COURSE_LIST: Required
          CHEM1215
        """));
    CoreRequirementProgram.setup(write("cores.txt", """
        === Test Core ===
        name: Test Core
        school: YC
        degreeType: BA
        major: Test Major
        programType: Core
        """));
    resetCourseIndexes();
    Course lecture = courseWithCorequisite(10010, "CHEM", "1215", "M}0900}1000}SCW}101", "CHEM1213");
    Course lab = course(10011, "CHEM", "1213", "T}0900}1000}SCW}102");
    Course oldCourse = course(10012, "OLD", "1000", "W}0900}1000}SCW}103");
    addCourse(lecture);
    addCourse(lab);
    User user = new User("Ari", false, "wilf", "yc", "test major@yc", Set.of(), Set.of());
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();
    Recommendation oldSelection = new Recommendation(oldCourse, java.util.List.of(oldCourse),
        java.util.List.of("Advances another program"), java.util.List.of());
    SchedulePlan plan = new SchedulePlan(java.util.List.of(), java.util.List.of(oldSelection), 3, 0);

    ScheduleSubstitution substitute = SchedulePlanner.substitutes(plan, oldCourse.getCRN(),
        SchedulePreferences.defaults(), SubstitutionGoal.ANY_PROGRESS).getFirst();

    assertEquals("CHEM1215", substitute.primary().course().getCourseCode());
    assertEquals(java.util.List.of("CHEM1213"), substitute.corequisites().stream()
        .map(item -> item.course().getCourseCode()).toList());
  }

  private String write(String name, String contents) throws Exception {
    Path file = tempDir.resolve(name);
    Files.writeString(file, contents);
    return file.toString();
  }

  private static void resetCourseIndexes() {
    SearchEngine.mapByCRN = new HashMap<>();
    SearchEngine.mapByCourseCode = new HashMap<>();
  }

  private static void addCourse(Course course) {
    SearchEngine.mapByCRN.put(course.getCRN(), course);
    SearchEngine.mapByCourseCode.computeIfAbsent(course.getCourseCode(), ignored -> new ArrayList<>()).add(course);
  }

  private static Course course(int crn, String department, String number, String meeting) {
    return new Course("\"" + crn + "\",\"" + number + "\",\"" + department
        + "\",\"A\",\"Wilf\",\"Test Course\",3,\"Professor\",0,20,20,0,0,0,\""
        + meeting + "\",\"\",\"\"");
  }

  private static Course courseWithAttributes(int crn, String department, String number, String meeting,
                                             String attributes) {
    return new Course("\"" + crn + "\",\"" + number + "\",\"" + department
        + "\",\"A\",\"Wilf\",\"Test Course\",3,\"Professor\",0,20,20,0,0,0,\""
        + meeting + "\",\"" + attributes + "\",\"\"");
  }

  private static Course courseWithCorequisite(int crn, String department, String number, String meeting,
                                              String corequisite) {
    return new Course("\"" + crn + "\",\"" + number + "\",\"" + department
        + "\",\"A\",\"Wilf\",\"Test Course\",3,\"Professor\",0,20,20,0,0,0,\""
        + meeting + "\",\"\",\"\",\"\",\"" + corequisite + "\",\"\",\"\",\"\"");
  }
}
