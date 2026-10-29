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
    // A list preserves every completed offering, including repeatable courses
    // that legitimately share a department+number across semesters.
    private List<CompletedCourse> completedCourses;
    // These store DegreeProgram catalog keys (for example,
    // "computer science, ba@stern"), not informal student-entered names.
    // LinkedHashSet retains the first major as the deterministic selector for
    // a student's core profile while allowing double majors.
    private Set<String> majors;
    private Set<String> minors;
    // Catalog program key -> explicitly selected (or automatically resolved)
    // track name for that program.
    private Map<String, String> selectedProgramTracks;
    // MYP, BMP, IBC, JSS, or "undeclared" when not applicable/known.
    private String jewishStudiesTrack;

    public User(Path filePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            this.name = reader.readLine();
            this.honors = Boolean.parseBoolean(reader.readLine());
            this.campus = reader.readLine();
            this.school = reader.readLine();
            List<String> remainingLines = reader.lines().toList();
            this.majors = new LinkedHashSet<>();
            String majorsLine = remainingLines.isEmpty() ? "" : remainingLines.get(0);
            if (!majorsLine.isBlank() && !majorsLine.equalsIgnoreCase("undeclared")) {
                for (String major : majorsLine.split(";")) {
                    majors.add(major.trim());
                }
            }
            this.minors = new LinkedHashSet<>();
            String minorsLine = remainingLines.size() >= 2 ? remainingLines.get(1) : "";
            if (!minorsLine.isBlank()) {
                for (String minor : minorsLine.split(";")) {
                    minors.add(minor.trim());
                }
            }
            this.completedCourses = new ArrayList<>();
            this.selectedProgramTracks = new LinkedHashMap<>();
            this.jewishStudiesTrack = "undeclared";

            // Profiles from before degree-program information had only four
            // header lines followed immediately by course codes.  Detect that
            // shape rather than losing those completed courses.
            int courseStart = 2;
            if (!remainingLines.isEmpty() && looksLikeCourseCode(remainingLines.get(0))) {
                this.majors.clear();
                this.minors.clear();
                courseStart = 0;
            }
            if (courseStart < remainingLines.size() && remainingLines.get(courseStart).startsWith("TRACK:")) {
                setJewishStudiesTrack(remainingLines.get(courseStart).substring("TRACK:".length()));
                courseStart++;
            }
            for (int i = courseStart; i < remainingLines.size(); i++) {
                String line = remainingLines.get(i);
                if (line.startsWith("PROGRAM_TRACK|")) {
                    addSerializedProgramTrack(line);
                } else {
                    addSerializedCompletedCourse(line);
                }
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
        this.majors = new LinkedHashSet<>();
        if (major != null && !major.isBlank() && !major.equalsIgnoreCase("undeclared")) {
            this.majors.add(major.trim());
        }
        setJewishStudiesTrack(jewishStudiesTrack);
        this.minors = new LinkedHashSet<>();
        for (String minor : minors) {
            this.minors.add(minor.trim());
        }
        this.completedCourses = new ArrayList<>();
        this.selectedProgramTracks = new LinkedHashMap<>();
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
        this.campus = "wilf beren"; // Guest users may bookmark offerings from either campus.
        this.school = "yc syms beren";
        this.majors = new LinkedHashSet<>();
        this.minors = new LinkedHashSet<>();
        this.jewishStudiesTrack = "undeclared";
        this.completedCourses = new ArrayList<>();
        this.selectedProgramTracks = new LinkedHashMap<>();
    }

    /** Snapshot constructor used to restore a profile when an interactive
     * update is cancelled before it is finished. */
    public User(User other) {
        this.name = other.name;
        this.honors = other.honors;
        this.campus = other.campus;
        this.school = other.school;
        this.majors = new LinkedHashSet<>(other.majors);
        this.minors = new LinkedHashSet<>(other.minors);
        this.jewishStudiesTrack = other.jewishStudiesTrack;
        this.completedCourses = new ArrayList<>(other.completedCourses);
        this.selectedProgramTracks = new LinkedHashMap<>(other.selectedProgramTracks);
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
        majors.clear();
        addMajor(major);
    }

    public void addMajor(String major) {
        if (major != null && !major.isBlank() && !major.equalsIgnoreCase("undeclared")) {
            majors.add(major.trim());
        }
    }

    public void removeMajor(String major) {
        majors.remove(major.trim());
        removeSelectedProgramTrack(major.trim());
    }

    public void addMinor(String minor) {
        minors.add(minor.trim());
    }

    public void removeMinor(String minor) {
        minors.remove(minor.trim());
        removeSelectedProgramTrack(minor.trim());
    }

    public String getMajor() {
        return majors.isEmpty() ? "undeclared" : majors.iterator().next();
    }

    public Set<String> getMajors() {
        return Collections.unmodifiableSet(majors);
    }

    public Set<String> getMinors() {
        return minors;
    }

    public String getSelectedProgramTrack(String programKey) {
        return selectedProgramTracks.get(programKey);
    }

    public void setSelectedProgramTrack(String programKey, String trackName) {
        if (programKey == null || trackName == null || trackName.isBlank()) {
            return;
        }
        selectedProgramTracks.put(programKey, trackName.trim());
    }

    public void removeSelectedProgramTrack(String programKey) {
        selectedProgramTracks.remove(programKey);
    }

    public Map<String, String> getSelectedProgramTracks() {
        return Collections.unmodifiableMap(selectedProgramTracks);
    }

    public String getJewishStudiesTrack() {
        return jewishStudiesTrack;
    }

    public void setJewishStudiesTrack(String jewishStudiesTrack) {
        this.jewishStudiesTrack = jewishStudiesTrack == null || jewishStudiesTrack.isBlank()
                ? "undeclared" : jewishStudiesTrack.trim().toUpperCase();
    }

    public Set<String> getCompletedCourses() {
        Set<String> courseCodes = new LinkedHashSet<>();
        for (CompletedCourse course : completedCourses) {
            courseCodes.add(course.getCourseCode());
        }
        return Collections.unmodifiableSet(courseCodes);
    }

    /** Rich completed-course history, including legitimate repeated courses. */
    public List<CompletedCourse> getCompletedCourseRecords() {
        return Collections.unmodifiableList(completedCourses);
    }

    public List<CompletedCourse> getCompletedCourseRecords(String courseCode) {
        List<CompletedCourse> matches = new ArrayList<>();
        String normalizedCode = normalizeCourseCode(courseCode);
        for (CompletedCourse course : completedCourses) {
            if (course.getCourseCode().equals(normalizedCode)) {
                matches.add(course);
            }
        }
        return Collections.unmodifiableList(matches);
    }

    public void addCompletedClass(String courseCode){
        addCompletedCourse(new CompletedCourse(courseCode, null, null, Set.of()));
    }

    public void addCompletedClass(Course course) {
        addCompletedCourse(new CompletedCourse(course));
    }

    public void addCompletedCourse(CompletedCourse course) {
        completedCourses.add(course);
    }

    /** Removes the first matching record; interactive removal should use the
     * record-specific overload when repeated courses are present. */
    public void removeCompletedClass(String courseCode) {
        String normalizedCode = normalizeCourseCode(courseCode);
        for (int i = 0; i < completedCourses.size(); i++) {
            if (completedCourses.get(i).getCourseCode().equals(normalizedCode)) {
                completedCourses.remove(i);
                return;
            }
        }
    }

    public void removeCompletedCourse(CompletedCourse course) {
        completedCourses.remove(course);
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

    private void addSerializedProgramTrack(String line) {
        String[] fields = line.split("\\|", 3);
        if (fields.length != 3 || fields[1].isBlank() || fields[2].isBlank()) {
            throw new IllegalArgumentException("Malformed program-track record in profile: " + line);
        }
        setSelectedProgramTrack(fields[1], fields[2]);
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
            writer.write(majors.isEmpty() ? "undeclared" : String.join(";", majors));
            writer.newLine();
            writer.write(String.join(";", minors));
            writer.newLine();
            writer.write("TRACK:" + jewishStudiesTrack);
            writer.newLine();
            for (Map.Entry<String, String> selection : selectedProgramTracks.entrySet()) {
                writer.write("PROGRAM_TRACK|" + selection.getKey() + "|" + selection.getValue());
                writer.newLine();
            }
            for (CompletedCourse course : completedCourses) {
                writer.write("COMPLETED|" + course.getCourseCode() + "|"
                    + (course.getCredits() == null ? "" : course.getCredits()) + "|"
                    + (course.getDepartment() == null ? "" : course.getDepartment()) + "|"
                    + String.join(",", course.getAttributes()));
                writer.newLine();
            }
        }
    }
}
