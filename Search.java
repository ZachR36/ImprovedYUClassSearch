import java.util.*;
import java.io.BufferedReader;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Search
 */
public class Search {
  private static HashMap<Integer, Course> mapByCRN = new HashMap<>();

  public static void main(String[] args) {
    try {
      setup(args[0]);
    } catch (IOException e) {
      System.out.println("Failed to read CSV");
      e.printStackTrace();
    }
  }

  private static void setup(String path) throws IOException {
    Path csv = Path.of(path);
    try (BufferedReader reader = Files.newBufferedReader(csv)) {
      String line;
      while ((line = reader.readLine()) != null) {
        Course newCourse = new Course(line);
        mapByCRN.put(newCourse.getCRN(), newCourse);
      }
    }
  }
}
