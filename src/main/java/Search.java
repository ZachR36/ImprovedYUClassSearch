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
public class Search {
  private static User currentUser = null;
  private static HashMap<Integer, Course> mapByCRN = new HashMap<>();
  private static HashMap<String, Course> mapByDep = new HashMap<>();
  private static HashMap<Integer, Course> interested = new HashMap<>();
  private static HashMap<Integer, Course> bookmarked = new HashMap<>();
  private static ArrayList<Course> currentSearchResults = new ArrayList<>();

  // TODO: Add the other data structures that we need to help out search functions
  public static void main(String[] args) {
    try {
      setup(args[0]); // The user should give the csv file holding all the class information when
                      // starting the program.
    } catch (IOException e) {
      System.out.println("Failed to read CSV");
      e.printStackTrace();
    }
    Scanner scan = new Scanner(System.in);
    String selection = "";
    userSetUp(selection, scan);
    // This is the string that we'll print every time we want to prompt the user to
    // choose their next action
    String actionPrompt = "Please choose an option from the following list: \n ...";
    while (true) {
      System.out.println(actionPrompt);
      selection = scan.nextLine();
      // Now perform the action selected...
      switch (selection.toLowerCase()) {
        case "user" -> System.out.println("(Call method to update the user)");// Do something to the user;
        case "search" -> System.out.println("(Call corresponding search method)");
        case "etc" -> System.out.println("you get the idea");
      }

    }
  }

  private static void userSetUp(String selection, Scanner scan) {
    while (currentUser == null) {
      System.out.println("Do you want to use a pre-existing User object, create a new one, or use a guest one?" +
          " (Path,\"make\", or \"guest\"): ");
      selection = scan.nextLine();
      currentUser = setUser(selection);
    }
    if (selection.equalsIgnoreCase("make")) {
      System.out.println("Provide a user name: ");
      currentUser.setName(scan.nextLine());
      System.out.println("Are you in honors? (Y/N): ");
      currentUser.setHonors(scan.nextLine().equalsIgnoreCase("y"));
      System.out.println("What campus are you on? (Wilf/Beren): ");
      currentUser.setName(scan.nextLine().equalsIgnoreCase("wilf") ? "wilf" : "beren");
      System.out.println("What school are you in? (YC, Syms, Beren): ");
      currentUser.setName(scan.nextLine());
    }
  }

  private static User setUser(String selection) {
    if (selection.equalsIgnoreCase("guest") || selection.equalsIgnoreCase("make")) {
      return new User();
    } else {
      try {
        return new User(Path.of(selection));
      } catch (IOException e) {
        System.out.println("Failed to read user file");
      }
    }
    return new User();
  }

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

  /**
   * We need to implement all the search methods that we want to provide. The user
   * will tell us what kind
   * of search they want to do, and then the criteria they're giving, and then
   * this class will print out
   * a list of those classes.
   * 
   * @param something
   */
  private static void searchCriteriaNum1(String something) {

  }

  /**
   * User can search by multiple criteria at once, so we'll have to work that out
   * 
   * @param something
   */
  private static void compoundSearchMethods(String something) {

  }

  private static void setup(String path) throws IOException {
    Path csv = Path.of(path);
    try (BufferedReader reader = Files.newBufferedReader(csv)) {
      String line;
      while ((line = reader.readLine()) != null) {
        Course newCourse = new Course(line);
        mapByCRN.put(newCourse.getCRN(), newCourse);
        mapByDep.put(newCourse.getDepartment(), newCourse);
        // Add the courses to the other Data Structures here
      }
    }
  }
}
