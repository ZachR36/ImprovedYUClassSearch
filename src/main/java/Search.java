import java.util.*;
import java.io.BufferedReader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runner class for users to search through classes that Yeshiva University is
 * offering the next semester.
 * When the program begins, the user is prompted to set up their profile so that
 * searches can be altered based on their
 * information.
 * Then, the main program will be begin: The user will be prompted to choose
 * from a list of possible actions, such as
 * bookmark a class that they're interested in, limit their search criteria, or
 * update user information. The program will
 * print out the requested list of classes after each search.
 *
 * List of actions:
 * List of Searches:
 */

/*
Things to add after to make this better/actually provide some more functionality:
TODO: Ability to compound different searches
TODO: New search method - don't show them classes that they've already taken

Even more advanced:
TODO: Implement a search based on the classes that the user fulfilled the requirements for (involves fixing the fetchDescription time issue)

 */

public class Search {
  // The Data Structures to hold different lists of classes to help with searches
  private static HashMap<Integer, Course> mapByCRN = new HashMap<>();
  private static HashMap<String, ArrayList<Course>> mapByDep = new HashMap<>();
  private static HashMap<String, ArrayList<Course>> mapByTeacher = new HashMap<>();
  private static HashMap<Double, ArrayList<Course>> mapByCredits = new HashMap<>();

  // The user object that they're using
  private static User currentUser = null;
  // Lists of classes that the User will save
  private static HashMap<Integer, Course> interested = new HashMap<>();
  private static HashMap<Integer, Course> bookmarked = new HashMap<>();

  // A List of all the classes that the last search returned
  private static ArrayList<Course> currentSearchResults = new ArrayList<>();

  // the number of courses that will be printed on one page
  private static final int INCREMENT = 15;

  // This is the string that we'll print every time we want to prompt the user to
  // choose their next action
  private static String actionPrompt = "Please choose an option from the following list: \n" +
          "If you want to make a change to the user info: \"user\" \n" +
          "If you want to search based on a certain criteria: \"search\" \n" +
          "If you want to scroll to the next (>) or previous (<) page of classes. \n" +
          "If you want to mark a class that you're interested in: \"interested\" \n" +
          "If you want to bookmark a class (so that time slot will be knocked off: \"bookmark\"";

  public static void main(String[] args) throws IOException {
    System.out.println("The program is now going to load up all the class info from the csv file you provided. This could take a few minutes. You'll get a message when it's finished.");
    try {
      setup(args[0]); // The user should give the csv file holding all the class information when
                      // starting the program.
      System.out.println("All courses successfully loaded.");
    } catch (IOException e) {
      System.out.println("Failed to read CSV");
      e.printStackTrace();
    }
    Scanner scan = new Scanner(System.in);
    String selection = "";
    int printingSpot = 0;
    userSetUp(scan);

    while (true) {
      System.out.println(actionPrompt);
      selection = scan.nextLine();
      // Now perform the action selected...
      switch (selection.toLowerCase()) {
        case "user" -> {
          userChange(scan);
        }
        case "search" -> {
          chooseSearchOption(); // Need to implement this method
          printingSpot = 1; // Once they do a new search, when we print the classes we're start from the top of the list
          coursePrinting(currentSearchResults,printingSpot);
          printingSpot += INCREMENT;
        }
        case "bookmark" -> {
          System.out.println("Give the CRN of the class that you want to bookmark: ");
          bookmarkClass(scan.nextLine());
        }
        case "interested" -> {
          System.out.println("Give the CRN of the class that you're interested in: ");
          interestedInClass(scan.nextLine());
        }
        case "<" -> {
          // Clear the screen
          System.out.print("\033[H\033[2J");
          System.out.flush();
          // Print the previous page of classes
          printingSpot -= INCREMENT;
          coursePrinting(currentSearchResults,printingSpot);
          printingSpot += INCREMENT;
        }
        case ">" -> {
          // Clear the screen
          System.out.print("\033[H\033[2J");
          System.out.flush();
          // Print the next page of classes
          coursePrinting(currentSearchResults,printingSpot);
          printingSpot += INCREMENT;
        }
      }

    }
  }


  /*

  SEARCH METHODS

   */
  private static void chooseSearchOption() {
    /* This will prompt them further for what exactly they want. Refine or expand the search. New search.
     Whether to exclude or include certain classes. Then also what criteria they want to search by. Anything else.
     Then set the currentSearchResults to what the list should now be, so the main method can deal with printing.

     */
    // TODO: This is the next and final step for version 1/a complete working version of the program
  }

  private static Course searchByCRN(int CRN) {
    if (mapByCRN.containsKey(CRN)) {
      return mapByCRN.get(CRN);
    } else {
      return null;
    }
  }

  private static ArrayList<Course> searchByDepartment(String dept) {
    if (mapByDep.containsKey(dept)) {
      return mapByDep.get(dept);
    } else {
      return null;
    }
  }

  private static ArrayList<Course> searchByTeacher(String nameFrag) {
    ArrayList<Course> results = new ArrayList<>();
    for (String name : mapByTeacher.keySet()) {
      if (name.contains(nameFrag)) {
        results.addAll(mapByTeacher.get(name));
      }
    }
    if (results.size() == 0) {
      return null;
    } else {
      return results;
    }
  }

  private static Course searchByDeptAndNumber(String dept, String num) {
    ArrayList<Course> deptList = searchByDepartment(dept);
    for (Course course : deptList) {
      if (course.getDeptNumber().equals(num)) {
        return course;
      }
    }
    return null;
  }

  private static ArrayList<Course> searchByCredits(double credits) {
    if (mapByCredits.containsKey(credits)) {
      return mapByCredits.get(credits);
    } else {
      return null;
    }
  }

  /*

  BOOKMARK / INTERESTED METHODS

   */

  /**
   * Add a class, selected by the user, to a list of classes that they plan on
   * taking.
   * This will prevent them from double booking that time slot or from selecting
   * too many credits.
   *
   * @param crn of the class that they want to mark
   * @return true if the class was successfully added to the list of bookmarked
   *         classes.
   *         false otherwise (such as if this conflicts with classes they already
   *         marked).
   */
  private static boolean bookmarkClass(String crn) {
    if (mapByCRN.containsKey(Integer.parseInt(crn)) == false) {
      System.out.println("Course is not in database");
      return false;
    }
    // TODO: before adding the class, we can check with the user that this is what
    // they wanted, since they
    // might've accidentally given the wrong CRN
    Course newCourse = mapByCRN.get(Integer.parseInt(crn));
    float credits = 0;
    for (Course course : bookmarked.values()) {
      if (course.getCredits() == 0) {
        credits += 0.5; // Accounting for an error that courses with 0.5 credits are stored with 0
      } else {
        credits += course.getCredits();
      }
    }
    if ((currentUser.getCampus().equals("wilf") && credits + newCourse.getCredits() > 17.5)
            || (currentUser.getCampus().equals("beren") && credits + newCourse.getCredits() > 21)) {
      System.out.println("Over credit maximum");
      return false;
    }
    if (newCourse.getCampus().toLowerCase().contains(currentUser.getCampus()) == false) {
      System.out.println("Course and User campuses do not match");
      return false;
    }
    String section = newCourse.getSection(); // section refers to the date and time slot
    if (currentUser.getCampus().equals("wilf")) {
      for (Course course : bookmarked.values()) { // TODO: Deal with edge cases.
        if (course.getSection().equals(section)) {
          System.out.println("Course has a section conflict");
          return false;
        }
      }
    } else {
      for (String c : section.split("")) {
        for (Course ccourse : bookmarked.values()) {
          if (ccourse.getSection().contains(c)) {
            System.out.println("Course has a section conflict");
            return false;
          }
        }
      }
    }
    bookmarked.put(Integer.parseInt(crn), newCourse);
    return true;
  }

  /**
   * Add this class to a list of classes that they're interested in, but don't
   * block out that time slot
   *
   * @param crn
   * @return true if successful, false otherwise
   */
  private static boolean interestedInClass(String crn) {
    int intcrn = Integer.parseInt(crn);
    if (mapByCRN.containsKey(intcrn) == false) {
      System.out.println("CRN is not in database");
      return false;
    }
    if (interested.containsKey(intcrn)) {
      return false;
    } else {
      interested.put(intcrn, mapByCRN.get(intcrn));
      return true;
    }
  }
  /*

  USER OPTION METHODS

   */
  private static void userChange(Scanner scan) throws IOException {
    String choice = "";
    boolean validChoice = false;
    while (!validChoice){
      System.out.println("Do you want to make an update to this User, save this one, or switch users? (\"update\",\"save\",\"switch\",\"exit\"");
      choice = scan.nextLine();
      if(choice.equalsIgnoreCase("update")){
        updateUserData(scan);
        validChoice = true;
      } else if (choice.equalsIgnoreCase("save")) {
        System.out.println("Give the path where you want to write the file with the user data: ");
        Path path = Path.of(scan.nextLine());
        if (validateGivenPath(path)){
          currentUser.saveToFile(path);
          validChoice = true;
        }
      } else if (choice.equalsIgnoreCase("switch")){
        userSetUp(scan);
        validChoice = true;
      } else if (choice.equalsIgnoreCase("exit")){
        return;
      } else {
        System.out.println("Invalid choice, try again");
      }
    }
  }

  private static void updateUserData(Scanner scan){
    System.out.println("What attribute do you want to update: (\"name\",\"honors\",\"campus\",\"school\"");
    String choice = scan.nextLine();
    switch (choice.toLowerCase()){
      case "name" -> {
        System.out.println("Provide a user name: ");
        currentUser.setName(scan.nextLine());
      }
      case "honors" -> {
        System.out.println("Are you in honors? (Y/N): ");
        currentUser.setHonors(scan.nextLine().equalsIgnoreCase("y"));
      }
      case "campus" -> {
        System.out.println("What campus are you on? (Wilf/Beren): ");
        currentUser.setName(scan.nextLine().equalsIgnoreCase("wilf") ? "wilf" : "beren");
      }
      case "school" -> {
        System.out.println("What school are you in? (YC, Syms, Beren): ");
        currentUser.setName(scan.nextLine());
      }
      default -> {
        System.out.println("Invalid choice");
        updateUserData(scan);
      }
    }
  }
  /*

  HELPER METHODS

   */

  private static boolean validateGivenPath(Path path) {
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
    } else {
      return true;
    }
  }

  private static void coursePrinting(List<Course> courses, int startingPoint) {
    // Print the header, explaining what's in each column
    System.out.printf("%-30s %-6s %-10s %-10s %-7s %-8s %-20s %-7s %-11s %-15s%n%s%n",
            "Title", "Dpt.", "Course #", "Section", "Hours", "CRN", "Instructor", "Seats", "Seats Rem.", "Attributes",
            "-".repeat(132));
    // Print each class in line
    for (int i = startingPoint; i < startingPoint + INCREMENT; i++) {
      Course course = courses.get(i);
      System.out.printf("%-30s %-6s %-10s %-10s %-7s %-8s %-20s %-7s %-11s %-15s%n%s%n",
              course.getName(), course.getDepartment(), course.getDeptNumber(), course.getSection(), course.getCredits(),
              course.getCRN(), course.getTeacher(), course.getMaxEnrolled(), course.getRemainingEnrollment(),
              String.join(", ", course.getAttributes()), "-".repeat(132));
    }
    // Print how many elements we're showing and how many are left
    System.out.println("Classes " + startingPoint + " to " + (startingPoint + 10) + ". Out of " + courses.size());
    // Print the action prompt
    System.out.println(actionPrompt);
  }


  /*

  METHODS FOR PROGRAM SET UP

   */

  private static void setup(String path) throws IOException {
    Path csv = Path.of(path);
    try (BufferedReader reader = Files.newBufferedReader(csv)) {
      String line;
      while ((line = reader.readLine()) != null) {
        Course newCourse = new Course(line);
        mapByCRN.put(newCourse.getCRN(), newCourse);
        if (!mapByDep.containsKey(newCourse.getDepartment())) {
          mapByDep.put(newCourse.getDepartment(), new ArrayList<Course>());
        }
        mapByDep.get(newCourse.getDepartment()).add(newCourse);
        if (!mapByTeacher.containsKey(newCourse.getTeacher())) {
          mapByTeacher.put(newCourse.getTeacher(), new ArrayList<Course>());
        }
        mapByDep.get(newCourse.getDepartment()).add(newCourse);
        if (!mapByCredits.containsKey(newCourse.getCredits())) {
          mapByCredits.put(newCourse.getCredits(), new ArrayList<Course>());
        }
        mapByCredits.get(newCourse.getCredits()).add(newCourse);
      }
      // Add the courses to the other Data Structures here
    }
  }
  private static void userSetUp(Scanner scan) {
    String choice = "";
    boolean finished = false;
    while (!finished) {
      System.out.println("Do you want to use a pre-existing User object, create a new one, or use a guest one?" +
              " (\"pre\",\"make\", or \"guest\"): ");
      choice = scan.nextLine();
      switch(choice.toLowerCase()){
        case "pre" -> {
          System.out.println("Give the Path to the file with the user info: ");
          Path path = Path.of(scan.nextLine());
          if(validateGivenPath(path)){
            try {
              currentUser = new User(path);
              finished = true;
            } catch (IOException e) {
              System.out.println("Failed to read user file");
            }
          }
        }
        case "make" -> {
          System.out.println("Provide a user name: ");
          currentUser.setName(scan.nextLine());
          System.out.println("Are you in honors? (Y/N): ");
          currentUser.setHonors(scan.nextLine().equalsIgnoreCase("y"));
          System.out.println("What campus are you on? (Wilf/Beren): ");
          currentUser.setName(scan.nextLine().equalsIgnoreCase("wilf") ? "wilf" : "beren");
          System.out.println("What school are you in? (YC, Syms, Beren): ");
          currentUser.setName(scan.nextLine());
          finished = true;
        }
        case "guest" -> {
          currentUser = new User();
          finished = true;
        }
        default -> {
          System.out.println("Invalid choice");
        }
      }
    }
  }
}
