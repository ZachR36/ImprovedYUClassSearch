import java.net.*;
import java.net.http.*;

public class Course {
  private final int CRN;
  private final String deptNumber;
  private final String department;
  private final String section;
  private final String campus;
  private final String name;
  private final String courseDescription;
  private final double credits;
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
    this.CRN = Integer.parseInt(this.removeQuotes(info[0]));
    this.deptNumber = this.removeQuotes(info[1]);
    this.department = this.removeQuotes(info[2]);

    String sectionString = this.removeQuotes(info[3]);
    this.campus = this.removeQuotes(info[4]);
    if (!this.campus.toLowerCase().contains("wilf")) {
      this.section = sectionString.replaceAll("[^A-Z]", "");
    } else {
      this.section = sectionString;
    }
    this.name = this.removeQuotes(info[5]).replaceAll("&amp;", "&");
    this.credits = Double.parseDouble(info[6]);
    this.teacher = this.removeQuotes(info[7]).replace(";", ",");
    this.enrolled = Integer.parseInt(info[8]);
    this.maxEnrolled = Integer.parseInt(info[9]);
    this.remainingEnrollment = Integer.parseInt(info[10]);
    this.waitlist = Integer.parseInt(info[11]);
    this.maxWaitlist = Integer.parseInt(info[12]);
    this.remainingWaitlist = Integer.parseInt(info[13]);
    String[] meetings = this.removeQuotes(info[14]).split("::");
    String[][][] meetingsTwo = new String[meetings.length][][];
    for (int i = 0; i < meetings.length; i++) {
      String[] fields = meetings[i].split("}");
      meetingsTwo[i] = new String[fields.length][];
      for (int j = 0; j < fields.length; j++) {
        meetingsTwo[i][j] = fields[j].split("\\|\\|");
      }
    }
    this.meetings = meetingsTwo;
    this.attributes = this.removeQuotes(info[15]).replaceAll("&amp;", "&").split("\\|\\|");
    this.attributeDescriptions = this.removeQuotes(info[16]).split("\\|\\|");
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

      String[] lines = sharedClient.send(request, HttpResponse.BodyHandlers.ofString()).body().split("\\R");
      return lines[2];
    } catch (Exception e) {
      return "Error: " + e.getMessage();
    }
  }

  private String removeQuotes(String parent) {
    if (parent.isBlank() || parent.isEmpty()) {
      return "";
    } else {
      return parent.substring(1, parent.length() - 1);
    }
  }

  public int getCRN() {
    return CRN;
  }

  public String getDeptNumber() {
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

  public double getCredits() {
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
