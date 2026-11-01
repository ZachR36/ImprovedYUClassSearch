import java.util.ArrayList;
import java.util.List;

/** Transient workspace for proposed schedules; nothing is bookmarked until accepted. */
public final class ScheduleSession {
  static List<SchedulePlan> lastPlans = List.of();
  static SchedulePlan selectedPlan = null;
  static SchedulePreferences preferences = SchedulePreferences.defaults();

  private ScheduleSession() { }

  static void setPlans(List<SchedulePlan> plans, SchedulePreferences selectedPreferences) {
    lastPlans = List.copyOf(plans);
    selectedPlan = null;
    preferences = selectedPreferences;
  }

  static boolean select(int number) {
    if (number < 1 || number > lastPlans.size()) return false;
    selectedPlan = lastPlans.get(number - 1);
    return true;
  }

  static boolean acceptSelected() {
    if (selectedPlan == null) return false;
    return UserSession.acceptScheduleAtomically(
        selectedPlan.selections().stream().map(Recommendation::course).toList());
  }

  static void replace(int crn, ScheduleSubstitution replacement) {
    List<Recommendation> next = new ArrayList<>(selectedPlan.selections());
    for (int i = 0; i < next.size(); i++) {
      if (next.get(i).course().getCRN() == crn) {
        double oldCredits = next.get(i).course().getCredits();
        next.remove(i);
        next.addAll(replacement.allSelections());
        double credits = selectedPlan.credits() - oldCredits
            + replacement.allSelections().stream().mapToDouble(item -> item.course().getCredits()).sum();
        selectedPlan = new SchedulePlan(selectedPlan.fixedCourses(), List.copyOf(next), credits,
            selectedPlan.score(), SchedulePlanner.explanationsFor(selectedPlan.fixedCourses(), next,
                credits, preferences));
        return;
      }
    }
  }
}
