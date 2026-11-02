package edu.yu.courseplanner;

import static org.junit.jupiter.api.Assertions.*;

import edu.yu.courseplanner.catalog.*;
import edu.yu.courseplanner.model.*;
import edu.yu.courseplanner.recommendation.SchedulePlanner;
import edu.yu.courseplanner.session.UserSession;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchedulePlannerTest {
  @TempDir Path tempDir;

  @Test
  void plannerAddsEligibleStrictCorequisiteAndReachesMinimumCredits() throws Exception {
    setupPrograms();
    resetIndexes();
    add(course(10001, "MAJ", "1000", "M}1500}1600}SCW}101", "LAB1000"));
    add(course(10002, "LAB", "1000", "T}1500}1600}SCW}102", ""));
    add(course(10003, "ELE", "1000", "W}1500}1600}SCW}103", ""));
    add(course(10004, "ELE", "1001", "R}1500}1600}SCW}104", ""));
    add(course(10005, "ELE", "1002", "F}1500}1600}SCW}105", ""));
    User user = new User("Ari", false, "wilf", "yc", "test major@yc", Set.of(), Set.of());
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();
    UserSession.interested = new HashMap<>();

    SchedulePlan plan = SchedulePlanner.build(user, "").getFirst();

    assertTrue(plan.credits() >= 12.0);
    assertEquals(1, plan.majorCourses());
    assertTrue(plan.courses().stream().anyMatch(course -> course.getCourseCode().equals("MAJ1000")));
    assertTrue(plan.courses().stream().anyMatch(course -> course.getCourseCode().equals("LAB1000")));
  }

  @Test
  void mypEarlyDayAllowsOnlyTheChosenAfternoonDayBeforeThree() throws Exception {
    setupPrograms();
    resetIndexes();
    add(course(10001, "MAJ", "1000", "T}1300}1400}SCW}101", ""));
    add(course(10002, "ELE", "1000", "M}1500}1600}SCW}102", ""));
    add(course(10003, "ELE", "1001", "W}1500}1600}SCW}103", ""));
    add(course(10004, "ELE", "1002", "R}1500}1600}SCW}104", ""));
    add(course(10005, "ELE", "1003", "F}1500}1600}SCW}105", ""));
    User user = new User("Ari", false, "wilf", "yc", "test major@yc", Set.of(), Set.of());
    user.setJewishStudiesTrack("MYP");
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();
    UserSession.interested = new HashMap<>();

    assertTrue(SchedulePlanner.build(user, "R").stream()
        .flatMap(plan -> plan.courses().stream()).noneMatch(course -> course.getCourseCode().equals("MAJ1000")));
    assertTrue(SchedulePlanner.build(user, "T").stream()
        .flatMap(plan -> plan.courses().stream()).anyMatch(course -> course.getCourseCode().equals("MAJ1000")));
  }

  private void setupPrograms() throws Exception {
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
        """));
  }

  private String write(String name, String text) throws Exception {
    Path file = tempDir.resolve(name);
    Files.writeString(file, text);
    return file.toString();
  }

  private static void resetIndexes() {
    SearchEngine.mapByCRN = new HashMap<>();
    SearchEngine.mapByCourseCode = new HashMap<>();
  }

  private static void add(Course course) {
    SearchEngine.mapByCRN.put(course.getCRN(), course);
    SearchEngine.mapByCourseCode.computeIfAbsent(course.getCourseCode(), ignored -> new ArrayList<>()).add(course);
  }

  private static Course course(int crn, String department, String number, String meeting, String corequisite) {
    return new Course("\"" + crn + "\",\"" + number + "\",\"" + department
        + "\",\"A\",\"Wilf\",\"Test\",3,\"Professor\",0,20,20,0,0,0,\""
        + meeting + "\",\"\",\"\",\"\",\"" + corequisite + "\",\"\",\"\",\"\"");
  }
}
