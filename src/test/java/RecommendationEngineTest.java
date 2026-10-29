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
}
