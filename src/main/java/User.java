import java.io.BufferedReader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * In the runner class, the user can select to use a preexisting user object, or make a new User on the spot, or use a guest user.
 * If they're using a guest one, they can choose to set pieces of information for the user one at a time. And they can also decide
 * later to save the user that they've built during this use into a given path, so that they can log in to it next time.
 */
public class User {
    private String name;
    private boolean honors;
    private String campus; // Wilf or Beren
    private String school; // YC, Syms, Beren

    // Stores completed courses as course codes (department + course number,
    // e.g. "IDS1010" - matching Course.getCourseCode()'s format exactly),
    // NOT CRNs. A CRN identifies one specific section in one specific
    // semester, so it can't be checked against prerequisites in a later
    // semester's course data - the course code is what's stable and what
    // Course.prerequisitesSatisfied(...) actually checks against.
    private Set<String> completedCourses;

    public User(Path filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            this.name = reader.readLine();
            this.honors = Boolean.parseBoolean(reader.readLine());
            this.campus = reader.readLine();
            this.school = reader.readLine();

            this.completedCourses = new HashSet<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    completedCourses.add(normalizeCourseCode(line));
                }
            }
        }
    }

    public User(String name, boolean honors, String campus, String school, Set<String> completedCourseCodes) {
        this.name = name;
        this.honors = honors;
        this.campus = campus;
        this.school = school;
        this.completedCourses = new HashSet<>();
        for (String code : completedCourseCodes) {
            this.completedCourses.add(normalizeCourseCode(code));
        }
    }

    public User(){
        this.name = "guest";
        this.honors = false;
        this.campus = "wilf beren"; // Temporarily setting these like this so errors aren't thrown
        this.school = "yc syms beren";
        this.completedCourses = new HashSet<>();
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setHonors(boolean honors) {
        this.honors = honors;
    }

    public void setCampus(String campus) {
        this.campus = campus;
    }

    public void setSchool(String school) {
        this.school = school.toLowerCase();
    }

    public String getName(){
        return this.name;
    }

    public boolean isHonors() {
        return honors;
    }

    public String getCampus() {
        return campus;
    }

    public String getSchool() {
        return school;
    }

    public Set<String> getCompletedCourses() {
        return completedCourses;
    }

    public void addCompletedClass(String courseCode){
        completedCourses.add(normalizeCourseCode(courseCode));
    }

    public void removeCompletedClass(String courseCode) {
        completedCourses.remove(normalizeCourseCode(courseCode));
    }

    // Trims whitespace and uppercases, so a course code entered/stored in any
    // casing still matches Course.getCourseCode()'s canonical format.
    private static String normalizeCourseCode(String courseCode) {
        return courseCode.trim().toUpperCase();
    }

    public void saveToFile(Path filePath) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            writer.write(name);
            writer.newLine();
            writer.write(Boolean.toString(honors));
            writer.newLine();
            writer.write(campus);
            writer.newLine();
            writer.write(school);
            writer.newLine();
            for (String courseCode : completedCourses) {
                writer.write(courseCode);
                writer.newLine();
            }
        }
    }
}