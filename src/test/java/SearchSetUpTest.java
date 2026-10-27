import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SearchSetUpTest {
  @TempDir
  Path tempDir;

  @Test
  void newUserStoresTheCanonicalCatalogKeyForTheirMajor() throws IOException {
    Path catalog = tempDir.resolve("programs.txt");
    Files.writeString(catalog, """
        === Test Major ===
        name: Test Major
        school: YC
        degreeType: BA

        COURSE_LIST: Required
          TEST1000
        """);
    DegreeProgram.setup(catalog.toString());

    UserSetup.userSetUp(new Scanner("make\nAlice\nn\nwilf\nyc\nTest Major\n\nn\n"));

    assertEquals("test major@yc", UserSession.currentUser.getMajor());
  }
}
