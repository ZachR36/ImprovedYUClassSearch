import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SearchSetUpTest {
  @TempDir
  Path tempDir;

  @Test
  void newUserStoresTheCanonicalCatalogKeyForTheirMajor() throws IOException {
    Path catalog = tempDir.resolve("programs.txt");
    Files.writeString(catalog, """
        === Test Major ===
        name: Test Major
        school: YC
        degreeType: BA

        COURSE_LIST: Required
          TEST1000
        """);
    DegreeProgram.setup(catalog.toString());

    UserSetup.userSetUp(new Scanner("make\nAlice\nn\nwilf\nyc\nTest Major\n\nn\nn\n"));

    assertEquals("test major@yc", UserSession.currentUser.getMajor());
  }

  @Test
  void setupRejectsAMajorFromAnotherSchoolBeforeSavingIt() throws IOException {
    Path catalog = tempDir.resolve("programs.txt");
    Files.writeString(catalog, """
        === YC Major ===
        name: YC Major
        school: YC
        degreeType: BA

        COURSE_LIST: Required
          YC1000

        === Stern Major ===
        name: Stern Major
        school: Stern
        degreeType: BA

        COURSE_LIST: Required
          ST1000
        """);
    DegreeProgram.setup(catalog.toString());

    UserSetup.userSetUp(new Scanner("make\nAri\nn\nwilf\nyc\nStern Major\nYC Major\n\nn\nn\n"));

    assertEquals("yc major@yc", UserSession.currentUser.getMajor());
    assertTrue(!UserSession.currentUser.getMajors().contains("stern major@stern"));
  }

  @Test
  void setupRejectsCampusAndSchoolCombinationsThatDoNotBelongTogether() throws IOException {
    Path catalog = tempDir.resolve("programs.txt");
    Files.writeString(catalog, """
        === YC Major ===
        name: YC Major
        school: YC
        degreeType: BA

        COURSE_LIST: Required
          YC1000
        """);
    DegreeProgram.setup(catalog.toString());

    UserSetup.userSetUp(new Scanner("make\nAri\nn\nwilf\nberen\nyc\nYC Major\n\nn\nn\n"));

    assertEquals("wilf", UserSession.currentUser.getCampus());
    assertEquals("yc", UserSession.currentUser.getSchool());
  }

  @Test
  void berenProfilesMatchProgramsFiledUnderStern() throws IOException {
    Path catalog = tempDir.resolve("programs.txt");
    Files.writeString(catalog, """
        === Stern Major ===
        name: Stern Major
        school: Stern
        degreeType: BA

        COURSE_LIST: Required
          ST1000
        """);
    DegreeProgram.setup(catalog.toString());

    UserSetup.userSetUp(new Scanner("make\nLea\nn\nberen\nberen\nStern Major\n\nn\nn\n"));

    assertEquals("stern major@stern", UserSession.currentUser.getMajor());
  }

  @Test
  void loadingAnOlderProfileRepromptsForAMajorThatDoesNotMatchItsSchool() throws IOException {
    Path catalog = tempDir.resolve("programs.txt");
    Files.writeString(catalog, """
        === YC Major ===
        name: YC Major
        school: YC
        degreeType: BA

        COURSE_LIST: Required
          YC1000

        === Stern Major ===
        name: Stern Major
        school: Stern
        degreeType: BA

        COURSE_LIST: Required
          ST1000
        """);
    DegreeProgram.setup(catalog.toString());
    Path profile = tempDir.resolve("profile.txt");
    new User("Ari", false, "wilf", "yc", "stern major@stern", java.util.Set.of(), java.util.Set.of())
        .saveToFile(profile);

    UserSetup.userSetUp(new Scanner("pre\n" + profile + "\nYC Major\n"));

    assertEquals("yc major@yc", UserSession.currentUser.getMajor());
  }

  @Test
  void setupCanAddASecondMajorAndClassifiesMinorProgramsByTheirCatalogName() throws IOException {
    Path catalog = tempDir.resolve("programs.txt");
    Files.writeString(catalog, """
        === First Major ===
        name: First Major
        school: YC
        degreeType: BA

        COURSE_LIST: Required
          TEST1000

        === Second Major ===
        name: Second Major
        school: YC
        degreeType: BA

        COURSE_LIST: Required
          TEST2000

        === Test Minor ===
        name: Test Minor
        school: YC
        degreeType: Minor

        COURSE_LIST: Required
          TEST3000
        """);
    DegreeProgram.setup(catalog.toString());

    UserSetup.userSetUp(new Scanner("make\nAlice\nn\nwilf\nyc\nFirst Major\n\ny\nSecond Major\ny\nTest Minor\nn\nn\n"));

    assertEquals(java.util.Set.of("first major@yc", "second major@yc"), UserSession.currentUser.getMajors());
    assertEquals(java.util.Set.of("test minor@yc"), UserSession.currentUser.getMinors());
    assertEquals("first major@yc", UserSession.currentUser.getMajor());
  }

  @Test
  void completedCourseBatchRequiresTheEntireListToBeValidBeforeAddingAnyCourse() {
    UserSession.currentUser = new User();

    UserSetup.updateCompletedCourse(new Scanner("COMP1300 not-a-code\nCOMP1300 MATH1412\n1\nCOMP\n\n1\nMATH\n\n"));

    assertEquals(java.util.Set.of("COMP1300", "MATH1412"), UserSession.currentUser.getCompletedCourses());
    assertTrue(!UserSession.currentUser.getCompletedCourses().contains("NOT-A-CODE"));
  }

  @Test
  void missingCatalogCourseNamesTheFailedCodeAndAllowsACorrection() {
    UserSession.currentUser = new User();
    SearchEngine.mapByCRN = new HashMap<>();
    SearchEngine.mapByCourseCode = new HashMap<>();
    Course correctedCourse = new Course("\"10001\",\"1300\",\"COM\",\"A\",\"Wilf\",\"Test Course\",3,"
        + "\"Professor\",0,20,20,0,0,0,\"M}0900}1000}SCW}101\",\"\",\"\"");
    SearchEngine.mapByCRN.put(correctedCourse.getCRN(), correctedCourse);
    SearchEngine.mapByCourseCode.put("COM1300", new java.util.ArrayList<>(java.util.List.of(correctedCourse)));

    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    java.io.PrintStream originalOutput = System.out;
    try {
      System.setOut(new java.io.PrintStream(output));
      UserSetup.updateCompletedCourse(new Scanner("COM1310\nCOM1300\n1\n"));
    } finally {
      System.setOut(originalOutput);
    }

    assertTrue(output.toString().contains("COM1310 is not in this semester's catalog"));
    assertEquals(java.util.Set.of("COM1300"), UserSession.currentUser.getCompletedCourses());
  }

  @Test
  void missingCatalogCourseCanBeIntentionallyRecordedWithEnteredCredits() {
    UserSession.currentUser = new User();
    SearchEngine.mapByCRN = new HashMap<>();
    SearchEngine.mapByCourseCode = new HashMap<>();

    UserSetup.updateCompletedCourse(new Scanner("COM1310\n3\nCOM\n\n"));

    CompletedCourse saved = UserSession.currentUser.getCompletedCourseRecords().iterator().next();
    assertEquals("COM1310", saved.getCourseCode());
    assertEquals(3.0, saved.getCredits());
    assertEquals("COM", saved.getDepartment());
  }

  @Test
  void completedCourseSetupAsksWhichOfferingSuppliedTheCoursesAttributes() {
    UserSession.currentUser = new User();
    SearchEngine.mapByCourseCode = new HashMap<>();
    Course firstOffering = new Course("\"10001\",\"2920\",\"ENG\",\"A\",\"Wilf\",\"Topics\",3,"
        + "\"Professor One\",0,20,20,0,0,0,\"M}0900}1000}SCW}101\",\"CUOT||HONR\",\"\"");
    Course secondOffering = new Course("\"10002\",\"2920\",\"ENG\",\"B\",\"Wilf\",\"Topics\",3,"
        + "\"Professor Two\",0,20,20,0,0,0,\"T}0900}1000}SCW}102\",\"INTC||HONR\",\"\"");
    SearchEngine.mapByCourseCode.put("ENG2920", new java.util.ArrayList<>(java.util.List.of(firstOffering, secondOffering)));

    UserSetup.updateCompletedCourse(new Scanner("ENG2920\n2\n"));

    CompletedCourse saved = UserSession.currentUser.getCompletedCourseRecords().iterator().next();
    assertEquals(java.util.Set.of("INTC", "HONR"), saved.getAttributes());
  }

  @Test
  void completedCourseSetupLetsStudentsAddOrRemoveRepeatedCourseRecords() {
    UserSession.currentUser = new User();
    UserSession.currentUser.addCompletedCourse(new CompletedCourse("BIB1000", 2.0, "BIB", java.util.Set.of("HONR")));
    SearchEngine.mapByCourseCode = new HashMap<>();
    Course repeatedOffering = new Course("\"10001\",\"1000\",\"BIB\",\"A\",\"Wilf\",\"Topics\",2,"
        + "\"Professor\",0,20,20,0,0,0,\"M}0900}1000}SCW}101\",\"INTC\",\"\"");
    SearchEngine.mapByCourseCode.put("BIB1000", new java.util.ArrayList<>(java.util.List.of(repeatedOffering)));

    UserSetup.updateCompletedCourse(new Scanner("BIB1000\nadd\n1\n"));
    assertEquals(2, UserSession.currentUser.getCompletedCourseRecords("BIB1000").size());
    assertEquals(java.util.Set.of("INTC"), UserSession.currentUser.getCompletedCourseRecords("BIB1000").get(1).getAttributes());

    UserSetup.updateCompletedCourse(new Scanner("BIB1000\nremove\n2\n"));
    assertEquals(1, UserSession.currentUser.getCompletedCourseRecords("BIB1000").size());
    assertEquals(java.util.Set.of("HONR"), UserSession.currentUser.getCompletedCourseRecords("BIB1000").getFirst().getAttributes());
  }

  @Test
  void completedCourseSetupOffersACustomPriorSemesterVersionOfCurrentCourseCodes() {
    UserSession.currentUser = new User();
    SearchEngine.mapByCourseCode = new HashMap<>();
    Course currentOffering = new Course("\"10001\",\"1000\",\"BIB\",\"A\",\"Wilf\",\"Topics\",2,"
        + "\"Professor\",0,20,20,0,0,0,\"M}0900}1000}SCW}101\",\"CUOT\",\"\"");
    SearchEngine.mapByCourseCode.put("BIB1000", new java.util.ArrayList<>(java.util.List.of(currentOffering)));

    UserSetup.updateCompletedCourse(new Scanner("BIB1000\n2\n2.5\nBIB\nHONR\n"));

    CompletedCourse saved = UserSession.currentUser.getCompletedCourseRecords().getFirst();
    assertEquals(2.5, saved.getCredits());
    assertEquals(java.util.Set.of("HONR"), saved.getAttributes());
  }

  @Test
  void exitingDuringAUserUpdateRestoresTheOriginalProfile() throws Exception {
    UserSession.currentUser = new User("Original", false, "wilf", "yc", new java.util.HashSet<>());
    Method runUserCommand = Runner.class.getDeclaredMethod("runUserCommand", Scanner.class, boolean.class);
    runUserCommand.setAccessible(true);

    runUserCommand.invoke(null, new Scanner("update\nname\nexit\n"), false);

    assertEquals("Original", UserSession.currentUser.getName());
  }

  @Test
  void setupAutomaticallySelectsTheOnlyTrackTaggedForTheStudentsSchool() throws IOException {
    Path catalog = tempDir.resolve("programs.txt");
    Files.writeString(catalog, """
        === Finance Minor ===
        name: Finance Minor
        school: Syms
        degreeType: Minor

        TRACK_CHOICE: Requirements
          TRACK: Sy Syms students | school: Syms
            COURSE_LIST: Required
              SYMS1000
          TRACK: Stern/YC students | school: Stern|YC
            COURSE_LIST: Required
              YC1000
        """);
    DegreeProgram.setup(catalog.toString());

    UserSetup.userSetUp(new Scanner("make\nAri\nn\nwilf\nyc\nFinance Minor\n\nn\nn\n"));

    assertEquals("finance minor@syms", UserSession.currentUser.getMinors().iterator().next());
    assertEquals("Stern/YC students", UserSession.currentUser.getSelectedProgramTrack("finance minor@syms"));

    UserSetup.updateUserData(new Scanner("school\nsyms\n"));
    assertEquals("Sy Syms students", UserSession.currentUser.getSelectedProgramTrack("finance minor@syms"));
  }

  @Test
  void setupPromptsForAndStoresAnAmbiguousConcentration() throws IOException {
    Path catalog = tempDir.resolve("programs.txt");
    Files.writeString(catalog, """
        === Computer Science, BS ===
        name: Computer Science, BS
        school: YC
        degreeType: BS

        TRACK_CHOICE: Concentration
          TRACK: Systems
            COURSE_LIST: Required
              COMP1000
          TRACK: Theory
            COURSE_LIST: Required
              COMP2000
        """);
    DegreeProgram.setup(catalog.toString());

    UserSetup.userSetUp(new Scanner("make\nAri\nn\nwilf\nyc\nComputer Science, BS\n2\n\nn\nn\n"));

    assertEquals("Theory", UserSession.currentUser.getSelectedProgramTrack("computer science, bs@yc"));
  }
}
