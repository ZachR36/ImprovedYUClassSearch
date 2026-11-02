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

  @Test
  void plannerNeverUsesACompletedCourseAsARequirementOrFiller() throws Exception {
    setupPrograms();
    resetIndexes();
    add(course(10001, "MAJ", "1000", "M}1500}1600}SCW}101", ""));
    add(course(10002, "ELE", "1000", "T}1500}1600}SCW}102", ""));
    add(course(10003, "ELE", "1001", "W}1500}1600}SCW}103", ""));
    add(course(10004, "ELE", "1002", "R}1500}1600}SCW}104", ""));
    add(course(10005, "ELE", "1003", "F}1500}1600}SCW}105", ""));
    User user = new User("Ari", false, "wilf", "yc", "test major@yc", Set.of(), Set.of("ELE1000"));
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();
    UserSession.interested = new HashMap<>();

    assertTrue(SchedulePlanner.build(user, "").stream()
        .flatMap(plan -> plan.courses().stream()).noneMatch(course -> course.getCourseCode().equals("ELE1000")));
  }

  @Test
  void mypStudentsMayTakeFridayClassesAtAnyTime() throws Exception {
    setupPrograms();
    resetIndexes();
    add(course(10001, "MAJ", "1000", "F}1000}1100}SCW}101", ""));
    add(course(10002, "ELE", "1000", "M}1500}1600}SCW}102", ""));
    add(course(10003, "ELE", "1001", "T}1500}1600}SCW}103", ""));
    add(course(10004, "ELE", "1002", "W}1500}1600}SCW}104", ""));
    add(course(10005, "ELE", "1003", "R}1500}1600}SCW}105", ""));
    User user = new User("Ari", false, "wilf", "yc", "test major@yc", Set.of(), Set.of());
    user.setJewishStudiesTrack("MYP");
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();
    UserSession.interested = new HashMap<>();

    assertTrue(SchedulePlanner.build(user, "T").stream()
        .flatMap(plan -> plan.courses().stream()).anyMatch(course -> course.getCourseCode().equals("MAJ1000")));
  }

  @Test
  void plannerUsesOnlyOneOptionFromAOneOfMajorRequirement() throws Exception {
    DegreeProgram.setup(write("choice-programs.txt", """
        === Choice Major ===
        name: Choice Major
        school: YC
        degreeType: BA

        CHOOSE_N: 1
          OPTION: MAJ1000
          OPTION: MAJ2000
        """));
    CoreRequirementProgram.setup(write("choice-cores.txt", """
        === Choice Core ===
        name: Choice Core
        school: YC
        degreeType: BA
        major: Choice Major
        programType: Core
        """));
    resetIndexes();
    add(course(10001, "MAJ", "1000", "M}1500}1600}SCW}101", ""));
    add(course(10002, "MAJ", "2000", "T}1500}1600}SCW}102", ""));
    add(course(10003, "ELE", "1000", "W}1500}1600}SCW}103", ""));
    add(course(10004, "ELE", "1001", "R}1500}1600}SCW}104", ""));
    add(course(10005, "ELE", "1002", "F}1500}1600}SCW}105", ""));
    User user = new User("Ari", false, "wilf", "yc", "choice major@yc", Set.of(), Set.of());
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();
    UserSession.interested = new HashMap<>();

    SchedulePlan plan = SchedulePlanner.build(user, "").getFirst();
    assertEquals(1, plan.majorCourses());
    assertEquals(1, plan.courses().stream().filter(c -> c.getCourseCode().startsWith("MAJ")).count());
  }

  @Test
  void plannerAllowsFlexiblePrerequisiteThatIsAddedInTheSameCorequisiteBundle() throws Exception {
    setupPrograms();
    resetIndexes();
    add(courseWithRelations(10001, "MAJ", "1000", "M}1500}1600}SCW}101", "", "LAB1000", ""));
    // The lab may be taken as a prerequisite or corequisite of the lecture.
    add(courseWithRelations(10002, "LAB", "1000", "T}1500}1600}SCW}102", "", "", "MAJ1000"));
    add(course(10003, "ELE", "1000", "W}1500}1600}SCW}103", ""));
    add(course(10004, "ELE", "1001", "R}1500}1600}SCW}104", ""));
    add(course(10005, "ELE", "1002", "F}1500}1600}SCW}105", ""));
    User user = new User("Ari", false, "wilf", "yc", "test major@yc", Set.of(), Set.of());
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();
    UserSession.interested = new HashMap<>();

    SchedulePlan plan = SchedulePlanner.build(user, "").getFirst();
    assertTrue(plan.courses().stream().anyMatch(c -> c.getCourseCode().equals("MAJ1000")));
    assertTrue(plan.courses().stream().anyMatch(c -> c.getCourseCode().equals("LAB1000")));
  }

  @Test
  void berenPlanNeverFulfillsMoreThanFiveCoreCourses() throws Exception {
    DegreeProgram.setup(write("beren-programs.txt", """
        === Beren Test Major ===
        name: Beren Test Major
        school: Stern
        degreeType: BA
        """));
    CoreRequirementProgram.setup(write("beren-cores.txt", """
        === Beren Test Core ===
        name: Beren Test Core
        school: Stern
        campus: Beren
        degreeType: BA
        programType: Core

        COURSE_LIST: Required courses
          COR1000
          COR1001
          COR1002
          COR1003
          COR1004
          COR1005
        """));
    resetIndexes();
    for (int i = 0; i < 6; i++) {
      add(courseAtCampus(10100 + i, "COR", "100" + i, "TBA", "", "Beren"));
    }
    User user = new User("Ari", false, "beren", "beren", "beren test major@stern", Set.of(), Set.of());
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();
    UserSession.interested = new HashMap<>();

    SchedulePlan plan = SchedulePlanner.build(user, "").getFirst();
    assertEquals(5, plan.coreCourses());
    assertEquals(5, plan.courses().stream().filter(c -> c.getCourseCode().startsWith("COR")).count());
  }

  @Test
  void berenPlanNeverExceedsThePlannerSafetyCeiling() throws Exception {
    DegreeProgram.setup(write("beren-heavy-programs.txt", """
        === Beren Heavy Major ===
        name: Beren Heavy Major
        school: Stern
        degreeType: BA

        COURSE_LIST: Required courses
          MAJ1000
          MAJ1001
          MAJ1002
        """));
    CoreRequirementProgram.setup(write("beren-heavy-cores.txt", """
        === Beren Heavy Core ===
        name: Beren Heavy Core
        school: Stern
        campus: Beren
        degreeType: BA
        programType: Core

        COURSE_LIST: Required courses
          COR1000
          COR1001
          COR1002
          COR1003
          COR1004
        """));
    resetIndexes();
    for (int i = 0; i < 3; i++) add(courseAtCampus(10200 + i, "MAJ", "100" + i, "TBA", "", "Beren"));
    for (int i = 0; i < 5; i++) add(courseAtCampus(10300 + i, "COR", "100" + i, "TBA", "", "Beren"));
    User user = new User("Ari", false, "beren", "beren", "beren heavy major@stern", Set.of(), Set.of());
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();
    UserSession.interested = new HashMap<>();

    SchedulePlan plan = SchedulePlanner.build(user, "").getFirst();
    assertTrue(plan.credits() <= 25.0);
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
    return courseWithRelations(crn, department, number, meeting, "", corequisite, "");
  }

  private static Course courseWithRelations(int crn, String department, String number, String meeting,
                                            String prerequisite, String corequisite, String flexiblePrerequisite) {
    return courseAtCampus(crn, department, number, meeting, corequisite, "Wilf", prerequisite,
        flexiblePrerequisite);
  }

  private static Course courseAtCampus(int crn, String department, String number, String meeting,
                                       String corequisite, String campus) {
    return courseAtCampus(crn, department, number, meeting, corequisite, campus, "", "");
  }

  private static Course courseAtCampus(int crn, String department, String number, String meeting,
                                       String corequisite, String campus, String prerequisite,
                                       String flexiblePrerequisite) {
    return new Course("\"" + crn + "\",\"" + number + "\",\"" + department
        + "\",\"A\",\"" + campus + "\",\"Test\",3,\"Professor\",0,20,20,0,0,0,\""
        + meeting + "\",\"\",\"\",\"" + prerequisite + "\",\"" + corequisite + "\",\""
        + flexiblePrerequisite + "\",\"\",\"\"");
  }
}
