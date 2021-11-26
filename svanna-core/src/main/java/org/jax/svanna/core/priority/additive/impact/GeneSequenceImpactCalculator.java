package org.jax.svanna.core.priority.additive.impact;

import org.jax.svanna.core.LogUtils;
import org.jax.svanna.core.priority.additive.Event;
import org.jax.svanna.core.priority.additive.Projection;
import org.jax.svanna.core.priority.additive.Segment;
import org.jax.svanna.core.service.GeneDosageDataService;
import org.jax.svanna.model.landscape.dosage.GeneDosageData;
import org.monarchinitiative.svart.CoordinateSystem;
import org.monarchinitiative.svart.Coordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import xyz.ielis.silent.genes.model.Coding;
import xyz.ielis.silent.genes.model.Gene;
import xyz.ielis.silent.genes.model.Transcript;

import java.util.*;
import java.util.stream.Collectors;

public class GeneSequenceImpactCalculator implements SequenceImpactCalculator<Gene> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeneSequenceImpactCalculator.class);

    // These are fitnesses!
    private static final double INS_DOES_NOT_FIT_INTO_CODING_FRAME = .1;
    private static final double INS_FITS_INTO_CODING_FRAME_IS_OUT_OF_FRAME = .5;
    private static final double INS_FITS_INTO_CODING_FRAME_IS_INFRAME = .8;
    private static final int INTRONIC_ACCEPTOR_PADDING = 25;
    private static final int INTRONIC_DONOR_PADDING = 6;

    private final GeneDosageDataService geneDosageDataService;
    private final double geneFactor;
    private final int promoterLength;
    private final double promoterFitnessGain;

    private final Map<Event, Double> fitnessWithEvent;

    public GeneSequenceImpactCalculator(GeneDosageDataService geneDosageDataService,
                                        double geneFactor,
                                        int promoterLength,
                                        double promoterFitnessGain) {
        this.geneDosageDataService = geneDosageDataService;
        this.promoterLength = promoterLength;
        this.geneFactor = geneFactor;
        if (promoterFitnessGain > 1.)
            LOGGER.warn("Promoter fitness gain {} cannot be greater than 1. Clipping to 1", promoterFitnessGain);
        this.promoterFitnessGain = Math.min(promoterFitnessGain, 1.);
        this.fitnessWithEvent = Map.of(
                Event.GAP, geneFactor,
                Event.SNV, .85 * geneFactor,
                Event.DUPLICATION, .0,
                Event.INSERTION, .1 * geneFactor,
                Event.DELETION, .0 * geneFactor,
                Event.INVERSION, .0,
                Event.BREAKEND, .0
        );
    }

    private static double defaultUtrFitness(int segmentLength, int utrLength) {
        // min fitness is attained if >=50% of the UTR length is affected
        double seqImpact = 2. * segmentLength / utrLength;
        return Math.max(1 - seqImpact, 0.);
    }

    private static double insertionUtrFitness(int segmentLength, int utrLength) {
        double impact = (double) segmentLength / utrLength;
        return 1 - Math.min(impact, 1.);
    }

    private static List<PaddedExon> mapToPaddedExons(Transcript tx, int cdsStart, int cdsEnd) {
        if (tx.exons().isEmpty())
            throw new IllegalArgumentException("Transcript with no exons: " + tx.accession());

        if (tx.exons().size() == 1) {
            Coordinates first = tx.exons().get(0);
            int start = first.startWithCoordinateSystem(CoordinateSystem.zeroBased());
            int end = first.endWithCoordinateSystem(CoordinateSystem.zeroBased());

            int nCoding = cdsEnd - cdsStart;
            return List.of(PaddedExon.of(start, start, end, end, nCoding));
        }


        List<PaddedExon> exons = new ArrayList<>(tx.exons().size());

        Coordinates first = tx.exons().get(0);
        int firstExonStart = first.startWithCoordinateSystem(CoordinateSystem.zeroBased());
        int firstExonEnd = first.endWithCoordinateSystem(CoordinateSystem.zeroBased());
        int firstExonPaddedEnd = firstExonEnd + INTRONIC_DONOR_PADDING;

        int nCoding = Coordinates.overlapLength(CoordinateSystem.zeroBased(), cdsStart, cdsEnd,
                first.coordinateSystem(), first.start(), first.end());

        exons.add(PaddedExon.of(firstExonStart, firstExonStart, firstExonEnd, firstExonPaddedEnd, nCoding));

        // internal exons
        for (Coordinates exon : tx.exons().subList(1, tx.exons().size() - 1)) {
            int exonStart = exon.startWithCoordinateSystem(CoordinateSystem.zeroBased());
            int paddedExonStart = exonStart - INTRONIC_ACCEPTOR_PADDING;
            int exonEnd = exon.endWithCoordinateSystem(CoordinateSystem.zeroBased());
            int paddedExonEnd = exonEnd + INTRONIC_DONOR_PADDING;

            int nCodingInternal = Coordinates.overlapLength(CoordinateSystem.zeroBased(), cdsStart, cdsEnd,
                    exon.coordinateSystem(), exon.start(), exon.end());

            exons.add(PaddedExon.of(paddedExonStart, exonStart, exonEnd, paddedExonEnd, nCodingInternal));
        }

        Coordinates last = tx.exons().get(tx.exons().size() - 1);
        int lastExonStart = last.startWithCoordinateSystem(CoordinateSystem.zeroBased());
        int lastExonPaddedStart = lastExonStart - INTRONIC_ACCEPTOR_PADDING;
        int lastExonEnd = last.endWithCoordinateSystem(CoordinateSystem.zeroBased());

        int nCodingLast = Coordinates.overlapLength(CoordinateSystem.zeroBased(), cdsStart, cdsEnd,
                last.coordinateSystem(), last.start(), last.end());

        exons.add(PaddedExon.of(lastExonPaddedStart, lastExonStart, lastExonEnd, lastExonEnd, nCodingLast));

        return exons;
    }

    private static int fiveUtrLength(int txStart, int cdsStart) {
        return cdsStart - txStart;
    }

    private static int threeUtrLength(int cdsEnd, int txEnd) {
        return txEnd - cdsEnd;
    }

    @Override
    public double projectImpact(Projection<Gene> projection) {
        Set<Transcript> transcripts = projection.source().transcriptStream()
                .collect(Collectors.toUnmodifiableSet());
        double promoterImpact = checkPromoter(projection.route().segments(), transcripts);

        double geneImpact = projection.isIntraSegment()
                ? processIntraSegmentProjection(projection)
                : processInterSegmentProjection(projection, transcripts);

        return Double.isNaN(promoterImpact)
                ? geneImpact
                : Math.min(geneImpact, promoterImpact);
    }

    @Override
    public double noImpact() {
        return geneFactor;
    }

    private double checkPromoter(List<Segment> segments, Set<Transcript> transcripts) {
        Set<Segment> nonGapSegments = segments.stream()
                .filter(s -> s.event() != Event.GAP)
                .collect(Collectors.toSet());

        double score = Double.NaN;
        for (Transcript tx : transcripts) {
            int txStart = tx.start();
            int promoterStart = txStart - promoterLength;
            int promoterEnd = txStart + Coordinates.endDelta(tx.coordinateSystem());

            for (Segment nonGapSegment : nonGapSegments) {
                int segmentStart = nonGapSegment.startOnStrand(tx.strand());
                int segmentEnd = nonGapSegment.endOnStrand(tx.strand());
                if (Coordinates.overlap(tx.coordinateSystem(), promoterStart, promoterEnd,
                        nonGapSegment.coordinateSystem(), segmentStart, segmentEnd)) {
                    double fitness = fitnessWithEvent.get(nonGapSegment.event());
                    // In theory, `fitness + geneFactor * promoterFitnessGain` can be above 1, which does not make sense.
                    // Let's not allow that to happen.
                    double bla = Math.min(fitness + geneFactor * promoterFitnessGain, 1.);
                    fitness = Math.min(bla, geneFactor);
                    if (Double.isNaN(score) || fitness < score)
                        score = fitness;
                }
            }
        }
        return score;
    }

    private double processIntraSegmentProjection(Projection<Gene> projection) {
        Gene gene = projection.source();
        // we need all available gene dosage data, both for genes and for regions other than genes
        Optional<GeneDosageData> geneDosageData = gene.id().hgncId()
                .map(hgncId -> geneDosageDataService.geneDosageDataForHgncIdAndRegion(hgncId, gene.location()));

        // the entire gene is spanned by an event (DELETION, INVERSION, DUPLICATION...)
        switch (projection.startEvent()) {
            case DELETION:
                // the entire gene is deleted
                return geneDosageData.isPresent() && geneDosageData.get().isHaploinsufficient()
                        // the dosage data indicates the deletion IS an issue
                        ? 0.
                        // the dosage data indicates the deletion is NOT an issue or there is NO dosage data
                        : noImpact();
            case DUPLICATION:
                // the entire gene is duplicated
                return geneDosageData.isPresent() && geneDosageData.get().isTriplosensitive()
                        // the dosage data indicates the duplication IS an issue
                        ? 2 * noImpact()
                        // the dosage data indicates the duplication is NOT an issue or there is NO dosage data
                        : noImpact();
            case BREAKEND:
            case SNV:
            case INSERTION:
                // should not happen since these are events with length 0
                LOGGER.warn("Gene is unexpectedly located within a {}. " +
                        "This should not have happened since length of {} on reference contig should be too small to encompass a gene",
                        projection.startEvent(), projection.startEvent());
                return noImpact();
            default:
                LOGGER.warn("Unable to process unknown impact");
            case GAP: // no impact
            case INVERSION: // the entire gene is inverted, this should not be an issue
                return noImpact();
        }
    }

    private double processInterSegmentProjection(Projection<Gene> projection, Set<Transcript> transcripts) {
        Set<Segment> causalSegments = projection.spannedSegments().stream()
                .filter(s -> !s.event().equals(Event.GAP))
                .collect(Collectors.toUnmodifiableSet());

        double score = noImpact();
        for (Transcript tx : transcripts) {
            double txScore = evaluateSegmentsWrtTranscript(causalSegments, tx);
            score = Math.min(score, txScore);
        }
        return score;
    }

    private double evaluateSegmentsWrtTranscript(Set<Segment> segments, Transcript tx) {
        double score = noImpact();

        if (!(tx instanceof Coding))
            // heuristics shortcut for noncoding transcript
            return score;

        Coding ctx = (Coding) tx;
        int cdsStart = ctx.codingStartWithCoordinateSystem(CoordinateSystem.zeroBased());
        int cdsEnd = ctx.codingEndWithCoordinateSystem(CoordinateSystem.zeroBased());
        for (Segment segment : segments) {
            double segmentScore;
            //noinspection SwitchStatementWithTooFewBranches
            switch (segment.event()) {
                case INSERTION:
                    segmentScore = scoreInsertionSegment(segment, tx, cdsStart, cdsEnd);
                    break;
                default:
                    segmentScore = scoreDefaultSegment(segment, tx, cdsStart, cdsEnd);
                    break;
            }
            score = Math.min(segmentScore, score);
        }

        return score;
    }

    double scoreInsertionSegment(Segment segment, Transcript tx, int cdsStart, int cdsEnd) {
        int insLengthOnContig = segment.endWithCoordinateSystem(CoordinateSystem.zeroBased()) - segment.startWithCoordinateSystem(CoordinateSystem.zeroBased());
        if (insLengthOnContig != 0) {
            LOGGER.warn("Bad insertion with nonzero length {}", insLengthOnContig);
            return Double.NaN;
        }

        // segment start and end of an empty region are the same numbers in both half-open coordinate systems
        int segmentPos = segment.startOnStrandWithCoordinateSystem(tx.strand(), CoordinateSystem.zeroBased());

        double score = noImpact();

        List<PaddedExon> paddedExons = mapToPaddedExons(tx, cdsStart, cdsEnd);
        int fivePrimeUtrLength = fiveUtrLength(tx.startWithCoordinateSystem(CoordinateSystem.zeroBased()), cdsStart);
        int threePrimeUtrLength = threeUtrLength(cdsEnd, tx.endWithCoordinateSystem(CoordinateSystem.zeroBased()));


        int nCodingBasesInPreviousExons = 0;
        for (PaddedExon exon : paddedExons) {
            if (Coordinates.aContainsB(CoordinateSystem.zeroBased(), exon.paddedStart(), exon.paddedEnd(),
                    segment.coordinateSystem(), segment.startOnStrand(tx.strand()), segment.endOnStrand(tx.strand()))) {
                // is the insertion in UTR?
                if (segmentPos <= cdsStart) {
                    // 5'UTR
                    score = Math.min(insertionUtrFitness(segment.length(), fivePrimeUtrLength), score);
                } else if (cdsEnd < segmentPos) {
                    // 3'UTR
                    score = Math.min(insertionUtrFitness(segment.length(), threePrimeUtrLength), score);
                } else {
                    // coding region
                    int nCurrentCodingBases = segmentPos - Math.max(cdsStart, exon.start());
                    int nTotalCodingBases = nCodingBasesInPreviousExons + nCurrentCodingBases;
                    boolean fitsIntoCodingFrame = nTotalCodingBases % 3 == 0;
                    if (fitsIntoCodingFrame) {
                        boolean isInFrame = segment.length() % 3 == 0;
                        score = isInFrame
                                ? Math.min(INS_FITS_INTO_CODING_FRAME_IS_INFRAME, score)
                                : Math.min(INS_FITS_INTO_CODING_FRAME_IS_OUT_OF_FRAME, score);
                    } else
                        score = Math.min(INS_DOES_NOT_FIT_INTO_CODING_FRAME, score);
                }
                break; // insertion has length 0 and does not overlap with multiple exons
            } else {
                nCodingBasesInPreviousExons += exon.nCodingBases();
            }
        }

        return score;
    }

    double scoreDefaultSegment(Segment segment, Transcript tx, int cdsStart, int cdsEnd) {
        double score = noImpact();
        if (segment.event().equals(Event.GAP))
            return score;

        int segmentStart = segment.startOnStrandWithCoordinateSystem(tx.strand(), CoordinateSystem.zeroBased());
        int segmentEnd = segment.endOnStrandWithCoordinateSystem(tx.strand(), CoordinateSystem.zeroBased());

        UtrData utrData = new UtrData(tx.startWithCoordinateSystem(CoordinateSystem.zeroBased()),
                cdsStart,
                tx.endWithCoordinateSystem(CoordinateSystem.zeroBased()),
                cdsEnd);

        List<PaddedExon> paddedExons = mapToPaddedExons(tx, cdsStart, cdsEnd);
        boolean isFirstExonOfTranscript = true;
        for (PaddedExon paddedExon : paddedExons) {
            double exonScore = evaluateExon(segmentStart, segmentEnd, segment.event(), paddedExon, utrData, isFirstExonOfTranscript);
            score = Math.min(exonScore, score);
            if (isFirstExonOfTranscript)
                isFirstExonOfTranscript = false;
        }

        return score;
    }

    private double evaluateExon(int segmentStart, int segmentEnd, Event event, PaddedExon exon, UtrData utrData, boolean isFirstExonOfTranscript) {
        double score = noImpact();
        if (!Coordinates.overlap(CoordinateSystem.zeroBased(), segmentStart, segmentEnd, CoordinateSystem.zeroBased(), exon.paddedStart(), exon.paddedEnd()))
            // segment does not affect the exon at all
            return score;

        // from now on the segment overlaps with the exon
        boolean affectsCds = Coordinates.overlap(CoordinateSystem.zeroBased(), segmentStart, segmentEnd,
                CoordinateSystem.zeroBased(), utrData.cdsStart, utrData.cdsEnd);

        // check if segment overlaps with transcription start site
        if (isFirstExonOfTranscript) {
            boolean segmentContainsExonStart = segmentStart <= exon.start() && exon.start() < segmentEnd;
            if (segmentContainsExonStart)
                return fitnessWithEvent.getOrDefault(event, score);
        }

        if (!affectsCds) {
            if (segmentEnd <= utrData.cdsStart) { // 5UTR
                if (utrData.fiveUtrLength() == 0) {
                    LOGGER.warn("5'UTR was 0bp long!");
                    return score;
                }
                return defaultUtrFitness(segmentEnd - segmentStart, utrData.fiveUtrLength());
            } else if (utrData.cdsEnd <= segmentStart) { // 3UTR
                // get 3'UTR
                if (utrData.threeUtrLength() == 0) {
                    LOGGER.warn("3'UTR was 0bp long!");
                    return score;
                }
                return defaultUtrFitness(segmentEnd - segmentStart, utrData.threeUtrLength());
            } else {
                // something fishy! Complain!
                throw new IllegalArgumentException("Segment " + segmentStart + '-' + segmentEnd + " should overlap with 5UTR or 3UTR, but it does not!");
            }
        }

        return fitnessWithEvent.getOrDefault(event, score);
    }

    private static class UtrData {

        private final int txStart, cdsStart, txEnd, cdsEnd;

        // MAKE SURE THESE ARE IN THE SAME COORDINATE SYSTEM!
        private UtrData(int txStart, int cdsStart, int txEnd, int cdsEnd) {
            this.txStart = txStart;
            this.cdsStart = cdsStart;
            this.txEnd = txEnd;
            this.cdsEnd = cdsEnd;
        }

        int fiveUtrLength() {
            return GeneSequenceImpactCalculator.fiveUtrLength(txStart, cdsStart);
        }

        int threeUtrLength() {
            return GeneSequenceImpactCalculator.threeUtrLength(cdsEnd, txEnd);
        }
    }

}
