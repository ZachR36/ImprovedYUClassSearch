import java.net.*;
import java.net.http.*;
import java.util.concurrent.CompletableFuture;

public class Course {
  private final int CRN;
  private final int deptNumber;
  private final String department;
  private final String section;
  private final String campus;
  private final String name;
  private final String courseDescription;
  private final int credits;
  private final String teacher;
  private final int enrolled;
  private final int maxEnrolled;
  private final int remainingEnrollment;
  private final int waitlist;
  private final int maxWaitlist;
  private final int remainingWaitlist;
  private final String[][][] meetings;
  private final String[] attributes;
  private final String[] attributeDescriptions;
  private static final HttpClient sharedClient = HttpClient.newBuilder()
      .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
      .build();

  public Course(String args) {
    String[] info = args.split(",");
    this.CRN = Integer.parseInt(info[0]);
    this.deptNumber = Integer.parseInt(info[1]);
    this.department = info[2];
    this.section = info[3];
    this.campus = info[4];
    this.name = info[5];
    this.credits = Integer.parseInt(info[6]);
    this.teacher = info[7];
    this.enrolled = Integer.parseInt(info[8]);
    this.maxEnrolled = Integer.parseInt(info[9]);
    this.remainingEnrollment = Integer.parseInt(info[10]);
    this.waitlist = Integer.parseInt(info[11]);
    this.maxWaitlist = Integer.parseInt(info[12]);
    this.remainingWaitlist = Integer.parseInt(info[13]);
    String[] meetings = info[14].split("::");
    String[][][] meetingsTwo = new String[meetings.length][][];
    for (int i = 0; i < meetings.length; i++) {
      String[] fields = meetings[i].split("}");
      meetingsTwo[i] = new String[fields.length][];
      for (int j = 0; j < fields.length; j++) {
        meetingsTwo[i][j] = fields[j].split("\\|\\|");
      }
    }
    this.meetings = meetingsTwo;
    this.attributes = info[15].split("\\|\\|");
    this.attributeDescriptions = info[16].split("\\|\\|");
    this.courseDescription = this.fetchDescription("202609", String.valueOf(this.CRN));
  }

  private String fetchDescription(String term, String crn) {
    try {
      String formData = "term=" + term + "&courseReferenceNumber=" + crn;

      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://banner.oci.yu.edu/StudentRegistrationSsb/ssb/searchResults/getCourseDescription"))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(formData))
          .build();

      // sharedClient automatically handles the cookies behind the scenes
      return sharedClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
    } catch (Exception e) {
      return "Error: " + e.getMessage();
    }
  }

  public int getCRN() {
    return CRN;
  }

  public int getDeptNumber() {
    return deptNumber;
  }

  public String getDepartment() {
    return department;
  }

  public String getSection() {
    return section;
  }

  public String getCampus() {
    return campus;
  }

  public String getName() {
    return name;
  }

  public int getCredits() {
    return credits;
  }

  public String getTeacher() {
    return teacher;
  }

  public int getEnrolled() {
    return enrolled;
  }

  public int getMaxEnrolled() {
    return maxEnrolled;
  }

  public int getRemainingEnrollment() {
    return remainingEnrollment;
  }

  public int getWaitlist() {
    return waitlist;
  }

  public int getMaxWaitlist() {
    return maxWaitlist;
  }

  public int getRemainingWaitlist() {
    return remainingWaitlist;
  }

  public String[][][] getMeetings() {
    return meetings;
  }

  public String[] getAttributes() {
    return attributes;
  }

  public String[] getAttributeDescriptions() {
    return attributeDescriptions;
  }

  public String getCourseDescription() {
    return courseDescription;
  }
}
