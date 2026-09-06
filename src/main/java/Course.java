import java.net.*;
import java.net.http.*;
import java.util.*;

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
  private final String[][] prerequisites;    // from CSV column 18
  private final String[][] corequisites;     // from CSV column 19
  private final String[][] flexiblePrereqs;  // from CSV column 20
  private final String manualRequirementNotes; // from CSV column 21, raw, unparsed
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
    this.credits = Double.parseDouble(stripQuotesIfPresent(info[6]));
    this.teacher = this.removeQuotes(info[7]).replace(";", ",");
    this.enrolled = Integer.parseInt(stripQuotesIfPresent(info[8]));
    this.maxEnrolled = Integer.parseInt(stripQuotesIfPresent(info[9]));
    this.remainingEnrollment = Integer.parseInt(stripQuotesIfPresent(info[10]));
    this.waitlist = Integer.parseInt(stripQuotesIfPresent(info[11]));
    this.maxWaitlist = Integer.parseInt(stripQuotesIfPresent(info[12]));
    this.remainingWaitlist = Integer.parseInt(stripQuotesIfPresent(info[13]));
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
    this.prerequisites = parseRequirementGroups(stripQuotesIfPresent(info[17]));
    this.corequisites = parseRequirementGroups(stripQuotesIfPresent(info[18]));
    this.flexiblePrereqs = parseRequirementGroups(stripQuotesIfPresent(info[19]));
    this.manualRequirementNotes = stripQuotesIfPresent(info[20]);
    this.courseDescription = stripQuotesIfPresent(info[21]);
  }


  private static String[][] parseRequirementGroups(String raw) {
    if (raw == null || raw.isBlank()) {
      return new String[0][];
    }
    String[] groupTexts = raw.split(";;");
    String[][] groups = new String[groupTexts.length][];
    for (int i = 0; i < groupTexts.length; i++) {
      groups[i] = groupTexts[i].split("\\|");
    }
    return groups;
  }

  private String removeQuotes(String parent) {
    if (parent.isBlank() || parent.isEmpty()) {
      return "";
    } else {
      return parent.substring(1, parent.length() - 1);
    }
  }

  /** Removes one surrounding quote pair, while leaving unquoted fields unchanged. */
  private String stripQuotesIfPresent(String field) {
    if (field.length() >= 2 && field.startsWith("\"") && field.endsWith("\"")) {
      return field.substring(1, field.length() - 1);
    }
    return field;
  }

  public int getCRN() {
    return CRN;
  }

  public String getDeptNumber() {
    return deptNumber;
  }

  public String getCourseCode() {
    return department.toUpperCase() + deptNumber;
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

  public String[][] getPrerequisites() {
    return prerequisites;
  }
  public String[][] getCorequisites() {
    return corequisites;
  }
  public String[][] getFlexiblePrereqs() {
    return flexiblePrereqs;
  }
  public String getManualRequirementNotes() {
    return manualRequirementNotes;
  }
  public boolean prerequisitesSatisfied(Set<String> completedCourseCodes) {
    for (String[] group : prerequisites) {
      boolean groupSatisfied = false;
      for (String alternative : group) {
        if (completedCourseCodes.contains(alternative)) {
          groupSatisfied = true;
          break;
        }
      }
      if (!groupSatisfied) {
        return false;
      }
    }
    return true;
  }
  public boolean flexiblePrereqsSatisfied(Set<String> completedOrCurrentlyRegisteringCodes) {
    for (String[] group : flexiblePrereqs) {
      boolean groupSatisfied = false;
      for (String alternative : group) {
        if (completedOrCurrentlyRegisteringCodes.contains(alternative)) {
          groupSatisfied = true;
          break;
        }
      }
      if (!groupSatisfied) {
        return false;
      }
    }
    return true;
  }

  public String getCourseDescription() {
    return courseDescription;
  }
}
