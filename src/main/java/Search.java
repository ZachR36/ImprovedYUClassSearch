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
 * Things to add after to make this better/actually provide some more
 * functionality:
 * TODO: Ability to compound different searches
 * TODO: New search method - don't show them classes that they've already taken
 * 
 * Even more advanced:
 * TODO: Implement a search based on the classes that the user fulfilled the
 * requirements for (involves fixing the fetchDescription time issue)
 * 
 */

public class Search {
  // The Data Structures to hold different lists of classes to help with searches
  private static HashMap<Integer, Course> mapByCRN = new HashMap<>();
  private static HashMap<String, ArrayList<Course>> mapByDep = new HashMap<>();
  private static HashMap<String, ArrayList<Course>> mapByTeacher = new HashMap<>();
  private static HashMap<Double, ArrayList<Course>> mapByCredits = new HashMap<>();
  private static HashMap<String, Course> mapByName = new HashMap<>();
  private static HashMap<String, ArrayList<Course>> mapByAttribute = new HashMap<>();
  private static HashMap<String, ArrayList<Course>> mapByCampus = new HashMap<>();

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
  private static String actionPrompt = "\nPlease choose an option from the following list: \n" +
      "To make a change to the user info: \"user\" \n" +
      "To search based on a certain criteria: \"search\" \n" +
      "To refine previous searches: \"refine\" \n" +
      "To scroll to the next (>) or previous (<) page of classes. \n" +
      "To mark (or unmark) a class that you're interested in: \"interested\" \n" +
      "To get your \"Interested\" list: \"get interested\" \n" +
      "To bookmark (or unmark) a class (so that time slot will be reserved): \"bookmark\" \n" +
      "To get your \"Bookmarked\" list: \"get bookmarked\" \n" +
      "To clear the screen: \"clear\" \n" +
      "To exit the program: \"exit\"";

  public static void main(String[] args) throws IOException {
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
    // Clear the screen
    System.out.print("\033[H\033[2J");
    System.out.flush();
    boolean run = true;
    while (run) {
      System.out.println(actionPrompt);
      selection = scan.nextLine().trim();
      // Now perform the action selected...
      switch (selection.toLowerCase()) {
        case "user" -> {
          userChange(scan);
        }
        case "search" -> {
          // Need to implement this method. And decide if the method should return a list
          // or set currentSearchResults
          boolean success = chooseSearchOption(scan, false);
          if (success) {
            if (currentSearchResults.isEmpty()) {
              System.out.println("\n There were no matches for this search.\n");
            } else {
              printingSpot = 1; // Once they do a new search, when we print the classes we're start from the top
                                // of the list
              System.out.print("\033[H" + "\033[2J");
              System.out.flush();
              coursePrinting(currentSearchResults, printingSpot);
              printingSpot += INCREMENT;
            }
          }
        }
        case "refine" -> {
          // Need to implement this method. And decide if the method should return a list
          // or set currentSearchResults
          if (currentSearchResults.isEmpty()) {
            System.out.println("No previous search to refine");
          } else {
            boolean success = chooseSearchOption(scan, true);
            if (success) {
              if (currentSearchResults.isEmpty()) {
                System.out.println("\n There were no matches for this search.\n");
              } else {
                printingSpot = 1; // Once they do a new search, when we print the classes we're start from the top
                                  // of the list
                System.out.print("\033[H" + "\033[2J");
                System.out.flush();
                coursePrinting(currentSearchResults, printingSpot);
                printingSpot += INCREMENT;
              }
            }
          }
        }
        case "bookmark" -> {
          System.out.println("Give the CRN of the class that you want to bookmark: ");
          bookmarkClass(scan.nextLine().trim());
        }
        case "interested" -> {
          System.out.println("Give the CRN of the class that you're interested in: ");
          interestedInClass(scan.nextLine().trim());
        }
        case "get bookmarked" -> {
          coursePrinting(getBookmarked(), 1);
        }
        case "get interested" -> {
          coursePrinting(getInterested(), 1);
        }
        case "<" -> {
          // Clear the screen
          System.out.print("\033[H" + "\033[2J");
          System.out.flush();
          // Print the previous page of classes
          if (printingSpot - (2 * INCREMENT) > 1) {
            printingSpot = printingSpot - (2 * INCREMENT);
          } else {
            printingSpot = 1;
          }
          coursePrinting(currentSearchResults, printingSpot);
          printingSpot += INCREMENT;
        }
        case ">" -> {
          // Clear the screen
          System.out.print("\033[H" + "\033[2J");
          System.out.flush();
          // Print the next page of classes
          coursePrinting(currentSearchResults, printingSpot);
          if (printingSpot + INCREMENT < currentSearchResults.size()) {
            printingSpot += INCREMENT;
          }
        }
        case "clear" -> {
          // Clear the screen
          System.out.print("\033[H\033[2J");
          System.out.flush();
        }
        case "exit" -> {
          run = false;
        }
      }

    }
  }

  /*
   * 
   * SEARCH METHODS
   * 
   */
  private static boolean chooseSearchOption(Scanner scan, boolean refine) {
    /*
     * This will prompt them further for what exactly they want. Refine or expand
     * the search. New search.
     * Whether to exclude or include certain classes. Then also what criteria they
     * want to search by. Anything else.
     * Then set the currentSearchResults to what the list should now be, so the main
     * method can deal with printing.
     */
    boolean validGiven = false;
    String choice = "";
    ArrayList<Course> results = new ArrayList<>();
    while (!validGiven) {
      System.out.println("What criteria do you want to search for (spelling, not case, sensitive): " +
          "\n CRN, name, campus, department, teacher, credits, attribute, dept + course number \n" +
          "(\"exit\" if you want to cancel the search request)");
      choice = scan.nextLine().trim();
      switch (choice.toLowerCase()) {
        case "crn" -> {
          System.out.println("Give the crn of the course you want to look at (Five digit number): ");
          try {
            int crn = Integer.parseInt(scan.nextLine().trim());
            if (crn < 10000 || crn > 99999) { // Ensuring it's a five digit number
              throw new IllegalArgumentException();
            }
            validGiven = true;
            Course course = searchByCRN(crn);
            if (course != null) {
              results.add(course);
            }
          } catch (NumberFormatException e) {
            System.out.println("\nMake sure you're giving a number/only digits\n");
          } catch (IllegalArgumentException e) {
            System.out.println("\nYou must give a five digit number/a valid CRN\n");
          }
        }
        case "campus" -> {
          System.out.println("Give the campus you want to search for: ");
          String campus = scan.nextLine().toLowerCase().trim();
          validGiven = true;
          results = searchByCampus(campus);
        }
        case "department" -> {
          System.out.println(
              "Give the department that you want to search for (you may have multiple sepearated by spaces): ");
          String dept = scan.nextLine().toLowerCase().trim();
          validGiven = true;
          results = searchByDepartment(dept);
        }
        case "teacher" -> {
          System.out
              .println("Give the teacher that you want to search for (you may have multiple separated by spaces):");
          String teacher = scan.nextLine().toLowerCase().trim();
          validGiven = true;
          results = searchByTeacher(teacher);
        }
        case "credits" -> {
          System.out.println("Give the number of credits that you want to search for: ");
          double credits = -1;
          try {
            credits = Integer.parseInt(scan.nextLine().trim());
          } catch (NumberFormatException e) {
            System.out.println("\nMake sure you're giving a number/only digits\n");
          }
          results = searchByCredits(credits);
          validGiven = true;
        }
        case "dept + course number" -> {
          System.out.println("First give the department: ");
          String dept = scan.nextLine().trim();
          System.out.println("Now give the course number: ");
          String courseNum = scan.nextLine().trim();
          validGiven = true;
          Course course = searchByDeptAndNumber(dept, courseNum);
          if (course != null) {
            results.add(course);
          }
        }
        case "name" -> {
          System.out.println("What is the name of the class? (You may give part of the name)");
          String nameFrag = scan.nextLine().trim().toLowerCase();
          results.addAll(searchByName(nameFrag));
          validGiven = true;
        }
        case "attribute" -> {
          System.out
              .println("Give the attributes you want to search for (you may have multiple speparated by spaces): ");
          String attributes = scan.nextLine().toLowerCase().trim();
          validGiven = true;
          results = searchByAttributes(attributes);
        }
        case "exit" -> {
          return false;
        }
        default -> {
          System.out.println("Not a valid option. Try again.");
        }
      }
    }
    if (refine) {
      currentSearchResults.retainAll(results);
    } else {
      currentSearchResults = results;
    }
    return true;
  }

  /*
   * Note: Methods that return a list of courses return an empty list if there are
   * no matches.
   * Methods that return one course return null, so other methods will have to
   * deal with that.
   */

  private static Course searchByCRN(int CRN) {
    return mapByCRN.getOrDefault(CRN, null);
  }

  private static ArrayList<Course> searchByCampus(String campus) {
    ArrayList<Course> results = new ArrayList<>();
    for (String key : mapByCampus.keySet()) {
      if (key.toLowerCase().contains(campus)) {
        results.addAll(mapByCampus.get(key));
      }
    }
    return results;
  }

  private static ArrayList<Course> searchByDepartment(String departments) {
    ArrayList<Course> results = new ArrayList<>();
    for (String dept : departments.split(" ")) {
      if (mapByDep.get(dept) != null) {
        results.addAll(mapByDep.get(dept));
      }
    }
    return results;
  }

  private static ArrayList<Course> searchByTeacher(String names) {
    ArrayList<Course> results = new ArrayList<>();
    for (String name : names.split(" ")) {
      for (String key : mapByTeacher.keySet()) {
        if (key.contains(name)) {
          results.addAll(mapByTeacher.get(key));
        }
      }
    }
    return results;
  }

  private static Course searchByDeptAndNumber(String dept, String num) {
    Course resultCourse = null;
    ArrayList<Course> deptList = searchByDepartment(dept);
    for (Course course : deptList) {
      if (course.getDeptNumber().equals(num)) {
        resultCourse = course;
      }
    }
    return resultCourse;
  }

  private static ArrayList<Course> searchByCredits(double credits) {
    return mapByCredits.getOrDefault(credits, new ArrayList<>());
  }

  private static ArrayList<Course> searchByName(String nameFrag) {
    ArrayList<Course> result = new ArrayList<>();
    for (String name : mapByName.keySet()) {
      if (name.contains(nameFrag)) {
        result.add(mapByName.get(name));
      }
    }
    return result;
  }

  private static ArrayList<Course> searchByAttributes(String attributes) {
    ArrayList<Course> results = new ArrayList<>();
    for (String attribute : attributes.split(" ")) {
      if (mapByAttribute.containsKey(attribute)) {
        results.addAll(mapByAttribute.get(attribute));
      }
    }
    return results;
  }

  /*
   * 
   * BOOKMARK / INTERESTED METHODS
   * 
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
    if (bookmarked.containsKey(Integer.parseInt(crn))) {
      bookmarked.remove(Integer.parseInt(crn));
      System.out.println("Removed " + crn + " from bookmarked");
      return true;
    }
    // TODO: before adding the class, we can check with the user that this is what
    // they wanted,
    // since they might've accidentally given the wrong CRN
    Course newCourse = mapByCRN.get(Integer.parseInt(crn));
    float credits = 0;
    for (Course course : bookmarked.values()) {
      credits += course.getCredits();
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
          System.out.println("Course has a section conflict: " + section + " in " + course.getName());
          return false;
        }
      }
    } else {
      for (String c : section.split("")) {
        for (Course course : bookmarked.values()) {
          if (course.getSection().contains(c)) {
            System.out.println("Course has a section conflict: " + c + " in " + course.getName());
            return false;
          }
        }
      }
    }
    bookmarked.put(Integer.parseInt(crn), newCourse);
    return true;
  }

  private static ArrayList<Course> getBookmarked() {
    return new ArrayList<Course>(bookmarked.values());
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
      interested.remove(intcrn);
      return true;
    } else {
      interested.put(intcrn, mapByCRN.get(intcrn));
      return true;
    }
  }

  private static ArrayList<Course> getInterested() {
    return new ArrayList<>(interested.values());
  }

  /*
   * 
   * USER OPTION METHODS
   * 
   */
  private static void userChange(Scanner scan) throws IOException {
    String choice = "";
    boolean validChoice = false;
    while (!validChoice) {
      System.out.println(
          "Do you want to make an update to this User, save this one, or switch users? (\"update\",\"save\",\"switch\",\"exit\"");
      choice = scan.nextLine().trim();
      if (choice.equalsIgnoreCase("update")) {
        updateUserData(scan);
        validChoice = true;
      } else if (choice.equalsIgnoreCase("save")) {
        System.out.println("Give the path where you want to write the file with the user data: ");
        Path path = Path.of(scan.nextLine().trim());
        if (validateGivenPath(path)) {
          currentUser.saveToFile(path);
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

  private static void updateUserData(Scanner scan) {
    String choice = "";
    boolean validGiven = false;
    while (!validGiven) {
      System.out.println("What attribute do you want to update: (\"name\",\"honors\",\"campus\",\"school\"");
      choice = scan.nextLine().trim();
      switch (choice.toLowerCase()) {
        case "name" -> {
          System.out.println("Provide a user name: ");
          currentUser.setName(scan.nextLine().trim());
          validGiven = true;
        }
        case "honors" -> {
          System.out.println("Are you in honors? (Y/N): ");
          validGiven = true; // Setting to not-in-honors if anything other than "y" is given
          currentUser.setHonors(scan.nextLine().trim().equalsIgnoreCase("y"));
        }
        case "campus" -> {
          System.out.println("What campus are you on? (Wilf/Beren): ");
          String campus = scan.nextLine().trim();
          switch (campus.toLowerCase()) {
            case "wilf", "beren" -> {
              validGiven = true;
              currentUser.setCampus(campus);
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
              currentUser.setCampus(school);
            }
            default -> System.out.println("Invalid school name");
          }
        }
        default -> {
          System.out.println("Invalid choice");
        }
      }
    }
  }
  /*
   * 
   * HELPER METHODS
   * 
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
    boolean endOfList = false;
    for (int i = startingPoint; i < startingPoint + INCREMENT; i++) {
      if (i > courses.size()) {
        endOfList = true;
        break;
      }
      Course course = courses.get(i - 1);
      System.out.printf("%-30.30s %-6s %-10s %-10s %-7s %-8s %-20.20s %-7s %-11s %-15s%n%s%n",
          course.getName(), course.getDepartment(), course.getDeptNumber(), course.getSection(), course.getCredits(),
          course.getCRN(), course.getTeacher(), course.getMaxEnrolled(), course.getRemainingEnrollment(),
          String.join(", ", course.getAttributes()), "-".repeat(132));
    }
    // Print how many elements we're showing and how many are left
    int endSpot = (endOfList ? courses.size() : startingPoint + INCREMENT);
    System.out.println("Classes " + startingPoint + " to " + endSpot + ". Out of " + courses.size());
    // Print the action prompt
  }

  // TODO: not sure how I want to do this yet, especially with class descriptions
  /*
   * private static void displayCourseInfo(Course course) {
   * System.out.println("CRN: " + course.getCRN() + "\nTitle: " + course.getName()
   * + "\nTeacher: " + course.getTeacher()
   * + "\nDepartment ID: "
   * + course.getDepartment() + course.getDeptNumber());
   * }
   */

  private static ArrayList<Course> removeWrongCampus(ArrayList<Course> list) {
    ArrayList<Course> results = new ArrayList<>();
    String campus = currentUser.getCampus();
    for (Course course : list) {
      if (campus.equals(course.getCampus())) {
        results.add(course);
      }
    }
    return results;
  }

  // TODO: needs to have user class updated before implemeting
  /*
   * private static ArrayList<Course> removeAlreadyTaken(ArrayList<Course> list) {
   * ArrayList<Course> results = new ArrayList<>();
   * for (String department : currentUser.getCompletedCourses().keySet()) {
   * List<String> departmentNumbers =
   * currentUser.getCompletedCourses().get(department);
   * for (Course course : list) {
   * if (!(course.getDepartment().equals(department) &&
   * departmentNumbers.contains(course.getDeptNumber()))) {
   * results.add(course);
   * }
   * }
   * }
   * return results;
   * }
   */

  private static ArrayList<Course> removeHonors(ArrayList<Course> list) {
    ArrayList<Course> results = new ArrayList<>();
    for (Course course : list) {
      ArrayList<String> attributes = new ArrayList<>(Arrays.asList(course.getAttributes()));
      if (!attributes.contains("HONR")) {
        results.add(course);
      }
    }
    return results;
  }

  /*
   * private static ArrayList<Course> getUnfinishedRequirements(ArrayList<Course>
   * list) {
   * ArrayList<Course> results = new ArrayList<>();
   * TODO: get user requirements and the ones they have yet to fulfill
   * for (Course course : list) {
   * ArrayList<String> courseAttributes = new
   * ArrayList<>(Arrays.asList(course.getAttributes()));
   * if (!courseAttributes.retainAll(unfulfilled).isEmpty()) {
   * results.add(course);
   * }
   * }
   * return results;
   * }
   */

  /*
   * 
   * METHODS FOR PROGRAM SET UP
   * 
   */

  private static void setup(String path) throws IOException {
    Path csv = Path.of(path);
    try (BufferedReader reader = Files.newBufferedReader(csv)) {
      String line;
      while ((line = reader.readLine()) != null) {
        Course newCourse = new Course(line);
        mapByCRN.put(newCourse.getCRN(), newCourse);
        mapByName.put(newCourse.getName().toLowerCase(), newCourse);
        if (!mapByDep.containsKey(newCourse.getDepartment().toLowerCase())) {
          mapByDep.put(newCourse.getDepartment().toLowerCase(), new ArrayList<Course>());
        }
        mapByDep.get(newCourse.getDepartment().toLowerCase()).add(newCourse);
        if (!mapByTeacher.containsKey(newCourse.getTeacher().toLowerCase())) {
          mapByTeacher.put(newCourse.getTeacher().toLowerCase(), new ArrayList<Course>());
        }
        mapByTeacher.get(newCourse.getTeacher().toLowerCase()).add(newCourse);
        if (!mapByCredits.containsKey(newCourse.getCredits())) {
          mapByCredits.put(newCourse.getCredits(), new ArrayList<Course>());
        }
        mapByCredits.get(newCourse.getCredits()).add(newCourse);
        for (String attribute : newCourse.getAttributes()) {
          if (!mapByAttribute.containsKey(attribute.toLowerCase())) {
            mapByAttribute.put(attribute.toLowerCase(), new ArrayList<Course>());
          }
          mapByAttribute.get(attribute.toLowerCase()).add(newCourse);
        }
        if (!mapByCampus.containsKey(newCourse.getCampus())) {
          mapByCampus.put(newCourse.getCampus(), new ArrayList<Course>());
        }
        mapByCampus.get(newCourse.getCampus()).add(newCourse);
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
      choice = scan.nextLine().trim();
      switch (choice.toLowerCase()) {
        case "pre" -> {
          System.out.println("Give the Path to the file with the user info: ");
          Path path = Path.of(scan.nextLine().trim());
          if (validateGivenPath(path)) {
            try {
              currentUser = new User(path);
              finished = true;
            } catch (IOException e) {
              System.out.println("Failed to read user file");
            }
          }
        }
        case "make" -> {
          currentUser = new User();
          System.out.println("Provide a user name: ");
          currentUser.setName(scan.nextLine().trim());
          System.out.println("Are you in honors? (Y/N): ");
          currentUser.setHonors(scan.nextLine().trim().equalsIgnoreCase("y"));
          System.out.println("What campus are you on? (Wilf/Beren): ");
          currentUser.setName(scan.nextLine().trim().equalsIgnoreCase("wilf") ? "wilf" : "beren");
          System.out.println("What school are you in? (YC, Syms, Beren): ");
          currentUser.setName(scan.nextLine().trim());
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
