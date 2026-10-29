import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Scanner;

/** Creates, loads, saves, and updates user profiles. */
public class UserSetup {
  static void userChange(Scanner scan) throws IOException {
    String choice = "";
    boolean validChoice = false;
    while (!validChoice) {
      System.out.println(
          "Do you want to make an update to this User, save this one, or switch users? (\"update\",\"save\",\"switch\",\"exit\")");
      choice = scan.nextLine().trim();
      if (choice.equalsIgnoreCase("update")) {
        updateUserData(scan);
        validChoice = true;
      } else if (choice.equalsIgnoreCase("save")) {
        System.out.println("Give the path where you want to write the file with the user data: ");
        Path path = Path.of(ConsoleInput.read(scan));
        if (validateGivenPath(path)) {
          UserSession.currentUser.saveToFile(path);
          validChoice = true;
        }
      } else if (choice.equalsIgnoreCase("switch")) {
        userSetUp(scan);
        validChoice = true;
      } else if (choice.equalsIgnoreCase("exit")) {
        return;
      } else {
        System.out.println("Invalid choice, try again");
      }
    }
  }

  static void updateUserData(Scanner scan) {
    String choice = "";
    boolean validGiven = false;
    while (!validGiven) {
      System.out.println("What attribute do you want to update: " +
              "(\"name\", \"honors\", \"campus\", \"school\", \"completed\", \"degree program\", \"jewish track\")");
      choice = scan.nextLine().trim();
      switch (choice.toLowerCase()) {
        case "name" -> {
          System.out.println("Provide a user name: ");
          UserSession.currentUser.setName(ConsoleInput.read(scan));
          validGiven = true;
        }
        case "honors" -> {
          System.out.println("Are you in honors? (Y/N): ");
          validGiven = true;
          UserSession.currentUser.setHonors(ConsoleInput.read(scan).equalsIgnoreCase("y"));
        }
        case "campus" -> {
          System.out.println("What campus are you on? (Wilf/Beren): ");
          String campus = ConsoleInput.read(scan);
          switch (campus.toLowerCase()) {
            case "wilf", "beren" -> {
              if (campusAndSchoolMatch(campus, UserSession.currentUser.getSchool())) {
                validGiven = true;
                UserSession.currentUser.setCampus(campus);
              } else {
                System.out.println("Wilf campus is only compatible with YC or Syms; Beren campus is only compatible with Beren or Syms.");
              }
            }
            default -> System.out.println("Invalid campus name");
          }
        }
        case "school" -> {
          System.out.println("What school are you in? (YC, Syms, Beren): ");
          String school = ConsoleInput.read(scan);
          switch (school.toLowerCase()) {
            case "yc", "syms", "beren" -> {
              if (!campusAndSchoolMatch(UserSession.currentUser.getCampus(), school)) {
                System.out.println("Wilf campus is only compatible with YC or Syms; Beren campus is only compatible with Beren or Syms.");
              } else if (!savedMajorsMatchSchool(school)) {
                System.out.println("Change or remove your current major before changing to that school.");
              } else {
                validGiven = true;
                UserSession.currentUser.setSchool(school);
                refreshSchoolBasedProgramTracks(scan);
              }
            }
            default -> System.out.println("Invalid school name");
          }
        }
        case "degree program", "major", "minor" -> validGiven = updateDegreeProgram(scan);
        case "completed" -> validGiven = updateCompletedCourse(scan);
        case "jewish track" -> {
          updateJewishStudiesTrack(scan);
          validGiven = true;
        }
        case "exit" -> {
          return;
        }
        default -> System.out.println("Invalid choice");
      }
    }
  }
  private static boolean updateDegreeProgram(Scanner scan) {
    DegreeProgram program = promptForProgram(scan, UserSession.currentUser.getSchool(), "degree program", false);
    String key = program.getCatalogKey();
    boolean isMinor = program.getName().toLowerCase().contains("minor");
    Set<String> programs = isMinor ? UserSession.currentUser.getMinors() : UserSession.currentUser.getMajors();
    if (programs.contains(key)) {
      if (isMinor) {
        UserSession.currentUser.removeMinor(key);
      } else {
        UserSession.currentUser.removeMajor(key);
      }
      System.out.println("Removed " + program.getDisplayLabel());
    } else {
      addDegreeProgram(scan, program);
      System.out.println("Added " + program.getDisplayLabel());
    }
    return true;
  }

  /** Toggles one or more completed courses. The entire whitespace-separated
   * batch must contain valid course-code-shaped entries before any profile
   * change is made. */
  static boolean updateCompletedCourse(Scanner scan) {
    List<String> courseCodes = promptForValidCompletedCourseCodes(scan);
    for (String courseCode : courseCodes) {
      if (!UserSession.currentUser.getCompletedCourseRecords(courseCode).isEmpty()
          && promptToRemoveOrAddRepeatedCourse(scan, courseCode)) {
        continue;
      }
      List<Course> matchingCourses = SearchEngine.searchByCourseCode(courseCode);
      if (!matchingCourses.isEmpty()) {
        addCompletedCatalogCourse(scan, courseCode, matchingCourses);
      } else {
        resolveNonCatalogCompletedCourse(scan, courseCode);
      }
    }
    return true;
  }

  private static List<String> promptForValidCompletedCourseCodes(Scanner scan) {
    while (true) {
      System.out.println("Give course codes to mark completed (or un-mark if already completed), "
          + "separated by spaces — e.g. COMP1300 MATH1412: ");
      String input = ConsoleInput.read(scan);
      if (input.isBlank()) {
        System.out.println("Enter at least one course code.");
        continue;
      }
      List<String> codes = new java.util.ArrayList<>();
      boolean valid = true;
      for (String enteredCode : input.split("\\s+")) {
        String courseCode = enteredCode.toUpperCase();
        if (!courseCode.matches("[A-Z]+\\d+")) {
          valid = false;
          break;
        }
        codes.add(courseCode);
      }
      if (valid) {
        return List.copyOf(codes);
      }
      System.out.println("Every entry must be a course code made of letters followed by digits. "
          + "Please re-enter the complete list.");
    }
  }

  /** Returns true after removing an existing record; false means the student
   * chose to add another completion of this repeatable course. */
  private static boolean promptToRemoveOrAddRepeatedCourse(Scanner scan, String courseCode) {
    List<CompletedCourse> existing = UserSession.currentUser.getCompletedCourseRecords(courseCode);
    while (true) {
      System.out.println("You already have " + existing.size() + " completed record(s) for " + courseCode
          + ". Enter \"remove\" to remove one, or \"add\" to record another completion: ");
      String choice = ConsoleInput.read(scan);
      if (choice.equalsIgnoreCase("add")) {
        return false;
      }
      if (choice.equalsIgnoreCase("remove")) {
        CompletedCourse record = promptForCompletedRecordToRemove(scan, courseCode, existing);
        UserSession.currentUser.removeCompletedCourse(record);
        System.out.println("Removed one " + courseCode + " completed-course record.");
        return true;
      }
      System.out.println("Enter \"remove\" or \"add\".");
    }
  }

  private static CompletedCourse promptForCompletedRecordToRemove(Scanner scan, String courseCode,
                                                                   List<CompletedCourse> records) {
    if (records.size() == 1) {
      return records.getFirst();
    }
    while (true) {
      System.out.println("Which " + courseCode + " record do you want to remove?");
      for (int i = 0; i < records.size(); i++) {
        CompletedCourse record = records.get(i);
        System.out.println("  " + (i + 1) + ") " + formatCompletedRecord(record));
      }
      System.out.println("Enter a number, or type exit to cancel: ");
      try {
        int index = Integer.parseInt(ConsoleInput.read(scan)) - 1;
        if (index >= 0 && index < records.size()) {
          return records.get(index);
        }
      } catch (NumberFormatException ignored) {
        // Repeat with the concise validation message below.
      }
      System.out.println("Enter the number of one of the listed records.");
    }
  }

  private static String formatCompletedRecord(CompletedCourse record) {
    String credits = record.getCredits() == null ? "unknown credits" : record.getCredits() + " credits";
    String department = record.getDepartment() == null ? "unknown department" : record.getDepartment();
    String attributes = record.getAttributes().isEmpty() ? "no attributes"
        : "attributes: " + String.join(", ", record.getAttributes());
    return credits + ", " + department + ", " + attributes;
  }

  /** Gives a missing catalog course an explicit correction path before asking
   * for manual prior-semester information. */
  private static void resolveNonCatalogCompletedCourse(Scanner scan, String missingCourseCode) {
    String courseCode = missingCourseCode;
    while (true) {
      System.out.println(courseCode + " is not in this semester's catalog. If that was a typo, enter the "
          + "correct course code to try again. If " + courseCode + " was intentionally taken outside this "
          + "catalog, enter its credits as a number (for example, 3 or 0.5), or press Enter if unknown: ");
      String response = ConsoleInput.read(scan);
      if (response.isBlank() || isCreditAmount(response)) {
        addPriorSemesterCompletedCourse(scan, courseCode, response);
        return;
      }
      String correctedCode = response.toUpperCase();
      if (!correctedCode.matches("[A-Z]+\\d+")) {
        System.out.println("Enter a course code made of letters followed by digits, or a numeric credit amount.");
        continue;
      }
      List<Course> correctedCourses = SearchEngine.searchByCourseCode(correctedCode);
      if (!correctedCourses.isEmpty()) {
        addCompletedCatalogCourse(scan, correctedCode, correctedCourses);
        return;
      }
      courseCode = correctedCode;
    }
  }

  private static boolean isCreditAmount(String value) {
    try {
      Double.parseDouble(value);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  /** Confirms every catalog-matching completed course, including a custom
   * option for prior-semester offerings with different attributes. */
  private static void addCompletedCatalogCourse(Scanner scan, String courseCode, List<Course> courses) {
    Course selectedCourse = promptForCompletedCourseOffering(scan, courseCode, courses);
    if (selectedCourse == null) {
      addPriorSemesterCompletedCourse(scan, courseCode, promptForPriorSemesterCredits(scan));
      return;
    }
    UserSession.currentUser.addCompletedClass(selectedCourse);
    System.out.println("Added " + courseCode + " to completed courses with its catalog credits, department, and attributes");
  }

  private static Course promptForCompletedCourseOffering(Scanner scan, String courseCode, List<Course> courses) {
    while (true) {
      CoursePrinter.courseChoicePrinting(courseCode, courses);
      try {
        int index = Integer.parseInt(ConsoleInput.read(scan)) - 1;
        if (index >= 0 && index < courses.size()) {
          return courses.get(index);
        }
        if (index == courses.size()) {
          return null;
        }
      } catch (NumberFormatException ignored) {
        // Repeat the choices with a precise validation message below.
      }
      System.out.println("Enter the number of one of the listed offerings.");
    }
  }

  private static String promptForPriorSemesterCredits(Scanner scan) {
    while (true) {
      System.out.println("Credits for this prior-semester offering (leave blank if unknown): ");
      String creditInput = ConsoleInput.read(scan);
      if (creditInput.isBlank() || isCreditAmount(creditInput)) {
        return creditInput;
      }
      System.out.println("Enter a numeric credit amount, such as 3 or 0.5, or leave it blank if unknown.");
    }
  }

  private static void addPriorSemesterCompletedCourse(Scanner scan, String courseCode, String creditInput) {
    Double credits = creditInput.isBlank() ? null : Double.parseDouble(creditInput);
    System.out.println("Department/subject abbreviation (leave blank if unknown): ");
    String department = ConsoleInput.read(scan);
    System.out.println("Attributes, separated by spaces (leave blank if none or unknown): ");
    String attributeInput = ConsoleInput.read(scan);
    Set<String> attributes = attributeInput.isBlank() ? Set.of() : Set.of(attributeInput.split("\\s+"));
    UserSession.currentUser.addCompletedCourse(new CompletedCourse(courseCode, credits, department, attributes));
    System.out.println("Added " + courseCode + " to completed courses.");
  }

  /** Resolves a student-entered program name to exactly one catalog entry.
   *  The returned program's catalog key is what gets saved in the User profile. */
  private static DegreeProgram promptForProgram(Scanner scan, String studentSchool, String kind,
                                                 boolean allowBlank) {
    while (true) {
      System.out.println("What is your " + kind + "? (type \"list\" to see exact program names, "
          + "optionally followed by a search term): ");
      String input = ConsoleInput.read(scan);
      if (input.equalsIgnoreCase("list") || input.toLowerCase().startsWith("list ")) {
        String query = input.length() > 4 ? input.substring(4).trim() : "";
        printProgramList(query);
        continue;
      }
      if (allowBlank && input.isBlank()) {
        return null;
      }
      if (input.isBlank()) {
        System.out.println(kind.substring(0, 1).toUpperCase() + kind.substring(1) + " can't be blank.");
        continue;
      }
      DegreeProgram selected = resolveEnteredProgram(scan, input, studentSchool, kind);
      if (selected != null) {
        if (majorMatchesSchool(selected, studentSchool)) {
          return selected;
        }
        System.out.println(selected.getDisplayLabel() + " is not a major offered by your saved school. "
            + "Choose a major for " + displaySchool(studentSchool) + ".");
      }
    }
  }

  private static DegreeProgram resolveEnteredProgram(Scanner scan, String input, String studentSchool, String kind) {
    Map<String, DegreeProgram> catalog = DegreeProgram.getCatalog();
    if (catalog == null) {
      System.out.println("Degree program data was not loaded.");
      return null;
    }
    DegreeProgram directKeyMatch = DegreeProgram.findByCatalogKey(catalog, input);
    if (directKeyMatch != null) {
      return directKeyMatch;
    }
    DegreeProgram schoolMatch = DegreeProgram.findForStudent(catalog, input, studentSchool);
    if (schoolMatch != null) {
      return schoolMatch;
    }
    List<DegreeProgram> matches = DegreeProgram.findMatches(catalog, input);
    if (matches.isEmpty()) {
      System.out.println("No programs match " + kind + " \"" + input + "\". Try again or type \"list\".");
      return null;
    }
    if (matches.size() == 1) {
      return matches.get(0);
    }
    System.out.println("Multiple programs match " + kind + " \"" + input + "\" - which did you mean?");
    for (int i = 0; i < matches.size(); i++) {
      System.out.println("  " + (i + 1) + ") " + matches.get(i).getDisplayLabel());
    }
    System.out.println("Enter a number, or anything else to search again: ");
    try {
      int index = Integer.parseInt(ConsoleInput.read(scan)) - 1;
      if (index >= 0 && index < matches.size()) {
        return matches.get(index);
      }
    } catch (NumberFormatException ignored) {
      // The outer prompt supplies another chance to search or list programs.
    }
    return null;
  }

  /** Prints every catalog program name (optionally filtered by a substring
   *  search), so a student who doesn't know the exact on-file spelling of
   *  their major/minor can look it up. */
  static void printProgramList(String query) {
    Map<String, DegreeProgram> catalog = DegreeProgram.getCatalog();
    if (catalog == null) {
      System.out.println("Degree program data was not loaded.");
      return;
    }
    if (query.isBlank()) {
      System.out.println("All programs on file:");
      for (String label : DegreeProgram.listAllPrograms(catalog)) {
        System.out.println("  " + label);
      }
      return;
    }
    List<DegreeProgram> matches = DegreeProgram.findMatches(catalog, query);
    if (matches.isEmpty()) {
      System.out.println("No programs match \"" + query + "\".");
      return;
    }
    System.out.println("Programs matching \"" + query + "\":");
    for (DegreeProgram program : matches) {
      System.out.println("  " + program.getDisplayLabel());
    }
  }

  static boolean validateGivenPath(Path path) {
    File file = path.toFile();
    File parentDir = file.getParentFile();
    if (parentDir != null && !parentDir.exists()) {
      System.out.println("Error: the directory does not exist.");
      return false;
    } else if (parentDir != null && !parentDir.canWrite()) {
      System.out.println("Error: you don't have permission to write there.");
      return false;
    } else if (file.exists() && !file.canWrite()) {
      System.out.println("Error: a file already exists there and can't be overwritten.");
      return false;
    }
    return true;
  }

  static void userSetUp(Scanner scan) {
    String choice = "";
    boolean finished = false;
    while (!finished) {
      System.out.println("Do you want to use a pre-existing User object, create a new one, or use a guest one?"
          + " (\"pre\",\"make\", or \"guest\"): ");
      choice = scan.nextLine().trim();
      switch (choice.toLowerCase()) {
        case "pre" -> {
          System.out.println("Give the Path to the file with the user info: ");
          Path path = Path.of(ConsoleInput.read(scan));
          if (validateGivenPath(path)) {
            try {
              UserSession.currentUser = new User(path);
              canonicalizeLoadedProgramSelections(scan);
              finished = true;
            } catch (IOException e) {
              System.out.println("Failed to read user file");
            }
          }
        }
        case "make" -> {
          UserSession.currentUser = new User();
          System.out.println("Provide a user name: ");
          UserSession.currentUser.setName(ConsoleInput.read(scan));
          System.out.println("Are you in honors? (Y/N): ");
          UserSession.currentUser.setHonors(ConsoleInput.read(scan).equalsIgnoreCase("y"));
          UserSession.currentUser.setCampus(promptForCampus(scan));
          UserSession.currentUser.setSchool(promptForCompatibleSchool(scan, UserSession.currentUser.getCampus()));
          DegreeProgram major = promptForProgram(scan, UserSession.currentUser.getSchool(), "degree program", false);
          addDegreeProgram(scan, major);
          updateJewishStudiesTrack(scan);
          addInitialDegreePrograms(scan);
          addInitialCompletedCourses(scan);
          finished = true;
        }
        case "guest" -> {
          UserSession.currentUser = new User();
          finished = true;
        }
        case "exit" -> {
          return;
        }
        default -> System.out.println("Invalid choice");
      }
    }
  }

  private static void addInitialDegreePrograms(Scanner scan) {
    System.out.println("Do you want to add another degree program now? (Y/N): ");
    while (ConsoleInput.read(scan).equalsIgnoreCase("y")) {
      DegreeProgram program = promptForProgram(scan, UserSession.currentUser.getSchool(), "degree program", false);
      addDegreeProgram(scan, program);
      System.out.println("Add another degree program? (Y/N): ");
    }
  }

  private static void addInitialCompletedCourses(Scanner scan) {
    System.out.println("Do you want to add completed courses now? (Y/N): ");
    while (ConsoleInput.read(scan).equalsIgnoreCase("y")) {
      updateCompletedCourse(scan);
      System.out.println("Do you want to add more completed courses? (Y/N): ");
    }
  }

  /** Uses the catalog display name rather than a separate user prompt to
   * classify a selected program as a major or minor. */
  private static void addDegreeProgram(Scanner scan, DegreeProgram program) {
    if (program.getName().toLowerCase().contains("minor")) {
      UserSession.currentUser.addMinor(program.getCatalogKey());
    } else {
      UserSession.currentUser.addMajor(program.getCatalogKey());
    }
    configureProgramTrack(scan, program);
  }

  /** Selects a program track once. Tracks tagged for exactly one matching
   * school are selected automatically; concentrations are asked explicitly. */
  private static void configureProgramTrack(Scanner scan, DegreeProgram program) {
    if (program.getTrackChoices().isEmpty() || UserSession.currentUser.getSelectedProgramTrack(program.getCatalogKey()) != null) {
      return;
    }
    // Current requirements data has one TRACK_CHOICE per program. The saved
    // profile map likewise stores one selection per program.
    for (DegreeProgram.TrackChoiceRequirement choice : program.getTrackChoices()) {
      List<DegreeProgram.Track> schoolMatches = new java.util.ArrayList<>();
      for (DegreeProgram.Track track : choice.getTracks()) {
        if (!track.getSchools().isEmpty() && trackMatchesSchool(track, UserSession.currentUser.getSchool())) {
          schoolMatches.add(track);
        }
      }
      if (schoolMatches.size() == 1) {
        UserSession.currentUser.setSelectedProgramTrack(program.getCatalogKey(), schoolMatches.getFirst().getName());
        System.out.println("Selected " + schoolMatches.getFirst().getName() + " for " + program.getName()
            + " based on your school.");
      } else {
        promptForProgramTrack(scan, program, choice);
      }
    }
  }

  private static boolean trackMatchesSchool(DegreeProgram.Track track, String userSchool) {
    String normalizedUserSchool = userSchool != null && userSchool.equalsIgnoreCase("beren") ? "stern" : userSchool;
    for (String trackSchool : track.getSchools()) {
      if (trackSchool.trim().equalsIgnoreCase(normalizedUserSchool)) {
        return true;
      }
    }
    return false;
  }

  /** Re-selects only tracks whose data explicitly depends on the home school.
   * A concentration without school metadata remains the student's own choice. */
  private static void refreshSchoolBasedProgramTracks(Scanner scan) {
    Map<String, DegreeProgram> catalog = DegreeProgram.getCatalog();
    if (catalog == null) {
      return;
    }
    for (String programKey : allProgramKeys(UserSession.currentUser)) {
      DegreeProgram program = DegreeProgram.findByCatalogKey(catalog, programKey);
      if (program == null || !hasSchoolBasedTrack(program)) {
        continue;
      }
      UserSession.currentUser.removeSelectedProgramTrack(programKey);
      configureProgramTrack(scan, program);
    }
  }

  private static boolean hasSchoolBasedTrack(DegreeProgram program) {
    for (DegreeProgram.TrackChoiceRequirement choice : program.getTrackChoices()) {
      for (DegreeProgram.Track track : choice.getTracks()) {
        if (!track.getSchools().isEmpty()) {
          return true;
        }
      }
    }
    return false;
  }

  private static void promptForProgramTrack(Scanner scan, DegreeProgram program,
                                            DegreeProgram.TrackChoiceRequirement choice) {
    while (true) {
      System.out.println("Select your " + choice.getLabel() + " for " + program.getDisplayLabel() + ":");
      for (int i = 0; i < choice.getTracks().size(); i++) {
        System.out.println("  " + (i + 1) + ") " + choice.getTracks().get(i).getName());
      }
      System.out.println("Enter a number: ");
      try {
        int selected = Integer.parseInt(ConsoleInput.read(scan)) - 1;
        if (selected >= 0 && selected < choice.getTracks().size()) {
          UserSession.currentUser.setSelectedProgramTrack(program.getCatalogKey(),
              choice.getTracks().get(selected).getName());
          return;
        }
      } catch (NumberFormatException ignored) {
        // Repeat the same concise choice list.
      }
      System.out.println("Invalid track selection.");
    }
  }

  private static void updateJewishStudiesTrack(Scanner scan) {
    System.out.println("Jewish studies track (MYP, BMP, IBC, JSS, or leave blank if not applicable/unknown): ");
    String track = ConsoleInput.read(scan);
    if (!track.isBlank() && !track.equalsIgnoreCase("myp") && !track.equalsIgnoreCase("bmp")
        && !track.equalsIgnoreCase("ibc") && !track.equalsIgnoreCase("jss")) {
      System.out.println("Unrecognized track; saved as undeclared.");
      track = "";
    }
    UserSession.currentUser.setJewishStudiesTrack(track);
  }

  /** Converts profiles saved before canonical catalog keys were introduced.
   *  This happens during profile loading, never later during a progress check. */
  private static void canonicalizeLoadedProgramSelections(Scanner scan) {
    User user = UserSession.currentUser;
    Map<String, DegreeProgram> catalog = DegreeProgram.getCatalog();
    if (catalog == null) {
      return;
    }
    for (String majorKey : List.copyOf(user.getMajors())) {
      DegreeProgram savedMajor = DegreeProgram.findByCatalogKey(catalog, majorKey);
      if (savedMajor == null || !majorMatchesSchool(savedMajor, user.getSchool())) {
        System.out.println("Your saved degree program " + majorKey
            + " needs to be matched to the current catalog and school.");
        DegreeProgram major = promptForProgram(scan, user.getSchool(), "degree program", false);
        user.removeMajor(majorKey);
        addDegreeProgram(scan, major);
      }
    }
    for (String minorKey : List.copyOf(user.getMinors())) {
      if (DegreeProgram.findByCatalogKey(catalog, minorKey) == null) {
        System.out.println("Your saved degree program \"" + minorKey + "\" needs to be matched to the current catalog.");
        DegreeProgram minor = promptForProgram(scan, user.getSchool(), "degree program", false);
        user.removeMinor(minorKey);
        addDegreeProgram(scan, minor);
      }
    }
    for (String programKey : allProgramKeys(user)) {
      DegreeProgram program = DegreeProgram.findByCatalogKey(catalog, programKey);
      if (program != null) {
        configureProgramTrack(scan, program);
      }
    }
  }

  private static List<String> allProgramKeys(User user) {
    List<String> keys = new java.util.ArrayList<>(user.getMajors());
    keys.addAll(user.getMinors());
    return keys;
  }

  private static String promptForCampus(Scanner scan) {
    while (true) {
      System.out.println("What campus are you on? (Wilf/Beren): ");
      String campus = ConsoleInput.read(scan);
      if (campus.equalsIgnoreCase("wilf") || campus.equalsIgnoreCase("beren")) {
        return campus;
      }
      System.out.println("Invalid campus name.");
    }
  }

  private static String promptForCompatibleSchool(Scanner scan, String campus) {
    while (true) {
      System.out.println("What school are you in? (YC, Syms, Beren): ");
      String school = ConsoleInput.read(scan);
      if (!school.equalsIgnoreCase("yc") && !school.equalsIgnoreCase("syms") && !school.equalsIgnoreCase("beren")) {
        System.out.println("Invalid school name.");
      } else if (!campusAndSchoolMatch(campus, school)) {
        System.out.println("Wilf campus is only compatible with YC or Syms; Beren campus is only compatible with Beren or Syms.");
      } else {
        return school;
      }
    }
  }

  private static boolean campusAndSchoolMatch(String campus, String school) {
    if (campus == null || school == null) {
      return false;
    }
    return (campus.equalsIgnoreCase("wilf") && (school.equalsIgnoreCase("yc") || school.equalsIgnoreCase("syms")))
        || (campus.equalsIgnoreCase("beren") && (school.equalsIgnoreCase("beren") || school.equalsIgnoreCase("syms")));
  }

  /** Minors can intentionally use the Syms fallback; a major must belong to
   * the student's own school (Beren profiles map to Stern catalog programs). */
  private static boolean majorMatchesSchool(DegreeProgram program, String studentSchool) {
    if (program.getName().toLowerCase().contains("minor")) {
      return true;
    }
    return program.getSchool().equalsIgnoreCase(catalogSchoolFor(studentSchool));
  }

  private static boolean savedMajorsMatchSchool(String proposedSchool) {
    Map<String, DegreeProgram> catalog = DegreeProgram.getCatalog();
    if (catalog == null) {
      return true;
    }
    for (String majorKey : UserSession.currentUser.getMajors()) {
      DegreeProgram major = DegreeProgram.findByCatalogKey(catalog, majorKey);
      if (major != null && !majorMatchesSchool(major, proposedSchool)) {
        return false;
      }
    }
    return true;
  }

  private static String catalogSchoolFor(String profileSchool) {
    return profileSchool != null && profileSchool.equalsIgnoreCase("beren") ? "Stern" : profileSchool;
  }

  private static String displaySchool(String school) {
    return school != null && school.equalsIgnoreCase("beren") ? "Beren/Stern" : school;
  }
}
