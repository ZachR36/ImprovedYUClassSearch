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
 * Then, the main program will begin: The user will be prompted to choose
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
 * Even more advanced - the part Judah actually wants:
 * TODO: Implement a search based on the classes that the user fulfilled the requirements for (involves fixing the fetchDescription time issue)
 *
 * Advice from Zach, 7/28/26:
 * The part that the school would actually be interested in here is mainly the ability to give recommendations.
 * At least that's the way it was pitched to Judah in the original email. So I think maybe we can hold off on more
 * advanced search options for now.
 * My suggested game plan from here - I would start by fixing all of the bugs in the current version of the
 * program so that it's fully functional in its simple form, it might be there already, I'll do some random testing.
 * I would also recommend restructuring the program - right now all of the logic is in this one giant class, so it's
 * not so organized for someone else (or even myself, coming back to my own program two months later) to understand how
 * the program works and what the different functions are all meant for. I'm thinking at the least make different classes
 * of helper functions, like a class for search functions and later a class for recommendations, maybe a class for setup
 * functions, etc. I'll have a clearer picture of this once I can figure out again how the program works.
 * Finally, once we have a simple functioning program, then I would start working on the recommendation function. This
 * means getting the pre and coreq info for every class, and getting the lists of classes for every major and minor.
 * Once we have all the data stored, we can have the program recommend the next viable class in the track, or write an
 * algorithm to figure out what classes they like and recommend similar ones (even outside of the major), or an algorithm
 * to decide "you can only take two classes for your major now, so you should try to knock out some cores while you have
 * the time" or something like that.
 */

  /*
  Todo: Bugs
  Zach's list of bugs that need fixing:

  - When saving a user name to file, it wrote "yc beren..." or whatever even though I selected one
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
    int printingSpot = 1;
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
          if (printingSpot - INCREMENT >= 1) {
            printingSpot = printingSpot - INCREMENT;
          } else {
            printingSpot = 1;
          }
          coursePrinting(currentSearchResults, printingSpot);
        }
        case ">" -> {
          // Clear the screen
          System.out.print("\033[H" + "\033[2J");
          System.out.flush();
          // Print the next page of classes
          if (hasNextPage(printingSpot, currentSearchResults.size())) {
            printingSpot += INCREMENT;
          }
          coursePrinting(currentSearchResults, printingSpot);
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
          int crn = 0;
          Course course = null;
          try {
            crn = Integer.parseInt(scan.nextLine().trim());
            if (crn < 10000 || crn > 99999) { // Ensuring it's a five digit number
              throw new IllegalArgumentException();
            } else {
              validGiven = true;
            }
          } catch (NumberFormatException e) {
            System.out.println("\nMake sure you're giving a number/only digits\n");
          } catch (IllegalArgumentException e) {
            System.out.println("\nYou must give a five digit number/a valid CRN\n");
          }
          if(validGiven){
            course = searchByCRN(crn);
          }
          if (course != null) {
            results.add(course);
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
              "Give the department that you want to search for (you may have multiple separated by spaces): ");
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
          double credits;
          try {
            credits = Double.parseDouble(scan.nextLine().trim());
            if (!Double.isFinite(credits) || credits < 0) {
              throw new NumberFormatException();
            }
            results = searchByCredits(credits);
            validGiven = true;
          } catch (NumberFormatException e) {
            System.out.println("\nMake sure you're giving a valid, non-negative number\n");
          }
        }
        case "dept + course number" -> {
          System.out.println("First give the department: ");
          String dept = scan.nextLine().toLowerCase().trim();
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
          // TODO: Why is this .addAll but all other search methods reset results? Mistake? Try to be consistent
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
    // TODO: Add a compound option. Just need "if compound -> currentSearchResults.addAll)"
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

  static ArrayList<Course> searchByCredits(double credits) {
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
  static boolean bookmarkClass(String crn) {
    int courseCrn;
    try {
      courseCrn = Integer.parseInt(crn);
    } catch (NumberFormatException e) {
      System.out.println("CRN must be a number");
      return false;
    }
    if (!mapByCRN.containsKey(courseCrn)) {
      System.out.println("Course is not in database");
      return false;
    }
    if (bookmarked.containsKey(courseCrn)) {
      bookmarked.remove(courseCrn);
      System.out.println("Removed " + crn + " from bookmarked");
      return true;
    }
    // TODO: before adding the class, we can check with the user that this is what
    // they wanted,
    // since they might've accidentally given the wrong CRN
    Course newCourse = mapByCRN.get(courseCrn);
    float credits = 0;
    for (Course course : bookmarked.values()) {
      credits += course.getCredits();
    }
    if ((currentUser.getCampus().equals("wilf") && credits + newCourse.getCredits() > 17.5)
        || credits + newCourse.getCredits() > 21) { // if they're not on wilf, they're either a guest or on beren. So either way limit to 21 credits
      System.out.println("Over credit maximum");
      return false;
    }
    if (!currentUser.getCampus().contains(newCourse.getCampus().toLowerCase())) {
      System.out.println("Course and User campuses do not match");
      return false;
    }
    for (Course course : bookmarked.values()) {
      if (hasMeetingConflict(newCourse, course)) {
        System.out.println("Course has a meeting-time conflict with " + course.getName());
        return false;
      }
    }
    bookmarked.put(courseCrn, newCourse);
    return true;
  }

  /** Returns whether two courses meet on a shared day during overlapping times. */
  static boolean hasMeetingConflict(Course first, Course second) {
    for (String[][] firstMeeting : first.getMeetings()) {
      if (!hasUsableTime(firstMeeting)) {
        continue;
      }
      for (String[][] secondMeeting : second.getMeetings()) {
        if (!hasUsableTime(secondMeeting) || !sharesDay(firstMeeting[0], secondMeeting[0])) {
          continue;
        }
        int firstStart = Integer.parseInt(firstMeeting[1][0]);
        int firstEnd = Integer.parseInt(firstMeeting[2][0]);
        int secondStart = Integer.parseInt(secondMeeting[1][0]);
        int secondEnd = Integer.parseInt(secondMeeting[2][0]);
        if (firstStart < secondEnd && secondStart < firstEnd) {
          return true;
        }
      }
    }
    return false;
  }

  private static boolean hasUsableTime(String[][] meeting) {
    return meeting.length >= 3
        && meeting[0].length > 0
        && meeting[1].length > 0
        && meeting[2].length > 0
        && meeting[1][0].matches("\\d{4}")
        && meeting[2][0].matches("\\d{4}");
  }

  private static boolean sharesDay(String[] firstDays, String[] secondDays) {
    for (String firstDay : firstDays) {
      for (String secondDay : secondDays) {
        if (firstDay.equalsIgnoreCase(secondDay)) {
          return true;
        }
      }
    }
    return false;
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
      System.out.println("What attribute do you want to update: " +
              "(\"name\", \"honors\", \"campus\", \"school\", \"completed\")");
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
              currentUser.setSchool(school);
            }
            default -> System.out.println("Invalid school name");
          }
        }
        case "completed" -> {
          validGiven = updateCompletedCourse(scan);
        }
        default -> {
          System.out.println("Invalid choice");
        }
      }
    }
  }

  /**
   * Prompts for a course code (e.g. "COMP1300") and toggles it in the
   * current user's completed-courses list. Takes the code directly rather
   * than looking it up by CRN, since completed courses may include ones
   * from past semesters that aren't in the currently loaded CSV at all.
   *
   * @return true (always accepts the input - a malformed code just won't
   *         match any real course's getCourseCode() later, which fails
   *         safe rather than causing an incorrect match).
   */
  private static boolean updateCompletedCourse(Scanner scan) {
    System.out.println("Give the course code of the class to mark completed "
            + "(or un-mark, if already completed) - e.g. COMP1300: ");
    String courseCode = scan.nextLine().trim().toUpperCase();
    if (currentUser.getCompletedCourses().contains(courseCode)) {
      currentUser.removeCompletedClass(courseCode);
      System.out.println("Removed " + courseCode + " from completed courses");
    } else {
      currentUser.addCompletedClass(courseCode);
      System.out.println("Added " + courseCode + " to completed courses");
    }
    return true;
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
    System.out.printf("%-30s %-10s %-6s %-10s %-10s %-7s %-8s %-20s %-7s %-11s %-15s%n%s%n",
            "Title", "Code", "Dpt.", "Course #", "Section", "Hours", "CRN", "Instructor", "Seats", "Seats Rem.", "Attributes",
            "-".repeat(142));
    for (int i = startingPoint; i < startingPoint + INCREMENT; i++) {
      if (i > courses.size()) {
        break;
      }
      Course course = courses.get(i - 1);
      System.out.printf("%-30.30s %-10s %-6s %-10s %-10s %-7s %-8s %-20.20s %-7s %-11s %-15s%n%s%n",
              course.getName(), course.getCourseCode(), course.getDepartment(), course.getDeptNumber(), course.getSection(), course.getCredits(),
              course.getCRN(), course.getTeacher(), course.getMaxEnrolled(), course.getRemainingEnrollment(),
              String.join(", ", course.getAttributes()), "-".repeat(142));
    }
    int endSpot = lastPrintedCourseIndex(startingPoint, courses.size());
    System.out.println("Classes " + startingPoint + " to " + endSpot + ". Out of " + courses.size());
  }

  static boolean hasNextPage(int startingPoint, int courseCount) {
    return startingPoint + INCREMENT <= courseCount;
  }

  static int lastPrintedCourseIndex(int startingPoint, int courseCount) {
    return Math.min(courseCount, startingPoint + INCREMENT - 1);
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
          currentUser.setCampus(scan.nextLine().trim().equalsIgnoreCase("wilf") ? "wilf" : "beren");
          System.out.println("What school are you in? (YC, Syms, Beren): ");
          currentUser.setSchool(scan.nextLine().trim());
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
