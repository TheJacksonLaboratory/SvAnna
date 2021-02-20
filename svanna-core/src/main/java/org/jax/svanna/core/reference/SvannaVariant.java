package org.jax.svanna.core.reference;

import org.jax.svanna.core.filter.Filterable;
import org.monarchinitiative.svart.CoordinateSystem;
import org.monarchinitiative.svart.Strand;
import org.monarchinitiative.svart.Variant;

/**
 * A unit of work in SvAnna.
 */
public interface SvannaVariant extends Variant, Filterable, VariantMetadata, Prioritized {

    @Override
    SvannaVariant withCoordinateSystem(CoordinateSystem other);

    @Override
    SvannaVariant withStrand(Strand other);

}
