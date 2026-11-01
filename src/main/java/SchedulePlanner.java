import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Builds schedules while selecting requirement-advancing courses, not afterward. */
public final class SchedulePlanner {
  private static final int BEAM_WIDTH = 20;
  private static final int RESULT_LIMIT = 3;

  private SchedulePlanner() { }

  static List<SchedulePlan> plan(User user, SchedulePreferences preferences) {
    if (user == null) return List.of();
    List<Recommendation> candidates = RecommendationEngine.eligibleRecommendations(user);
    List<Course> fixedCourses = List.copyOf(UserSession.getBookmarked());
    List<Partial> beam = List.of(new Partial(new ArrayList<>(fixedCourses), new ArrayList<>(), 0));

    for (String phase : phases(preferences.strategy())) {
      List<Recommendation> phaseCandidates = candidates.stream()
          .filter(candidate -> category(candidate).equals(phase)).toList();
      beam = expand(beam, phaseCandidates, preferences, phase);
    }
    return beam.stream()
        .filter(plan -> plan.credits >= preferences.minimumCredits())
        .sorted(Comparator.comparingInt((Partial plan) -> plan.score(preferences)).reversed())
        .limit(RESULT_LIMIT)
        .map(plan -> new SchedulePlan(fixedCourses, List.copyOf(plan.selections), plan.credits,
            plan.score(preferences), explanationsFor(fixedCourses, plan.selections, plan.credits, preferences)))
        .toList();
  }

  /** Explains the planner's decisions without exposing its internal score. */
  static List<String> explanationsFor(List<Course> fixedCourses, List<Recommendation> selections,
                                      double credits, SchedulePreferences preferences) {
    int majors = 0, minors = 0, cores = 0, corequisites = 0;
    for (Recommendation selection : selections) {
      switch (category(selection)) {
        case "minor" -> minors++;
        case "core" -> cores++;
        case "support" -> corequisites++;
        default -> majors++;
      }
    }
    List<String> explanations = new ArrayList<>();
    explanations.add("Strategy: " + strategyLabel(preferences.strategy()) + ". This plan includes "
        + majors + " major, " + minors + " minor, and " + cores + " core course(s)"
        + (corequisites == 0 ? "." : ", plus " + corequisites + " strict corequisite section(s)."));
    explanations.add("Requested course mix: up to " + preferences.desiredMajorCourses() + " major and "
        + preferences.desiredMinorCourses() + " minor course(s); total credits: " + credits + " of the "
        + preferences.minimumCredits() + "–" + preferences.maximumCredits() + " range.");
    explanations.add(fixedCourses.isEmpty()
        ? "No existing bookmarks were held fixed in this plan."
        : fixedCourses.size() + " existing bookmarked course(s) were kept fixed while new sections were added.");
    String timing = timingPreferenceExplanation(preferences);
    if (!timing.isEmpty()) explanations.add(timing);
    String courseQuality = courseQualityPreferenceExplanation(preferences);
    if (!courseQuality.isEmpty()) explanations.add(courseQuality);
    explanations.add("Every added section has available seats and passed campus, prerequisite, credit-limit, "
        + "and meeting-conflict checks.");
    return List.copyOf(explanations);
  }

  private static String strategyLabel(SchedulePreferences.Strategy strategy) {
    return switch (strategy) {
      case MAJOR_FIRST -> "major-first";
      case MINOR_FIRST -> "minor-first";
      case CORE_FIRST -> "core-first";
      case BALANCED -> "balanced";
    };
  }

  private static String timingPreferenceExplanation(SchedulePreferences preferences) {
    List<String> factors = new ArrayList<>();
    if (!preferences.preferredTimeWindows().isEmpty()) {
      factors.add("favored classes entirely within " + formatWindows(preferences.preferredTimeWindows()));
    }
    if (!preferences.avoidedTimeWindows().isEmpty()) {
      factors.add("avoided classes overlapping " + formatWindows(preferences.avoidedTimeWindows()));
    }
    if (!preferences.lightDays().isEmpty()) factors.add("kept "
        + String.join(", ", preferences.lightDays()) + " lighter when possible");
    if (preferences.balancedDays()) factors.add("favored balanced weekdays");
    return factors.isEmpty() ? "" : "Timing preferences applied: " + String.join("; ", factors) + ".";
  }

  private static String courseQualityPreferenceExplanation(SchedulePreferences preferences) {
    List<String> factors = new ArrayList<>();
    if (!preferences.preferredDepartments().isEmpty()) {
      factors.add("favored " + String.join(", ", preferences.preferredDepartments()) + " courses");
    }
    if (!preferences.preferredAttributes().isEmpty()) {
      factors.add("favored " + String.join(", ", preferences.preferredAttributes()) + " attributes");
    }
    if (!preferences.avoidedInstructors().isEmpty()) factors.add("avoided listed instructors when possible");
    if (preferences.seatPreference() == SchedulePreferences.SeatPreference.MORE_OPEN_SEATS) {
      factors.add("favored sections with more open seats");
    }
    return factors.isEmpty() ? "" : "Course preferences applied: " + String.join("; ", factors) + ".";
  }

  private static String formatWindows(List<TimeRange> windows) {
    return windows.stream().map(TimeRange::display).collect(java.util.stream.Collectors.joining(", "));
  }

  /** Rebuilds one open slot while every other section in the selected plan stays fixed. */
  static List<ScheduleSubstitution> substitutes(SchedulePlan plan, int removedCrn,
                                                SchedulePreferences preferences, SubstitutionGoal goal) {
    if (plan == null) return List.of();
    Recommendation removed = plan.selections().stream()
        .filter(item -> item.course().getCRN() == removedCrn).findFirst().orElse(null);
    if (removed == null) return List.of();
    List<Course> fixed = new ArrayList<>(plan.fixedCourses());
    for (Recommendation item : plan.selections()) if (item != removed) fixed.add(item.course());
    double fixedCredits = fixed.stream().mapToDouble(Course::getCredits).sum();
    return RecommendationEngine.eligibleRecommendations(UserSession.currentUser).stream()
        .filter(item -> matchesSubstitutionGoal(item, removed, goal))
        .flatMap(item -> item.offerings().stream().filter(offering ->
            offering.getCRN() != removedCrn
                &&
                fixedCredits + offering.getCredits() <= preferences.maximumCredits()
                && fixed.stream().noneMatch(existing -> existing.getCourseCode()
                    .equalsIgnoreCase(offering.getCourseCode()))
                && fixed.stream().noneMatch(existing -> UserSession.hasMeetingConflict(existing, offering)))
            .map(offering -> new Recommendation(offering, List.of(offering), item.reasons(), item.cautions())))
        .map(primary -> strictCorequisiteSubstitution(primary, fixed, preferences))
        .filter(java.util.Objects::nonNull)
        .sorted(Comparator.comparingInt((ScheduleSubstitution item) ->
                courseQualityScore(item.primary().course(), preferences)).reversed()
            .thenComparing(Comparator.comparingInt((ScheduleSubstitution item) ->
                item.primary().course().getRemainingEnrollment()).reversed())
            .thenComparing(item -> item.primary().course().getCourseCode()))
        .limit(3).toList();
  }

  /** Builds a complete substitute bundle or returns null when no compatible
   * strict corequisite offering exists. */
  private static ScheduleSubstitution strictCorequisiteSubstitution(Recommendation primary,
                                                                     List<Course> fixed,
                                                                     SchedulePreferences preferences) {
    List<Recommendation> bundle = new ArrayList<>();
    bundle.add(primary);
    if (!addStrictCorequisiteSubstitutions(bundle, fixed, preferences, new HashSet<>())) return null;
    return new ScheduleSubstitution(primary, List.copyOf(bundle.subList(1, bundle.size())));
  }

  private static boolean addStrictCorequisiteSubstitutions(List<Recommendation> bundle, List<Course> fixed,
                                                           SchedulePreferences preferences, Set<String> visiting) {
    for (int index = 0; index < bundle.size(); index++) {
      Course course = bundle.get(index).course();
      if (!visiting.add(course.getCourseCode())) continue;
      for (String[] group : course.getCorequisites()) {
        if (corequisiteGroupSatisfied(group, fixed, bundle)) continue;
        Recommendation companion = findCorequisiteOffering(group, course, fixed, bundle, preferences);
        if (companion == null) return false;
        bundle.add(companion);
      }
    }
    return true;
  }

  private static Recommendation findCorequisiteOffering(String[] group, Course requiredBy, List<Course> fixed,
                                                         List<Recommendation> bundle,
                                                         SchedulePreferences preferences) {
    for (String code : group) for (Course offering : SearchEngine.searchByCourseCode(code)) {
      if (!UserSession.canFitRecommendation(offering) || hasCourseCode(fixed, bundle, offering.getCourseCode())
          || conflictsWithBundle(offering, fixed, bundle)
          || creditsWith(fixed, bundle) + offering.getCredits() > preferences.maximumCredits()) continue;
      return new Recommendation(offering, List.of(offering),
          List.of("Strict corequisite for " + requiredBy.getCourseCode()), List.of());
    }
    return null;
  }

  private static boolean corequisiteGroupSatisfied(String[] group, List<Course> fixed,
                                                    List<Recommendation> bundle) {
    for (String code : group) if (UserSession.currentUser.getCompletedCourses().contains(code)
        || hasCourseCode(fixed, bundle, code)) return true;
    return false;
  }

  private static boolean hasCourseCode(List<Course> fixed, List<Recommendation> bundle, String code) {
    return fixed.stream().anyMatch(course -> course.getCourseCode().equalsIgnoreCase(code))
        || bundle.stream().anyMatch(item -> item.course().getCourseCode().equalsIgnoreCase(code));
  }

  private static boolean conflictsWithBundle(Course offering, List<Course> fixed, List<Recommendation> bundle) {
    return fixed.stream().anyMatch(course -> UserSession.hasMeetingConflict(offering, course))
        || bundle.stream().anyMatch(item -> UserSession.hasMeetingConflict(offering, item.course()));
  }

  private static double creditsWith(List<Course> fixed, List<Recommendation> bundle) {
    return fixed.stream().mapToDouble(Course::getCredits).sum()
        + bundle.stream().mapToDouble(item -> item.course().getCredits()).sum();
  }

  private static boolean matchesSubstitutionGoal(Recommendation candidate, Recommendation removed,
                                                 SubstitutionGoal goal) {
    return switch (goal) {
      case DIFFERENT_SECTION -> candidate.course().getCourseCode()
          .equalsIgnoreCase(removed.course().getCourseCode());
      case SAME_ROLE -> !candidate.course().getCourseCode().equalsIgnoreCase(removed.course().getCourseCode())
          && category(candidate).equals(category(removed));
      case MAJOR -> category(candidate).equals("major");
      case MINOR -> category(candidate).equals("minor");
      case CORE -> category(candidate).equals("core");
      case ANY_PROGRESS -> !candidate.course().getCourseCode().equalsIgnoreCase(removed.course().getCourseCode());
    };
  }

  private static List<Partial> expand(List<Partial> starting, List<Recommendation> candidates,
                                      SchedulePreferences preferences, String phase) {
    List<Partial> beam = starting;
    for (Recommendation recommendation : candidates) {
      List<Partial> next = new ArrayList<>(beam); // The course is optional.
      for (Partial partial : beam) for (Course offering : recommendation.offerings()) {
        int requestedCount = phase.equals("major") ? preferences.desiredMajorCourses()
            : phase.equals("minor") ? preferences.desiredMinorCourses() : Integer.MAX_VALUE;
        if (partial.countCategory(phase) >= requestedCount) continue;
        if (partial.hasCode(offering.getCourseCode()) || !partial.fits(offering)
            || partial.credits + offering.getCredits() > preferences.maximumCredits()) continue;
        int phaseBonus = switch (phase) {
          case "major" -> 30; case "minor" -> 20; default -> 10;
        };
        Partial bundled = partial.addWithStrictCorequisites(
            new Recommendation(offering, List.of(offering), recommendation.reasons(), List.of()),
            phaseBonus + timePreferenceScore(offering, preferences) + courseQualityScore(offering, preferences),
            preferences.maximumCredits(), new HashSet<>());
        if (bundled != null) next.add(bundled);
      }
      beam = next.stream().sorted(Comparator.comparingInt((Partial plan) -> plan.score()).reversed())
          .limit(BEAM_WIDTH).toList();
    }
    return beam;
  }

  private static List<String> phases(SchedulePreferences.Strategy strategy) {
    return switch (strategy) {
      case MINOR_FIRST -> List.of("minor", "major", "core");
      case CORE_FIRST -> List.of("core", "major", "minor");
      case BALANCED, MAJOR_FIRST -> List.of("major", "minor", "core");
    };
  }

  private static String category(Recommendation recommendation) {
    String reason = String.join(" ", recommendation.reasons()).toLowerCase();
    if (reason.contains("advances") && !reason.contains("core")) return "minor";
    if (reason.contains("fulfills")) return "core";
    if (reason.contains("strict corequisite")) return "support";
    return "major";
  }

  private static int timePreferenceScore(Course course, SchedulePreferences preferences) {
    int score = 0;
    for (String[][] meeting : course.getMeetings()) {
      if (meeting.length < 2 || meeting[0].length == 0 || meeting[1].length == 0
          || !meeting[1][0].matches("\\d{4}")) continue;
      if (meeting.length < 3 || meeting[2].length == 0 || !meeting[2][0].matches("\\d{4}")) continue;
      int start = Integer.parseInt(meeting[1][0]);
      int end = Integer.parseInt(meeting[2][0]);
      if (!preferences.preferredTimeWindows().isEmpty()) {
        boolean fitsAWindow = preferences.preferredTimeWindows().stream()
            .anyMatch(window -> window.contains(start, end));
        score += fitsAWindow ? 2 : -3;
      }
      if (preferences.avoidedTimeWindows().stream().anyMatch(window -> window.overlaps(start, end))) score -= 5;
      for (String day : meeting[0]) if (preferences.lightDays().contains(day.toUpperCase())) score -= 3;
    }
    return score;
  }

  /** Soft ranking only: none of these preferences excludes an eligible class. */
  private static int courseQualityScore(Course course, SchedulePreferences preferences) {
    int score = 0;
    if (preferences.preferredDepartments().contains(course.getDepartment().toUpperCase())) score += 4;
    for (String attribute : course.getAttributes()) {
      if (preferences.preferredAttributes().contains(attribute.toUpperCase())) score += 3;
    }
    String instructor = course.getTeacher().toLowerCase();
    for (String avoided : preferences.avoidedInstructors()) {
      if (!avoided.isBlank() && instructor.contains(avoided.toLowerCase())) score -= 8;
    }
    if (preferences.seatPreference() == SchedulePreferences.SeatPreference.MORE_OPEN_SEATS) {
      score += Math.min(5, course.getRemainingEnrollment() / 5);
    }
    return score;
  }

  private static final class Partial {
    private final List<Course> sections;
    private final List<Recommendation> selections;
    private final double credits;
    private final int points;

    private Partial(List<Course> sections, List<Recommendation> selections, int points) {
      this.sections = sections; this.selections = selections;
      this.credits = sections.stream().mapToDouble(Course::getCredits).sum();
      this.points = points;
    }
    private boolean hasCode(String code) {
      return sections.stream().anyMatch(section -> section.getCourseCode().equalsIgnoreCase(code));
    }
    private boolean fits(Course course) {
      return sections.stream().noneMatch(section -> UserSession.hasMeetingConflict(course, section));
    }
    private Partial add(Recommendation recommendation, int bonus) {
      List<Course> nextSections = new ArrayList<>(sections); nextSections.add(recommendation.course());
      List<Recommendation> nextSelections = new ArrayList<>(selections); nextSelections.add(recommendation);
      return new Partial(nextSections, nextSelections, points + bonus + recommendation.reasons().size());
    }
    /** Adds a section and every strict corequisite group it still needs, or fails
     * the branch when a complete compatible bundle cannot be formed. */
    private Partial addWithStrictCorequisites(Recommendation recommendation, int bonus,
                                              double maximumCredits, Set<String> visiting) {
      if (!visiting.add(recommendation.course().getCourseCode())) return this;
      Partial expanded = add(recommendation, bonus);
      for (String[] group : recommendation.course().getCorequisites()) {
        if (expanded.hasCompletedOrSelected(group)) continue;
        Partial resolved = null;
        for (String code : group) {
          for (Course offering : SearchEngine.searchByCourseCode(code)) {
            if (expanded.hasCode(offering.getCourseCode()) || !UserSession.canFitRecommendation(offering)
                || !expanded.fits(offering) || expanded.credits + offering.getCredits() > maximumCredits) continue;
            Recommendation corequisite = new Recommendation(offering, List.of(offering),
                List.of("Strict corequisite for " + recommendation.course().getCourseCode()), List.of());
            resolved = expanded.addWithStrictCorequisites(corequisite, 0, maximumCredits,
                new HashSet<>(visiting));
            if (resolved != null) break;
          }
          if (resolved != null) break;
        }
        if (resolved == null) return null;
        expanded = resolved;
      }
      return expanded;
    }
    private boolean hasCompletedOrSelected(String[] group) {
      Set<String> completed = UserSession.currentUser.getCompletedCourses();
      for (String code : group) if (completed.contains(code) || hasCode(code)) return true;
      return false;
    }
    private int countCategory(String category) {
      return (int) selections.stream().filter(selection ->
          SchedulePlanner.category(selection).equals(category)).count();
    }
    private int score() { return points + (int) Math.round(credits * 2); }
    private int score(SchedulePreferences preferences) {
      if (!preferences.balancedDays()) return score();
      int[] dayCounts = new int[5];
      for (Course section : sections) for (String[][] meeting : section.getMeetings()) {
        if (meeting.length == 0) continue;
        for (String day : meeting[0]) {
          int index = "MTWRF".indexOf(day.toUpperCase());
          if (index >= 0) dayCounts[index]++;
        }
      }
      int min = Integer.MAX_VALUE, max = 0;
      for (int count : dayCounts) if (count > 0) { min = Math.min(min, count); max = Math.max(max, count); }
      return score() - (min == Integer.MAX_VALUE ? 0 : (max - min) * 3);
    }
  }
}
