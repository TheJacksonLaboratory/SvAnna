package org.monarchinitiative.svanna.core.overlap;

import org.monarchinitiative.svanna.core.LogUtils;
import org.monarchinitiative.svanna.core.service.GeneService;
import org.monarchinitiative.svanna.core.service.QueryResult;
import org.monarchinitiative.sgenes.model.Coding;
import org.monarchinitiative.sgenes.model.Gene;
import org.monarchinitiative.sgenes.model.Transcript;
import org.monarchinitiative.svart.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

import static org.monarchinitiative.svanna.core.overlap.OverlapType.*;

class GeneOverlapperImpl implements GeneOverlapper {

    /*
    Note: Always check presence of contigId in the gene interval array before working with the corresponding IntervalArray<Gene>.
    The array can be null!
     */

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneOverlapperImpl.class);

    /**
     * Map where key corresponds to {@link Contig#id()} and the value contains interval array with the
     * genes.
     */
    private final GeneService geneService;

    GeneOverlapperImpl(GeneService geneService) {
        this.geneService = Objects.requireNonNull(geneService, "Gene service must not be null");
    }

    private static List<GeneOverlap> parseIntrachromosomalEventQueryResult(GenomicRegion region, QueryResult<Gene> result) {
        return result.hasOverlapping()
                ? parseEventThatOverlapsWithAGene(region, result.overlapping())
                : intergenic(region, result);
    }

    private static List<GeneOverlap> intergenic(GenomicRegion region, QueryResult<Gene> result) {
        List<GeneOverlap> overlaps = new ArrayList<>(2);

        if (result.upstream().isPresent())
            overlaps.add(processOverlapForGene(region, result.upstream().get()));
        if (result.downstream().isPresent())
            overlaps.add(processOverlapForGene(region, result.downstream().get()));

        return overlaps;
    }

    private static List<GeneOverlap> parseEventThatOverlapsWithAGene(GenomicRegion region, Collection<Gene> genes) {
        return genes.stream()
                .map(gene -> processOverlapForGene(region, gene))
                .collect(Collectors.toList());
    }

    private static GeneOverlap processOverlapForGene(GenomicRegion region, Gene gene) {
        Map<String, TranscriptOverlap> overlaps = new HashMap<>(gene.transcriptCount());

        gene.transcripts()
                .forEachRemaining(transcript -> overlaps.put(transcript.accession(), processOverlapForTranscript(region, transcript)));

        return GeneOverlap.of(gene, overlaps);
    }

    private static TranscriptOverlap processOverlapForTranscript(GenomicRegion region, Transcript transcript) {
        return region.overlapsWith(transcript.location())
                ? processOverlappingTranscript(region, transcript)
                : processNonOverlappingTranscript(region, transcript);

    }

    private static TranscriptOverlap processOverlappingTranscript(GenomicRegion region, Transcript transcript) {
        String accessionId = transcript.accession();
        if (region.contains(transcript.location()))
            return TranscriptOverlap.of(TRANSCRIPT_CONTAINED_IN_SV, accessionId, OverlapDistance.fromContainedIn(), accessionId);

        ExonPair affectedExons = Utils.getAffectedExons(region, transcript);
        boolean affectsCds = false; // note this can only true if the SV is exonic and the transcript is coding
        if (transcript instanceof Coding) {
            Coding ctx = (Coding) transcript;
            boolean overlap = Coordinates.overlap(transcript.coordinateSystem(), ctx.codingStart(), ctx.codingEnd(),
                    region.coordinateSystem(), region.startOnStrand(transcript.strand()), region.endOnStrand(transcript.strand()));
            affectsCds = overlap && affectedExons.atLeastOneExonOverlap();
        }

        // TSS is defined as a 1bp-long region that spans the first base of the transcript
        if (Coordinates.aContainsB(region.coordinateSystem(), region.startOnStrand(transcript.strand()), region.endOnStrand(transcript.strand()),
                CoordinateSystem.oneBased(), transcript.startWithCoordinateSystem(CoordinateSystem.oneBased()), transcript.startWithCoordinateSystem(CoordinateSystem.oneBased()))) {
            OverlapType overlapType = transcript instanceof Coding ? AFFECTS_CODING_TRANSCRIPT_TSS : AFFECTS_NONCODING_TRANSCRIPT_TSS;
            return TranscriptOverlap.of(overlapType, accessionId, OverlapDistance.fromExonic(accessionId, affectsCds), accessionId);
        }

        if (affectedExons.atLeastOneExonOverlap()) {
            // determine which exons are affected
            int firstAffectedExon = affectedExons.getFirstAffectedExon();
            int lastAffectedExon = affectedExons.getLastAffectedExon();
            if (firstAffectedExon == lastAffectedExon) {
                String msg = String.format("%s[exon %d]", accessionId, firstAffectedExon);
                OverlapDistance overlapDistance = OverlapDistance.fromExonic(accessionId, affectsCds);
                // check if the exon is coding or not
                if (affectsCds)
                    return TranscriptOverlap.of(SINGLE_EXON_IN_TRANSCRIPT, accessionId, overlapDistance, msg);
                else if (transcript.isCoding())
                    return TranscriptOverlap.of(NON_CDS_REGION_IN_SINGLE_EXON, accessionId, overlapDistance, msg);
                else
                    return TranscriptOverlap.of(SINGLE_EXON_IN_NC_TRANSCRIPT, accessionId, overlapDistance, msg);
            } else {
                String msg = String.format("%s[exon %d-%d]", accessionId, firstAffectedExon, lastAffectedExon);
                OverlapDistance od = OverlapDistance.fromExonic(accessionId, affectsCds);
                return TranscriptOverlap.of(MULTIPLE_EXON_IN_TRANSCRIPT, accessionId, od, msg);
            }
        } else {
            // if we get here, then both positions must be in the same intron
            IntronDistance intronDist = Utils.getIntronNumber(region, transcript);

            String msg = String.format("%s[%s]", accessionId, intronDist.getUpDownStreamDistance(transcript.strand().isPositive()));
            OverlapDistance od = OverlapDistance.fromIntronic(accessionId, intronDist);
            return TranscriptOverlap.of(INTRONIC, accessionId, od, msg);
        }
    }

    private static TranscriptOverlap processNonOverlappingTranscript(GenomicRegion region, Transcript transcript) {
        int distance = transcript.location().distanceTo(region);

        OverlapType type;
        OverlapDistance overlapDistance;
        if (distance < 0) {
            // event is upstream from the transcript
            if (distance >= -500) {
                type = UPSTREAM_GENE_VARIANT_500B;
            } else if (distance >= -2_000) {
                type = UPSTREAM_GENE_VARIANT_2KB;
            } else if (distance >= -5_000) {
                type = UPSTREAM_GENE_VARIANT_5KB;
            } else if (distance >= -500_000) {
                type = UPSTREAM_GENE_VARIANT_500KB;
            } else {
                type = UPSTREAM_GENE_VARIANT;
            }
            overlapDistance = OverlapDistance.fromUpstreamFlankingGene(distance, transcript.accession());
        } else {
            // event is downstream from the tx
            if (distance <= 500) {
                type = DOWNSTREAM_GENE_VARIANT_500B;
            } else if (distance <= 2_000) {
                type = DOWNSTREAM_GENE_VARIANT_2KB;
            } else if (distance <= 5_000) {
                type = DOWNSTREAM_GENE_VARIANT_5KB;
            } else if (distance <= 500_000) {
                type = DOWNSTREAM_GENE_VARIANT_500KB;
            } else {
                type = DOWNSTREAM_GENE_VARIANT;
            }
            overlapDistance = OverlapDistance.fromDownstreamFlankingGene(distance, transcript.accession());
        }

        return TranscriptOverlap.of(type, transcript.accession(), overlapDistance, overlapDistance.getDescription());
    }

    @Override
    public List<GeneOverlap> getOverlaps(GenomicVariant variant) {
        switch (variant.variantType().baseType()) {
            case DEL:
            case DUP:
            case INV:
            case CNV:
            case SNV:
            case MNV:
                return intrachromosomalEventOverlaps(variant);
            case INS:
                return variant.length() == 0 ? emptyRegionOverlap(variant) : intrachromosomalEventOverlaps(variant);
            case TRA:
            case BND:
                if (variant instanceof GenomicBreakendVariant) {
                    return translocationOverlaps(((GenomicBreakendVariant) variant));
                } else {
                    LogUtils.logWarn(LOGGER, "Variant `{}` has type `{}` but it is not instance of BreakendVariant", LogUtils.variantSummary(variant), variant.variantType());
                    return List.of();
                }
            default:
                LogUtils.logWarn(LOGGER, "Getting overlaps for `{}` is not yet supported", variant.variantType());
                return List.of();
        }
    }

    private List<GeneOverlap> intrachromosomalEventOverlaps(GenomicRegion region) {
        QueryResult<Gene> results = geneService.overlappingGenes(region);
        return parseIntrachromosomalEventQueryResult(region, results);

    }

    private List<GeneOverlap> emptyRegionOverlap(GenomicRegion region) {
        QueryResult<Gene> results = geneService.overlappingGenes(region);
        return parseIntrachromosomalEventQueryResult(region, results);
    }

    private List<GeneOverlap> translocationOverlaps(GenomicBreakendVariant breakendVariant) {
        List<GeneOverlap> overlaps = new LinkedList<>();

        // the loop is unrolled as we only have 2 breakends here
        GenomicBreakend left = breakendVariant.left();
        QueryResult<Gene> leftResult = geneService.overlappingGenes(left);
        overlaps.addAll(parseIntrachromosomalEventQueryResult(left, leftResult));

        GenomicBreakend right = breakendVariant.right();
        QueryResult<Gene> rightResult = geneService.overlappingGenes(right);
        overlaps.addAll(parseIntrachromosomalEventQueryResult(right, rightResult));

        return overlaps;
    }

}
