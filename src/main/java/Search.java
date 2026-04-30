import java.util.*;
import java.io.BufferedReader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Search
 */
public class Search {
  private static HashMap<Integer, Course> mapByCRN = new HashMap<>();
  private static User currentUser;
  private static HashMap<String, Course> mapByDep = new HashMap<>();
  private static HashMap<Integer, Course> interested = new HashMap<>();
  private static HashMap<Integer, Course> bookmarked = new HashMap<>();

  // TODO: Add the other data structures that we need to help out search functions
  public static void main(String[] args) {
    try {
      setup(args[0]);
    } catch (IOException e) {
      System.out.println("Failed to read CSV");
      e.printStackTrace();
    }
    Scanner scan = new Scanner(System.in);
    System.out.println("Do you want to use a pre-existing User object (Yes - give the path; No - \"no\"): ");
    setUser(scan.nextLine());
    // while(true){
    // // Give the user a list of things that they can do next, and they'll pick one
    // of those functions
    // }
  }

  private static User setUser(String user) {
    if (user.toLowerCase().equals("no")) {
      // TODO: Make new user, and ask if they want to save said user file
    } else {
      try {
        currentUser = new User(Path.of(user));
      } catch (IOException e) {
        System.out.println("Failed to read user file");
      }
    }
  }

  private static User changeUser(String user) {
    if (user.equals("guest")) {
    } else {
      try {
        currentUser = new User(Path.of(user));
      } catch (IOException e) {
        System.out.println("Failed to read user file");
      }
    }
    // In middle of running, the user can select to give a new Path to User info, or
    // can ask for "guest"
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
    Course course = mapByCRN.get(Integer.parseInt(crn));
    float credits = 0;
    for (Course course : bookmarked.values()) {
      if (course.getCredits() == 0) {
        credits += 0.5;
      } else {
        credits += course.getCredits();
      }
    }
    if ((currentUser.getCampus().toLowerCase().equals("wilf") && credits + course.getCredits() > 17.5)) {
      System.out.println("Over credit maximum");
      return false;
    }

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
