package edu.yu.courseplanner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import edu.yu.courseplanner.catalog.CoreRequirementProgram;
import edu.yu.courseplanner.catalog.DegreeProgram;
import edu.yu.courseplanner.catalog.SearchEngine;
import edu.yu.courseplanner.model.Course;
import edu.yu.courseplanner.model.User;
import edu.yu.courseplanner.recommendation.RecommendationEngine;
import edu.yu.courseplanner.session.UserSession;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreRecommendationChoiceTest {
  @TempDir Path tempDir;

  @Test
  void recommendsAnUnmetCuotOrCowcChoice() throws Exception {
    DegreeProgram.setup(write("programs.txt", """
        === Test Major ===
        name: Test Major
        school: YC
        degreeType: BS
        """));
    CoreRequirementProgram.setup(write("cores.txt", """
        === Test Core ===
        name: Test Core
        school: YC
        degreeType: BS
        major: Test Major
        programType: Core

        CHOOSE_N: 1
          OPTION:
            ATTRIBUTE_COUNT: CUOT | count: 1
          OPTION:
            ATTRIBUTE_COUNT: COWC | count: 1
        """));
    SearchEngine.mapByCRN = new HashMap<>();
    SearchEngine.mapByCourseCode = new HashMap<>();
    Course cuot = new Course("\"10008\",\"2000\",\"HIS\",\"A\",\"Wilf\",\"Test\",3,\"Professor\",0,20,20,0,0,0,\"T}1500}1615}SCW}101\",\"CUOT\",\"\"");
    SearchEngine.mapByCRN.put(cuot.getCRN(), cuot);
    SearchEngine.mapByCourseCode.put(cuot.getCourseCode(), new ArrayList<>(Set.of(cuot)));
    User user = new User("Ari", false, "wilf", "yc", "test major@yc", Set.of(), Set.of());
    UserSession.currentUser = user;
    UserSession.bookmarked = new HashMap<>();

    assertEquals("HIS2000", RecommendationEngine.recommend(user).getFirst().course().getCourseCode());
  }

  private String write(String name, String contents) throws Exception {
    Path file = tempDir.resolve(name);
    Files.writeString(file, contents);
    return file.toString();
  }
}
