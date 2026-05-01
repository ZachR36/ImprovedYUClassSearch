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
    private Set<Integer> completedCourses;
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
                    completedCourses.add(Integer.parseInt(line));
                }
            }
        }
    }
    public User(String name, boolean honors, String campus, String school, Set<Integer> completedCourseCRNs) {
        this.name = name;
        this.honors = honors;
        this.campus = campus;
        this.school = school;
        this.completedCourses = completedCourseCRNs;
    }
    public User(){
        this.name = "guest";
        this.honors = false; // TODO: Hopefully this doesn't prevent honors courses from being displayed
        this.campus = null;
        this.school = null;
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

    public Set<Integer> getCompletedCourses() {
        return completedCourses;
    }
    public void addCompletedClass(Integer crn){
        completedCourses.add(crn);
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
            for (Integer course : completedCourses) {
                writer.write(Integer.toString(course));
                writer.newLine();
            }
        }
    }
}