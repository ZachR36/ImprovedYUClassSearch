import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/** Builds indexes over the loaded course data and performs course searches. */
public class SearchEngine {
  static HashMap<Integer, Course> mapByCRN = new HashMap<>();
  static HashMap<String, ArrayList<Course>> mapByDep = new HashMap<>();
  static HashMap<String, ArrayList<Course>> mapByTeacher = new HashMap<>();
  static HashMap<Double, ArrayList<Course>> mapByCredits = new HashMap<>();
  static HashMap<String, Course> mapByName = new HashMap<>();
  static HashMap<String, ArrayList<Course>> mapByAttribute = new HashMap<>();
  static HashMap<String, ArrayList<Course>> mapByCampus = new HashMap<>();

  static void setup(String path) throws IOException {
    Path csv = Path.of(path);
    try (BufferedReader reader = Files.newBufferedReader(csv)) {
      String line;
      while ((line = reader.readLine()) != null) {
        Course newCourse = new Course(line);
        mapByCRN.put(newCourse.getCRN(), newCourse);
        mapByName.put(newCourse.getName().toLowerCase(), newCourse);
        if (!mapByDep.containsKey(newCourse.getDepartment().toLowerCase())) {
          mapByDep.put(newCourse.getDepartment().toLowerCase(), new ArrayList<Course>());
        }
        mapByDep.get(newCourse.getDepartment().toLowerCase()).add(newCourse);
        if (!mapByTeacher.containsKey(newCourse.getTeacher().toLowerCase())) {
          mapByTeacher.put(newCourse.getTeacher().toLowerCase(), new ArrayList<Course>());
        }
        mapByTeacher.get(newCourse.getTeacher().toLowerCase()).add(newCourse);
        if (!mapByCredits.containsKey(newCourse.getCredits())) {
          mapByCredits.put(newCourse.getCredits(), new ArrayList<Course>());
        }
        mapByCredits.get(newCourse.getCredits()).add(newCourse);
        for (String attribute : newCourse.getAttributes()) {
          if (!mapByAttribute.containsKey(attribute.toLowerCase())) {
            mapByAttribute.put(attribute.toLowerCase(), new ArrayList<Course>());
          }
          mapByAttribute.get(attribute.toLowerCase()).add(newCourse);
        }
        if (!mapByCampus.containsKey(newCourse.getCampus())) {
          mapByCampus.put(newCourse.getCampus(), new ArrayList<Course>());
        }
        mapByCampus.get(newCourse.getCampus()).add(newCourse);
      }
    }
  }

  static Course searchByCRN(int CRN) {
    return mapByCRN.getOrDefault(CRN, null);
  }

  static ArrayList<Course> searchByCampus(String campus) {
    ArrayList<Course> results = new ArrayList<>();
    for (String key : mapByCampus.keySet()) {
      if (key.toLowerCase().contains(campus)) {
        results.addAll(mapByCampus.get(key));
      }
    }
    return results;
  }

  static ArrayList<Course> searchByDepartment(String departments) {
    ArrayList<Course> results = new ArrayList<>();
    for (String dept : departments.split(" ")) {
      if (mapByDep.get(dept) != null) {
        results.addAll(mapByDep.get(dept));
      }
    }
    return results;
  }

  static ArrayList<Course> searchByTeacher(String names) {
    ArrayList<Course> results = new ArrayList<>();
    for (String name : names.split(" ")) {
      for (String key : mapByTeacher.keySet()) {
        if (key.contains(name)) {
          results.addAll(mapByTeacher.get(key));
        }
      }
    }
    return results;
  }

  static Course searchByDeptAndNumber(String dept, String num) {
    Course resultCourse = null;
    ArrayList<Course> deptList = searchByDepartment(dept);
    for (Course course : deptList) {
      if (course.getDeptNumber().equals(num)) {
        resultCourse = course;
      }
    }
    return resultCourse;
  }

  static ArrayList<Course> searchByCredits(double credits) {
    return mapByCredits.getOrDefault(credits, new ArrayList<>());
  }

  static ArrayList<Course> searchByName(String nameFrag) {
    ArrayList<Course> result = new ArrayList<>();
    for (String name : mapByName.keySet()) {
      if (name.contains(nameFrag)) {
        result.add(mapByName.get(name));
      }
    }
    return result;
  }

  static ArrayList<Course> searchByAttributes(String attributes) {
    ArrayList<Course> results = new ArrayList<>();
    for (String attribute : attributes.split(" ")) {
      if (mapByAttribute.containsKey(attribute)) {
        results.addAll(mapByAttribute.get(attribute));
      }
    }
    return results;
  }

  // Currently unused: retained for a future decision about automatic filters.
  static ArrayList<Course> removeWrongCampus(ArrayList<Course> list) {
    ArrayList<Course> results = new ArrayList<>();
    String campus = UserSession.currentUser.getCampus();
    for (Course course : list) {
      if (campus.equals(course.getCampus())) {
        results.add(course);
      }
    }
    return results;
  }

  // Currently unused: retained for a future decision about automatic filters.
  static ArrayList<Course> removeHonors(ArrayList<Course> list) {
    ArrayList<Course> results = new ArrayList<>();
    for (Course course : list) {
      ArrayList<String> attributes = new ArrayList<>(Arrays.asList(course.getAttributes()));
      if (!attributes.contains("HONR")) {
        results.add(course);
      }
    }
    return results;
  }
}
