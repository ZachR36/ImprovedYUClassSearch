import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CompletedCourseTest {
    @TempDir
    Path tempDir;

    @Test
    void completedCourseBuiltFromCatalogCourseRetainsCoreCheckingFields() {
        Course course = new Course("\"93089\",\"1630\",\"ART\",\"621\",\"Wilf\",\"American Architecture\",3,\"Glass\",0,15,15,0,5,5,\"F}1000}1200}TBA}\",\"HONR||INTC||WRIN\",\"Honors||INTC||Writing\"");

        CompletedCourse completed = new CompletedCourse(course);

        assertEquals("ART1630", completed.getCourseCode());
        assertEquals(3.0, completed.getCredits());
        assertEquals("ART", completed.getDepartment());
        assertEquals(Set.of("HONR", "INTC", "WRIN"), completed.getAttributes());
    }

    @Test
    void enrichedCompletedCoursesSurviveProfileRoundTrip() throws Exception {
        User original = new User("Ari", false, "Wilf", "YC", "undeclared", Set.of(), Set.of());
        original.addCompletedCourse(new CompletedCourse("ART1630", 3.0, "ART", Set.of("INTC", "WRIN")));
        Path profile = tempDir.resolve("profile.txt");
        original.saveToFile(profile);

        User reloaded = new User(profile);
        CompletedCourse completed = reloaded.getCompletedCourseRecords().iterator().next();
        assertEquals("ART1630", completed.getCourseCode());
        assertEquals(3.0, completed.getCredits());
        assertEquals("ART", completed.getDepartment());
        assertEquals(Set.of("INTC", "WRIN"), completed.getAttributes());
    }

    @Test
    void legacyCodeOnlyProfileStillLoadsWithUnknownOptionalDetails() throws Exception {
        Path profile = tempDir.resolve("legacy.txt");
        java.nio.file.Files.writeString(profile, "Ari\nfalse\nWilf\nYC\nCOMP1300\n");

        User reloaded = new User(profile);
        CompletedCourse completed = reloaded.getCompletedCourseRecords().iterator().next();
        assertTrue(reloaded.getCompletedCourses().contains("COMP1300"));
        assertNull(completed.getCredits());
        assertNull(completed.getDepartment());
        assertTrue(completed.getAttributes().isEmpty());
    }

    @Test
    void jewishStudiesTrackSurvivesProfileRoundTrip() throws Exception {
        User original = new User("Leah", false, "Beren", "beren", "undeclared", Set.of(), "JSS", Set.of());
        Path profile = tempDir.resolve("track-profile.txt");
        original.saveToFile(profile);

        assertEquals("JSS", new User(profile).getJewishStudiesTrack());
    }
}
