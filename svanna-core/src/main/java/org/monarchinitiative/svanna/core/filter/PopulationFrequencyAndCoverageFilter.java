package org.monarchinitiative.svanna.core.filter;

import org.monarchinitiative.svanna.core.LogUtils;
import org.monarchinitiative.svanna.core.reference.SvannaVariant;
import org.monarchinitiative.svanna.core.reference.VariantMetadata;
import org.monarchinitiative.svanna.core.service.AnnotationDataService;
import org.monarchinitiative.svanna.model.landscape.variant.PopulationVariant;
import org.monarchinitiative.svanna.model.landscape.variant.PopulationVariantOrigin;
import org.monarchinitiative.svart.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class PopulationFrequencyAndCoverageFilter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PopulationFrequencyAndCoverageFilter.class);

    private static final int BATCH = 100;

    // FREQUENCY
    private static final FilterType FF_FILTER_TYPE = FilterType.FREQUENCY_FILTER;
    private static final FilterResult FF_FAIL = FilterResult.fail(FF_FILTER_TYPE);
    private static final FilterResult FF_PASS = FilterResult.pass(FF_FILTER_TYPE);
    private static final FilterResult FF_NOT_RUN = FilterResult.notRun(FF_FILTER_TYPE);

    // READ_DEPTH
    private static final FilterType COVERAGE_FILTER_TYPE = FilterType.COVERAGE_FILTER;
    private static final FilterResult COVERAGE_FAIL = FilterResult.fail(COVERAGE_FILTER_TYPE);
    private static final FilterResult COVERAGE_PASS = FilterResult.pass(COVERAGE_FILTER_TYPE);
    private static final FilterResult COVERAGE_NOT_RUN = FilterResult.notRun(COVERAGE_FILTER_TYPE);

    private static final Set<VariantType> FREQ_FILTER_RECOGNIZED_VARIANTS = Set.of(
            VariantType.INS, VariantType.DUP, VariantType.DEL, VariantType.INV, VariantType.CNV);

    private final AnnotationDataService annotationDataService;
    private final float similarityThreshold;
    private final float frequencyThreshold;
    private final int minReads;

    public PopulationFrequencyAndCoverageFilter(AnnotationDataService annotationDataService,
                                                float similarityThreshold,
                                                float frequencyThreshold,
                                                int minReads) {
        this.annotationDataService = annotationDataService;
        this.similarityThreshold = similarityThreshold;
        this.frequencyThreshold = frequencyThreshold;
        this.minReads = minReads;
    }


    public <T extends SvannaVariant> List<T> filter(Collection<T> variants) {
        Map<Integer, List<T>> variantsByContig = variants.stream()
                .collect(Collectors.groupingBy(v -> v.genomicVariant().contigId()));
        List<T> results = new LinkedList<>();
        for (Integer contigId : variantsByContig.keySet()) {
            List<T> contigVariants = variantsByContig.get(contigId).stream()
                    .sorted(byPositionOnPositiveStrand())
                    .collect(Collectors.toList());
            LogUtils.logDebug(LOGGER, "Filtering variants on contig `{}`", contigId);
            int start = 0;
            int end = Math.min(start + BATCH, contigVariants.size());
            while (true) {
                List<T> sublist = contigVariants.subList(start, end);
                results.addAll(processSublist(sublist));

                if (end == contigVariants.size())
                    break;
                start = end;
                end = Math.min(end + BATCH, contigVariants.size());
            }
        }

        return results;
    }

    private <T extends SvannaVariant> Collection<? extends T> processSublist(List<T> sublist) {
        if (sublist.isEmpty()) {
            return sublist;
        }

        int minPos = Integer.MAX_VALUE, maxPos = Integer.MIN_VALUE;

        for (T t : sublist) {
            GenomicVariant v = t.genomicVariant();
            int startPos = v.startOnStrandWithCoordinateSystem(Strand.POSITIVE, CoordinateSystem.zeroBased());
            minPos = Math.min(minPos, startPos);

            int endPos = v.endOnStrandWithCoordinateSystem(Strand.POSITIVE, CoordinateSystem.zeroBased());
            maxPos = Math.max(maxPos, endPos);
        }
        List<T> results = new ArrayList<>(sublist.size());
        T variant = sublist.get(0);
        GenomicRegion query = GenomicRegion.of(variant.genomicVariant().contig(), Strand.POSITIVE, CoordinateSystem.zeroBased(), minPos, maxPos);
        LogUtils.logTrace(LOGGER, "Filtering variants on contig `{}-{}-{}`", query.contigId(), query.start(), query.end());
        List<PopulationVariant> populationVariants = annotationDataService.overlappingPopulationVariants(query, PopulationVariantOrigin.benign());
        for (T item : sublist) {
            FilterResult freqFilterResult = runFrequencyFilter(populationVariants, item);
            item.addFilterResult(freqFilterResult);

            FilterResult coverageFilterResult = runCoverageFilter(item);
            item.addFilterResult(coverageFilterResult);

            results.add(item);
        }

        return results;
    }

    private <T extends SvannaVariant> FilterResult runFrequencyFilter(List<PopulationVariant> populationVariants, T item) {
        FilterResult freqFilterResult = null;
        if (FREQ_FILTER_RECOGNIZED_VARIANTS.contains(item.genomicVariant().variantType().baseType())) {
            for (PopulationVariant populationVariant : populationVariants) {
                if (populationVariant.variantType().baseType() == item.genomicVariant().variantType().baseType()
                        && populationVariant.alleleFrequency() >= frequencyThreshold
                        && FilterUtils.reciprocalOverlap(populationVariant.location(), item.genomicVariant()) * 100.F > similarityThreshold) {
                    freqFilterResult = FF_FAIL;
                    break;
                }
            }
            if (freqFilterResult == null)
                freqFilterResult = FF_PASS;
        } else {
            freqFilterResult = FF_NOT_RUN;
        }
        return freqFilterResult;
    }

    private <T extends SvannaVariant> FilterResult runCoverageFilter(T item) {
        return (item.numberOfAltReads() == VariantMetadata.MISSING_DEPTH_PLACEHOLDER)
                ? COVERAGE_NOT_RUN
                : (item.numberOfAltReads() < minReads)
                ? COVERAGE_FAIL
                : COVERAGE_PASS;
    }

    private static <T extends SvannaVariant> Comparator<? super T> byPositionOnPositiveStrand() {
        return Comparator
                .comparingInt(t -> ((SvannaVariant) t).genomicVariant().startOnStrandWithCoordinateSystem(Strand.POSITIVE, CoordinateSystem.zeroBased()))
                .thenComparingInt(v -> (((SvannaVariant) v).genomicVariant().endOnStrandWithCoordinateSystem(Strand.POSITIVE, CoordinateSystem.zeroBased())));
    }

}
