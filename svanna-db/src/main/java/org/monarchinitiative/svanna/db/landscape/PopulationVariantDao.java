package org.monarchinitiative.svanna.db.landscape;

import org.monarchinitiative.svanna.model.landscape.variant.PopulationVariant;
import org.monarchinitiative.svanna.model.landscape.variant.PopulationVariantOrigin;
import org.monarchinitiative.svart.GenomicRegion;

import java.util.List;
import java.util.Set;

public interface PopulationVariantDao extends AnnotationDao<PopulationVariant> {

    Set<PopulationVariantOrigin> availableOrigins();

    List<PopulationVariant> getOverlapping(GenomicRegion query, Set<PopulationVariantOrigin> origins);

    default List<PopulationVariant> getOverlapping(GenomicRegion query) {
        return getOverlapping(query, availableOrigins());
    }
}
