package org.monarchinitiative.svanna.cli.writer.html;

import org.monarchinitiative.svanna.core.overlap.GeneOverlap;
import org.monarchinitiative.svanna.core.reference.SvannaVariant;
import org.monarchinitiative.svanna.model.landscape.dosage.DosageRegion;
import org.monarchinitiative.svanna.model.landscape.enhancer.Enhancer;
import org.monarchinitiative.sgenes.model.Gene;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Information related to a single variant that is required to generate analysis summary.
 */
public interface VariantLandscape {

    SvannaVariant variant();

    List<GeneOverlap> overlaps();

    default List<Gene> genes() {
        return overlaps().stream()
                .map(GeneOverlap::gene)
                .collect(Collectors.toUnmodifiableList());
    }

    List<Enhancer> enhancers();

    List<DosageRegion> dosageRegions();

}
