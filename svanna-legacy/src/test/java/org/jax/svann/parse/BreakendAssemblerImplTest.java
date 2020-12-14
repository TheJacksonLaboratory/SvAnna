package org.jax.svann.parse;

import org.jax.svann.TestBase;
import org.jax.svann.reference.*;
import org.jax.svann.reference.genome.Contig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;

public class BreakendAssemblerImplTest extends TestBase {

    private static Contig CHR2, CHR13, CHR17;
    private BreakendAssemblerImpl assembler;

    @BeforeAll
    public static void beforeAll() throws Exception {
        CHR2 = GENOME_ASSEMBLY.getContigByName("chr2").orElseThrow();
        CHR13 = GENOME_ASSEMBLY.getContigByName("chr13").orElseThrow();
        CHR17 = GENOME_ASSEMBLY.getContigByName("chr17").orElseThrow();
    }

    @BeforeEach
    public void setUp() {
        assembler = new BreakendAssemblerImpl();
    }

    @Test
    public void assemble_usingEventIds() {
        final BreakendRecord bnd_W = new BreakendRecord(ChromosomalPosition.precise(CHR2, 321681, Strand.FWD),
                "bnd_W", "TRA0", "bnd_Y", "G", "G]17:198982]", 5);
        final BreakendRecord bnd_Y = new BreakendRecord(ChromosomalPosition.precise(CHR17, 198982, Strand.FWD),
                "bnd_Y", "TRA0", "bnd_W", "A", "A]2:321681]", 5);

        final BreakendRecord bnd_V = new BreakendRecord(ChromosomalPosition.precise(CHR2, 321682, Strand.FWD),
                "bnd_V", "TRA1", "bnd_U", "T", "]13:123456]T", 6);
        final BreakendRecord bnd_U = new BreakendRecord(ChromosomalPosition.precise(CHR13, 123456, Strand.FWD),
                "bnd_U", "TRA1", "bnd_V", "C", "C[2:321682[", 6);

        final BreakendRecord bnd_X = new BreakendRecord(ChromosomalPosition.precise(CHR13, 198982, Strand.FWD),
                "bnd_X", "TRA2", "bnd_Z", "A", "[17:198983[A", 7);
        final BreakendRecord bnd_Z = new BreakendRecord(ChromosomalPosition.precise(CHR17, 198983, Strand.FWD),
                "bnd_Z", "TRA2", "bnd_X", "C", "[13:123457[C", 7);

        final List<? extends SequenceRearrangement> rearrangements = assembler.assemble(Set.of(bnd_W, bnd_Y, bnd_V, bnd_U, bnd_X, bnd_Z));

//        assertThat(rearrangements, hasSize(3));
        rearrangements.forEach(System.err::println);
    }

    @Test
    public void assemble_withoutEventIds() {
        final BreakendRecord bnd_W = new BreakendRecord(ChromosomalPosition.precise(CHR2, 321681, Strand.FWD),
                "bnd_W", null, "bnd_Y", "G", "G]17:198982]", 5);
        final BreakendRecord bnd_Y = new BreakendRecord(ChromosomalPosition.precise(CHR17, 198982, Strand.FWD),
                "bnd_Y", null, "bnd_W", "A", "A]2:321681]", 5);

        final BreakendRecord bnd_V = new BreakendRecord(ChromosomalPosition.precise(CHR2, 321682, Strand.FWD),
                "bnd_V", null, "bnd_U", "T", "]13:123456]T", 6);
        final BreakendRecord bnd_U = new BreakendRecord(ChromosomalPosition.precise(CHR13, 123456, Strand.FWD),
                "bnd_U", null, "bnd_V", "C", "C[2:321682[", 6);

        final BreakendRecord bnd_X = new BreakendRecord(ChromosomalPosition.precise(CHR13, 198982, Strand.FWD),
                "bnd_X", null, "bnd_Z", "A", "[17:198983[A", 7);
        final BreakendRecord bnd_Z = new BreakendRecord(ChromosomalPosition.precise(CHR17, 198983, Strand.FWD),
                "bnd_Z", null, "bnd_X", "C", "[13:123457[C", 7);

        final List<? extends SequenceRearrangement> rearrangements = assembler.assemble(Set.of(bnd_W, bnd_Y, bnd_V, bnd_U, bnd_X, bnd_Z));
        assertThat(rearrangements, hasSize(3));

        // not testing anything else now, as these are being tested below
    }

    /**
     * Test assembling situation depicted using mates `W` and `Y` on VCF4.3 | Section 5.4 | Figure 2.
     */
    @Test
    public void assembleBreakendRecords_WY() {
        BreakendRecord bnd_W = new BreakendRecord(ChromosomalPosition.precise(CHR2, 321681, Strand.FWD),
                "bnd_W", "TRA0", "bnd_Y", "G", "G]17:198982]", 6);
        BreakendRecord bnd_Y = new BreakendRecord(ChromosomalPosition.precise(CHR17, 198982, Strand.FWD),
                "bnd_Y", "TRA0", "bnd_W", "A", "A]2:321681]", 7);
        Optional<? extends StructuralVariant> rearrangementOpt = BreakendAssemblerImpl.assembleBreakendRecords(bnd_W, bnd_Y);
        assertThat(rearrangementOpt.isPresent(), is(true));

        SequenceRearrangement rearrangement = rearrangementOpt.get();
        assertThat(rearrangement.getType(), is(SvType.TRANSLOCATION));
        assertThat(rearrangement.getLeftmostStrand(), is(Strand.FWD)); // W is on FWD strand
        assertThat(rearrangement.getRightmostStrand(), is(Strand.REV)); // Y is on REV strand

        List<Adjacency> adjacencies = rearrangement.getAdjacencies();
        assertThat(adjacencies, hasSize(1));

        Adjacency adjacency = adjacencies.get(0);
        assertThat(adjacency.getStrand(), is(Strand.FWD)); // again, W is on FWD strand
        assertThat(adjacency.depthOfCoverage(), is(6)); // smaller depth is reported

        Breakend left = adjacency.getStart(); // W
        assertThat(left.getId(), is("bnd_W"));
        assertThat(left.getContig().getPrimaryName(), is("2"));
        assertThat(left.getPosition(), is(321681));
        assertThat(left.getStrand(), is(Strand.FWD));
        assertThat(left.getRef(), is("G"));

        Breakend right = adjacency.getEnd(); // Y
        assertThat(right.getId(), is("bnd_Y"));
        assertThat(right.getContig().getPrimaryName(), is("17"));
        assertThat(right.getPosition(), is(83058460)); // 83257441 - 198982 + 1 (chr17 length is 83,257,441)
        assertThat(right.getStrand(), is(Strand.REV));
        assertThat(right.getRef(), is("T"));
    }

    /**
     * Test assembling situation depicted using mates `U` and `V` on VCF4.3 | Section 5.4 | Figure 2.
     */
    @Test
    public void assembleBreakendRecords_UV() {
        BreakendRecord bnd_U = new BreakendRecord(ChromosomalPosition.precise(CHR13, 123456, Strand.FWD),
                "bnd_U", "TRA1", "bnd_V", "C", "C[2:321682[", 5);
        BreakendRecord bnd_V = new BreakendRecord(ChromosomalPosition.precise(CHR2, 321682, Strand.FWD),
                "bnd_V", "TRA1", "bnd_U", "T", "]13:123456]T", 6);

        Optional<? extends StructuralVariant> rearrangementOpt = BreakendAssemblerImpl.assembleBreakendRecords(bnd_U, bnd_V);
        assertThat(rearrangementOpt.isPresent(), is(true));

        SequenceRearrangement rearrangement = rearrangementOpt.get();
        assertThat(rearrangement.getType(), is(SvType.TRANSLOCATION));
        assertThat(rearrangement.getLeftmostStrand(), is(Strand.FWD)); // U is on FWD strand
        assertThat(rearrangement.getRightmostStrand(), is(Strand.FWD)); // V is on FWD strand

        List<Adjacency> adjacencies = rearrangement.getAdjacencies();
        assertThat(adjacencies, hasSize(1));

        Adjacency adjacency = adjacencies.get(0);
        assertThat(adjacency.getStrand(), is(Strand.FWD)); // again, U is on FWD strand
        assertThat(adjacency.depthOfCoverage(), is(5)); // smaller depth is reported

        Breakend left = adjacency.getStart(); // U
        assertThat(left.getId(), is("bnd_U"));
        assertThat(left.getContig().getPrimaryName(), is("13"));
        assertThat(left.getPosition(), is(123456));
        assertThat(left.getStrand(), is(Strand.FWD));
        assertThat(left.getRef(), is("C"));

        Breakend right = adjacency.getEnd(); // V
        assertThat(right.getId(), is("bnd_V"));
        assertThat(right.getContig().getPrimaryName(), is("2"));
        assertThat(right.getPosition(), is(321682));
        assertThat(right.getStrand(), is(Strand.FWD));
        assertThat(right.getRef(), is("T"));
    }

    /**
     * Test assembling situation depicted using mates `X` and `Z` on VCF4.3 | Section 5.4 | Figure 2.
     */
    @Test
    public void assembleBreakendRecords_XZ() {
        BreakendRecord bnd_X = new BreakendRecord(ChromosomalPosition.precise(CHR13, 198982, Strand.FWD),
                "bnd_X", "TRA2", "bnd_Z", "A", "[17:198983[A", 5);
        BreakendRecord bnd_Z = new BreakendRecord(ChromosomalPosition.precise(CHR17, 198983, Strand.FWD),
                "bnd_Z", "TRA2", "bnd_X", "C", "[13:123457[C", 5);

        Optional<? extends StructuralVariant> rearrangementOpt = BreakendAssemblerImpl.assembleBreakendRecords(bnd_X, bnd_Z);
        assertThat(rearrangementOpt.isPresent(), is(true));

        SequenceRearrangement rearrangement = rearrangementOpt.get();
        assertThat(rearrangement.getType(), is(SvType.TRANSLOCATION));
        assertThat(rearrangement.getLeftmostStrand(), is(Strand.REV)); // X is on REV strand
        assertThat(rearrangement.getRightmostStrand(), is(Strand.FWD)); // Z is on FWD strand

        List<Adjacency> adjacencies = rearrangement.getAdjacencies();
        assertThat(adjacencies, hasSize(1));

        Adjacency adjacency = adjacencies.get(0);
        assertThat(adjacency.getStrand(), is(Strand.REV)); // again, X is on REV strand
        assertThat(adjacency.depthOfCoverage(), is(5)); // smaller depth is reported

        Breakend left = adjacency.getStart(); // X
        assertThat(left.getId(), is("bnd_X"));
        assertThat(left.getContig().getPrimaryName(), is("13"));
        assertThat(left.getPosition(), is(114165347)); // 114364328 - 198982 + 1 (chr13 length is 114,364,328)
        assertThat(left.getStrand(), is(Strand.REV));
        assertThat(left.getRef(), is("T"));

        Breakend right = adjacency.getEnd(); // Z
        assertThat(right.getId(), is("bnd_Z"));
        assertThat(right.getContig().getPrimaryName(), is("17"));
        assertThat(right.getPosition(), is(198983));
        assertThat(right.getStrand(), is(Strand.FWD));
        assertThat(right.getRef(), is("C"));
    }

    @Test
    public void assembleBreakendRecords_withInsertedSequence() {
        BreakendRecord bnd_U = new BreakendRecord(ChromosomalPosition.precise(CHR13, 123456, Strand.FWD),
                "bnd_U", "TRA1", "bnd_V", "C", "CAGTNNNNNCA[2:321682[", 5);
        BreakendRecord bnd_V = new BreakendRecord(ChromosomalPosition.precise(CHR2, 321682, Strand.FWD),
                "bnd_V", "TRA1", "bnd_U", "T", "]13:123456]AGTNNNNNCAT", 5);

        Optional<? extends StructuralVariant> rearrangementOpt = BreakendAssemblerImpl.assembleBreakendRecords(bnd_U, bnd_V);
        assertThat(rearrangementOpt.isPresent(), is(true));

        SequenceRearrangement rearrangement = rearrangementOpt.get();
        Adjacency adjacency = rearrangement.getAdjacencies().get(0);
        assertThat(adjacency.getStrand(), is(Strand.FWD)); // again, U is on FWD strand
        assertThat(adjacency.getInserted(), is("CAGTNNNNNCA".getBytes(StandardCharsets.US_ASCII)));
        assertThat(adjacency.depthOfCoverage(), is(5)); // smaller depth is reported

        // now let's do that the other way around, submitting `bnd_V` as the 1st breakend
        rearrangementOpt = BreakendAssemblerImpl.assembleBreakendRecords(bnd_V, bnd_U);
        assertThat(rearrangementOpt.isPresent(), is(true));

        rearrangement = rearrangementOpt.get();
        adjacency = rearrangement.getAdjacencies().get(0);
        assertThat(adjacency.getStrand(), is(Strand.REV)); //  V is on FWD strand
        assertThat(adjacency.getInserted(), is("ATGNNNNNACT".getBytes(StandardCharsets.US_ASCII)));

    }
}