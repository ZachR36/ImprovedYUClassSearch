import java.io.BufferedReader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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