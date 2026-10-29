import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    // -------------------------------------------------------------------------
    // File constructor
    // -------------------------------------------------------------------------

    @TempDir
    Path tempDir;

    private Path writeUserFile(String name, boolean honors, String campus, String school, int... crns) throws IOException {
        Path file = tempDir.resolve("user.txt");
        try (BufferedWriter writer = Files.newBufferedWriter(file)) {
            writer.write(name);       writer.newLine();
            writer.write(String.valueOf(honors)); writer.newLine();
            writer.write(campus);     writer.newLine();
            writer.write(school);     writer.newLine();
            for (int crn : crns) {
                writer.write(String.valueOf(crn));
                writer.newLine();
            }
        }
        return file;
    }

    @Test
    void fileConstructor_setsNameCorrectly() throws IOException {
        Path file = writeUserFile("Alice", true, "Wilf", "YC");
        User user = new User(file);
        assertEquals("Alice", user.getName());
    }

    @Test
    void fileConstructor_setsHonorsCorrectly() throws IOException {
        Path file = writeUserFile("Alice", true, "Wilf", "YC");
        User user = new User(file);
        assertTrue(user.isHonors());
    }

    @Test
    void fileConstructor_setsHonorsFalseCorrectly() throws IOException {
        Path file = writeUserFile("Bob", false, "Beren", "Beren");
        User user = new User(file);
        assertFalse(user.isHonors());
    }

    @Test
    void fileConstructor_setsCampusCorrectly() throws IOException {
        Path file = writeUserFile("Alice", true, "Wilf", "YC");
        User user = new User(file);
        assertEquals("Wilf", user.getCampus());
    }

    @Test
    void fileConstructor_setsSchoolCorrectly() throws IOException {
        Path file = writeUserFile("Alice", true, "Wilf", "YC");
        User user = new User(file);
        assertEquals("YC", user.getSchool());
    }

    @Test
    void fileConstructor_completedCoursesEmptyWhenNoneInFile() throws IOException {
        Path file = writeUserFile("Alice", true, "Wilf", "YC");
        User user = new User(file);
        assertTrue(user.getCompletedCourses().isEmpty());
    }

    @Test
    void fileConstructor_completedCoursesNotNull() throws IOException {
        Path file = writeUserFile("Alice", true, "Wilf", "YC");
        User user = new User(file);
        assertNotNull(user.getCompletedCourses());
    }

    // -------------------------------------------------------------------------
    // Full constructor
    // -------------------------------------------------------------------------

    @Test
    void fullConstructor_setsNameCorrectly() {
        User user = new User("Charlie", false, "Beren", "Syms", new HashSet<>());
        assertEquals("Charlie", user.getName());
    }

    @Test
    void fullConstructor_setsHonorsCorrectly() {
        User user = new User("Charlie", true, "Wilf", "YC", new HashSet<>());
        assertTrue(user.isHonors());
    }

    @Test
    void fullConstructor_setsCampusCorrectly() {
        User user = new User("Charlie", false, "Beren", "Syms", new HashSet<>());
        assertEquals("Beren", user.getCampus());
    }

    @Test
    void fullConstructor_setsSchoolCorrectly() {
        User user = new User("Charlie", false, "Beren", "Syms", new HashSet<>());
        assertEquals("Syms", user.getSchool());
    }

    @Test
    void fullConstructor_completedCoursesEmptyWhenPassedEmptySet() {
        User user = new User("Charlie", false, "Beren", "Syms", new HashSet<>());
        assertTrue(user.getCompletedCourses().isEmpty());
    }

    @Test
    void fullConstructor_completedCoursesNotNull() {
        User user = new User("Charlie", false, "Beren", "Syms", new HashSet<>());
        assertNotNull(user.getCompletedCourses());
    }

    // -------------------------------------------------------------------------
    // Round-trip: saveToFile -> file constructor
    // -------------------------------------------------------------------------

    @Test
    void roundTrip_namePreserved() throws IOException {
        User original = new User("Diana", true, "Wilf", "YC", new HashSet<>());
        Path file = tempDir.resolve("roundtrip.txt");
        original.saveToFile(file);
        User loaded = new User(file);
        assertEquals(original.getName(), loaded.getName());
    }

    @Test
    void roundTrip_honorsPreserved() throws IOException {
        User original = new User("Diana", true, "Wilf", "YC", new HashSet<>());
        Path file = tempDir.resolve("roundtrip.txt");
        original.saveToFile(file);
        User loaded = new User(file);
        assertEquals(original.isHonors(), loaded.isHonors());
    }

    @Test
    void roundTrip_campusPreserved() throws IOException {
        User original = new User("Diana", true, "Wilf", "YC", new HashSet<>());
        Path file = tempDir.resolve("roundtrip.txt");
        original.saveToFile(file);
        User loaded = new User(file);
        assertEquals(original.getCampus(), loaded.getCampus());
    }

    @Test
    void roundTrip_schoolPreserved() throws IOException {
        User original = new User("Diana", true, "Wilf", "YC", new HashSet<>());
        Path file = tempDir.resolve("roundtrip.txt");
        original.saveToFile(file);
        User loaded = new User(file);
        assertEquals(original.getSchool(), loaded.getSchool());
    }

    @Test
    void roundTrip_completedCoursesEmptyWhenNoneAdded() throws IOException {
        User original = new User("Diana", true, "Wilf", "YC", new HashSet<>());
        Path file = tempDir.resolve("roundtrip.txt");
        original.saveToFile(file);
        User loaded = new User(file);
        assertEquals(original.getCompletedCourses(), loaded.getCompletedCourses());
    }

    @Test
    void roundTrip_multipleMajorsPreservesTheirOrderAndPrimaryMajor() throws IOException {
        User original = new User("Diana", true, "Wilf", "YC", "first major@yc", new HashSet<>(), new HashSet<>());
        original.addMajor("second major@yc");
        Path file = tempDir.resolve("multiple-majors.txt");
        original.saveToFile(file);

        User loaded = new User(file);
        assertEquals(List.of("first major@yc", "second major@yc"), new ArrayList<>(loaded.getMajors()));
        assertEquals("first major@yc", loaded.getMajor());
    }

    @Test
    void roundTrip_preservesSelectedProgramTracks() throws IOException {
        User original = new User("Diana", true, "Wilf", "YC", "computer science, bs@yc", new HashSet<>(), new HashSet<>());
        original.setSelectedProgramTrack("computer science, bs@yc", "Cybersecurity");
        Path file = tempDir.resolve("program-track.txt");
        original.saveToFile(file);

        User loaded = new User(file);
        assertEquals("Cybersecurity", loaded.getSelectedProgramTrack("computer science, bs@yc"));
    }

    @Test
    void roundTrip_preservesRepeatedCompletedCourseRecords() throws IOException {
        User original = new User("Diana", true, "Wilf", "YC", new HashSet<>());
        original.addCompletedCourse(new CompletedCourse("BIB1000", 2.0, "BIB", Set.of("HONR")));
        original.addCompletedCourse(new CompletedCourse("BIB1000", 2.0, "BIB", Set.of("INTC")));
        Path file = tempDir.resolve("repeated-course.txt");
        original.saveToFile(file);

        User loaded = new User(file);
        assertEquals(2, loaded.getCompletedCourseRecords("BIB1000").size());
        assertEquals(Set.of("BIB1000"), loaded.getCompletedCourses());
        assertEquals(Set.of("HONR"), loaded.getCompletedCourseRecords("BIB1000").getFirst().getAttributes());
        assertEquals(Set.of("INTC"), loaded.getCompletedCourseRecords("BIB1000").get(1).getAttributes());
    }

    // -------------------------------------------------------------------------
    // Guest constructor
    // -------------------------------------------------------------------------

    @Test
    void guestConstructor_nameIsGuest() {
        User guest = new User();
        assertEquals("guest", guest.getName());
    }

    @Test
    void guestConstructor_honorsIsFalse() {
        User guest = new User();
        assertFalse(guest.isHonors());
    }

    @Test
    void guestConstructor_campusIsNull() {
        User guest = new User();
        assertEquals("wilf beren",guest.getCampus());
    }

    @Test
    void guestConstructor_schoolIsNull() {
        User guest = new User();
        assertEquals("yc syms beren",guest.getSchool());
    }

    @Test
    void guestConstructor_completedCoursesIsEmpty() {
        User guest = new User();
        assertTrue(guest.getCompletedCourses().isEmpty());
    }

    @Test
    void guestConstructor_completedCoursesNotNull() {
        User guest = new User();
        assertNotNull(guest.getCompletedCourses());
    }
}
