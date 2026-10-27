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
    // LinkedHashMap preserves a stable save-file order while ensuring one
    // completed-course record exists for each stable course code.
    private Map<String, CompletedCourse> completedCourses;
    // These store DegreeProgram catalog keys (for example,
    // "computer science, ba@stern"), not informal student-entered names.
    private String major;
    private Set<String> minors;
    // MYP, BMP, IBC, JSS, or "undeclared" when not applicable/known.
    private String jewishStudiesTrack;

    public User(Path filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            this.name = reader.readLine();
            this.honors = Boolean.parseBoolean(reader.readLine());
            this.campus = reader.readLine();
            this.school = reader.readLine();
            List<String> remainingLines = reader.lines().toList();
            this.major = remainingLines.isEmpty() ? "undeclared" : remainingLines.get(0);
            this.minors = new HashSet<>();
            String minorsLine = remainingLines.size() >= 2 ? remainingLines.get(1) : "";
            if (!minorsLine.isBlank()) {
                for (String minor : minorsLine.split(";")) {
                    minors.add(minor.trim());
                }
            }
            this.completedCourses = new LinkedHashMap<>();
            this.jewishStudiesTrack = "undeclared";

            // Profiles from before degree-program information had only four
            // header lines followed immediately by course codes.  Detect that
            // shape rather than losing those completed courses.
            int courseStart = 2;
            if (!remainingLines.isEmpty() && looksLikeCourseCode(remainingLines.get(0))) {
                this.major = "undeclared";
                this.minors.clear();
                courseStart = 0;
            }
            if (courseStart < remainingLines.size() && remainingLines.get(courseStart).startsWith("TRACK:")) {
                setJewishStudiesTrack(remainingLines.get(courseStart).substring("TRACK:".length()));
                courseStart++;
            }
            for (int i = courseStart; i < remainingLines.size(); i++) {
                addSerializedCompletedCourse(remainingLines.get(i));
            }
        }
    }

    public User(String name, boolean honors, String campus, String school, String major,
                Set<String> minors, Set<String> completedCourseCodes) {
        this(name, honors, campus, school, major, minors, "undeclared", completedCourseCodes);
    }

    public User(String name, boolean honors, String campus, String school, String major,
                Set<String> minors, String jewishStudiesTrack, Set<String> completedCourseCodes) {
        this.name = name;
        this.honors = honors;
        this.campus = campus;
        this.school = school;
        this.major = major;
        setJewishStudiesTrack(jewishStudiesTrack);
        this.minors = new HashSet<>();
        for (String minor : minors) {
            this.minors.add(minor.trim());
        }
        this.completedCourses = new LinkedHashMap<>();
        for (String code : completedCourseCodes) {
            addCompletedClass(code);
        }
    }

    /** Backward-compatible constructor for callers without degree-program data. */
    public User(String name, boolean honors, String campus, String school, Set<String> completedCourseCodes) {
        this(name, honors, campus, school, "undeclared", new HashSet<>(), completedCourseCodes);
    }

    public User(){
        this.name = "guest";
        this.honors = false;
        this.campus = "wilf beren"; // Temporarily setting these like this so errors aren't thrown
        this.school = "yc syms beren";
        this.major = "undeclared";
        this.minors = new HashSet<>();
        this.jewishStudiesTrack = "undeclared";
        this.completedCourses = new LinkedHashMap<>();
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

    public void setMajor(String major) {
        this.major = major.trim();
    }

    public void addMinor(String minor) {
        minors.add(minor.trim());
    }

    public void removeMinor(String minor) {
        minors.remove(minor.trim());
    }

    public String getMajor() {
        return major;
    }

    public Set<String> getMinors() {
        return minors;
    }

    public String getJewishStudiesTrack() {
        return jewishStudiesTrack;
    }

    public void setJewishStudiesTrack(String jewishStudiesTrack) {
        this.jewishStudiesTrack = jewishStudiesTrack == null || jewishStudiesTrack.isBlank()
                ? "undeclared" : jewishStudiesTrack.trim().toUpperCase();
    }

    public Set<String> getCompletedCourses() {
        return Collections.unmodifiableSet(completedCourses.keySet());
    }

    /** Rich completed-course data for core-progress checks. */
    public Collection<CompletedCourse> getCompletedCourseRecords() {
        return Collections.unmodifiableCollection(completedCourses.values());
    }

    public void addCompletedClass(String courseCode){
        addCompletedCourse(new CompletedCourse(courseCode, null, null, Set.of()));
    }

    public void addCompletedClass(Course course) {
        addCompletedCourse(new CompletedCourse(course));
    }

    public void addCompletedCourse(CompletedCourse course) {
        completedCourses.put(course.getCourseCode(), course);
    }

    public void removeCompletedClass(String courseCode) {
        completedCourses.remove(normalizeCourseCode(courseCode));
    }

    // Trims whitespace and uppercases, so a course code entered/stored in any
    // casing still matches Course.getCourseCode()'s canonical format.
    private static String normalizeCourseCode(String courseCode) {
        return courseCode.trim().toUpperCase();
    }

    private static boolean looksLikeCourseCode(String value) {
        return value != null && value.trim().matches("[A-Za-z]+\\d+");
    }

    private void addSerializedCompletedCourse(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        // Older profile files stored only a course code on each line.
        if (!line.startsWith("COMPLETED|")) {
            addCompletedClass(line);
            return;
        }
        String[] fields = line.split("\\|", -1);
        if (fields.length != 5 || fields[1].isBlank()) {
            throw new IllegalArgumentException("Malformed completed-course record in profile: " + line);
        }
        Double credits = fields[2].isBlank() ? null : Double.parseDouble(fields[2]);
        Set<String> attributes = fields[4].isBlank() ? Set.of() : Set.of(fields[4].split(","));
        addCompletedCourse(new CompletedCourse(fields[1], credits, fields[3], attributes));
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
            writer.write(major);
            writer.newLine();
            writer.write(String.join(";", minors));
            writer.newLine();
            writer.write("TRACK:" + jewishStudiesTrack);
            writer.newLine();
            for (CompletedCourse course : completedCourses.values()) {
                writer.write("COMPLETED|" + course.getCourseCode() + "|"
                    + (course.getCredits() == null ? "" : course.getCredits()) + "|"
                    + (course.getDepartment() == null ? "" : course.getDepartment()) + "|"
                    + String.join(",", course.getAttributes()));
                writer.newLine();
            }
        }
    }
}
