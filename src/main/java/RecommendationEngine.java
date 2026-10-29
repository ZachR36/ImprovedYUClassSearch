import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds a small, explainable list of next courses from saved degree/core data. */
public final class RecommendationEngine {
  private static final int LIMIT = 5;
  private static final Pattern COURSE_CODE = Pattern.compile("^([A-Z]+)(\\d+)$");
  private static final Pattern RANGE = Pattern.compile("([A-Za-z|]+)\\s+(\\d+)-(\\d+)");
  private static final Pattern COUNT = Pattern.compile("(?:^|\\|)\\s*count:\\s*(\\d+)");
  private static final Pattern CREDITS = Pattern.compile("(?:^|\\|)\\s*credits:\\s*(\\d+(?:\\.\\d+)?)");

  private RecommendationEngine() { }

  static List<Recommendation> recommend(User user) {
    if (user == null || DegreeProgram.getCatalog() == null) return List.of();
    Set<String> completed = user.getCompletedCourses();
    Map<String, Target> targets = new LinkedHashMap<>();

    for (String key : user.getMajors()) {
      DegreeProgram program = DegreeProgram.findByCatalogKey(DegreeProgram.getCatalog(), key);
      if (program != null) collectDegreeTargets(program, completed, user.getSelectedProgramTrack(key),
          targets, 3, "Required for " + program.getName());
    }
    for (String key : user.getMinors()) {
      DegreeProgram program = DegreeProgram.findByCatalogKey(DegreeProgram.getCatalog(), key);
      if (program != null) collectDegreeTargets(program, completed, user.getSelectedProgramTrack(key),
          targets, 2, "Advances " + program.getName());
    }
    CoreRequirementProgram core = CoreRequirementProgram.findForUser(user);
    if (core != null) collectCoreTargets(core.getRequirements(), user.getCompletedCourseRecords(),
        user.getJewishStudiesTrack(), targets, "Fulfills " + core.getName());

    Set<String> unavailableCodes = new HashSet<>(completed);
    for (Course course : UserSession.bookmarked.values()) unavailableCodes.add(course.getCourseCode());
    List<Recommendation> recommendations = new ArrayList<>();
    for (Target target : targets.values()) {
      if (unavailableCodes.contains(target.courseCode)) continue;
      Course best = bestOffering(target.courseCode);
      if (best == null) continue;
      List<String> cautions = new ArrayList<>();
      for (String[] group : UserSession.missingStrictCorequisites(best)) {
        cautions.add("Also needs strict corequisite " + String.join(" OR ", group) + ".");
      }
      recommendations.add(new Recommendation(best, List.copyOf(target.reasons), List.copyOf(cautions)));
    }
    recommendations.sort(Comparator.comparingInt((Recommendation item) -> targets.get(item.course().getCourseCode()).priority)
        .reversed().thenComparing(Comparator.comparingInt((Recommendation item) -> item.course().getRemainingEnrollment()).reversed())
        .thenComparing(item -> item.course().getCourseCode()).thenComparingInt(item -> item.course().getCRN()));
    return List.copyOf(recommendations.subList(0, Math.min(LIMIT, recommendations.size())));
  }

  private static Course bestOffering(String code) {
    return SearchEngine.searchByCourseCode(code).stream().filter(UserSession::canFitRecommendation)
        .max(Comparator.comparingInt(Course::getRemainingEnrollment).thenComparingInt(Course::getCRN)).orElse(null);
  }

  private static void collectDegreeTargets(DegreeProgram program, Set<String> completed, String selectedTrack,
                                           Map<String, Target> targets, int priority, String reason) {
    for (DegreeProgram.RequirementNode node : program.getRequirements()) {
      collectDegreeNode(node, completed, selectedTrack, targets, priority, reason);
    }
  }

  private static void collectDegreeNode(DegreeProgram.RequirementNode node, Set<String> completed, String track,
                                        Map<String, Target> targets, int priority, String reason) {
    if (node instanceof DegreeProgram.CourseListRequirement list) {
      for (String code : list.getCourseCodes()) if (!completed.contains(code)) addTarget(targets, code, priority, reason);
    } else if (node instanceof DegreeProgram.RangeRequirement range) {
      if (!degreeNodeSatisfied(range, completed, track)) addRangeTargets(range.getDepartments().toArray(String[]::new), range.getRangeStart(),
          range.getRangeEnd(), targets, priority, reason + " elective/range requirement");
    } else if (node instanceof DegreeProgram.ChooseNRequirement choose) {
      int met = 0;
      for (DegreeProgram.Option option : choose.getOptions()) if (degreeOptionSatisfied(option, completed, track)) met++;
      if (met < choose.getN()) {
        for (DegreeProgram.Option option : choose.getOptions()) {
          if (option.isSimple()) {
            if (!completed.contains(option.getCourseCode())) addTarget(targets, option.getCourseCode(), priority,
                reason + " choice requirement");
          } else collectDegreeNode(option.getNestedContent(), completed, track, targets, priority,
              reason + " choice requirement");
        }
      }
    } else if (node instanceof DegreeProgram.TrackChoiceRequirement choice && track != null) {
      for (DegreeProgram.Track candidate : choice.getTracks()) {
        if (candidate.getName().equalsIgnoreCase(track.trim())) {
          for (DegreeProgram.RequirementNode child : candidate.getRequirements()) {
            collectDegreeNode(child, completed, track, targets, priority, reason + " (" + candidate.getName() + " track)");
          }
        }
      }
    }
  }

  private static boolean degreeOptionSatisfied(DegreeProgram.Option option, Set<String> completed, String track) {
    return option.isSimple() ? completed.contains(option.getCourseCode()) : degreeNodeSatisfied(option.getNestedContent(), completed, track);
  }

  private static boolean degreeNodeSatisfied(DegreeProgram.RequirementNode node, Set<String> completed, String track) {
    if (node instanceof DegreeProgram.CourseListRequirement list) return completed.containsAll(list.getCourseCodes());
    if (node instanceof DegreeProgram.RangeRequirement range) {
      int count = 0;
      for (String code : completed) if (matchesRange(code, range.getDepartments().toArray(String[]::new), range.getRangeStart(), range.getRangeEnd())) count++;
      return count >= (range.getCount() == null ? 1 : range.getCount());
    }
    if (node instanceof DegreeProgram.ChooseNRequirement choose) {
      int met = 0;
      for (DegreeProgram.Option option : choose.getOptions()) if (degreeOptionSatisfied(option, completed, track)) met++;
      return met >= choose.getN();
    }
    return true;
  }

  private static void collectCoreTargets(List<CoreRequirementProgram.RequirementNode> nodes,
                                         Collection<CompletedCourse> completed, String track,
                                         Map<String, Target> targets, String reason) {
    for (CoreRequirementProgram.RequirementNode node : nodes) collectCoreNode(node, completed, track, targets, reason);
  }

  private static void collectCoreNode(CoreRequirementProgram.RequirementNode node, Collection<CompletedCourse> completed,
                                      String track, Map<String, Target> targets, String reason) {
    switch (node.getType()) {
      case COURSE_LIST -> {
        for (CoreRequirementProgram.RequirementNode child : node.getChildren()) {
          if (child.getType() == CoreRequirementProgram.NodeType.COURSE_CODE && !hasCode(completed, child.getValue()))
            addTarget(targets, child.getValue(), 1, reason + ": " + label(node, "required course"));
        }
      }
      case CHOOSE_N -> {
        int required = Integer.parseInt(target(node.getValue()));
        int met = 0;
        for (CoreRequirementProgram.RequirementNode option : node.getChildren()) if (coreOptionSatisfied(option, completed, track)) met++;
        if (met < required) for (CoreRequirementProgram.RequirementNode option : node.getChildren()) {
          if (option.getChildren().isEmpty() && !option.getValue().isBlank()) {
            if (!hasCode(completed, option.getValue())) addTarget(targets, option.getValue(), 1, reason + " choice requirement");
          } else {
            collectCoreTargets(option.getChildren(), completed, track, targets, reason + " choice requirement");
          }
        }
      }
      case RANGE -> {
        Matcher matcher = RANGE.matcher(target(node.getValue()));
        if (matcher.matches() && countRange(completed, matcher.group(1), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)))
            < optionalCount(node.getValue(), 1)) addRangeTargets(matcher.group(1).split("\\|"), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)), targets, 1, reason + ": " + label(node, "range requirement"));
      }
      case ATTRIBUTE_COUNT -> {
        String attribute = canonicalAttribute(target(node.getValue()));
        if (countAttribute(completed, attribute) < optionalCount(node.getValue(), 1)) {
          for (ArrayList<Course> offerings : SearchEngine.mapByCourseCode.values()) for (Course course : offerings)
            if (hasAttribute(course, attribute)) addTarget(targets, course.getCourseCode(), 1, reason + ": " + attribute + " attribute");
        }
      }
      case COURSE_COUNT_FROM_DEPARTMENTS -> {
        String[] departments = target(node.getValue()).split("\\|");
        if (countDepartments(completed, departments) < optionalCount(node.getValue(), 1))
          addDepartmentTargets(departments, targets, reason + ": " + label(node, "subject requirement"));
      }
      case DISTINCT_SUBJECTS, DISTRIBUTION -> {
        Set<String> departments = subjects(node);
        int required = node.getType() == CoreRequirementProgram.NodeType.DISTRIBUTION
            ? optionalCount(node.getValue(), 1) : optionalCount(node.getValue(), 1);
        if (countDepartments(completed, departments.toArray(String[]::new)) < required)
          addDepartmentTargets(departments.toArray(String[]::new), targets, reason + ": " + label(node, "distribution requirement"));
      }
      case CREDIT_COUNT -> {
        String[] departments = target(node.getValue()).split("\\|");
        if (creditsInDepartments(completed, departments) < requiredCredits(node.getValue()))
          addDepartmentTargets(departments, targets, reason + ": " + label(node, "credit requirement"));
      }
      case COURSE_COUNT -> addAllCatalogTargets(targets, reason + ": " + label(node, "general elective"));
      case TRACK_CHOICE -> {
        if (track != null) for (CoreRequirementProgram.RequirementNode candidate : node.getChildren())
          if (matchesTrack(candidate.getValue(), track)) collectCoreTargets(candidate.getChildren(), completed, track, targets, reason);
      }
      default -> { }
    }
  }

  private static boolean coreOptionSatisfied(CoreRequirementProgram.RequirementNode option,
                                             Collection<CompletedCourse> completed, String track) {
    if (option.getChildren().isEmpty() && !option.getValue().isBlank()) return hasCode(completed, option.getValue());
    // A grouped option is recommendable only while one of its checkable slots is still open.
    for (CoreRequirementProgram.RequirementNode child : option.getChildren()) {
      if (child.getType() == CoreRequirementProgram.NodeType.COURSE_CODE && !hasCode(completed, child.getValue())) return false;
    }
    return !option.getChildren().isEmpty();
  }

  private static void addTarget(Map<String, Target> targets, String code, int priority, String reason) {
    String normalized = code.trim().toUpperCase(Locale.ROOT);
    if (!SearchEngine.mapByCourseCode.containsKey(normalized)) return;
    Target target = targets.computeIfAbsent(normalized, Target::new);
    target.priority = Math.max(target.priority, priority);
    if (!target.reasons.contains(reason)) target.reasons.add(reason);
  }

  private static void addRangeTargets(String[] departments, int start, int end, Map<String, Target> targets,
                                      int priority, String reason) {
    for (String code : SearchEngine.mapByCourseCode.keySet()) if (matchesRange(code, departments, start, end))
      addTarget(targets, code, priority, reason);
  }

  private static void addDepartmentTargets(String[] departments, Map<String, Target> targets, String reason) {
    Set<String> wanted = canonicalDepartments(departments);
    for (String code : SearchEngine.mapByCourseCode.keySet()) {
      Matcher matcher = COURSE_CODE.matcher(code);
      if (matcher.matches() && wanted.contains(canonicalDepartment(matcher.group(1)))) addTarget(targets, code, 1, reason);
    }
  }

  private static void addAllCatalogTargets(Map<String, Target> targets, String reason) {
    for (String code : SearchEngine.mapByCourseCode.keySet()) addTarget(targets, code, 1, reason);
  }

  private static boolean matchesRange(String code, String[] departments, int start, int end) {
    Matcher matcher = COURSE_CODE.matcher(code);
    if (!matcher.matches() || !canonicalDepartments(departments).contains(canonicalDepartment(matcher.group(1)))) return false;
    int number = Integer.parseInt(matcher.group(2));
    return number >= start && number <= end;
  }

  private static boolean hasCode(Collection<CompletedCourse> completed, String code) {
    for (CompletedCourse course : completed) if (course.getCourseCode().equalsIgnoreCase(code)) return true;
    return false;
  }
  private static int countRange(Collection<CompletedCourse> completed, String departments, int start, int end) {
    int count = 0; for (CompletedCourse course : completed) if (matchesRange(course.getCourseCode(), departments.split("\\|"), start, end)) count++; return count;
  }
  private static int countAttribute(Collection<CompletedCourse> completed, String attribute) {
    int count = 0; for (CompletedCourse course : completed) for (String value : course.getAttributes()) if (canonicalAttribute(value).equals(attribute)) { count++; break; } return count;
  }
  private static boolean hasAttribute(Course course, String attribute) {
    for (String value : course.getAttributes()) if (canonicalAttribute(value).equals(attribute)) return true;
    return false;
  }
  private static int countDepartments(Collection<CompletedCourse> completed, String[] departments) {
    Set<String> wanted = canonicalDepartments(departments); int count = 0;
    for (CompletedCourse course : completed) if (course.getDepartment() != null && wanted.contains(canonicalDepartment(course.getDepartment()))) count++;
    return count;
  }
  private static double creditsInDepartments(Collection<CompletedCourse> completed, String[] departments) {
    Set<String> wanted = canonicalDepartments(departments); double total = 0;
    for (CompletedCourse course : completed) if (course.getCredits() != null && course.getDepartment() != null && wanted.contains(canonicalDepartment(course.getDepartment()))) total += course.getCredits();
    return total;
  }
  private static int optionalCount(String value, int fallback) { Matcher m = COUNT.matcher(value); return m.find() ? Integer.parseInt(m.group(1)) : fallback; }
  private static double requiredCredits(String value) { Matcher m = CREDITS.matcher(value); return m.find() ? Double.parseDouble(m.group(1)) : 0; }
  private static String target(String value) { int index = value.indexOf(" | "); return (index < 0 ? value : value.substring(0, index)).trim(); }
  private static String label(CoreRequirementProgram.RequirementNode node, String fallback) { int index = node.getValue().indexOf("label:"); return index < 0 ? fallback : node.getValue().substring(index + 6).trim(); }
  private static boolean matchesTrack(String value, String track) { for (String choice : value.split("/")) if (choice.trim().equalsIgnoreCase(track.trim())) return true; return false; }
  private static String canonicalAttribute(String value) { String attr = value.trim().toUpperCase(Locale.ROOT); return attr.equals("WI") ? "WRIN" : attr; }
  private static Set<String> canonicalDepartments(String[] values) { Set<String> result = new HashSet<>(); for (String value : values) result.add(canonicalDepartment(value)); return result; }
  private static String canonicalDepartment(String value) {
    return switch (value.trim().toUpperCase(Locale.ROOT)) {
      case "ARTS" -> "ART"; case "MUSI" -> "MUS"; case "ENGL" -> "ENG"; case "HIST" -> "HIS";
      case "PHIL" -> "PHI"; case "ECON" -> "ECO"; case "BIOL" -> "BIO"; case "CHEM" -> "CHE";
      case "PHYS" -> "PHY"; case "POLI" -> "POL"; case "PSYC" -> "PSY"; case "SOCI" -> "SOC";
      default -> value.trim().toUpperCase(Locale.ROOT);
    };
  }
  private static Set<String> subjects(CoreRequirementProgram.RequirementNode node) {
    Set<String> result = new HashSet<>();
    for (CoreRequirementProgram.RequirementNode child : node.getChildren()) if (child.getType() == CoreRequirementProgram.NodeType.SUBJECT) {
      String subject = child.getValue().toUpperCase(Locale.ROOT);
      if (subject.equals("ECONOMICS")) result.add("ECO"); else if (subject.equals("HISTORY")) result.add("HIS");
      else if (subject.equals("PHILOSOPHY")) result.add("PHI"); else if (subject.equals("POLITICAL SCIENCE")) result.add("POL");
      else if (subject.equals("PSYCHOLOGY")) result.add("PSY"); else if (subject.equals("SOCIOLOGY")) result.add("SOC");
      else if (subject.equals("ART HISTORY")) result.add("ART"); else if (subject.equals("ENGLISH")) result.add("ENG");
      else if (subject.equals("MUSIC")) result.add("MUS"); else if (subject.equals("FOREIGN LANGUAGE")) result.addAll(Set.of("HEB", "SPA", "FRE", "GER", "YID"));
      else result.add(canonicalDepartment(subject));
    }
    return result;
  }

  private static final class Target {
    private final String courseCode;
    private int priority;
    private final List<String> reasons = new ArrayList<>();
    private Target(String courseCode) { this.courseCode = courseCode; }
  }
}
