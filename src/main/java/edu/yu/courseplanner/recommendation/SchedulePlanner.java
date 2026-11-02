package edu.yu.courseplanner.recommendation;

import edu.yu.courseplanner.catalog.CoreRequirementProgram;
import edu.yu.courseplanner.catalog.DegreeProgram;
import edu.yu.courseplanner.catalog.SearchEngine;
import edu.yu.courseplanner.model.CompletedCourse;
import edu.yu.courseplanner.model.Course;
import edu.yu.courseplanner.model.SchedulePlan;
import edu.yu.courseplanner.model.User;
import edu.yu.courseplanner.session.UserSession;
import java.util.*;

/**
 * Builds small, valid schedules in the fixed order: majors, minors, cores,
 * then interested/fallback fillers. Candidate generation is deterministic for
 * now so it can be tested reliably.
 */
public final class SchedulePlanner {
  private static final double MIN_CREDITS = 12.0;
  private static final double WILF_MAX_CREDITS = 17.5;
  private static final int MAJOR_CAP = 3;
  private static final int MINOR_CAP = 2;
  private static final int CANDIDATE_COUNT = 5;

  private SchedulePlanner() { }

  public static List<SchedulePlan> build(User user, String mypEarlyShiurDay) {
    if (user == null || DegreeProgram.getCatalog() == null) return List.of();
    List<SchedulePlan> plans = new ArrayList<>();
    for (int variation = 0; variation < 40 && plans.size() < CANDIDATE_COUNT; variation++) {
      State state = buildOne(user, normalizeEarlyDay(mypEarlyShiurDay), variation);
      if (state != null && state.credits >= MIN_CREDITS) {
        SchedulePlan plan = state.toPlan();
        if (plans.stream().noneMatch(existing -> sameSchedule(existing, plan))) plans.add(plan);
      }
    }
    plans.sort(Comparator.comparingInt(SchedulePlan::fulfilledRequirementCourses).reversed()
        .thenComparing(Comparator.comparingInt(SchedulePlan::degreeProgramCourses).reversed())
        .thenComparingDouble(SchedulePlan::credits).reversed());
    return List.copyOf(plans.subList(0, Math.min(3, plans.size())));
  }

  private static State buildOne(User user, String earlyDay, int variation) {
    Set<String> completed = new HashSet<>(user.getCompletedCourses());
    Set<String> majorTargets = degreeTargets(user.getMajors(), user, completed);
    Set<String> minorTargets = degreeTargets(user.getMinors(), user, completed);
    CoreRequirementProgram coreProgram = CoreRequirementProgram.findForUser(user);
    Set<String> coreTargets = coreProgram == null ? Set.of()
        : RecommendationEngine.coreTargetCodes(coreProgram,
            RecommendationEngine.coreEligibleCompletedCourses(user), user.getJewishStudiesTrack());
    State state = new State(user, completed, earlyDay, majorTargets, minorTargets, coreTargets);

    for (Course bookmarked : UserSession.getBookmarked()) {
      List<Course> bundle = state.bundleFor(bookmarked, new HashSet<>());
      if (bundle == null || !state.addBundle(bundle, Phase.FILLER, true)) return null;
    }
    fillPhase(state, majorTargets, Phase.MAJOR, MAJOR_CAP, variation);
    fillPhase(state, minorTargets, Phase.MINOR, MINOR_CAP, variation + 7);
    fillPhase(state, coreTargets, Phase.CORE, Integer.MAX_VALUE, variation + 13);
    fillFillers(state, UserSession.getInterested(), variation + 19);
    if (state.credits < MIN_CREDITS) {
      List<Course> catalog = new ArrayList<>(SearchEngine.mapByCRN.values());
      catalog.sort(Comparator.comparing(Course::getCourseCode).thenComparingInt(Course::getCRN));
      fillFillers(state, rotate(catalog, variation + 23), 0);
    }
    return state;
  }

  private static Set<String> degreeTargets(Collection<String> keys, User user, Set<String> completed) {
    Set<String> result = new LinkedHashSet<>();
    for (String key : keys) {
      DegreeProgram program = DegreeProgram.findByCatalogKey(DegreeProgram.getCatalog(), key);
      if (program != null) result.addAll(RecommendationEngine.degreeTargetCodes(program, completed,
          user.getSelectedProgramTrack(key)));
    }
    return result;
  }

  private static Phase classify(String code, Set<String> major, Set<String> minor, Set<String> core,
                                Set<String> alreadyUsed) {
    if (major.contains(code) && !alreadyUsed.contains(code)) return Phase.MAJOR;
    if (minor.contains(code) && !alreadyUsed.contains(code)) return Phase.MINOR;
    if (core.contains(code) && !alreadyUsed.contains(code)) return Phase.CORE;
    return Phase.FILLER;
  }

  private static void fillPhase(State state, Set<String> targets, Phase phase, int cap, int variation) {
    List<String> codes = new ArrayList<>(targets);
    Collections.sort(codes);
    for (String code : rotate(codes, variation)) {
      if (state.count(phase) >= cap || state.atCreditLimit()) return;
      if (state.usedCodes.contains(code)) continue;
      for (Course offering : rotate(sortedOfferings(code), variation)) {
        List<Course> bundle = state.bundleFor(offering, new HashSet<>());
        if (bundle != null && state.count(phase) + state.countForPhase(bundle, phase) <= cap
            && state.addBundle(bundle, phase, false)) break;
      }
    }
  }

  private static void fillFillers(State state, Collection<Course> candidates, int variation) {
    List<Course> ordered = new ArrayList<>(candidates);
    ordered.sort(Comparator.comparing(Course::getCourseCode).thenComparingInt(Course::getCRN));
    for (Course course : rotate(ordered, variation)) {
      if (state.credits >= MIN_CREDITS || state.atCreditLimit()) return;
      if (state.usedCodes.contains(course.getCourseCode())) continue;
      List<Course> bundle = state.bundleFor(course, new HashSet<>());
      if (bundle != null) state.addBundle(bundle, Phase.FILLER, false);
    }
  }

  private static List<Course> sortedOfferings(String code) {
    List<Course> offerings = new ArrayList<>(SearchEngine.searchByCourseCode(code));
    offerings.sort(Comparator.comparingInt(Course::getCRN));
    return offerings;
  }

  private static <T> List<T> rotate(List<T> source, int offset) {
    if (source.isEmpty()) return source;
    int start = Math.floorMod(offset, source.size());
    List<T> result = new ArrayList<>(source.size());
    result.addAll(source.subList(start, source.size()));
    result.addAll(source.subList(0, start));
    return result;
  }

  private static boolean sameSchedule(SchedulePlan first, SchedulePlan second) {
    Set<Integer> a = new TreeSet<>(), b = new TreeSet<>();
    for (Course course : first.courses()) a.add(course.getCRN());
    for (Course course : second.courses()) b.add(course.getCRN());
    return a.equals(b);
  }

  private static String normalizeEarlyDay(String earlyDay) {
    if (earlyDay == null) return "";
    String normalized = earlyDay.trim().toUpperCase(Locale.ROOT);
    return normalized.equals("T") || normalized.equals("R") ? normalized : "";
  }

  private enum Phase { MAJOR, MINOR, CORE, FILLER }

  private static final class State {
    private final User user;
    private final Set<String> completed;
    private final String earlyDay;
    private final List<Course> courses = new ArrayList<>();
    private final Set<String> usedCodes = new HashSet<>();
    private final Set<String> majorTargets;
    private final Set<String> minorTargets;
    private final Set<String> coreTargets;
    private double credits;
    private int majors;
    private int minors;
    private int cores;
    private boolean creditLimitReached;

    private State(User user, Set<String> completed, String earlyDay, Set<String> majorTargets,
                  Set<String> minorTargets, Set<String> coreTargets) {
      this.user = user; this.completed = completed; this.earlyDay = earlyDay;
      this.majorTargets = majorTargets; this.minorTargets = minorTargets; this.coreTargets = coreTargets;
    }

    private int count(Phase phase) {
      return switch (phase) { case MAJOR -> majors; case MINOR -> minors; case CORE -> cores; default -> 0; };
    }

    private int countForPhase(List<Course> bundle, Phase phase) {
      int count = 0;
      for (Course course : bundle) if (phaseFor(course.getCourseCode()) == phase) count++;
      return count;
    }

    private Phase phaseFor(String code) {
      if (majorTargets.contains(code)) return Phase.MAJOR;
      if (minorTargets.contains(code)) return Phase.MINOR;
      if (coreTargets.contains(code)) return Phase.CORE;
      return Phase.FILLER;
    }

    private boolean atCreditLimit() {
      return creditLimitReached || (user.getCampus().equalsIgnoreCase("wilf") && credits >= WILF_MAX_CREDITS);
    }

    private List<Course> bundleFor(Course course, Set<String> visiting) {
      if (usedCodes.contains(course.getCourseCode())) return List.of();
      if (!eligible(course) || !visiting.add(course.getCourseCode())) return null;
      List<Course> bundle = new ArrayList<>();
      bundle.add(course);
      Set<String> plannedCodes = new HashSet<>(usedCodes);
      plannedCodes.add(course.getCourseCode());
      for (String[] group : course.getCorequisites()) {
        if (matchesAny(group, completed) || matchesAny(group, plannedCodes)) continue;
        Course choice = null;
        for (String option : group) {
          for (Course offering : sortedOfferings(option)) {
            if (plannedCodes.contains(offering.getCourseCode())) { choice = offering; break; }
            if (eligible(offering) && !conflictsBundle(offering, bundle)) { choice = offering; break; }
          }
          if (choice != null) break;
        }
        if (choice == null) return null;
        List<Course> nested = bundleFor(choice, visiting);
        if (nested == null || conflictsBundleList(nested, bundle)) return null;
        bundle.addAll(nested);
        for (Course added : nested) plannedCodes.add(added.getCourseCode());
      }
      return bundle;
    }

    private boolean eligible(Course course) {
      if (course.getRemainingEnrollment() <= 0 || !campusAllowed(course)
          || !course.prerequisitesSatisfied(completed)) return false;
      Set<String> registering = new HashSet<>(completed);
      registering.addAll(usedCodes);
      if (!course.flexiblePrereqsSatisfied(registering) || !fitsYeshivaTime(course)) return false;
      for (Course existing : courses) if (UserSession.hasMeetingConflict(course, existing)) return false;
      return true;
    }

    private boolean campusAllowed(Course course) {
      String campus = course.getCampus().toLowerCase(Locale.ROOT);
      if (campus.contains("online")) return true;
      String userCampus = user.getCampus().toLowerCase(Locale.ROOT);
      return (userCampus.contains("wilf") && campus.contains("wilf"))
          || (userCampus.contains("beren") && campus.contains("beren"));
    }

    private boolean fitsYeshivaTime(Course course) {
      if (!user.getCampus().equalsIgnoreCase("wilf")) return true;
      String track = user.getJewishStudiesTrack() == null ? "" : user.getJewishStudiesTrack().toUpperCase(Locale.ROOT);
      if (!track.equals("MYP") && !track.equals("BMP")) return true;
      for (String[][] meeting : course.getMeetings()) {
        if (meeting.length < 3 || meeting[0].length == 0 || meeting[1].length == 0 || meeting[2].length == 0
            || !meeting[1][0].matches("\\d{4}") || !meeting[2][0].matches("\\d{4}")) continue;
        int start = Integer.parseInt(meeting[1][0]), end = Integer.parseInt(meeting[2][0]);
        boolean standard = start >= (track.equals("MYP") ? 1500 : 1300) && end <= 2000;
        boolean earlyMyp = track.equals("MYP") && (earlyDay.equals("T") || earlyDay.equals("R"))
            && allDaysAre(meeting[0], earlyDay) && start >= 1300 && end <= 1500;
        if (!standard && !earlyMyp) return false;
      }
      return true;
    }

    private boolean addBundle(List<Course> bundle, Phase phase, boolean bookmarked) {
      if (bundle == null || bundle.isEmpty()) return bundle != null;
      double addedCredits = bundle.stream().mapToDouble(Course::getCredits).sum();
      if (user.getCampus().equalsIgnoreCase("wilf") && credits + addedCredits > WILF_MAX_CREDITS) {
        creditLimitReached = true;
        return false;
      }
      for (Course course : bundle) {
        boolean existingBookmark = bookmarked && UserSession.bookmarked.containsKey(course.getCRN());
        boolean bookmarkStillFits = campusAllowed(course)
            && course.prerequisitesSatisfied(completed)
            && fitsYeshivaTime(course);
        if (usedCodes.contains(course.getCourseCode())
            || (existingBookmark && !bookmarkStillFits)
            || (!existingBookmark && !eligible(course))) return false;
        if (conflictsBundle(course, courses)) return false;
      }
      for (Course course : bundle) {
        courses.add(course); usedCodes.add(course.getCourseCode()); credits += course.getCredits();
        Phase coursePhase = phaseFor(course.getCourseCode());
        if (coursePhase == Phase.MAJOR) majors++;
        else if (coursePhase == Phase.MINOR) minors++;
        else if (coursePhase == Phase.CORE) cores++;
      }
      return true;
    }

    private SchedulePlan toPlan() { return new SchedulePlan(courses, credits, majors, minors, cores); }
  }

  private static boolean matchesAny(String[] options, Set<String> codes) {
    for (String option : options) if (codes.contains(option)) return true;
    return false;
  }
  private static boolean conflictsBundle(Course course, Collection<Course> others) {
    for (Course other : others) if (UserSession.hasMeetingConflict(course, other)) return true;
    return false;
  }
  private static boolean conflictsBundleList(Collection<Course> first, Collection<Course> second) {
    for (Course course : first) if (conflictsBundle(course, second)) return true;
    return false;
  }
  private static boolean allDaysAre(String[] days, String day) {
    if (days.length == 0) return false;
    for (String value : days) if (!value.equalsIgnoreCase(day)) return false;
    return true;
  }
}
