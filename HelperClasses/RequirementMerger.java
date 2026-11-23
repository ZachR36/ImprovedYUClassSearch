import java.nio.file.*;
import java.util.*;

/**
 * RequirementMerger
 * =================
 * Carries hand-corrected requirement columns from a previous final catalog
 * into a freshly regenerated one.
 *
 * WHY THIS EXISTS:
 * PrereqExtractor's output gets corrected by hand (AND vs OR groups,
 * self-references, lab/lecture pairs, ...). Re-running the pipeline to pick up
 * live seats and rooms would silently throw those corrections away. This step
 * keeps them whenever the course description - the only input the extractor
 * reads - is unchanged, so the corrections still apply.
 *
 * MATCHING, for each row of the new file:
 *   1. Same CRN in the previous file with an identical description, or else
 *   2. Same course code (subject + number) with an identical description
 *      (covers sections that were re-created with a new CRN).
 *   On a match, columns 17-20 (prereqs, coreqs, flexible, notes) are copied
 *   from the previous file. Otherwise the freshly extracted values stay, and
 *   the row is listed at the end for manual review.
 *
 * USAGE:
 *   java HelperClasses/RequirementMerger.java <newFinalCsv> <previousFinalCsv> <outputCsv>
 */
public class RequirementMerger {

    private static final int FIRST_REQ = 17;
    private static final int LAST_REQ = 20;
    private static final int DESCRIPTION = 21;

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.out.println("Usage: java HelperClasses/RequirementMerger.java <newFinalCsv> <previousFinalCsv> <outputCsv>");
            return;
        }
        List<String> fresh = Files.readAllLines(Path.of(args[0]));
        List<String> previous = Files.readAllLines(Path.of(args[1]));

        Map<String, String[]> byCrn = new HashMap<>();
        Map<String, String[]> byCourseAndDescription = new HashMap<>();
        for (String line : previous) {
            String[] f = line.split(",", -1);
            if (f.length <= DESCRIPTION) continue;
            byCrn.put(f[0], f);
            byCourseAndDescription.putIfAbsent(courseKey(f) + "\n" + f[DESCRIPTION], f);
        }

        List<String> out = new ArrayList<>();
        List<String> review = new ArrayList<>();
        int carried = 0;
        for (String line : fresh) {
            String[] f = line.split(",", -1);
            if (f.length <= DESCRIPTION) {
                out.add(line);
                continue;
            }
            String[] match = byCrn.get(f[0]);
            if (match == null || !match[DESCRIPTION].equals(f[DESCRIPTION])) {
                match = byCourseAndDescription.get(courseKey(f) + "\n" + f[DESCRIPTION]);
            }
            if (match != null) {
                System.arraycopy(match, FIRST_REQ, f, FIRST_REQ, LAST_REQ - FIRST_REQ + 1);
                carried++;
            } else {
                review.add(unquote(f[0]) + " " + courseKey(f) + " " + unquote(f[5]));
            }
            out.add(String.join(",", f));
        }
        Files.write(Path.of(args[2]), out);

        System.out.println("Carried requirements over for " + carried + " of " + out.size() + " rows. Wrote " + args[2]);
        if (!review.isEmpty()) {
            System.out.println(review.size() + " rows are new or have a changed description - review their requirements:");
            review.forEach(r -> System.out.println("  " + r));
        }
    }

    private static String courseKey(String[] f) {
        return unquote(f[2]) + unquote(f[1]);
    }

    private static String unquote(String s) {
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }
}
