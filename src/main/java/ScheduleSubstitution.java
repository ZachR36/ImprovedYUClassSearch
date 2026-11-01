import java.util.List;

/** A replacement section plus any strict corequisite sections it must include. */
public record ScheduleSubstitution(Recommendation primary, List<Recommendation> corequisites) {
  List<Recommendation> allSelections() {
    java.util.ArrayList<Recommendation> selections = new java.util.ArrayList<>();
    selections.add(primary);
    selections.addAll(corequisites);
    return List.copyOf(selections);
  }
}
