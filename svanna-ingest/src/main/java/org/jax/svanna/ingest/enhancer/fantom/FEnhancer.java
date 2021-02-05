package org.jax.svanna.ingest.enhancer.fantom;

import org.jax.svanna.core.reference.BaseEnhancer;
import org.jax.svanna.core.reference.EnhancerTissueSpecificity;
import org.monarchinitiative.svart.Contig;
import org.monarchinitiative.svart.CoordinateSystem;
import org.monarchinitiative.svart.Position;
import org.monarchinitiative.svart.Strand;

import java.util.Objects;
import java.util.Set;

class FEnhancer extends BaseEnhancer {

    private final double totalReadCpm;

    static FEnhancer of(Contig contig,
                        Strand strand,
                        CoordinateSystem coordinateSystem,
                        Position startPosition,
                        Position endPosition,
                        String id,
                        boolean isDevelopmental,
                        double tau,
                        Set<EnhancerTissueSpecificity> specificities,
                        double totalReadCounts) {
        return new FEnhancer(contig, strand, coordinateSystem, startPosition, endPosition, id, isDevelopmental, tau, specificities, totalReadCounts);
    }

    protected FEnhancer(Contig contig,
                        Strand strand,
                        CoordinateSystem coordinateSystem,
                        Position startPosition,
                        Position endPosition,
                        String id,
                        boolean isDevelopmental,
                        double tau,
                        Set<EnhancerTissueSpecificity> specificities,
                        double totalReadCpm) {
        super(contig, strand, coordinateSystem, startPosition, endPosition, id, isDevelopmental, tau, specificities);
        this.totalReadCpm = totalReadCpm;
    }

    public double totalReadCounts() {
        return totalReadCpm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        FEnhancer fEnhancer = (FEnhancer) o;
        return totalReadCpm == fEnhancer.totalReadCpm;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), totalReadCpm);
    }

    @Override
    public String toString() {
        return "FEnhancer{" +
                "totalReadCpm=" + totalReadCpm +
                "} " + super.toString();
    }
}
