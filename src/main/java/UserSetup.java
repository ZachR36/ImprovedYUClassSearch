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
        Path path = Path.of(scan.nextLine().trim());
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
              "(\"name\", \"honors\", \"campus\", \"school\", \"completed\", \"major\", \"minor\", \"jewish track\")");
      choice = scan.nextLine().trim();
      switch (choice.toLowerCase()) {
        case "name" -> {
          System.out.println("Provide a user name: ");
          UserSession.currentUser.setName(scan.nextLine().trim());
          validGiven = true;
        }
        case "honors" -> {
          System.out.println("Are you in honors? (Y/N): ");
          validGiven = true;
          UserSession.currentUser.setHonors(scan.nextLine().trim().equalsIgnoreCase("y"));
        }
        case "campus" -> {
          System.out.println("What campus are you on? (Wilf/Beren): ");
          String campus = scan.nextLine().trim();
          switch (campus.toLowerCase()) {
            case "wilf", "beren" -> {
              validGiven = true;
              UserSession.currentUser.setCampus(campus);
            }
            default -> System.out.println("Invalid campus name");
          }
        }
        case "school" -> {
          System.out.println("What school are you in? (YC, Syms, Beren): ");
          String school = scan.nextLine().trim();
          switch (school.toLowerCase()) {
            case "yc", "syms", "beren" -> {
              validGiven = true;
              UserSession.currentUser.setSchool(school);
            }
            default -> System.out.println("Invalid school name");
          }
        }
        case "major" -> {
          DegreeProgram major = promptForProgram(scan, UserSession.currentUser.getSchool(), "major", false);
          UserSession.currentUser.setMajor(major.getCatalogKey());
          System.out.println("Set major to " + major.getDisplayLabel());
          validGiven = true;
        }
        case "minor" -> {
          validGiven = updateMinor(scan);
        }
        case "completed" -> validGiven = updateCompletedCourse(scan);
        case "jewish track" -> {
          updateJewishStudiesTrack(scan);
          validGiven = true;
        }
        default -> System.out.println("Invalid choice");
      }
    }
  }
  private static boolean updateMinor(Scanner scan) {
    DegreeProgram minor = promptForProgram(scan, UserSession.currentUser.getSchool(), "minor", false);
    String minorKey = minor.getCatalogKey();
    if (UserSession.currentUser.getMinors().contains(minorKey)) {
      UserSession.currentUser.removeMinor(minorKey);
      System.out.println("Removed " + minor.getDisplayLabel() + " from minors");
    } else {
      UserSession.currentUser.addMinor(minorKey);
      System.out.println("Added " + minor.getDisplayLabel() + " to minors");
    }
    return true;
  }

  /** Toggles a completed course, preserving its credits, department, and
   * attributes whenever it is in the loaded semester's course catalog. */
  static boolean updateCompletedCourse(Scanner scan) {
    System.out.println("Give the course code of the class to mark completed "
        + "(or un-mark, if already completed) - e.g. COMP1300: ");
    String courseCode = scan.nextLine().trim().toUpperCase();
    if (UserSession.currentUser.getCompletedCourses().contains(courseCode)) {
      UserSession.currentUser.removeCompletedClass(courseCode);
      System.out.println("Removed " + courseCode + " from completed courses");
    } else {
      Course currentCourse = SearchEngine.searchByCourseCode(courseCode);
      if (currentCourse != null) {
        UserSession.currentUser.addCompletedClass(currentCourse);
        System.out.println("Added " + courseCode + " to completed courses with its catalog credits, department, and attributes");
      } else {
        addPriorSemesterCompletedCourse(scan, courseCode);
      }
    }
    return true;
  }

  private static void addPriorSemesterCompletedCourse(Scanner scan, String courseCode) {
    System.out.println("That course is not in this semester's catalog. Credits (leave blank if unknown): ");
    String creditInput = scan.nextLine().trim();
    Double credits = null;
    if (!creditInput.isBlank()) {
      try {
        credits = Double.parseDouble(creditInput);
      } catch (NumberFormatException e) {
        System.out.println("Credits were not a number, so they were saved as unknown.");
      }
    }
    System.out.println("Department/subject abbreviation (leave blank if unknown): ");
    String department = scan.nextLine().trim();
    System.out.println("Attributes, separated by spaces (leave blank if none or unknown): ");
    String attributeInput = scan.nextLine().trim();
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
      String input = scan.nextLine().trim();
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
        return selected;
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
      int index = Integer.parseInt(scan.nextLine().trim()) - 1;
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
          Path path = Path.of(scan.nextLine().trim());
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
          UserSession.currentUser.setName(scan.nextLine().trim());
          System.out.println("Are you in honors? (Y/N): ");
          UserSession.currentUser.setHonors(scan.nextLine().trim().equalsIgnoreCase("y"));
          System.out.println("What campus are you on? (Wilf/Beren): ");
          UserSession.currentUser.setCampus(scan.nextLine().trim().equalsIgnoreCase("wilf") ? "wilf" : "beren");
          System.out.println("What school are you in? (YC, Syms, Beren): ");
          UserSession.currentUser.setSchool(scan.nextLine().trim());
          DegreeProgram major = promptForProgram(scan, UserSession.currentUser.getSchool(), "major", false);
          UserSession.currentUser.setMajor(major.getCatalogKey());
          updateJewishStudiesTrack(scan);
          addInitialMinors(scan);
          finished = true;
        }
        case "guest" -> {
          UserSession.currentUser = new User();
          finished = true;
        }
        default -> System.out.println("Invalid choice");
      }
    }
  }

  private static void addInitialMinors(Scanner scan) {
    System.out.println("Do you want to add a minor now? (Y/N): ");
    while (scan.nextLine().trim().equalsIgnoreCase("y")) {
      DegreeProgram minor = promptForProgram(scan, UserSession.currentUser.getSchool(), "minor", false);
      UserSession.currentUser.addMinor(minor.getCatalogKey());
      System.out.println("Add another minor? (Y/N): ");
    }
  }

  private static void updateJewishStudiesTrack(Scanner scan) {
    System.out.println("Jewish studies track (MYP, BMP, IBC, JSS, or leave blank if not applicable/unknown): ");
    String track = scan.nextLine().trim();
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
    if (!user.getMajor().equalsIgnoreCase("undeclared")
        && DegreeProgram.findByCatalogKey(catalog, user.getMajor()) == null) {
      System.out.println("Your saved major needs to be matched to the current program catalog.");
      DegreeProgram major = promptForProgram(scan, user.getSchool(), "major", false);
      user.setMajor(major.getCatalogKey());
    }
    for (String minorKey : List.copyOf(user.getMinors())) {
      if (DegreeProgram.findByCatalogKey(catalog, minorKey) == null) {
        System.out.println("Your saved minor \"" + minorKey + "\" needs to be matched to the current catalog.");
        DegreeProgram minor = promptForProgram(scan, user.getSchool(), "minor", false);
        user.removeMinor(minorKey);
        user.addMinor(minor.getCatalogKey());
      }
    }
  }
}
