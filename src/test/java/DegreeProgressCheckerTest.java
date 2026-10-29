import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DegreeProgressCheckerTest {

    @TempDir
    Path tempDir;

    @Test
    void trackMetadataLoadsAndProgressUsesTheSavedTrackRatherThanGuessing() throws Exception {
        Path catalogFile = tempDir.resolve("programs.txt");
        Files.writeString(catalogFile, """
                === Test Program ===
                name: Test Program
                school: YC
                degreeType: BS

                TRACK_CHOICE: Concentration
                  TRACK: First track | school: Stern
                    COURSE_LIST: Required
                      FIRST1000
                  TRACK: Second track | school: YC
                    COURSE_LIST: Required
                      SECOND1000
                """);
        DegreeProgram program = DegreeProgram.loadCatalog(catalogFile.toString())
                .get(DegreeProgram.makeKey("Test Program", "YC"));

        DegreeProgram.TrackChoiceRequirement choice = program.getTrackChoices().getFirst();
        assertEquals(Set.of("Stern"), Set.copyOf(choice.getTracks().getFirst().getSchools()));
        assertEquals(Set.of("YC"), Set.copyOf(choice.getTracks().get(1).getSchools()));

        RequirementStatus savedSecondTrack = DegreeProgressChecker.check(program, Set.of("SECOND1000"), "Second track");
        assertTrue(savedSecondTrack.isSatisfied());

        RequirementStatus savedFirstTrack = DegreeProgressChecker.check(program, Set.of("SECOND1000"), "First track");
        assertFalse(savedFirstTrack.isSatisfied());
        assertTrue(savedFirstTrack.getRemainingCourseCodes().contains("FIRST1000"));

        RequirementStatus noSavedTrack = DegreeProgressChecker.check(program, Set.of("SECOND1000"));
        assertTrue(noSavedTrack.isSatisfied());
        assertTrue(noSavedTrack.getManualNotes().stream().anyMatch(note -> note.contains("no track is saved")));
    }

    @Test
    void repeatedCourseRecordsCountSeparatelyTowardRangeRequirements() throws Exception {
        Path catalogFile = tempDir.resolve("repeatable-program.txt");
        Files.writeString(catalogFile, """
                === Test Program ===
                name: Test Program
                school: YC
                degreeType: BA

                RANGE: BIB 1000-1999 | count: 2
                """);
        DegreeProgram program = DegreeProgram.loadCatalog(catalogFile.toString())
                .get(DegreeProgram.makeKey("Test Program", "YC"));
        java.util.List<CompletedCourse> completed = java.util.List.of(
                new CompletedCourse("BIB1000", 2.0, "BIB", Set.of()),
                new CompletedCourse("BIB1000", 2.0, "BIB", Set.of("HONR")));

        assertTrue(DegreeProgressChecker.check(program, completed, null).isSatisfied());
    }

    @Test
    void allocationIdentifiesTheSpecificRecordReservedForADegreeProgram() throws Exception {
        Path catalogFile = tempDir.resolve("allocated-program.txt");
        Files.writeString(catalogFile, """
                === Test Program ===
                name: Test Program
                school: YC
                degreeType: BA

                COURSE_LIST: Required
                  MAJ1000
                """);
        DegreeProgram program = DegreeProgram.loadCatalog(catalogFile.toString())
                .get(DegreeProgram.makeKey("Test Program", "YC"));
        CompletedCourse majorCourse = new CompletedCourse("MAJ1000", 3.0, "MAJ", Set.of());
        CompletedCourse elective = new CompletedCourse("ELEC1000", 3.0, "ELEC", Set.of());

        DegreeProgressChecker.AllocationResult result = DegreeProgressChecker.allocate(program,
                java.util.List.of(majorCourse, elective), null);

        assertTrue(result.status().isSatisfied());
        assertEquals(java.util.List.of(majorCourse), result.usedCourses());
    }

    @Test
    void allocationExplainsWhenAProgramChoiceHasMultipleValidOptions() throws Exception {
        Path catalogFile = tempDir.resolve("choice-program.txt");
        Files.writeString(catalogFile, """
                === Test Program ===
                name: Test Program
                school: YC
                degreeType: BA

                CHOOSE_N: 1
                  OPTION: MAJ1000
                  OPTION: MAJ2000
                """);
        DegreeProgram program = DegreeProgram.loadCatalog(catalogFile.toString())
                .get(DegreeProgram.makeKey("Test Program", "YC"));

        DegreeProgressChecker.AllocationResult result = DegreeProgressChecker.allocate(program,
                java.util.List.of(new CompletedCourse("MAJ1000", 3.0, "MAJ", Set.of()),
                        new CompletedCourse("MAJ2000", 3.0, "MAJ", Set.of())), null);

        assertTrue(result.status().isSatisfied());
        assertTrue(result.status().getAllocationNotes().stream().anyMatch(note -> note.contains("valid options")));
    }

    @Test
    void allocationDoesNotReuseOneRecordForTwoRequirementsInTheSameProgram() throws Exception {
        Path catalogFile = tempDir.resolve("non-reuse-program.txt");
        Files.writeString(catalogFile, """
                === Test Program ===
                name: Test Program
                school: YC
                degreeType: BA

                COURSE_LIST: Required
                  MAJ1000
                CHOOSE_N: 1
                  OPTION: MAJ1000
                """);
        DegreeProgram program = DegreeProgram.loadCatalog(catalogFile.toString())
                .get(DegreeProgram.makeKey("Test Program", "YC"));

        DegreeProgressChecker.AllocationResult result = DegreeProgressChecker.allocate(program,
                java.util.List.of(new CompletedCourse("MAJ1000", 3.0, "MAJ", Set.of())), null);

        assertFalse(result.status().isSatisfied());
    }
}
