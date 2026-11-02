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
  // Until Beren's official maximum is available, keep generated plans within
  // a practical planning ceiling rather than letting asynchronous offerings
  // create an unrealistic course load.
  private static final double NON_WILF_MAX_CREDITS = 25.0;
  private static final int MAJOR_CAP = 3;
  private static final int MINOR_CAP = 3;
  private static final int CORE_CAP = 5;
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
        .thenComparing(Comparator.comparingDouble(SchedulePlan::credits).reversed()));
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
    Map<String, List<String>> reasons = requirementReasons(user, completed, coreProgram);
    State state = new State(user, completed, earlyDay, majorTargets, minorTargets, coreTargets, reasons);

    for (Course bookmarked : UserSession.getBookmarked()) {
      List<Course> bundle = state.bundleFor(bookmarked, new HashSet<>());
      if (bundle == null || !state.addBundle(bundle, Phase.FILLER, true)) return null;
    }
    // Recompute degree targets after every selection.  A CHOOSE_N requirement
    // must stop offering its other alternatives once one of them is planned.
    fillDegreePhase(state, user, user.getMajors(), Phase.MAJOR, MAJOR_CAP, variation);
    fillDegreePhase(state, user, user.getMinors(), Phase.MINOR, MINOR_CAP, variation + 7);
    fillCores(state, coreProgram, user, variation + 13);
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

  private static Map<String, List<String>> requirementReasons(User user, Set<String> completed,
                                                               CoreRequirementProgram coreProgram) {
    Map<String, List<String>> reasons = new HashMap<>();
    for (String key : user.getMajors()) {
      DegreeProgram program = DegreeProgram.findByCatalogKey(DegreeProgram.getCatalog(), key);
      if (program != null) for (String code : RecommendationEngine.degreeTargetCodes(program, completed,
          user.getSelectedProgramTrack(key))) addReason(reasons, code, "Advances major: " + program.getName());
    }
    for (String key : user.getMinors()) {
      DegreeProgram program = DegreeProgram.findByCatalogKey(DegreeProgram.getCatalog(), key);
      if (program != null) for (String code : RecommendationEngine.degreeTargetCodes(program, completed,
          user.getSelectedProgramTrack(key))) addReason(reasons, code, "Advances minor: " + program.getName());
    }
    if (coreProgram != null) {
      for (Map.Entry<String, List<String>> entry : RecommendationEngine.coreTargetReasons(coreProgram,
          RecommendationEngine.coreEligibleCompletedCourses(user), user.getJewishStudiesTrack()).entrySet()) {
        for (String reason : entry.getValue()) addReason(reasons, entry.getKey(),
            "Fulfills core: " + (reason.isBlank() ? coreProgram.getName() : reason));
      }
    }
    return reasons;
  }

  private static void addReason(Map<String, List<String>> reasons, String code, String reason) {
    reasons.computeIfAbsent(code, ignored -> new ArrayList<>()).add(reason);
  }

  private static void fillDegreePhase(State state, User user, Collection<String> programKeys,
                                      Phase phase, int cap, int variation) {
    for (int attempt = 0; attempt < SearchEngine.mapByCourseCode.size()
        && state.count(phase) < cap && !state.atCreditLimit(); attempt++) {
      Set<String> completedForProgram = new HashSet<>(state.completed);
      completedForProgram.addAll(state.plannedCodesFor(phase));
      Set<String> targets = degreeTargets(programKeys, user, completedForProgram);
      targets.removeAll(state.usedCodes);
      if (targets.isEmpty()) return;

      boolean added = false;
      List<String> codes = new ArrayList<>(targets);
      Collections.sort(codes);
      for (String code : rotate(codes, variation + attempt)) {
        for (Course offering : rotate(sortedOfferings(code), variation + attempt)) {
          List<Course> bundle = state.bundleFor(offering, new HashSet<>());
          if (bundle != null && state.count(phase) + state.countForPhase(bundle, phase) <= cap
              && state.addBundle(bundle, phase, false)) {
            added = true;
            break;
          }
        }
        if (added || state.atCreditLimit()) break;
      }
      if (!added) return;
    }
  }

  /** Re-evaluates core requirements after every selection, so satisfying a
   * CHOOSE_N branch (such as CUOT OR COWC) removes its sibling candidates. */
  private static void fillCores(State state, CoreRequirementProgram coreProgram, User user, int variation) {
    if (coreProgram == null) return;
    for (int attempt = 0; attempt < SearchEngine.mapByCourseCode.size()
        && state.count(Phase.CORE) < CORE_CAP && !state.atCreditLimit(); attempt++) {
      List<CompletedCourse> completedForCore = new ArrayList<>(
          RecommendationEngine.coreEligibleCompletedCourses(user));
      completedForCore.addAll(state.corePlannedRecords());
      Set<String> targets = RecommendationEngine.coreTargetCodes(coreProgram, completedForCore,
          user.getJewishStudiesTrack());
      targets.removeAll(state.usedCodes);
      if (targets.isEmpty()) return;
      boolean added = false;
      List<String> codes = new ArrayList<>(targets);
      Collections.sort(codes);
      for (String code : rotate(codes, variation + attempt)) {
        for (Course offering : rotate(sortedOfferings(code), variation + attempt)) {
          List<Course> bundle = state.bundleFor(offering, new HashSet<>());
          if (bundle != null && state.count(Phase.CORE) + state.countForPhase(bundle, Phase.CORE) <= CORE_CAP
              && state.addBundle(bundle, Phase.CORE, false)) {
            added = true;
            break;
          }
        }
        if (added || state.atCreditLimit()) break;
      }
      if (!added) return;
    }
  }

  private static void fillFillers(State state, Collection<Course> candidates, int variation) {
    List<Course> ordered = new ArrayList<>(candidates);
    ordered.sort(Comparator.comparing(Course::getCourseCode).thenComparingInt(Course::getCRN));
    for (Course course : rotate(ordered, variation)) {
      if (state.credits >= MIN_CREDITS || state.atCreditLimit()) return;
      if (state.usedCodes.contains(course.getCourseCode())
          || state.completed.contains(course.getCourseCode())
          // Do not use an unselected requirement alternative as a generic
          // filler: that would silently bypass a major/minor/core course cap.
          || state.isRequirementTarget(course.getCourseCode())) continue;
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
    private final Map<String, List<String>> requirementReasons;
    private final Map<Integer, List<String>> courseReasons = new LinkedHashMap<>();
    private final Map<Integer, Phase> coursePhases = new HashMap<>();
    private double credits;
    private int majors;
    private int minors;
    private int cores;
    private boolean creditLimitReached;

    private State(User user, Set<String> completed, String earlyDay, Set<String> majorTargets,
                  Set<String> minorTargets, Set<String> coreTargets,
                  Map<String, List<String>> requirementReasons) {
      this.user = user; this.completed = completed; this.earlyDay = earlyDay;
      this.majorTargets = majorTargets; this.minorTargets = minorTargets; this.coreTargets = coreTargets;
      this.requirementReasons = requirementReasons;
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

    private boolean isRequirementTarget(String code) {
      return majorTargets.contains(code) || minorTargets.contains(code) || coreTargets.contains(code);
    }

    private Set<String> plannedCodesFor(Phase desiredPhase) {
      Set<String> result = new HashSet<>();
      for (Course course : courses) {
        if (coursePhases.get(course.getCRN()) == desiredPhase) result.add(course.getCourseCode());
      }
      return result;
    }

    private List<CompletedCourse> corePlannedRecords() {
      List<CompletedCourse> records = new ArrayList<>();
      for (Course course : courses) {
        Phase phase = coursePhases.get(course.getCRN());
        if (phase == Phase.CORE || phase == Phase.FILLER) records.add(new CompletedCourse(course));
      }
      return records;
    }

    private boolean atCreditLimit() {
      return creditLimitReached || credits >= maximumCredits();
    }

    private double maximumCredits() {
      return user.getCampus().equalsIgnoreCase("wilf") ? WILF_MAX_CREDITS : NON_WILF_MAX_CREDITS;
    }

    private List<Course> bundleFor(Course course, Set<String> visiting) {
      if (usedCodes.contains(course.getCourseCode()) || completed.contains(course.getCourseCode())) return null;
      if (!baseEligible(course) || !visiting.add(course.getCourseCode())) return null;
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
            if (baseEligible(offering) && !conflictsBundle(offering, bundle)) { choice = offering; break; }
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

    private boolean baseEligible(Course course) {
      if (course.getRemainingEnrollment() <= 0 || !campusAllowed(course)
          || !course.prerequisitesSatisfied(completed)) return false;
      if (!fitsYeshivaTime(course)) return false;
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
        for (String day : meeting[0]) {
          // Friday is unrestricted for MYP; its Monday–Thursday schedule is
          // still constrained by afternoon yeshiva study.
          if (track.equals("MYP") && day.equalsIgnoreCase("F")) continue;
          boolean standard = start >= (track.equals("MYP") ? 1500 : 1300) && end <= 2000;
          boolean earlyMyp = track.equals("MYP") && day.equalsIgnoreCase(earlyDay)
              && start >= 1300 && end <= 1500;
          if (!standard && !earlyMyp) return false;
        }
      }
      return true;
    }

    private boolean addBundle(List<Course> bundle, Phase phase, boolean bookmarked) {
      if (bundle == null || bundle.isEmpty()) return bundle != null;
      double addedCredits = bundle.stream().mapToDouble(Course::getCredits).sum();
      if (credits + addedCredits > maximumCredits()) {
        creditLimitReached = true;
        return false;
      }
      for (Course course : bundle) {
        boolean existingBookmark = bookmarked && UserSession.bookmarked.containsKey(course.getCRN());
        boolean bookmarkStillFits = campusAllowed(course)
            && course.prerequisitesSatisfied(completed)
            && fitsYeshivaTime(course);
        Set<String> registering = new HashSet<>(completed);
        registering.addAll(usedCodes);
        for (Course bundledCourse : bundle) registering.add(bundledCourse.getCourseCode());
        if (usedCodes.contains(course.getCourseCode())
            || (existingBookmark && !bookmarkStillFits)
            || (!existingBookmark && !baseEligible(course))
            || !course.flexiblePrereqsSatisfied(registering)) return false;
        if (conflictsBundle(course, courses)) return false;
      }
      for (int i = 0; i < bundle.size(); i++) {
        Course course = bundle.get(i);
        courses.add(course); usedCodes.add(course.getCourseCode()); credits += course.getCredits();
        Phase coursePhase = phaseFor(course.getCourseCode());
        coursePhases.put(course.getCRN(), coursePhase);
        List<String> reasons = new ArrayList<>(requirementReasons.getOrDefault(course.getCourseCode(), List.of()));
        if (i > 0) reasons.add("Required strict corequisite added automatically.");
        if (bookmarked && UserSession.bookmarked.containsKey(course.getCRN())) {
          reasons.add("Already bookmarked; included as mandatory.");
        }
        if (coursePhase == Phase.FILLER) {
          reasons.add(UserSession.interested.containsKey(course.getCRN())
              ? "Interested course selected to reach the 12-credit minimum."
              : "Eligible course selected to reach the 12-credit minimum.");
        }
        courseReasons.put(course.getCRN(), List.copyOf(reasons));
        if (coursePhase == Phase.MAJOR) majors++;
        else if (coursePhase == Phase.MINOR) minors++;
        else if (coursePhase == Phase.CORE) cores++;
      }
      return true;
    }

    private SchedulePlan toPlan() {
      return new SchedulePlan(courses, credits, majors, minors, cores, courseReasons);
    }
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
}
