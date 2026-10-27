import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Set;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoreRequirementProgramTest {

    @Test
    void repositoryCoreProfilesLoadWithTheirSelectorsAndRequirementTrees() throws Exception {
        CoreRequirementProgram.setup(Path.of("CoreRequirements.txt").toString());

        assertEquals(5, CoreRequirementProgram.getCatalog().size());
        CoreRequirementProgram stern = CoreRequirementProgram.getCatalog()
                .get(CoreRequirementProgram.makeKey("Stern College for Women Core"));
        assertEquals("Stern", stern.getSchool());
        assertEquals("Beren", stern.getCampus());
        assertEquals("BA", stern.getDegreeType());
        assertFalse(stern.getRequirements().isEmpty());
        assertEquals(CoreRequirementProgram.NodeType.CHOOSE_N, stern.getRequirements().getFirst().getType());
    }

    @Test
    void catalogCanFilterUsingStoredSelectorMetadata() throws Exception {
        CoreRequirementProgram.setup(Path.of("CoreRequirements.txt").toString());

        List<CoreRequirementProgram> matches = CoreRequirementProgram.findMatches(
                "YC", "Wilf", "BS", "Computer Science");

        assertEquals(1, matches.size());
        assertEquals("Yeshiva College BS Computer Science Core", matches.getFirst().getName());
        assertTrue(matches.getFirst().getMajor().equals("Computer Science"));
    }

    @Test
    void coreProfileIsSelectedFromCanonicalMajorSchoolAndCampus() throws Exception {
        DegreeProgram.setup(Path.of("MajorMinorRequirements.txt").toString());
        CoreRequirementProgram.setup(Path.of("CoreRequirements.txt").toString());
        User user = new User("Ari", false, "Wilf", "YC", "computer science, bs@yc", Set.of(), Set.of());

        CoreRequirementProgram selected = CoreRequirementProgram.findForUser(user);

        assertEquals("Yeshiva College BS Computer Science Core", selected.getName());
    }

    @Test
    void berenSchoolProfileNormalizesToTheSternCoreProfile() throws Exception {
        DegreeProgram.setup(Path.of("MajorMinorRequirements.txt").toString());
        CoreRequirementProgram.setup(Path.of("CoreRequirements.txt").toString());
        User user = new User("Leah", false, "Beren", "beren", "biology, ba@stern", Set.of(), Set.of());

        CoreRequirementProgram selected = CoreRequirementProgram.findForUser(user);

        assertEquals("Stern College for Women Core", selected.getName());
    }
}
