import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Course constructor using real lines from fall2026CoursesOriginal.csv.
 * fetchDescription() is excluded — tested separately once the network layer is
 * stable.
 *
 * Each test method names the CRN it is testing so failures are easy to trace
 * back
 * to the original CSV row.
 */
public class CourseTest {

  private static final String LINE_WITH_QUOTED_NUMERICS = "\"10001\",\"1001\",\"TEST\",\"A\",\"Beren\",\"Quoted Numerics\",\"0.5\",\"Professor\",\"2\",\"30\",\"28\",\"1\",\"5\",\"4\",\"M}0900}1000}SCW}101\",\"\",\"\"";

  // -------------------------------------------------------------------------
  // Shared test courses — constructed once, reused across related tests.
  // Using real CSV lines (quotes intact, as the Runner passes them in).
  // -------------------------------------------------------------------------

  // CRN 90495 — standard Wilf course, single meeting block, one attribute
  // "90495","1001","ACC","231","Wilf","Accounting Principles I",3,"Wax; Laurie
  // Faye",0,30,30,0,10,10,"M||W}1500}1615}FURST}F210","SYBC","Sy Syms-UG Business
  // Core"
  private static final String LINE_90495 = "\"90495\",\"1001\",\"ACC\",\"231\",\"Wilf\",\"Accounting Principles I\",3,\"Wax; Laurie Faye\",0,30,30,0,10,10,\"M||W}1500}1615}FURST}F210\",\"SYBC\",\"Sy Syms-UG Business Core\"";

  // CRN 90519 — honors course (single attribute HONR)
  // "90519","1001","ACC","241","Wilf","Accounting Principles I",3,"Crawford;
  // Constance J.",0,25,25,0,10,10,"M||W}1630}1745}FURST}F212","HONR","Honors
  // Course"
  private static final String LINE_90519 = "\"90519\",\"1001\",\"ACC\",\"241\",\"Wilf\",\"Accounting Principles I\",3,\"Crawford; Constance J.\",0,25,25,0,10,10,\"M||W}1630}1745}FURST}F212\",\"HONR\",\"Honors Course\"";

  // CRN 90465 — Beren campus, alphabetic section
  // "90465","1001","ACC","A","Beren","Accounting Principles I",3,"Hyman; Cody
  // Alyssa",0,30,30,0,5,5,"M||W}0900}1015}LEX215}","SYBC","Sy Syms-UG Business
  // Core"
  private static final String LINE_90465 = "\"90465\",\"1001\",\"ACC\",\"A\",\"Beren\",\"Accounting Principles I\",3,\"Hyman; Cody Alyssa\",0,30,30,0,5,5,\"M||W}0900}1015}LEX215}\",\"SYBC\",\"Sy Syms-UG Business Core\"";

  // CRN 90431 — two separate meeting blocks (split by ::)
  // "90431","1002","ACC","D1","Beren","Accounting Principles II",3,"Huang;
  // Henry",0,30,30,0,5,5,"M}1325}1440}LEX215}::W}1325}1440}TBA}","SYBC","Sy
  // Syms-UG Business Core"
  private static final String LINE_90431 = "\"90431\",\"1002\",\"ACC\",\"D1\",\"Beren\",\"Accounting Principles II\",3,\"Huang; Henry\",0,30,30,0,5,5,\"M}1325}1440}LEX215}::W}1325}1440}TBA}\",\"SYBC\",\"Sy Syms-UG Business Core\"";

  // CRN 93089 — multiple attributes (HONR||INTC||WRIN) and matching descriptions
  // "93089","1630","ART","621","Wilf","American Architecture",3,"Glassman;
  // Paul",0,15,15,0,5,5,"F}1000}1200}TBA}","HONR||INTC||WRIN","Honors
  // Course||YC-INTC Requirement||YC-WRIN Requirement"
  private static final String LINE_93089 = "\"93089\",\"1630\",\"ART\",\"621\",\"Wilf\",\"American Architecture\",3,\"Glassman; Paul\",0,15,15,0,5,5,\"F}1000}1200}TBA}\",\"HONR||INTC||WRIN\",\"Honors Course||YC-INTC Requirement||YC-WRIN Requirement\"";

  // CRN 90483 — empty attributes and descriptions (both "" in the CSV)
  // "90483","1101","ACC","231","Wilf","Intermediate Accounting I",3,"Resnik;
  // Jim",0,30,30,0,10,10,"M||W}1500}1615}FURST}F206","",""
  private static final String LINE_90483 = "\"90483\",\"1101\",\"ACC\",\"231\",\"Wilf\",\"Intermediate Accounting I\",3,\"Resnik; Jim\",0,30,30,0,10,10,\"M||W}1500}1615}FURST}F206\",\"\",\"\"";

  // CRN 93326 — two meeting blocks each with two days (M||W in both blocks)
  // "93326","6095","BIBL","ABC","Beren","Peshat and Derash...",2,"Levine;
  // Michelle J.",0,5,5,0,0,0,"M||W}0900}1100}SCW}BEIT
  // MDRSH::M||W}1116}1245}SCW}606","",""
  private static final String LINE_93326 = "\"93326\",\"6095\",\"BIBL\",\"ABC\",\"Beren\",\"Peshat and Derash in Medieval Ashkenazic Exegesis\",2,\"Levine; Michelle J.\",0,5,5,0,0,0,\"M||W}0900}1100}SCW}BEIT MDRSH::M||W}1116}1245}SCW}606\",\"\",\"\"";

  // -------------------------------------------------------------------------
  // CRN field
  // -------------------------------------------------------------------------

  @Test
  void crn_parsedCorrectly_standardCourse() {
    Course course = new Course(LINE_90495);
    assertEquals(90495, course.getCRN());
  }

  @Test
  void crn_parsedCorrectly_honorsCourse() {
    Course course = new Course(LINE_90519);
    assertEquals(90519, course.getCRN());
  }

  @Test
  void crn_parsedCorrectly_berenCampus() {
    Course course = new Course(LINE_90465);
    assertEquals(90465, course.getCRN());
  }

  // -------------------------------------------------------------------------
  // deptNumber field
  // -------------------------------------------------------------------------

  @Test
  void deptNumber_parsedCorrectly_standardCourse() {
    Course course = new Course(LINE_90495);
    assertEquals("1001", course.getDeptNumber());
  }

  @Test
  void deptNumber_parsedCorrectly_multipleAttributes() {
    Course course = new Course(LINE_93089);
    assertEquals("1630", course.getDeptNumber());
  }

  // -------------------------------------------------------------------------
  // department field
  // -------------------------------------------------------------------------

  @Test
  void department_parsedCorrectly_ACC() {
    Course course = new Course(LINE_90495);
    assertEquals("ACC", course.getDepartment());
  }

  @Test
  void department_parsedCorrectly_ART() {
    Course course = new Course(LINE_93089);
    assertEquals("ART", course.getDepartment());
  }

  @Test
  void department_parsedCorrectly_BIBL() {
    Course course = new Course(LINE_93326);
    assertEquals("BIBL", course.getDepartment());
  }

  // -------------------------------------------------------------------------
  // section field
  // -------------------------------------------------------------------------

  @Test
  void quotedNumericFieldsAreParsedCorrectly() {
    Course course = new Course(LINE_WITH_QUOTED_NUMERICS);
    assertEquals(0.5, course.getCredits());
    assertEquals(2, course.getEnrolled());
    assertEquals(30, course.getMaxEnrolled());
    assertEquals(28, course.getRemainingEnrollment());
    assertEquals(1, course.getWaitlist());
    assertEquals(5, course.getMaxWaitlist());
    assertEquals(4, course.getRemainingWaitlist());
  }

  @Test
  void section_parsedCorrectly_numericSection() {
    Course course = new Course(LINE_90495);
    assertEquals("231", course.getSection());
  }

  @Test
  void section_parsedCorrectly_alphabeticSection() {
    Course course = new Course(LINE_90465);
    assertEquals("A", course.getSection());
  }

  @Test
  void section_parsedCorrectly_alphanumericSection() {
    Course course = new Course(LINE_90431);
    assertEquals("D", course.getSection());
  }

  @Test
  void section_parsedCorrectly_multiLetterSection() {
    Course course = new Course(LINE_93326);
    assertEquals("ABC", course.getSection());
  }

  // -------------------------------------------------------------------------
  // campus field
  // -------------------------------------------------------------------------

  @Test
  void campus_parsedCorrectly_Wilf() {
    Course course = new Course(LINE_90495);
    assertEquals("Wilf", course.getCampus());
  }

  @Test
  void campus_parsedCorrectly_Beren() {
    Course course = new Course(LINE_90465);
    assertEquals("Beren", course.getCampus());
  }

  // -------------------------------------------------------------------------
  // name field
  // -------------------------------------------------------------------------

  @Test
  void name_parsedCorrectly_standardCourse() {
    Course course = new Course(LINE_90495);
    assertEquals("Accounting Principles I", course.getName());
  }

  @Test
  void name_parsedCorrectly_longName() {
    Course course = new Course(LINE_93326);
    assertEquals("Peshat and Derash in Medieval Ashkenazic Exegesis", course.getName());
  }

  @Test
  void name_parsedCorrectly_differentCourse() {
    Course course = new Course(LINE_93089);
    assertEquals("American Architecture", course.getName());
  }

  // -------------------------------------------------------------------------
  // credits field
  // -------------------------------------------------------------------------

  @Test
  void credits_parsedCorrectly_threeCredits() {
    Course course = new Course(LINE_90495);
    assertEquals(3, course.getCredits());
  }

  @Test
  void credits_parsedCorrectly_twoCredits() {
    Course course = new Course(LINE_93326);
    assertEquals(2, course.getCredits());
  }

  // -------------------------------------------------------------------------
  // teacher field — semicolons in CSV become commas
  // -------------------------------------------------------------------------

  @Test
  void teacher_semicolonReplacedWithComma() {
    // CSV has "Wax; Laurie Faye" — semicolons become commas
    Course course = new Course(LINE_90495);
    assertEquals("Wax, Laurie Faye", course.getTeacher());
  }

  @Test
  void teacher_multipleNamePartsWithSemicolon() {
    // "Crawford; Constance J." -> "Crawford, Constance J."
    Course course = new Course(LINE_90519);
    assertEquals("Crawford, Constance J.", course.getTeacher());
  }

  @Test
  void teacher_parsedCorrectly_noSemicolon() {
    // "Glassman; Paul" -> "Glassman, Paul"
    Course course = new Course(LINE_93089);
    assertEquals("Glassman, Paul", course.getTeacher());
  }

  // -------------------------------------------------------------------------
  // enrolled / maxEnrolled / remainingEnrollment
  // -------------------------------------------------------------------------

  @Test
  void enrolled_parsedCorrectly() {
    Course course = new Course(LINE_90495);
    assertEquals(0, course.getEnrolled());
  }

  @Test
  void maxEnrolled_parsedCorrectly_thirtySeats() {
    Course course = new Course(LINE_90495);
    assertEquals(30, course.getMaxEnrolled());
  }

  @Test
  void maxEnrolled_parsedCorrectly_fifteenSeats() {
    Course course = new Course(LINE_93089);
    assertEquals(15, course.getMaxEnrolled());
  }

  @Test
  void remainingEnrollment_parsedCorrectly_fullRemaining() {
    Course course = new Course(LINE_90495);
    assertEquals(30, course.getRemainingEnrollment());
  }

  // -------------------------------------------------------------------------
  // waitlist / maxWaitlist / remainingWaitlist
  // -------------------------------------------------------------------------

  @Test
  void waitlist_parsedCorrectly_zero() {
    Course course = new Course(LINE_90495);
    assertEquals(0, course.getWaitlist());
  }

  @Test
  void maxWaitlist_parsedCorrectly_ten() {
    Course course = new Course(LINE_90495);
    assertEquals(10, course.getMaxWaitlist());
  }

  @Test
  void maxWaitlist_parsedCorrectly_zero() {
    Course course = new Course(LINE_93326);
    assertEquals(0, course.getMaxWaitlist());
  }

  @Test
  void remainingWaitlist_parsedCorrectly_ten() {
    Course course = new Course(LINE_90495);
    assertEquals(10, course.getRemainingWaitlist());
  }

  @Test
  void remainingWaitlist_parsedCorrectly_five() {
    Course course = new Course(LINE_90465);
    assertEquals(5, course.getRemainingWaitlist());
  }

  // -------------------------------------------------------------------------
  // meetings field
  // -------------------------------------------------------------------------

  @Test
  void meetings_singleBlock_correctBlockCount() {
    // M||W}1500}1615}FURST}F210 — one block
    Course course = new Course(LINE_90495);
    assertEquals(1, course.getMeetings().length);
  }

  @Test
  void meetings_twoDaysInBlock_correctDayCount() {
    // M||W}... — the first field of the block splits by || into ["M","W"]
    Course course = new Course(LINE_90495);
    String[][] block = course.getMeetings()[0];
    assertEquals(2, block[0].length); // days: M and W
  }

  @Test
  void meetings_singleBlock_correctStartTime() {
    // M||W}1500}1615}FURST}F210 — second field (index 1) is start time
    Course course = new Course(LINE_90495);
    assertEquals("1500", course.getMeetings()[0][1][0]);
  }

  @Test
  void meetings_singleBlock_correctEndTime() {
    Course course = new Course(LINE_90495);
    assertEquals("1615", course.getMeetings()[0][2][0]);
  }

  @Test
  void meetings_singleBlock_correctBuilding() {
    Course course = new Course(LINE_90495);
    assertEquals("FURST", course.getMeetings()[0][3][0]);
  }

  @Test
  void meetings_singleBlock_correctRoom() {
    Course course = new Course(LINE_90495);
    assertEquals("F210", course.getMeetings()[0][4][0]);
  }

  @Test
  void meetings_twoBlocks_correctBlockCount() {
    // M}1325}1440}LEX215}::W}1325}1440}TBA} — two blocks separated by ::
    Course course = new Course(LINE_90431);
    assertEquals(2, course.getMeetings().length);
  }

  @Test
  void meetings_twoBlocks_firstBlockDay() {
    Course course = new Course(LINE_90431);
    assertEquals("M", course.getMeetings()[0][0][0]);
  }

  @Test
  void meetings_twoBlocks_secondBlockDay() {
    Course course = new Course(LINE_90431);
    assertEquals("W", course.getMeetings()[1][0][0]);
  }

  @Test
  void meetings_twoBlocksEachWithTwoDays_correctBlockCount() {
    // M||W}0900}1100}SCW}BEIT MDRSH::M||W}1116}1245}SCW}606
    Course course = new Course(LINE_93326);
    assertEquals(2, course.getMeetings().length);
  }

  @Test
  void meetings_twoBlocksEachWithTwoDays_firstBlockHasTwoDays() {
    Course course = new Course(LINE_93326);
    assertEquals(2, course.getMeetings()[0][0].length);
  }

  @Test
  void meetings_twoBlocksEachWithTwoDays_secondBlockStartTime() {
    Course course = new Course(LINE_93326);
    assertEquals("1116", course.getMeetings()[1][1][0]);
  }

  // -------------------------------------------------------------------------
  // attributes field
  // -------------------------------------------------------------------------

  @Test
  void attributes_singleAttribute_correctLength() {
    Course course = new Course(LINE_90495);
    assertEquals(1, course.getAttributes().length);
  }

  @Test
  void attributes_singleAttribute_correctValue() {
    Course course = new Course(LINE_90495);
    assertEquals("SYBC", course.getAttributes()[0]);
  }

  @Test
  void attributes_honorsAttribute_correctValue() {
    Course course = new Course(LINE_90519);
    assertEquals("HONR", course.getAttributes()[0]);
  }

  @Test
  void attributes_multipleAttributes_correctLength() {
    // HONR||INTC||WRIN
    Course course = new Course(LINE_93089);
    assertEquals(3, course.getAttributes().length);
  }

  @Test
  void attributes_multipleAttributes_correctValues() {
    Course course = new Course(LINE_93089);
    assertArrayEquals(new String[] { "HONR", "INTC", "WRIN" }, course.getAttributes());
  }

  @Test
  void attributes_emptyAttribute_arrayHasOneEmptyEntry() {
    // "" in CSV -> removeQuotes returns "" -> split("\\|\\|") gives [""]
    Course course = new Course(LINE_90483);
    assertEquals(1, course.getAttributes().length);
    assertEquals("", course.getAttributes()[0]);
  }

  // -------------------------------------------------------------------------
  // attributeDescriptions field
  // -------------------------------------------------------------------------

  @Test
  void attributeDescriptions_singleDescription_correctValue() {
    Course course = new Course(LINE_90495);
    assertEquals("Sy Syms-UG Business Core", course.getAttributeDescriptions()[0]);
  }

  @Test
  void attributeDescriptions_honorsDescription_correctValue() {
    Course course = new Course(LINE_90519);
    assertEquals("Honors Course", course.getAttributeDescriptions()[0]);
  }

  @Test
  void attributeDescriptions_multipleDescriptions_correctLength() {
    Course course = new Course(LINE_93089);
    assertEquals(3, course.getAttributeDescriptions().length);
  }

  @Test
  void attributeDescriptions_multipleDescriptions_correctValues() {
    Course course = new Course(LINE_93089);
    assertArrayEquals(
        new String[] { "Honors Course", "YC-INTC Requirement", "YC-WRIN Requirement" },
        course.getAttributeDescriptions());
  }

  @Test
  void attributeDescriptions_emptyDescription_arrayHasOneEmptyEntry() {
    Course course = new Course(LINE_90483);
    assertEquals(1, course.getAttributeDescriptions().length);
    assertEquals("", course.getAttributeDescriptions()[0]);
  }

  // -------------------------------------------------------------------------
  // attributes and descriptions stay in sync
  // -------------------------------------------------------------------------

  @Test
  void attributesAndDescriptions_sameLengthForMultipleAttributes() {
    Course course = new Course(LINE_93089);
    assertEquals(course.getAttributes().length, course.getAttributeDescriptions().length);
  }

  @Test
  void attributesAndDescriptions_sameLengthForEmptyAttributes() {
    Course course = new Course(LINE_90483);
    assertEquals(course.getAttributes().length, course.getAttributeDescriptions().length);
  }
}
