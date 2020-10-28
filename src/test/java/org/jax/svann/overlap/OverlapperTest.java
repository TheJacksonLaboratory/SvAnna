package org.jax.svann.overlap;

import org.jax.svann.TestBase;
import org.jax.svann.parse.TestVariants;
import org.jax.svann.reference.SequenceRearrangement;
import org.jax.svann.reference.genome.Contig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.jax.svann.overlap.OverlapType.*;
import static org.jax.svann.parse.TestVariants.*;
import static org.jax.svann.reference.SvType.INSERTION;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that we get the correct overlaps with respect to a small set of transcript models in our
 * "mini" Jannovar Data. These are the genes and transcripts that are contained:
 * SURF1: NM_003172.3,NM_001280787.1, XM_011518942.1
 * SURF2: NM_017503.4, NM_001278928.1
 * FBN1 NM_000138.4
 * ZNF436: NM_030634.2, NM_001077195.1,XM_011542201.1,XR_001737137.2,XR_001737135.2,
 * ZBTB48: XR_001737136.1, XM_017001110.2, XM_017001111.2, XM_017001112.2, XM_017001113.2, XM_017001114.2,
 *    NM_005341.3, XR_946621.3, NM_001278648.1, NM_001278647.1
 * HNF4A: NM_000457.4, NM_178850.2, NM_178849.2, NM_001258355.1, NM_001030004.2, NM_001030003.2, NM_175914.4,
 *     NM_001287182.1, NM_001287183.1, NM_001287184.1, XM_005260407.4
 * GCK: NM_033507.2, NM_033508.2, NM_000162.4, NM_001354800.1, NM_001354802.1, NM_001354801.1,
 *      NM_001354803.1,XM_024446707.1
 * BRCA2: NM_000059.3
 * COL4A5: XM_017029263.2, XM_017029262.2, XM_017029261.1, XM_017029260.1, XM_017029259.2, NM_033380.2
 *          XM_011530849.2, NM_000495.4
 * SRY: NM_003140.2
 */
public class OverlapperTest extends TestBase {

    private static Contig CHR9;

    private static Overlapper overlapper;



    @BeforeAll
    public static void beforeAll() {
        CHR9 = GENOME_ASSEMBLY.getContigByName("chr9").orElseThrow();
        overlapper = new Overlapper(JANNOVAR_DATA);
    }


    /**
     * We expect two overlapping transcripts for the SURF2 single exon deletion
     */
    @Test
    public void testSurf2Exon3Overlaps() {
        SequenceRearrangement surf1Exon3Deletion = TestVariants.singleExonDeletion_SURF2_exon3();
        List<Overlap> overlaps = overlapper.getOverlapList(surf1Exon3Deletion);
        assertEquals(2, overlaps.size());
        Set<String> expectedAccessionNumbers = Set.of("NM_017503.4", "NM_001278928.1");
        Set<String> observedAccessionNumbers = overlaps.stream().map(Overlap::getAccession).collect(Collectors.toSet());
        assertEquals(expectedAccessionNumbers, observedAccessionNumbers);
        for (var o : overlaps) {
            assertEquals(SINGLE_EXON_IN_TRANSCRIPT, o.getOverlapType());
            assertTrue(o.overlapsCds());
        }
    }

    /**
     *  We expect three overlapping transcripts for the SURF1 two-exon deletion
     */
    @Test
    public void testSurf1TwoExonDeletion() {
        SequenceRearrangement twoExonSurf1 = twoExonDeletion_SURF1_exons_6_and_7();
        List<Overlap> overlaps = overlapper.getOverlapList(twoExonSurf1);
        assertEquals(3, overlaps.size());
        Set<String> expectedAccessionNumbers = Set.of("NM_003172.3","NM_001280787.1", "XM_011518942.1");
        Set<String> observedAccessionNumbers = overlaps.stream().map(Overlap::getAccession).collect(Collectors.toSet());
        assertEquals(expectedAccessionNumbers, observedAccessionNumbers);
        for (var o : overlaps) {
            assertEquals(MULTIPLE_EXON_IN_TRANSCRIPT, o.getOverlapType());
            assertTrue(o.overlapsCds());
        }
    }

    /**
     * SURF1:NM_003172.4 entirely deleted, SURF2:NM_017503.5 partially deleted
     * chr9:133_350_001-133_358_000
     * Note that in the application, the SvPrioritizer will recognize that two different genes
     * are affected but the Overlapper does not do that
     * */
    @Test
    public void testTwoTranscriptDeletion() {
        SequenceRearrangement surf1and2deletion = deletionOfOneEntireTranscriptAndPartOfAnother();
        List<Overlap> overlaps = overlapper.getOverlapList(surf1and2deletion);
        assertEquals(5, overlaps.size());
        Set<String> expectedAccessionNumbers =
                Set.of("NM_017503.4", "NM_001278928.1","NM_003172.3","NM_001280787.1", "XM_011518942.1");
        Set<String> observedAccessionNumbers = overlaps.stream().map(Overlap::getAccession).collect(Collectors.toSet());
        assertEquals(expectedAccessionNumbers, observedAccessionNumbers);
        for (var o : overlaps) {
            if (o.getGeneSymbol().equals("SURF1"))
                assertEquals(TRANSCRIPT_CONTAINED_IN_SV, o.getOverlapType());
            else if (o.getGeneSymbol().equals("SURF2"))
                assertEquals(MULTIPLE_EXON_IN_TRANSCRIPT, o.getOverlapType());
            assertTrue(o.overlapsCds());
        }
    }


    /**
     * Deletion within an intron.
     * <p>
     * SURF2:NM_017503.5 700bp deletion within intron 3
     * chr9:133_359_001-133_359_700
     */
    @Test
    public void testdeletionWithinAnIntron() {
        SequenceRearrangement surf1DeletionWithinIntron = deletionWithinAnIntron();
        List<Overlap> overlaps = overlapper.getOverlapList(surf1DeletionWithinIntron);
        assertEquals(2, overlaps.size());
        Set<String> expectedAccessionNumbers = Set.of("NM_017503.4", "NM_001278928.1");
        Set<String> observedAccessionNumbers = overlaps.stream().map(Overlap::getAccession).collect(Collectors.toSet());
        assertEquals(expectedAccessionNumbers, observedAccessionNumbers);
        for (var o : overlaps) {
            assertEquals(INTRONIC, o.getOverlapType());
            assertFalse(o.overlapsCds());
        }
    }

    /**
     * Deletion in 5UTR.
     * <p>
     * SURF2:NM_017503.5 20bp deletion in 5UTR
     * chr9:133_356_561-133_356_580
     *
     * The deletion is thus EXONIC but NOT CDS
     */
    @Test
    public void testdeletionIn5UTR() {
        SequenceRearrangement surf1DeletionWithinIntron = deletionIn5UTR();
        List<Overlap> overlaps = overlapper.getOverlapList(surf1DeletionWithinIntron);
        assertEquals(2, overlaps.size());
        Set<String> expectedAccessionNumbers = Set.of("NM_017503.4", "NM_001278928.1");
        Set<String> observedAccessionNumbers = overlaps.stream().map(Overlap::getAccession).collect(Collectors.toSet());
        assertEquals(expectedAccessionNumbers, observedAccessionNumbers);
        for (var o : overlaps) {
            assertEquals(SINGLE_EXON_IN_TRANSCRIPT, o.getOverlapType());
            assertFalse(o.overlapsCds());
        }
    }



    /**
     * Deletion in 3UTR.
     * <p>
     * SURF1:NM_003172.4 100bp deletion in 3UTR
     * chr9:133_351_801-133_351_900
     *
     *  The deletion is thus EXONIC but NOT CDS
     */

    @Test
    public void testDeletionIn3UTR() {
        SequenceRearrangement surf1DeletionWithinIntron = deletionIn3UTR();
        List<Overlap> overlaps = overlapper.getOverlapList(surf1DeletionWithinIntron);
        assertEquals(3, overlaps.size());
        Set<String> expectedAccessionNumbers = Set.of("NM_003172.3","NM_001280787.1", "XM_011518942.1");
        Set<String> observedAccessionNumbers = overlaps.stream().map(Overlap::getAccession).collect(Collectors.toSet());
        assertEquals(expectedAccessionNumbers, observedAccessionNumbers);
        for (var o : overlaps) {
            assertEquals(SINGLE_EXON_IN_TRANSCRIPT, o.getOverlapType());
            assertFalse(o.overlapsCds());
        }
    }


    /**
     * Deletion downstream intergenic.
     * <p>
     * SURF1:NM_003172.4 downstream, 10kb deletion
     * chr9:133_300_001-133_310_000
     *
     * Note that the Jannovar API returns only one transcript for upstream/downstream matches, even though
     * all three SURF1 transcripts start at the same location. In our test we are getting
     * "NM_003172.3", but it is safer to test that we get the expected symbol
     */
    @Test
    public void testDeletionDownstreamIntergenic() {
        SequenceRearrangement surf1Downstream = deletionDownstreamIntergenic();
        List<Overlap> overlaps = overlapper.getOverlapList(surf1Downstream);
        assertEquals(1, overlaps.size());
        Overlap overlap = overlaps.get(0);
        assertEquals("SURF1", overlap.getGeneSymbol());
        assertEquals(DOWNSTREAM_GENE_VARIANT_500KB, overlap.getOverlapType());
        assertFalse(overlap.overlapsCds());
    }


    /**
     * Deletion upstream intergenic.
     * <p>
     * SURF1:NM_017503.5 upstream, 10kb deletion
     * chr9:133_380_001-133_381_000
     */

    @Test
    public void testDeletionUpstreamIntergenic() {
        SequenceRearrangement surf1Upstream = deletionUpstreamIntergenic();
        List<Overlap> overlaps = overlapper.getOverlapList(surf1Upstream);
        assertEquals(1, overlaps.size());
        Overlap overlap = overlaps.get(0);
        assertEquals("SURF2", overlap.getGeneSymbol());
        assertEquals(UPSTREAM_GENE_VARIANT_500KB, overlap.getOverlapType());
        assertFalse(overlap.overlapsCds());
    }

    /**
     * Insertion in 5'UTR.
     * <p>
     * SURF2:NM_017503.5 10bp insertion in 5UTR
     * chr9:133_356_571-133_356_571
     * The insertion overlaps with a single exon, noncoding.
     * Note that we do not test the SvType here, that is not the purpose
     * of the overlap class
     */
    @Test
    public void testInsertionIn5UTR() {
        SequenceRearrangement surf2insertion5utr = insertionIn5UTR();
        List<Overlap> overlaps = overlapper.getOverlapList(surf2insertion5utr);
        assertEquals(2, overlaps.size());
        Set<String> expectedAccessionNumbers = Set.of("NM_017503.4", "NM_001278928.1");
        Set<String> observedAccessionNumbers = overlaps.stream().map(Overlap::getAccession).collect(Collectors.toSet());
        assertEquals(expectedAccessionNumbers, observedAccessionNumbers);
        for (var o : overlaps) {
            assertEquals(SINGLE_EXON_IN_TRANSCRIPT, o.getOverlapType());
            assertFalse(o.overlapsCds());
        }
    }

}