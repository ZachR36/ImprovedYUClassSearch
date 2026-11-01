/** A 24-hour time window used as a soft schedule preference. */
public record TimeRange(int start, int end) {
  boolean contains(int meetingStart, int meetingEnd) {
    return meetingStart >= start && meetingEnd <= end;
  }

  boolean overlaps(int meetingStart, int meetingEnd) {
    return meetingStart < end && start < meetingEnd;
  }

  String display() {
    return String.format("%d:%02d–%d:%02d", start / 100, start % 100, end / 100, end % 100);
  }
}
