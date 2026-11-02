package edu.yu.courseplanner.model;

import java.util.List;

/** One explained course suggestion, with every currently viable section. */
public record Recommendation(Course course, List<Course> offerings, List<String> reasons,
                             List<String> cautions) { }
