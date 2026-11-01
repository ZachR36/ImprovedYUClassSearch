import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
      + "To view a course's full description and requirements: \"details\" \n"
      + "To see your degree progress for your degree programs: \"progress\" \n"
      + "To get up to five recommended next courses: \"recommend\" \n"
      + "To build a compatible recommended schedule: \"plan schedule\" \n"
      + "To select a proposed schedule: \"select schedule\" \n"
      + "To view the selected proposed schedule: \"view selected schedule\" \n"
      + "To replace one selected course: \"substitute\" \n"
      + "To bookmark the selected schedule: \"accept schedule\" \n"
      + "To look up exact major/minor program names: \"list programs\" \n"
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
    try {
      DegreeProgram.setup(args[1]);
      System.out.println("All degree programs successfully loaded.");
    } catch (IOException e) {
      System.out.println("Failed to read degree program requirements file");
      e.printStackTrace();
    }
    try {
      String coreRequirementsPath = args.length >= 3 ? args[2] : "CoreRequirements.txt";
      CoreRequirementProgram.setup(coreRequirementsPath);
      System.out.println("All core requirement profiles successfully loaded.");
    } catch (IOException | IllegalArgumentException e) {
      System.out.println("Failed to read core requirement profiles");
      e.printStackTrace();
    }
    Scanner scan = new Scanner(System.in);
    int printingSpot = 1;
    UserSession.currentUser = new User();
    runUserCommand(scan, true);
    System.out.print("\033[H\033[2J");
    System.out.flush();
    boolean run = true;
    while (run) {
      System.out.println(actionPrompt);
      String selection = scan.nextLine().trim();
      try {
      switch (selection.toLowerCase()) {
        case "user" -> runUserCommand(scan, false);
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
          UserSession.bookmarkClass(ConsoleInput.read(scan));
        }
        case "interested" -> {
          System.out.println("Give the CRN of the class that you're interested in: ");
          UserSession.interestedInClass(ConsoleInput.read(scan));
        }
        case "get bookmarked" -> {
          CoursePrinter.coursePrinting(UserSession.getBookmarked(), 1);
          for (String warning : UserSession.getCorequisiteWarnings()) {
            System.out.println("Warning: " + warning);
          }
        }
        case "get interested" -> CoursePrinter.coursePrinting(UserSession.getInterested(), 1);
        case "details" -> displayCourseDetails(scan);
        case "progress" -> displayDegreeProgress();
        case "recommend" -> CoursePrinter.recommendationPrinting(RecommendationEngine.recommend(UserSession.currentUser));
        case "plan schedule" -> {
          SchedulePreferences preferences = readSchedulePreferences(scan);
          ScheduleSession.setPlans(SchedulePlanner.plan(UserSession.currentUser, preferences), preferences);
          CoursePrinter.schedulePlanPrinting(ScheduleSession.lastPlans);
        }
        case "select schedule" -> {
          System.out.println("Choose a schedule number from the most recent plan: ");
          try {
            if (ScheduleSession.select(Integer.parseInt(ConsoleInput.read(scan)))) System.out.println("Schedule selected.");
            else System.out.println("That schedule number is unavailable.");
          } catch (NumberFormatException e) { System.out.println("Enter a schedule number."); }
        }
        case "view selected schedule" -> {
          if (ScheduleSession.selectedPlan == null) {
            System.out.println("No proposed schedule is selected.");
          } else {
            CoursePrinter.schedulePlanPrinting(List.of(ScheduleSession.selectedPlan));
          }
        }
        case "substitute" -> {
          if (ScheduleSession.selectedPlan == null) {
            System.out.println("Select a proposed schedule first.");
            break;
          }
          System.out.println("Enter the CRN you want to replace: ");
          int crn;
          try { crn = Integer.parseInt(ConsoleInput.read(scan)); }
          catch (NumberFormatException e) { System.out.println("CRN must be a number."); break; }
          SubstitutionGoal goal = readSubstitutionGoal(scan);
          List<ScheduleSubstitution> substitutes = SchedulePlanner.substitutes(ScheduleSession.selectedPlan, crn,
              ScheduleSession.preferences, goal);
          CoursePrinter.substitutionPrinting(substitutes);
          if (!substitutes.isEmpty()) {
            System.out.println("Enter a replacement option number, or press Enter to keep the current course: ");
            String replacement = ConsoleInput.read(scan);
            if (!replacement.isBlank()) try {
              int replacementNumber = Integer.parseInt(replacement);
              if (replacementNumber >= 1 && replacementNumber <= substitutes.size()) {
                ScheduleSession.replace(crn, substitutes.get(replacementNumber - 1));
              } else System.out.println("No replacement was made.");
            } catch (NumberFormatException e) { System.out.println("No replacement was made."); }
          }
        }
        case "accept schedule" -> {
          if (!ScheduleSession.acceptSelected()) System.out.println("No selected schedule was added to bookmarks.");
        }
        case "list programs" -> {
          System.out.println("Search term (or leave blank to list every program): ");
          UserSetup.printProgramList(ConsoleInput.read(scan));
        }
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
      } catch (CommandCancelledException e) {
        System.out.println("Command cancelled. Returning to the main menu.");
      }
    }
  }

  /** Runs profile setup/update transactionally so cancelling never leaves a
   * partially edited profile behind. */
  private static void runUserCommand(Scanner scan, boolean setup) {
    User previous = new User(UserSession.currentUser);
    try {
      if (setup) {
        UserSetup.userSetUp(scan);
      } else {
        UserSetup.userChange(scan);
      }
    } catch (CommandCancelledException e) {
      UserSession.currentUser = previous;
      System.out.println("Command cancelled. Keeping the current user unchanged.");
    } catch (IOException e) {
      UserSession.currentUser = previous;
      System.out.println("Unable to complete the user command. Keeping the current user unchanged.");
    }
  }

  private static SchedulePreferences readSchedulePreferences(Scanner scan) {
    SchedulePreferences defaults = SchedulePreferences.defaults();
    double minimum = readDoublePreference(scan, "Minimum credits [12]: ", defaults.minimumCredits());
    double maximum;
    do {
      maximum = readDoublePreference(scan, "Maximum credits [15]: ", defaults.maximumCredits());
      if (maximum < minimum) System.out.println("Maximum credits must be at least the minimum.");
    } while (maximum < minimum);
    int majorCount = readIntPreference(scan, "Desired major courses [2]: ", defaults.desiredMajorCourses());
    int minorCount = readIntPreference(scan, "Desired minor courses [1]: ", defaults.desiredMinorCourses());
    System.out.println("Schedule strategy: major-first, balanced, minor-first, or core-first [major-first]: ");
    String strategyInput = ConsoleInput.read(scan);
    SchedulePreferences.Strategy strategy = switch (strategyInput.isBlank() ? "major-first" : strategyInput.toLowerCase()) {
      case "balanced" -> SchedulePreferences.Strategy.BALANCED;
      case "minor-first" -> SchedulePreferences.Strategy.MINOR_FIRST;
      case "core-first" -> SchedulePreferences.Strategy.CORE_FIRST;
      default -> SchedulePreferences.Strategy.MAJOR_FIRST;
    };
    java.util.List<TimeRange> preferred = readTimeRanges(scan,
        "Preferred class-time window(s), e.g. 1000-1500, 1600-1900. "
            + "A class must fit entirely within one window (Enter for no preference): ");
    java.util.List<TimeRange> avoid = readTimeRanges(scan,
        "Class-time window(s) to avoid, e.g. 0800-0900, 1700-1800. "
            + "Separate multiple windows with commas (Enter for none): ");
    System.out.println("For day distribution, type weekdays to balance all days, or list lighter days "
        + "(M T W R F), or press Enter for no preference:");
    String dayInput = ConsoleInput.read(scan);
    boolean balancedDays = dayInput.equalsIgnoreCase("weekdays") || dayInput.equalsIgnoreCase("balanced");
    java.util.Set<String> lightDays = new java.util.HashSet<>();
    if (!balancedDays) for (String day : dayInput.toUpperCase().split("\\s+")) {
      if (day.matches("M|T|W|R|F")) lightDays.add(day);
    }
    java.util.Set<String> preferredDepartments = readPreferenceTokens(scan,
        "Preferred departments/subjects, separated by spaces (e.g. COMP MATH; Enter for none): ");
    java.util.Set<String> preferredAttributes = readPreferenceTokens(scan,
        "Preferred course attributes, separated by spaces (e.g. HONR WRIN; Enter for none): ");
    System.out.println("Instructors to avoid when possible, separated by semicolons (Enter for none): ");
    java.util.Set<String> avoidedInstructors = new java.util.HashSet<>();
    for (String instructor : ConsoleInput.read(scan).split(";")) {
      if (!instructor.isBlank()) avoidedInstructors.add(instructor.trim().toLowerCase());
    }
    System.out.println("Favor sections with more open seats? (Y/N) [N]: ");
    SchedulePreferences.SeatPreference seatPreference = ConsoleInput.read(scan).equalsIgnoreCase("y")
        ? SchedulePreferences.SeatPreference.MORE_OPEN_SEATS
        : SchedulePreferences.SeatPreference.NO_PREFERENCE;
    return new SchedulePreferences(minimum, maximum, majorCount, minorCount, strategy,
        preferred, avoid, java.util.Set.copyOf(lightDays), balancedDays,
        preferredDepartments, preferredAttributes, java.util.Set.copyOf(avoidedInstructors), seatPreference);
  }

  private static java.util.Set<String> readPreferenceTokens(Scanner scan, String prompt) {
    System.out.println(prompt);
    java.util.Set<String> tokens = new java.util.HashSet<>();
    for (String token : ConsoleInput.read(scan).toUpperCase().split("\\s+")) {
      if (!token.isBlank()) tokens.add(token);
    }
    return java.util.Set.copyOf(tokens);
  }

  private static double readDoublePreference(Scanner scan, String prompt, double fallback) {
    while (true) {
      System.out.print(prompt);
      String input = ConsoleInput.read(scan);
      if (input.isBlank()) return fallback;
      try {
        double value = Double.parseDouble(input);
        if (value >= 0) return value;
      } catch (NumberFormatException ignored) { }
      System.out.println("Enter a non-negative number, or press Enter for the default.");
    }
  }

  private static int readIntPreference(Scanner scan, String prompt, int fallback) {
    while (true) {
      System.out.print(prompt);
      String input = ConsoleInput.read(scan);
      if (input.isBlank()) return fallback;
      try {
        int value = Integer.parseInt(input);
        if (value >= 0) return value;
      } catch (NumberFormatException ignored) { }
      System.out.println("Enter a non-negative whole number, or press Enter for the default.");
    }
  }

  private static java.util.List<TimeRange> readTimeRanges(Scanner scan, String prompt) {
    while (true) {
      System.out.print(prompt);
      String input = ConsoleInput.read(scan);
      if (input.isBlank()) return java.util.List.of();
      java.util.List<TimeRange> ranges = new java.util.ArrayList<>();
      boolean valid = true;
      for (String range : input.split(",")) {
        String[] pieces = range.trim().split("-");
        try {
          if (pieces.length != 2) { valid = false; break; }
          int start = Integer.parseInt(pieces[0].trim());
          int end = Integer.parseInt(pieces[1].trim());
          if (!validTime(start) || !validTime(end) || start >= end) { valid = false; break; }
          ranges.add(new TimeRange(start, end));
        } catch (NumberFormatException ignored) { valid = false; break; }
      }
      if (valid && !ranges.isEmpty()) return java.util.List.copyOf(ranges);
      System.out.println("Use one or more 24-hour windows separated by commas, such as 1000-1500, 1600-1900.");
    }
  }

  private static boolean validTime(int time) {
    return time >= 0 && time <= 2359 && time % 100 < 60;
  }

  private static SubstitutionGoal readSubstitutionGoal(Scanner scan) {
    System.out.println("""
        What should the replacement do?
          1) Different section of the same course
          2) Another course with the same broad role (major/minor/core)
          3) Prioritize a major requirement
          4) Prioritize a minor requirement
          5) Prioritize a core requirement
          6) Any compatible course that advances progress
        Enter a number [2]: """);
    String input = ConsoleInput.read(scan);
    return switch (input.isBlank() ? "2" : input) {
      case "1" -> SubstitutionGoal.DIFFERENT_SECTION;
      case "3" -> SubstitutionGoal.MAJOR;
      case "4" -> SubstitutionGoal.MINOR;
      case "5" -> SubstitutionGoal.CORE;
      case "6" -> SubstitutionGoal.ANY_PROGRESS;
      default -> SubstitutionGoal.SAME_ROLE;
    };
  }

  private static boolean chooseSearchOption(Scanner scan, boolean refine) {
    return chooseSearchOption(scan, refine, false);
  }

  /** Checks and prints progress toward the current user's degree programs.
   *  Program keys are canonicalized when a profile is created, updated, or
   *  loaded, so this method never prompts the student to disambiguate one. */
  private static void displayDegreeProgress() {
    User user = UserSession.currentUser;
    Map<String, DegreeProgram> catalog = DegreeProgram.getCatalog();
    if (catalog == null) {
      System.out.println("Degree program data was not loaded.");
      return;
    }
    DegreeProgram primaryMajor = DegreeProgram.findByCatalogKey(catalog, user.getMajor());
    List<CompletedCourse> degreeUsedCourses = new java.util.ArrayList<>();
    for (String majorKey : user.getMajors()) {
      DegreeProgram majorProgram = DegreeProgram.findByCatalogKey(catalog, majorKey);
      if (majorProgram != null) {
      DegreeProgressChecker.AllocationResult result = DegreeProgressChecker.allocate(majorProgram,
          user.getCompletedCourseRecords(), user.getSelectedProgramTrack(majorKey));
      degreeUsedCourses.addAll(result.usedCourses());
      CoursePrinter.degreeProgressPrinting(majorProgram.getName(), result.status());
      } else {
        System.out.println("Saved degree program key was not found: " + majorKey
            + ". Update your user profile to select a current catalog program.");
      }
    }
    for (String minorKey : user.getMinors()) {
      DegreeProgram minorProgram = DegreeProgram.findByCatalogKey(catalog, minorKey);
      if (minorProgram != null) {
        DegreeProgressChecker.AllocationResult result = DegreeProgressChecker.allocate(minorProgram,
            user.getCompletedCourseRecords(), user.getSelectedProgramTrack(minorKey));
        degreeUsedCourses.addAll(result.usedCourses());
        CoursePrinter.degreeProgressPrinting(minorProgram.getName(), result.status());
      } else {
        System.out.println("Saved minor program key was not found: " + minorKey
            + ". Update your user profile to select a current catalog program.");
      }
    }
    if (primaryMajor != null) {
      CoreRequirementProgram coreProgram = CoreRequirementProgram.findForUser(user);
      if (coreProgram != null) {
        List<CompletedCourse> coreEligibleCourses = new java.util.ArrayList<>(user.getCompletedCourseRecords());
        // CompletedCourse intentionally has identity semantics, so duplicate
        // repeatable offerings remain distinct and removeAll removes only the
        // exact record assigned to a major/minor.
        coreEligibleCourses.removeAll(degreeUsedCourses);
        RequirementStatus coreStatus = CoreRequirementChecker.check(coreProgram, coreEligibleCourses,
            user.getJewishStudiesTrack());
        CoursePrinter.degreeProgressPrinting(coreProgram.getName(), coreStatus);
      } else {
        System.out.println("No single core-requirement profile matches your saved school, campus, and primary major yet.");
      }
    }
    // Honors is intentionally read-only: its courses may also satisfy a
    // degree program or core requirement. It is printed last for a natural
    // degree-progress reading order.
    if (user.isHonors()) {
      CoursePrinter.degreeProgressPrinting("Honors Program", HonorsProgressChecker.check(user));
    }
  }

  private static void displayCourseDetails(Scanner scan) {
    System.out.println("Give the CRN of the class whose details you want to view: ");
    int crn;
    try {
      crn = Integer.parseInt(ConsoleInput.read(scan));
    } catch (NumberFormatException e) {
      System.out.println("CRN must be a number");
      return;
    }
    Course course = SearchEngine.searchByCRN(crn);
    if (course == null) {
      System.out.println("Course is not in database");
      return;
    }
    CoursePrinter.courseDetailsPrinting(course);
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
            crn = Integer.parseInt(ConsoleInput.read(scan));
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
          results = SearchEngine.searchByCampus(ConsoleInput.read(scan).toLowerCase());
          validGiven = true;
        }
        case "department" -> {
          System.out.println("Give the department that you want to search for (you may have multiple separated by spaces): ");
          results = SearchEngine.searchByDepartment(ConsoleInput.read(scan).toLowerCase());
          validGiven = true;
        }
        case "teacher" -> {
          System.out.println("Give the teacher that you want to search for (you may have multiple separated by spaces):");
          results = SearchEngine.searchByTeacher(ConsoleInput.read(scan).toLowerCase());
          validGiven = true;
        }
        case "credits" -> {
          System.out.println("Give the number of credits that you want to search for: ");
          try {
            double credits = Double.parseDouble(ConsoleInput.read(scan));
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
          String dept = ConsoleInput.read(scan).toLowerCase();
          System.out.println("Now give the course number: ");
          results = SearchEngine.searchByDeptAndNumber(dept, ConsoleInput.read(scan));
          validGiven = true;
        }
        case "name" -> {
          System.out.println("What is the name of the class? (You may give part of the name)");
          results.addAll(SearchEngine.searchByName(ConsoleInput.read(scan).toLowerCase()));
          validGiven = true;
        }
        case "attribute" -> {
          System.out.println("Give the attributes you want to search for (you may have multiple speparated by spaces): ");
          results = SearchEngine.searchByAttributes(ConsoleInput.read(scan).toLowerCase());
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
