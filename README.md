# YU Course Planner

A Java 21 command-line planning tool for Yeshiva University students. It turns a semester course catalog, degree requirements, and a student's academic history into practical answers:

- Which sections match a search or a set of filters?
- Can this section be bookmarked without a time, campus, credit, or prerequisite conflict?
- What remains for the student's major, minor, university core, and Honors requirement?
- Which eligible courses advance the student's progress next semester?
- Which complete schedules satisfy as many outstanding requirements as possible?

The project is intentionally explainable. Recommendations and planned courses state *why* they were selected instead of relying on an opaque similarity score.

## Highlights

- Search by CRN, title, campus, department, instructor, credits, attributes, or course code.
- Refine a search with an additional constraint or expand it with an alternative constraint.
- Bookmark and save courses while checking campus eligibility, credit limits, meeting conflicts, prerequisites, and flexible prerequisites/corequisites.
- Track completed courses, including prior-semester courses with custom credits, subjects, and attributes.
- Support repeated completed courses when a course is legitimately repeatable.
- Report progress for multiple majors/minors, selected concentration tracks, core requirements, Jewish-studies track, and Honors courses.
- Generate up to five next-course recommendations and up to three requirement-driven schedule options.
- Save and reload user profiles, including academic programs, completed-course records, and selected tracks.

## How it works

The CLI loads three local data sources:

| File | Purpose |
| --- | --- |
| `fall2026CoursesFinal.csv` | Semester offerings, meeting times, seats, attributes, descriptions, and prerequisite/corequisite data. |
| `MajorMinorRequirements.txt` | Major and minor requirement trees, including ranges, choices, and concentration tracks. |
| `CoreRequirements.txt` | Core graduation profiles selected from the student's school, campus, degree type, and primary major. |

`Runner` is the entry point. It coordinates user input and delegates domain work to focused packages:

```text
cli/             commands, prompts, and presentation
catalog/         catalog indexes and requirement-file loaders
model/           Course, User, CompletedCourse, Recommendation, SchedulePlan
session/         active user, bookmarks, interested courses
progress/        degree, core, and Honors progress evaluation
recommendation/  course recommendations and schedule construction
```

## Key algorithmic decisions

### Fast catalog lookup

`SearchEngine` builds indexes by CRN, course code, department, instructor, campus, credits, title, and attribute. This avoids repeatedly scanning the CSV for common queries and makes the search/refine/expand workflow responsive.

### Requirement trees instead of hard-coded majors

Degree and core requirements are parsed into small tree structures. The model supports required course lists, `CHOOSE_N` choices, department/number ranges, selected tracks, attribute counts, course counts, credit counts, and subject distributions. That lets new programs be added primarily as data rather than as new Java conditionals.

### Correct credit allocation

The progress checker allocates specific completed-course records to degree requirements first, then passes the remaining records to the core checker. This prevents a course from incorrectly satisfying both a degree requirement and a core requirement. Honors is evaluated independently because an Honors designation can intentionally overlap another requirement. Major/minor overlap remains allowed under the current project policy.

### Eligibility-aware recommendations

Before a course is recommended, the engine checks that it has open seats, is offered on the student's campus (or online), has no conflict with bookmarks, and has all strict prerequisites completed. Flexible prerequisites may be completed or taken concurrently; strict corequisites are surfaced as a clear note.

### Schedule construction, not post-hoc packing

The scheduler builds a tentative schedule incrementally rather than recommending a large list and trying to pack it afterward. Its fixed strategy is:

1. Include bookmarked sections as mandatory.
2. Add up to three courses that advance majors.
3. Add up to three courses that advance minors.
4. Add up to five courses that advance core requirements.
5. Fill only enough remaining space to reach the 12-credit minimum, favoring interested courses first.

Every addition must satisfy prerequisite, corequisite, seat, campus, time-conflict, and Yeshiva-program constraints. Wilf schedules are capped at 17.5 credits; non-Wilf schedules use a 25-credit planning safety ceiling. The planner reevaluates `CHOOSE_N` requirements after each selection, so it does not fill a schedule with unnecessary alternatives. It ranks distinct schedules first by the number of requirement-advancing courses and then by major/minor progress.

## Data engineering challenges

The original registration export did not contain enough structured information to power prerequisite-aware planning. The project therefore preserves a one-time data pipeline in `HelperClasses/`:

- `DescriptionFetcher.java` retrieved course descriptions from the registration system.
- `PrereqExtractor.java` extracted strict prerequisites, strict corequisites, and flexible prerequisite/corequisite relationships into separate CSV fields.
- `PrereqAudit.java` supports auditing extracted requirements against source descriptions.

The final CSV uses a compact requirement grammar: `;;` means **AND**, while `|` means **OR**. For example, `ACC1001;;IDS1015|IDS1020` means `ACC1001` and either `IDS1015` or `IDS1020`.

Some real requirements are not course-based—for example, placement exams, instructor permission, or standing requirements. Those are retained as manual notes rather than being incorrectly modeled as impossible course prerequisites. The parser also tolerates quoted and unquoted numeric CSV fields, which protects the application from common spreadsheet-export changes.

## Running locally

### Prerequisites

- Java 21
- Maven 3.9+ recommended

From the repository root:

```bash
mvn test
mvn exec:java -Dexec.args="fall2026CoursesFinal.csv MajorMinorRequirements.txt CoreRequirements.txt"
```

The third argument is optional when `CoreRequirements.txt` is in the working directory.

```bash
mvn exec:java -Dexec.args="fall2026CoursesFinal.csv MajorMinorRequirements.txt"
```

Once the CLI starts, type `make`, `pre`, or `guest` to choose a profile flow, then use commands such as `search`, `bookmark`, `progress`, `recommend`, and `schedule`. Type `exit` during a supported prompt to return safely to the main menu.

## Testing

The JUnit suite covers catalog parsing and searching, profile persistence, completed-course collisions, requirement allocation, core selection, Honors tracking, recommendation eligibility, corequisite handling, and schedule constraints.

```bash
mvn test
```

## Limitations and scope

- The catalog is a Fall 2026 snapshot, not a live registration feed.
- Degree/core data is a planning aid, not an official graduation audit. Students should confirm decisions with an academic advisor.
- Some administrative rules remain manual notes because the catalog does not provide a reliable machine-readable representation.
- The 25-credit non-Wilf schedule ceiling is a conservative planner guardrail, not a claim about university policy.
- The current interface is a CLI; a graphical/web front end is the next planned milestone.

## What I learned

This project grew from a search utility into a rules-driven planning system. The work emphasized designing data representations before adding features, separating session state from persistent student data, translating prose requirements into testable structures, and validating algorithmic behavior against real catalog edge cases. It also reinforced that scheduling is a constraint-satisfaction problem: course recommendations are only useful when prerequisites, co-requisites, time conflicts, capacity, and graduation rules are all considered together.

## AI-assisted development

Generative AI was used as a development collaborator for design discussion, refactoring support, test drafting, debugging hypotheses, and documentation editing. Project requirements, university-specific rules, data corrections, and final behavioral decisions were directed and reviewed by the project authors. AI output was treated as a starting point rather than an authority; important logic was validated with targeted tests and hands-on CLI scenarios.

## License

No license has been selected yet. Do not reuse or redistribute the project data without confirming the applicable university and catalog-data terms.
