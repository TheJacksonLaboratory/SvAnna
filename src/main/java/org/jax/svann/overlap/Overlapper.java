package org.jax.svann.overlap;

import com.google.common.collect.ImmutableMap;
import de.charite.compbio.jannovar.data.*;
import de.charite.compbio.jannovar.impl.intervals.IntervalArray;
import de.charite.compbio.jannovar.reference.GenomeInterval;
import de.charite.compbio.jannovar.reference.GenomePosition;
import de.charite.compbio.jannovar.reference.Strand;
import de.charite.compbio.jannovar.reference.TranscriptModel;
import org.jax.svann.except.SvAnnRuntimeException;
import org.jax.svann.reference.IntrachromosomalEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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
     * Reference to the Jannovar transcript file data for annotating the VCF file.
     */
    private final JannovarData jannovarData;
    /**
     * Reference dictionary (part of {@link #jannovarData}).
     */
    private final ReferenceDictionary referenceDictionary;
    /**
     * Map of Chromosomes (part of {@link #jannovarData}). It assigns integers to chromosome names such as CM000666.2.
     */
    private final ImmutableMap<Integer, Chromosome> chromosomeMap;

    /**
     * Deserialize the Jannovar transcript data file that comes with Exomiser. Note that Exomiser
     * uses its own ProtoBuf serializetion and so we need to use its Deserializser. In case the user
     * provides a standard Jannovar serialzied file, we try the legacy deserializer if the protobuf
     * deserializer doesn't work.
     *
     * @return the object created by deserializing a Jannovar file.
     */
    public Overlapper(String jannovarPath){
        File f = new File(jannovarPath);
        if (!f.exists()) {
            throw new SvAnnRuntimeException("[FATAL] Could not find Jannovar transcript file at " + jannovarPath);
        }
        try {
            this.jannovarData = new JannovarDataSerializer(jannovarPath).load();
            this.referenceDictionary = this.jannovarData.getRefDict();
            this.chromosomeMap = this.jannovarData.getChromosomes();
        } catch (SerializationException e) {
            LOGGER.error("Could not deserialize Jannovar file with legacy deserializer...");
            throw new SvAnnRuntimeException(String.format("Could not load Jannovar data from %s (%s)",
                    jannovarPath, e.getMessage()));
        }
    }

    /**
     * This is the main method for getting a list of overlapping transcripts (with extra data in an {@link Overlap}
     * object) for structural variants that occur on one Chromosome, such as deletions
     * @param svEvent
     * @return list of overlapping transcripts.
     */
    public List<Overlap> getOverlapList(IntrachromosomalEvent svEvent) {
        int id = svEvent.getContig().getId();
        Strand strand = Strand.FWD; // We assume all SVs are on the forward strand
        GenomeInterval structVarInterval =
                new GenomeInterval(referenceDictionary, strand, id, svEvent.getBegin(), svEvent.getEnd());
        IntervalArray<TranscriptModel> iarray = this.chromosomeMap.get(id).getTMIntervalTree();
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
            if (! tmod.getTXRegion().contains(start) && ! tmod.getTXRegion().contains(end)) {
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


    private Overlap containedIn(TranscriptModel tmod, GenomePosition start, GenomePosition end) {
        int left = start.getPos() - tmod.getTXRegion().getBeginPos();
        int right = end.getPos() - tmod.getTXRegion().getEndPos();
        String msg = String.format("%s/%s", tmod.getGeneSymbol(), tmod.getAccession());
        return new Overlap(TRANSCRIPT_CONTAINED_IN_SV, left, right, msg);
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
        }
        int rightDistance = 0;
        if (tmodelRight != null) {
            rightDistance = tmodelRight.getTXRegion().withStrand(Strand.FWD).getBeginPos() - end.getPos();
            if (description.length()>0) {
                description = String.format("%s/%s[%s]", description,tmodelRight.getGeneSymbol(), tmodelRight.getGeneID());
            } else {
                description = String.format("%s[%s]", tmodelRight.getGeneSymbol(), tmodelRight.getGeneID());
            }
        }
        int minDistance = Math.min(leftDistance, rightDistance);
        boolean left = leftDistance == minDistance; // in case of ties, default to left
        if (left) {
            if (minDistance <= 2_000) {
                overlaps.add(new Overlap(OverlapType.UPSTREAM_GENE_VARIANT_2KB, leftDistance, rightDistance, description));
            } else if (minDistance <= 5_000) {
                overlaps.add(new Overlap(OverlapType.UPSTREAM_GENE_VARIANT_5KB, leftDistance, rightDistance, description));
            } else if (minDistance <= 500_000) {
                overlaps.add(new Overlap(OverlapType.UPSTREAM_GENE_VARIANT_500KB, leftDistance, rightDistance, description));
            } else {
                overlaps.add(new Overlap(OverlapType.UPSTREAM_GENE_VARIANT, leftDistance, rightDistance, description));
            }
        } else {
            if (minDistance <= 2_000) {
                overlaps.add(new Overlap(OverlapType.DOWNSTREAM_GENE_VARIANT_2KB, leftDistance, rightDistance, description));
            } else if (minDistance <= 5_000) {
                overlaps.add(new Overlap(OverlapType.DOWNSTREAM_GENE_VARIANT_5KB, leftDistance, rightDistance, description));
            } else if (minDistance <= 500_000) {
                overlaps.add(new Overlap(OverlapType.DOWNSTREAM_GENE_VARIANT_500KB, leftDistance, rightDistance, description));
            } else {
                overlaps.add(new Overlap(OverlapType.DOWNSTREAM_GENE_VARIANT, leftDistance, rightDistance, description));
            }
        }
        return overlaps;
    }



    /**
     * We check for overlap in three ways. The structural variant has an interval (b,e). b or e can
     * be contained within an exon. Alternatively, the entire exon can be contained within (b,e). Note
     * that if we call this method then
     * @param tmod A transcript
     * @param svInterval a structural variant interval
     * @return object representing the number of the first and last affected exon
     */
    private ExonPair getAffectedExons(TranscriptModel tmod, GenomeInterval svInterval) {
        Optional<Integer> firstAffectedExonNumber = Optional.empty();
        Optional<Integer> lastAffectedExonNumber = Optional.empty();
        List<GenomeInterval> exons = tmod.getExonRegions();
        GenomePosition svStartPos = svInterval.getGenomeBeginPos();
        GenomePosition svEndPos = svInterval.getGenomeEndPos();
        boolean [] affected = new boolean[exons.size()]; // initializes to false
        for (int i=0; i<exons.size(); i++) {
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
        for (int i=0;i<affected.length;i++) {
            if (first < 0 && affected[i]) {
                first = i+1;
                last = first;
            } else if (first > 0 && affected[i]) {
                last = i+1;
            }
        }
        return new ExonPair(first,last);
    }



    /**
     * Calculate overlap for a non-coding transcript. By assumption, if we get here then we have already
     * determined that the SV overlaps with a non-coding transcript
     *
     * @param tmod  a non-coding transcript (this is checked by calling code)
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
            if (firstAffectedExon == lastAffectedExon){
                String msg = String.format("%s/%s[exon %d]",
                        tmod.getGeneSymbol(),
                        tmod.getAccession(),
                        firstAffectedExon);
                return new Overlap(SINGLE_EXON_IN_TRANSCRIPT, affectsCds,  msg);
            } else {
                String msg = String.format("%s/%s[exon %d-%d]",
                        tmod.getGeneSymbol(),
                        tmod.getAccession(),
                        firstAffectedExon,
                        lastAffectedExon);
                return new Overlap(MULTIPLE_EXON_IN_TRANSCRIPT, affectsCds, msg);
            }
        } else {
            // if we get here, then both positions must be in the same intron
            int intronNum = getIntronNumber(tmod, start.getPos(), end.getPos());
            String msg = String.format("%s/%s[intron %d]", tmod.getGeneSymbol(), tmod.getAccession(), intronNum);
            return new Overlap(INTRONIC, false, msg);
        }
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
            for (int i=0;i<exons.size()-1;i++) {
                if (startPos > exons.get(i).getEndPos() && startPos < exons.get(i+1).getBeginPos()) {
                    return i+1;
                }
                if (endPos > exons.get(i).getEndPos() && endPos < exons.get(i+1).getBeginPos()) {
                    return i+1;
                }
            }
        } else {
            // reverse order for neg-strand genes.
            for (int i=0;i<exons.size()-1;i++) {
                if (startPos < exons.get(i).withStrand(Strand.FWD).getEndPos() && startPos > exons.get(i+1).withStrand(Strand.FWD).getBeginPos()) {
                    return i+1;
                }
                if (endPos < exons.get(i).withStrand(Strand.FWD).getEndPos() && endPos > exons.get(i+1).withStrand(Strand.FWD).getBeginPos()) {
                    return i+1;
                }
            }
        }
        throw new SvAnnRuntimeException("Could not find intron number");
    }


}