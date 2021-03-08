package org.jax.svanna.core;

import org.jax.svanna.core.reference.Gene;
import org.jax.svanna.core.reference.Transcript;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.monarchinitiative.svart.*;

import java.util.Objects;
import java.util.Set;

public class TestGene extends BaseGenomicRegion<TestGene> implements Gene {

    private final TermId accessionId;
    private final String geneName;

    public static TestGene of(TermId accessionId, String geneName, Contig contig, Strand strand, CoordinateSystem coordinateSystem, int start, int end) {
        return new TestGene(accessionId, geneName, contig, strand, coordinateSystem, Position.of(start), Position.of(end));
    }

    protected TestGene(TermId accessionId, String geneName, Contig contig, Strand strand, CoordinateSystem coordinateSystem, Position startPosition, Position endPosition) {
        super(contig, strand, coordinateSystem, startPosition, endPosition);
        this.accessionId = accessionId;
        this.geneName = geneName;
    }

    @Override
    public TermId accessionId() {
        return accessionId;
    }

    @Override
    public String geneName() {
        return geneName;
    }

    @Override
    public Set<Transcript> transcripts() {
        return Set.of();
    }

    @Override
    protected TestGene newRegionInstance(Contig contig, Strand strand, CoordinateSystem coordinateSystem, Position startPosition, Position endPosition) {
        return new TestGene(accessionId, geneName, contig, strand, coordinateSystem, startPosition, endPosition);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        TestGene testGene = (TestGene) o;
        return Objects.equals(accessionId, testGene.accessionId) && Objects.equals(geneName, testGene.geneName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), accessionId, geneName);
    }

    @Override
    public String toString() {
        return "TestGene{" +
                "accessionId=" + accessionId +
                ", geneName=" + geneName +
                '}';
    }
}