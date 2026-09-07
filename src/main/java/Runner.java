import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

/** Command-line entry point and search-option prompt loop. */
public class Runner {
  private static final String actionPrompt = "\nPlease choose an option from the following list: \n"
      + "To make a change to the user info: \"user\" \n"
      + "To search based on a certain criteria: \"search\" \n"
      + "To refine previous searches: \"refine\" \n"
      + "To add another criterion to previous search results: \"expand\" \n"
      + "To scroll to the next (>) or previous (<) page of classes. \n"
      + "To mark (or unmark) a class that you're interested in: \"interested\" \n"
      + "To get your \"Interested\" list: \"get interested\" \n"
      + "To bookmark (or unmark) a class (so that time slot will be reserved): \"bookmark\" \n"
      + "To get your \"Bookmarked\" list: \"get bookmarked\" \n"
      + "To clear the screen: \"clear\" \n"
      + "To exit the program: \"exit\"";

  public static void main(String[] args) throws IOException {
    try {
      SearchEngine.setup(args[0]);
      System.out.println("All courses successfully loaded.");
    } catch (IOException e) {
      System.out.println("Failed to read CSV");
      e.printStackTrace();
    }
    Scanner scan = new Scanner(System.in);
    int printingSpot = 1;
    UserSetup.userSetUp(scan);
    System.out.print("\033[H\033[2J");
    System.out.flush();
    boolean run = true;
    while (run) {
      System.out.println(actionPrompt);
      String selection = scan.nextLine().trim();
      switch (selection.toLowerCase()) {
        case "user" -> UserSetup.userChange(scan);
        case "search" -> {
          boolean success = chooseSearchOption(scan, false);
          if (success) {
            if (UserSession.currentSearchResults.isEmpty()) {
              System.out.println("\n There were no matches for this search.\n");
            } else {
              printingSpot = 1;
              System.out.print("\033[H\033[2J");
              System.out.flush();
              CoursePrinter.coursePrinting(UserSession.currentSearchResults, printingSpot);
            }
          }
        }
        case "refine" -> {
          if (UserSession.currentSearchResults.isEmpty()) {
            System.out.println("No previous search to refine");
          } else {
            boolean success = chooseSearchOption(scan, true);
            if (success) {
              if (UserSession.currentSearchResults.isEmpty()) {
                System.out.println("\n There were no matches for this search.\n");
              } else {
                printingSpot = 1;
                System.out.print("\033[H\033[2J");
                System.out.flush();
                CoursePrinter.coursePrinting(UserSession.currentSearchResults, printingSpot);
              }
            }
          }
        }
        case "expand" -> {
          if (UserSession.currentSearchResults.isEmpty()) {
            System.out.println("No previous search to expand");
          } else {
            boolean success = chooseSearchOption(scan, false, true);
            if (success) {
              printingSpot = 1;
              System.out.print("\033[H\033[2J");
              System.out.flush();
              CoursePrinter.coursePrinting(UserSession.currentSearchResults, printingSpot);
            }
          }
        }
        case "bookmark" -> {
          System.out.println("Give the CRN of the class that you want to bookmark: ");
          UserSession.bookmarkClass(scan.nextLine().trim());
        }
        case "interested" -> {
          System.out.println("Give the CRN of the class that you're interested in: ");
          UserSession.interestedInClass(scan.nextLine().trim());
        }
        case "get bookmarked" -> CoursePrinter.coursePrinting(UserSession.getBookmarked(), 1);
        case "get interested" -> CoursePrinter.coursePrinting(UserSession.getInterested(), 1);
        case "<" -> {
          System.out.print("\033[H\033[2J");
          System.out.flush();
          if (printingSpot - CoursePrinter.INCREMENT >= 1) {
            printingSpot -= CoursePrinter.INCREMENT;
          } else {
            printingSpot = 1;
          }
          CoursePrinter.coursePrinting(UserSession.currentSearchResults, printingSpot);
        }
        case ">" -> {
          System.out.print("\033[H\033[2J");
          System.out.flush();
          if (CoursePrinter.hasNextPage(printingSpot, UserSession.currentSearchResults.size())) {
            printingSpot += CoursePrinter.INCREMENT;
          }
          CoursePrinter.coursePrinting(UserSession.currentSearchResults, printingSpot);
        }
        case "clear" -> {
          System.out.print("\033[H\033[2J");
          System.out.flush();
        }
        case "exit" -> run = false;
      }
    }
  }

  private static boolean chooseSearchOption(Scanner scan, boolean refine) {
    return chooseSearchOption(scan, refine, false);
  }

  /**
   * Runs one criterion search. Refine intersects it with existing results;
   * expand adds its matches to the existing results.
   */
  private static boolean chooseSearchOption(Scanner scan, boolean refine, boolean expand) {
    boolean validGiven = false;
    ArrayList<Course> results = new ArrayList<>();
    while (!validGiven) {
      System.out.println("What criteria do you want to search for (spelling, not case, sensitive): "
          + "\n CRN, name, campus, department, teacher, credits, attribute, dept + course number \n"
          + "(\"exit\" if you want to cancel the search request)");
      String choice = scan.nextLine().trim();
      switch (choice.toLowerCase()) {
        case "crn" -> {
          System.out.println("Give the crn of the course you want to look at (Five digit number): ");
          int crn = 0;
          Course course = null;
          try {
            crn = Integer.parseInt(scan.nextLine().trim());
            if (crn < 10000 || crn > 99999) {
              throw new IllegalArgumentException();
            }
            validGiven = true;
          } catch (NumberFormatException e) {
            System.out.println("\nMake sure you're giving a number/only digits\n");
          } catch (IllegalArgumentException e) {
            System.out.println("\nYou must give a five digit number/a valid CRN\n");
          }
          if (validGiven) {
            course = SearchEngine.searchByCRN(crn);
          }
          if (course != null) {
            results.add(course);
          }
        }
        case "campus" -> {
          System.out.println("Give the campus you want to search for: ");
          results = SearchEngine.searchByCampus(scan.nextLine().toLowerCase().trim());
          validGiven = true;
        }
        case "department" -> {
          System.out.println("Give the department that you want to search for (you may have multiple separated by spaces): ");
          results = SearchEngine.searchByDepartment(scan.nextLine().toLowerCase().trim());
          validGiven = true;
        }
        case "teacher" -> {
          System.out.println("Give the teacher that you want to search for (you may have multiple separated by spaces):");
          results = SearchEngine.searchByTeacher(scan.nextLine().toLowerCase().trim());
          validGiven = true;
        }
        case "credits" -> {
          System.out.println("Give the number of credits that you want to search for: ");
          try {
            double credits = Double.parseDouble(scan.nextLine().trim());
            if (!Double.isFinite(credits) || credits < 0) {
              throw new NumberFormatException();
            }
            results = SearchEngine.searchByCredits(credits);
            validGiven = true;
          } catch (NumberFormatException e) {
            System.out.println("\nMake sure you're giving a valid, non-negative number\n");
          }
        }
        case "dept + course number" -> {
          System.out.println("First give the department: ");
          String dept = scan.nextLine().toLowerCase().trim();
          System.out.println("Now give the course number: ");
          Course course = SearchEngine.searchByDeptAndNumber(dept, scan.nextLine().trim());
          validGiven = true;
          if (course != null) {
            results.add(course);
          }
        }
        case "name" -> {
          System.out.println("What is the name of the class? (You may give part of the name)");
          results.addAll(SearchEngine.searchByName(scan.nextLine().trim().toLowerCase()));
          validGiven = true;
        }
        case "attribute" -> {
          System.out.println("Give the attributes you want to search for (you may have multiple speparated by spaces): ");
          results = SearchEngine.searchByAttributes(scan.nextLine().toLowerCase().trim());
          validGiven = true;
        }
        case "exit" -> {
          return false;
        }
        default -> System.out.println("Not a valid option. Try again.");
      }
    }
    if (refine) {
      UserSession.currentSearchResults.retainAll(results);
    } else if (expand) {
      for (Course course : results) {
        if (!UserSession.currentSearchResults.contains(course)) {
          UserSession.currentSearchResults.add(course);
        }
      }
    } else {
      UserSession.currentSearchResults = results;
    }
    return true;
  }
}
