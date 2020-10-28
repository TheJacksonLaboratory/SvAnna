package org.jax.svann.overlap;

import com.google.common.collect.ImmutableMap;
import de.charite.compbio.jannovar.data.*;
import de.charite.compbio.jannovar.impl.intervals.IntervalArray;
import de.charite.compbio.jannovar.reference.GenomeInterval;
import de.charite.compbio.jannovar.reference.GenomePosition;
import de.charite.compbio.jannovar.reference.Strand;
import de.charite.compbio.jannovar.reference.TranscriptModel;
import org.jax.svann.except.SvAnnRuntimeException;
import org.jax.svann.reference.Adjacency;
import org.jax.svann.reference.Breakend;
import org.jax.svann.reference.SequenceRearrangement;
import org.jax.svann.reference.SvType;
import org.jax.svann.reference.genome.Contig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


import static org.jax.svann.overlap.OverlapType.*;

/**
 * This class determines the kind and degree of overlap of a structural variant with transcript and enhancer
 * features. There is one static method {@link #getOverlapList(GenomeInterval, IntervalArray.QueryResult)}
 * that should be used to get overlaps for any structural variant.
 */
public class Overlapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(Overlapper.class);
    private final static int UNINITIALIZED_EXON = -1;

    /**
     * Reference dictionary (part of {@link JannovarData}).
     */
    private final ReferenceDictionary rd;
    /**
     * Map of Chromosomes (part of {@link JannovarData}). It assigns integers to chromosome names such as CM000666.2.
     */
    private final ImmutableMap<Integer, Chromosome> chromosomeMap;

    public Overlapper(JannovarData jannovarData) {
        rd = jannovarData.getRefDict();
        chromosomeMap = jannovarData.getChromosomes();
    }

    /**
     * By assumption, if we get here we have previously determined that the SV is located within an intron
     * of the transcript. If the method is called for an SV that is only partially in the intron, it can
     * return incorrect results. It does not check this.
     *
     * @param tmod
     * @return
     */
    public static int getIntronNumber(TranscriptModel tmod, int startPos, int endPos) {
        List<GenomeInterval> exons = tmod.getExonRegions();
        if (tmod.getStrand().equals(Strand.FWD)) {
            for (int i = 0; i < exons.size() - 1; i++) {
                if (startPos > exons.get(i).getEndPos() && startPos < exons.get(i + 1).getBeginPos()) {
                    return i + 1;
                }
                if (endPos > exons.get(i).getEndPos() && endPos < exons.get(i + 1).getBeginPos()) {
                    return i + 1;
                }
            }
        } else {
            // reverse order for neg-strand genes.
            for (int i = 0; i < exons.size() - 1; i++) {
                if (startPos < exons.get(i).withStrand(Strand.FWD).getEndPos() && startPos > exons.get(i + 1).withStrand(Strand.FWD).getBeginPos()) {
                    return i + 1;
                }
                if (endPos < exons.get(i).withStrand(Strand.FWD).getEndPos() && endPos > exons.get(i + 1).withStrand(Strand.FWD).getBeginPos()) {
                    return i + 1;
                }
            }
        }
        throw new SvAnnRuntimeException("Could not find intron number");
    }

    /**
     * This is the main method for getting a list of overlapping transcripts (with extra data in an {@link Overlap}
     * object) for structural variants that occur on one Chromosome, such as deletions
     *
     * @param rearrangement
     * @return list of overlapping transcripts.
     * TODO REFACTOR
     */
    public List<Overlap> getOverlapList(SequenceRearrangement rearrangement) {
        // TODO: 26. 10. 2020 check coordinate remapping
        if (rearrangement.getType() == SvType.DELETION) {
            return getDeletionOverlaps(rearrangement);
        } else if (rearrangement.getType() == SvType.INSERTION) {
            return getInsertionOverlaps(rearrangement);
        } else {
            return List.of();
        }
//        int id = 42;//svEvent.getAdjacencies().get(0)..getId();
//        Strand strand = Strand.FWD; // We assume all SVs are on the forward strand
//
//        GenomeInterval structVarInterval = new GenomeInterval(rd, strand, id, 42,43);//svEvent.getBegin(), svEvent.getEnd());
//        IntervalArray<TranscriptModel> iarray = chromosomeMap.get(id).getTMIntervalTree();
//        IntervalArray<TranscriptModel>.QueryResult queryResult =
//                iarray.findOverlappingWithInterval(structVarInterval.getBeginPos(), structVarInterval.getEndPos());
//        return getOverlapList(structVarInterval, queryResult);
    }

    /**
     * This method returns all overlapping transcripts for simple deletions. We assume that deletions
     * are forward strand (it does not matter but is needed by the API).
     * @param srearrangement The candidate deletion
     * @return List over overlapping transcripts
     */
    public List<Overlap> getDeletionOverlaps(SequenceRearrangement srearrangement) {
        List<Adjacency> adjacencies = srearrangement.getAdjacencies();
        if (adjacencies.size() != 1) {
            throw new SvAnnRuntimeException("Malformed delection adjacency list with size " + adjacencies.size());
        }
        Adjacency deletion = adjacencies.get(0);
        Breakend left = deletion.getLeft();
        Breakend right = deletion.getRight();
        Contig chrom = left.getContig();
        int id = chrom.getId();
        int begin = left.getBegin();
        int end = right.getEnd();
        GenomeInterval structVarInterval = new GenomeInterval(rd, Strand.FWD, id, begin,end);
        IntervalArray<TranscriptModel> iarray = chromosomeMap.get(id).getTMIntervalTree();
        IntervalArray<TranscriptModel>.QueryResult queryResult =
                iarray.findOverlappingWithInterval(structVarInterval.getBeginPos(), structVarInterval.getEndPos());
        return getOverlapList(structVarInterval, queryResult);
    }

    /**
     * This method returns all overlapping transcripts for insertions. We assume that insertions
     * are forward strand (it does not matter but is needed by the API). Insertions have two
     * adjancencies in our implementation
     * @param srearrangement The candidate insertion
     * @return List over overlapping transcripts
     */
    public List<Overlap> getInsertionOverlaps(SequenceRearrangement srearrangement) {
        List<Adjacency> adjacencies = srearrangement.getAdjacencies();
        if (adjacencies.size() != 2) {
            throw new SvAnnRuntimeException("Malformed insertion adjacency list with size " + adjacencies.size());
        }
        Adjacency insertion1 = adjacencies.get(0);
        Breakend left = insertion1.getLeft();
        Breakend right = insertion1.getRight();
        Contig chrom = left.getContig();
        int id = chrom.getId();
        int begin = left.getBegin();
        Adjacency insertion2 = adjacencies.get(1);
        int end = insertion2.getRight().getEnd();
        GenomeInterval structVarInterval = new GenomeInterval(rd, Strand.FWD, id, begin,end);
        IntervalArray<TranscriptModel> iarray = chromosomeMap.get(id).getTMIntervalTree();
        IntervalArray<TranscriptModel>.QueryResult queryResult =
                iarray.findOverlappingWithInterval(structVarInterval.getBeginPos(), structVarInterval.getEndPos());
        return getOverlapList(structVarInterval, queryResult);
    }

    public List<Overlap> getOverlapList(GenomeInterval svInt,
                                        IntervalArray<TranscriptModel>.QueryResult qresult) {
        GenomePosition start = svInt.getGenomeBeginPos();
        GenomePosition end = svInt.getGenomeEndPos();
        List<Overlap> overlaps = new ArrayList<>();
        if (qresult.getEntries().isEmpty()) {
            return intergenic(start, end, qresult);
        }
        // if we get here, then we overlap with one or more genes
        List<TranscriptModel> overlappingTranscripts = qresult.getEntries();
        for (var tmod : overlappingTranscripts) {
            if (svInt.contains(tmod.getTXRegion())) {
                // the transcript is completely contained in the SV
                Overlap vover = containedIn(tmod, start, end);
                overlaps.add(vover);
                continue;
            }
            if (!tmod.getTXRegion().contains(start) && !tmod.getTXRegion().contains(end)) {
                System.err.printf("[ERROR] Warning, transcript model (%s;%s) retrieved that does not overlap (chr%s:%d-%d): ",
                        tmod.getGeneSymbol(), tmod.getAccession(), start.getChr(), start.getPos(), end.getPos());
                // TODO I observed this once, it should never happen and may be a Jannovar bug or have some other cause
                //throw new L2ORuntimeException(tmod.getGeneSymbol());
            }
            // TODO if the above bug no longer occurs, make a regular if/else with the above
            Overlap voverlap = genic(tmod, svInt);
            overlaps.add(voverlap);
        }
        if (overlaps.isEmpty()) {
            System.err.println("Could not find any overlaps with this query result" + qresult);
            throw new SvAnnRuntimeException("Empty overlap list");
        }
        return overlaps;
    }

    /**
     * This is called if the transcriptmodel is entirely contained within an SV
     * @param tmod
     * @param start
     * @param end
     * @return
     */
    private Overlap containedIn(TranscriptModel tmod, GenomePosition start, GenomePosition end) {
        String msg = String.format("%s/%s", tmod.getGeneSymbol(), tmod.getAccession());
        return new Overlap(TRANSCRIPT_CONTAINED_IN_SV, tmod, true, msg);
    }


    private List<Overlap> intergenic(GenomePosition start,
                                     GenomePosition end,
                                     IntervalArray<TranscriptModel>.QueryResult qresult) {
        List<Overlap> overlaps = new ArrayList<>();
        // This means that the SV does not overlap with any annotated transcript
        // get distance to nearest transcripts to the left and right
        TranscriptModel tmodelLeft = qresult.getLeft();
        TranscriptModel tmodelRight = qresult.getRight();
        // if we are 5' or 3' to the first or last gene on the chromosome, then
        // there is not left or right gene anymore
        int leftDistance = 0;
        String description = "";
        if (tmodelLeft != null) {
            leftDistance = start.getPos() - tmodelLeft.getTXRegion().withStrand(Strand.FWD).getEndPos();
            description = String.format("%s[%s]", tmodelLeft.getGeneSymbol(), tmodelLeft.getGeneID());
            if (leftDistance <= 2_000) {
                overlaps.add(new Overlap(OverlapType.UPSTREAM_GENE_VARIANT_2KB, tmodelLeft, leftDistance, description));
            } else if (leftDistance <= 5_000) {
                overlaps.add(new Overlap(OverlapType.UPSTREAM_GENE_VARIANT_5KB, tmodelLeft, leftDistance, description));
            } else if (leftDistance <= 500_000) {
                overlaps.add(new Overlap(OverlapType.UPSTREAM_GENE_VARIANT_500KB, tmodelLeft, leftDistance, description));
            } else {
                overlaps.add(new Overlap(OverlapType.UPSTREAM_GENE_VARIANT, tmodelLeft, leftDistance, description));
            }
        }
        if (tmodelRight != null) {
            int rightDistance = tmodelRight.getTXRegion().withStrand(Strand.FWD).getBeginPos() - end.getPos();
            description = String.format("%s[%s]", tmodelRight.getGeneSymbol(), tmodelRight.getGeneID());
            if (rightDistance <= 2_000) {
                overlaps.add(new Overlap(OverlapType.DOWNSTREAM_GENE_VARIANT_2KB, tmodelRight, rightDistance, description));
            } else if (rightDistance <= 5_000) {
                overlaps.add(new Overlap(OverlapType.DOWNSTREAM_GENE_VARIANT_5KB, tmodelRight, rightDistance, description));
            } else if (rightDistance <= 500_000) {
                overlaps.add(new Overlap(OverlapType.DOWNSTREAM_GENE_VARIANT_500KB, tmodelRight, rightDistance, description));
            } else {
                overlaps.add(new Overlap(OverlapType.DOWNSTREAM_GENE_VARIANT, tmodelRight, rightDistance, description));
            }
        }
        return overlaps;
    }

    /**
     * We check for overlap in three ways. The structural variant has an interval (b,e). b or e can
     * be contained within an exon. Alternatively, the entire exon can be contained within (b,e). Note
     * that if we call this method then
     *
     * @param tmod       A transcript
     * @param svInterval a structural variant interval
     * @return object representing the number of the first and last affected exon
     */
    private ExonPair getAffectedExons(TranscriptModel tmod, GenomeInterval svInterval) {
        Optional<Integer> firstAffectedExonNumber = Optional.empty();
        Optional<Integer> lastAffectedExonNumber = Optional.empty();
        List<GenomeInterval> exons = tmod.getExonRegions();
        GenomePosition svStartPos = svInterval.getGenomeBeginPos();
        GenomePosition svEndPos = svInterval.getGenomeEndPos();
        boolean[] affected = new boolean[exons.size()]; // initializes to false
        for (int i = 0; i < exons.size(); i++) {
            GenomeInterval exon = exons.get(i);
            if (exon.contains(svStartPos)) {
                affected[i] = true;
            }
            if (exon.contains(svEndPos)) {
                affected[i] = true;
            }
            if (svInterval.contains(exon)) {
                affected[i] = true;
            }
        }
        // -1 is a code for not applicable
        // we may encounter transcripts where the exons do not overlap
        // in this case, first and last will not be changed
        // the ExonPair object will treat first=last=-1 as a signal that there
        // is no overlap.
        int first = -1;
        int last = -1;
        for (int i = 0; i < affected.length; i++) {
            if (first < 0 && affected[i]) {
                first = i + 1;
                last = first;
            } else if (first > 0 && affected[i]) {
                last = i + 1;
            }
        }
        return new ExonPair(first, last);
    }

    /**
     * Calculate overlap for a non-coding transcript. By assumption, if we get here then we have already
     * determined that the SV overlaps with a non-coding transcript
     *
     * @param tmod a non-coding transcript (this is checked by calling code)
     * @return
     */
    public Overlap genic(TranscriptModel tmod, GenomeInterval svInt) {
        GenomePosition start = svInt.getGenomeBeginPos();
        GenomePosition end = svInt.getGenomeEndPos();
        ExonPair exonPair = getAffectedExons(tmod, svInt);
        boolean affectsCds = false; // note this can only only true if the SV is exonic and the transcript is coding
        if (tmod.isCoding()) {
            GenomeInterval cds = tmod.getCDSRegion();
            if (cds.contains(svInt)) {
                affectsCds = true;
            } else if (svInt.contains(cds)) {
                affectsCds = true;
            } else if (cds.contains(svInt.getGenomeBeginPos()) || cds.contains(svInt.getGenomeEndPos())) {
                affectsCds = true;
            }
        }
        if (exonPair.atLeastOneExonOverlap()) {
            // determine which exons are affected
            int firstAffectedExon = exonPair.getFirstAffectedExon();
            int lastAffectedExon = exonPair.getLastAffectedExon();
            if (firstAffectedExon == lastAffectedExon) {
                String msg = String.format("%s/%s[exon %d]",
                        tmod.getGeneSymbol(),
                        tmod.getAccession(),
                        firstAffectedExon);
                return new Overlap(SINGLE_EXON_IN_TRANSCRIPT, tmod, affectsCds, msg);
            } else {
                String msg = String.format("%s/%s[exon %d-%d]",
                        tmod.getGeneSymbol(),
                        tmod.getAccession(),
                        firstAffectedExon,
                        lastAffectedExon);
                return new Overlap(MULTIPLE_EXON_IN_TRANSCRIPT, tmod, affectsCds, msg);
            }
        } else {
            // if we get here, then both positions must be in the same intron
            int intronNum = getIntronNumber(tmod, start.getPos(), end.getPos());
            String msg = String.format("%s/%s[intron %d]", tmod.getGeneSymbol(), tmod.getAccession(), intronNum);
            return new Overlap(INTRONIC, tmod, false, msg);
        }
    }

}
