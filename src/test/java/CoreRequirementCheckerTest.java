import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreRequirementCheckerTest {
    @TempDir
    Path tempDir;

    @Test
    void checkerUsesCourseCodesDepartmentsCreditsAndAttributeAliases() throws Exception {
        CoreRequirementProgram profile = load("""
            === Test Core ===
            name: Test Core
            school: YC
            programType: Core

            COURSE_LIST: Writing
              FYWR1020
            ATTRIBUTE_COUNT: WI | count: 1
            COURSE_COUNT_FROM_DEPARTMENTS: BIO|CHEM | count: 1
            CREDIT_COUNT: HEB | credits: 2 | label: Hebrew
            """);
        List<CompletedCourse> completed = List.of(
                new CompletedCourse("FYWR1020", 3.0, "ENG", Set.of("WRIN")),
                // CHEM is deliberately the second department in BIO|CHEM.
                new CompletedCourse("CHEM1010", 3.0, "CHEM", Set.of()),
                new CompletedCourse("HEB1001", 2.0, "HEB", Set.of()));

        RequirementStatus status = CoreRequirementChecker.check(profile, completed);

        assertTrue(status.isSatisfied());
    }

    @Test
    void manualAlternativeInsideChoiceDoesNotAutomaticallySatisfyIt() throws Exception {
        CoreRequirementProgram profile = load("""
            === Test Core ===
            name: Test Core
            school: YC
            programType: Core

            CHOOSE_N: 1
              OPTION:
                MANUAL_NOTE: Advisor must verify this option.
            """);

        RequirementStatus status = CoreRequirementChecker.check(profile, List.of());

        assertFalse(status.isSatisfied());
        assertTrue(status.getRemainingDescriptions().getFirst().contains("manual verification"));
    }

    @Test
    void checkerUsesTheSavedJewishStudiesTrackInsteadOfGuessingAClosestOne() throws Exception {
        CoreRequirementProgram profile = load("""
            === Test Core ===
            name: Test Core
            school: YC
            programType: Core

            TRACK_CHOICE: Jewish Studies Series
              TRACK: MYP/BMP/IBC
                COURSE_LIST:
                  MYP1000
              TRACK: JSS
                COURSE_LIST:
                  JSS1000
            """);
        List<CompletedCourse> completed = List.of(new CompletedCourse("JSS1000", 2.0, "JSS", Set.of()));

        assertTrue(CoreRequirementChecker.check(profile, completed, "JSS").isSatisfied());
        assertFalse(CoreRequirementChecker.check(profile, completed, "MYP").isSatisfied());
    }

    private CoreRequirementProgram load(String contents) throws Exception {
        Path file = tempDir.resolve("core.txt");
        Files.writeString(file, contents);
        Map<String, CoreRequirementProgram> catalog = CoreRequirementProgram.loadCatalog(file.toString());
        return catalog.get(CoreRequirementProgram.makeKey("Test Core"));
    }
}
