package org.jax.svann.parse;

import htsjdk.variant.variantcontext.VariantContext;
import htsjdk.variant.vcf.VCFFileReader;
import org.jax.svann.reference.*;
import org.jax.svann.reference.genome.Contig;
import org.jax.svann.reference.genome.GenomeAssembly;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public class VcfStructuralRearrangementParser implements StructuralRearrangementParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(VcfStructuralRearrangementParser.class);

    private static final String EMPTY_STRING = "";

    private final GenomeAssembly assembly;

    private final BreakendAssembler assembler;

    public VcfStructuralRearrangementParser(GenomeAssembly assembly, BreakendAssembler assembler) {
        this.assembly = assembly;
        this.assembler = assembler;
    }

    @Override
    public Collection<SequenceRearrangement> parseFile(Path filePath) throws IOException {
        List<SequenceRearrangement> rearrangements = new ArrayList<>();
        List<BreakendRecord> breakendRecords = new ArrayList<>();
        try (VCFFileReader reader = new VCFFileReader(filePath, false)) {
            for (VariantContext vc : reader) {
                final SvType svType = SvType.fromString(vc.getAttributeAsString("SVTYPE", "UNKNOWN"));
                if (svType.equals(SvType.BND)) {
                    // decode the required information from this breakend record
                    parseBreakend(vc).ifPresent(breakendRecords::add);
                } else {
                    // process structural variants
                    parseStructuralVariant(vc, svType).ifPresent(rearrangements::add);
                }
            }
        }

        // now assemble all breakends into rearrangements
        List<SequenceRearrangement> assembled = assembler.assemble(breakendRecords);
        rearrangements.addAll(assembled);

        return rearrangements;
    }

    private Optional<BreakendRecord> parseBreakend(VariantContext vc) {
        final Optional<Contig> contigOptional = assembly.getContigByName(vc.getContig());
        if (contigOptional.isEmpty()) {
            LOGGER.warn("Unknown contig `{}` for variant {}", vc.getContig(), vc);
            return Optional.empty();
        }
        final Contig contig = contigOptional.get();

        // position
        Position position;
        if (vc.hasAttribute("CIPOS")) {
            final List<Integer> cipos = vc.getAttributeAsIntList("CIPOS", 0);
            if (cipos.size() != 2) {
                LOGGER.warn("CIPOS size != 2 for variant {}", vc);
                return Optional.empty();
            } else {
                position = Position.imprecise(vc.getStart(), ConfidenceInterval.of(cipos.get(0), cipos.get(1)));
            }
        } else {
            position = Position.precise(vc.getStart(), CoordinateSystem.ONE_BASED);
        }

        // mate ID
        List<String> mateIds = vc.getAttributeAsStringList("MATEID", null);
        if (mateIds.size() > 1) {
            LOGGER.warn("Breakend with >1 mate IDs: {}", vc);
            return Optional.empty();
        }
        String mateId = mateIds.get(0);

        // ref & alt
        if (vc.getAlternateAlleles().size() != 1) {
            LOGGER.warn("Breakend with >1 alt allele: {}", vc);
            return Optional.empty();
        }
        final String ref = vc.getReference().getDisplayString();
        final String alt = vc.getAlternateAllele(0).getDisplayString();

        // in VCF specs, the position is always on the FWD(+) strand
        final ChromosomalPosition breakendPosition = ChromosomalPosition.of(contig, position, Strand.FWD);
        final BreakendRecord breakendRecord = new BreakendRecord(breakendPosition, vc.getID(), mateId, ref, alt);

        return Optional.of(breakendRecord);
    }

    /**
     * Process the variant into a structural rearrangement.
     *
     * @param vc     variant to be processed
     * @param svType previously parsed SV type to make the processing more convenient
     * @return optional with parsed rearrangement or empty optional if
     */
    private Optional<SequenceRearrangement> parseStructuralVariant(VariantContext vc, SvType svType) {
        List<Adjacency> adjacencies = new ArrayList<>();
        switch (svType) {
            // cases with a single adjacency
            case DELETION:
                makeDeletionAdjacency(vc).ifPresent(adjacencies::add);
                break;
            // cases with two adjacencies
            case DUPLICATION:
                adjacencies.addAll(makeDuplicationAdjacencies(vc));
                break;
            case INSERTION:
                adjacencies.addAll(makeInsertionAdjacencies(vc));
                break;
            case INVERSION:
                adjacencies.addAll(makeInversionAdjacencies(vc));
                break;

            // these cases are not yet implemented
            case CNV:
            case DELETION_SIMPLE:
            case DELETION_TWISTED:
            case DEL_INV:
            case DUP_INS:
            case INV_DUP:
            case INV_INV_DUP:
                LOGGER.warn("Parsing of `{}` is not yet implemented: {}:{}-{}", svType, vc.getContig(), vc.getStart(), vc.getID());
                return Optional.empty();
            // cases we are not supposed to handle here
            case UNKNOWN:
            case TRANSLOCATION:
            case BND:
            default:
                LOGGER.warn("Unsupported SV type `{}` passed to `makeRearrangement`: {}:{}-{}", svType, vc.getContig(), vc.getStart(), vc.getID());
                return Optional.empty();
        }


        return Optional.of(new SimpleSequenceRearrangement(adjacencies, svType));
    }

    Collection<? extends Adjacency> makeDuplicationAdjacencies(VariantContext vc) {
        // We know that this context represents a symbolic duplication - SvType.DUPLICATION
        //
        // TODO: 27. 10. 2020 implement

        return List.of();
    }

    Collection<? extends Adjacency> makeInsertionAdjacencies(VariantContext vc) {
        // We know that this context represents a symbolic insertion - SvType.INSERTION
        //
        // TODO: 27. 10. 2020 implement

        return List.of();
    }

    List<? extends Adjacency> makeInversionAdjacencies(VariantContext vc) {
        /*
        We know that this context represents a symbolic inversion - SvType.INVERSION
        Inversion consists of 2 adjacencies, we denote them as alpha and beta
        The VCF line might look like this:
        2   11  INV0    <INV>   6   PASS    SVTYPE=INV;END=19
        */
        Optional<CoreData> cdOpt = extractCoreData(vc);
        if (cdOpt.isEmpty()) {
            return List.of();
        }

        String id = vc.getID();
        CoreData cd = cdOpt.get();
        Contig contig = cd.getContig();
        Position begin = cd.getBegin();
        Position end = cd.getEnd();

        // TODO: 27. 10. 2020 this method involves shifting coordinates +- 1 base pair, while not adjusting the CIs.
        //  This should not be an issue, but evaluate just to be sure

        // the 1st adjacency (alpha) starts one base before begin coordinate (POS), by convention on + strand
        ChromosomalPosition alphaLeftPos = ChromosomalPosition.of(contig,
                Position.imprecise(begin.getPos() - 1, begin.getConfidenceInterval()),
                Strand.FWD);
        Breakend alphaLeft = new SimpleBreakend(alphaLeftPos, id, EMPTY_STRING, EMPTY_STRING);
        // the right position is the last base of the inverted segment on - strand
        ChromosomalPosition alphaRightPos = ChromosomalPosition.of(contig, end, Strand.FWD).withStrand(Strand.REV);
        SimpleBreakend alphaRight = new SimpleBreakend(alphaRightPos, id, EMPTY_STRING, EMPTY_STRING);
        Adjacency alpha = SimpleAdjacency.of(alphaLeft, alphaRight);


        // the 2nd adjacency (beta) starts at the begin coordinate on - strand
        ChromosomalPosition betaLeftPos = ChromosomalPosition.of(contig, begin, Strand.FWD).withStrand(Strand.REV);
        SimpleBreakend betaLeft = new SimpleBreakend(betaLeftPos, id, EMPTY_STRING, EMPTY_STRING);
        // the right position is one base past end coordinate, by convention on + strand
        ChromosomalPosition betaRightPos = ChromosomalPosition.of(contig,
                Position.imprecise(end.getPos() + 1, end.getConfidenceInterval()),
                Strand.FWD);
        SimpleBreakend betaRight = new SimpleBreakend(betaRightPos, id, EMPTY_STRING, EMPTY_STRING);
        Adjacency beta = SimpleAdjacency.of(betaLeft, betaRight);
        return List.of(alpha, beta);
    }

    private Optional<Adjacency> makeDeletionAdjacency(VariantContext vc) {
        // We know that this context represents symbolic deletion.
        // Let's get the required coordinates first
        return extractCoreData(vc).map(coords -> {
            // then convert the coordinates to adjacency
            ChromosomalPosition leftPos = ChromosomalPosition.of(coords.getContig(), coords.getBegin(), Strand.FWD);
            SimpleBreakend left = new SimpleBreakend(leftPos, vc.getID(), vc.getReference().getDisplayString(), EMPTY_STRING);
            ChromosomalPosition rightPos = ChromosomalPosition.of(coords.getContig(), coords.getEnd(), Strand.FWD);
            SimpleBreakend right = new SimpleBreakend(rightPos, vc.getID(), vc.getReference().getDisplayString(), EMPTY_STRING);
            return SimpleAdjacency.of(left, right);
        });
    }

    /**
     * Convenience method to extract contig, start and `END` (+confidence intervals) values from variant context.
     */
    private Optional<CoreData> extractCoreData(VariantContext vc) {
        // parse contig
        final Optional<Contig> contigOptional = assembly.getContigByName(vc.getContig());
        if (contigOptional.isEmpty()) {
            LOGGER.warn("Unknown contig `{}` for variant {}", vc.getContig(), vc);
            return Optional.empty();
        }
        final Contig contig = contigOptional.get();

        if (!vc.hasAttribute("END")) {
            LOGGER.warn("Missing `END` attribute for variant {}", vc);
            return Optional.empty();
        }

        // parse begin coordinate
        Position begin;
        if (vc.hasAttribute("CIPOS")) {
            final List<Integer> cipos = vc.getAttributeAsIntList("CIPOS", 0);
            if (cipos.size() == 2) {
                begin = Position.imprecise(vc.getStart(), ConfidenceInterval.of(cipos.get(0), cipos.get(1)), CoordinateSystem.ONE_BASED);
            } else {
                LOGGER.warn("CIPOS size != 2 for variant {}", vc);
                return Optional.empty();
            }
        } else {
            begin = Position.precise(vc.getStart(), CoordinateSystem.ONE_BASED);
        }

        // parse end coordinate
        Position end;
        int endPos = vc.getAttributeAsInt("END", 0);
        if (vc.hasAttribute("CIEND")) {
            final List<Integer> ciend = vc.getAttributeAsIntList("CIEND", 0);
            if (ciend.size() == 2) {
                end = Position.imprecise(endPos, ConfidenceInterval.of(ciend.get(0), ciend.get(1)), CoordinateSystem.ONE_BASED);
            } else {
                LOGGER.warn("CIEND size != 2 for variant {}", vc);
                return Optional.empty();
            }
        } else {
            end = Position.precise(endPos, CoordinateSystem.ONE_BASED);
        }

        return Optional.of(new CoreData(contig, begin, end));
    }

}
