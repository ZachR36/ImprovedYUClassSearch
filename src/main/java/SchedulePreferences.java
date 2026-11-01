import java.util.List;
import java.util.Set;

/** Student-controlled constraints and priorities for one proposed semester. */
public record SchedulePreferences(double minimumCredits, double maximumCredits, int desiredMajorCourses,
                                  int desiredMinorCourses, Strategy strategy,
                                  List<TimeRange> preferredTimeWindows, List<TimeRange> avoidedTimeWindows,
                                  Set<String> lightDays, boolean balancedDays,
                                  Set<String> preferredDepartments, Set<String> preferredAttributes,
                                  Set<String> avoidedInstructors, SeatPreference seatPreference) {
  enum Strategy { MAJOR_FIRST, BALANCED, MINOR_FIRST, CORE_FIRST }
  enum SeatPreference { NO_PREFERENCE, MORE_OPEN_SEATS }

  static SchedulePreferences defaults() {
    return new SchedulePreferences(12, 15, 2, 1, Strategy.MAJOR_FIRST,
        List.of(), List.of(), Set.of(), false, Set.of(), Set.of(), Set.of(), SeatPreference.NO_PREFERENCE);
  }
}
