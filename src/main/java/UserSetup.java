import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
              "(\"name\", \"honors\", \"campus\", \"school\", \"completed\", \"major\", \"minor\")");
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
          String major = promptProgramName(scan, "What is your major? (type \"list\" to see exact program names, "
              + "optionally followed by a search term): ");
          UserSession.currentUser.setMajor(major);
          System.out.println("Set major to " + major);
          validGiven = true;
        }
        case "minor" -> {
          validGiven = updateMinor(scan);
        }
        case "completed" -> validGiven = updateCompletedCourse(scan);
        default -> System.out.println("Invalid choice");
      }
    }
  }
  private static boolean updateMinor(Scanner scan) {
    String minor = promptProgramName(scan, "Give the minor to add (or remove, if already added) - "
        + "type \"list\" to see exact program names, optionally followed by a search term: ");
    if (minor.isBlank()) {
      System.out.println("Minor name can't be blank");
      return false;
    }
    if (UserSession.currentUser.getMinors().contains(minor)) {
      UserSession.currentUser.removeMinor(minor);
      System.out.println("Removed " + minor + " from minors");
    } else {
      UserSession.currentUser.addMinor(minor);
      System.out.println("Added " + minor + " to minors");
    }
    return true;
  }

  /** Toggles a completed course code, including courses from prior semesters. */
  static boolean updateCompletedCourse(Scanner scan) {
    System.out.println("Give the course code of the class to mark completed "
        + "(or un-mark, if already completed) - e.g. COMP1300: ");
    String courseCode = scan.nextLine().trim().toUpperCase();
    if (UserSession.currentUser.getCompletedCourses().contains(courseCode)) {
      UserSession.currentUser.removeCompletedClass(courseCode);
      System.out.println("Removed " + courseCode + " from completed courses");
    } else {
      UserSession.currentUser.addCompletedClass(courseCode);
      System.out.println("Added " + courseCode + " to completed courses");
    }
    return true;
  }

  /** Reads one line for a major/minor field, intercepting "list" (optionally
   *  followed by a search term, e.g. "list computer") to show the student the
   *  catalog's exact spellings before re-prompting for the real value. */
  private static String promptProgramName(Scanner scan, String prompt) {
    while (true) {
      System.out.println(prompt);
      String input = scan.nextLine().trim();
      if (input.equalsIgnoreCase("list") || input.toLowerCase().startsWith("list ")) {
        String query = input.length() > 4 ? input.substring(4).trim() : "";
        printProgramList(query);
        continue;
      }
      return input;
    }
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
          UserSession.currentUser.setMajor(promptProgramName(scan, "What is your major? (type \"list\" to see "
              + "exact program names, optionally followed by a search term): "));
          String minorsInput = promptProgramName(scan, "List any minors, separated by semicolons (or leave blank "
              + "for none) - type \"list\" to see exact program names, optionally followed by a search term: ");
          if (!minorsInput.isBlank()) {
            for (String minor : minorsInput.split(";")) {
              UserSession.currentUser.addMinor(minor);
            }
          }
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
}
