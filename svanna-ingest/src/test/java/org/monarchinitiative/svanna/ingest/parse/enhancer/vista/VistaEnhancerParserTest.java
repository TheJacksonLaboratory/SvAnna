package org.monarchinitiative.svanna.ingest.parse.enhancer.vista;

import org.monarchinitiative.svanna.ingest.parse.enhancer.AbstractEnhancerParserTest;
import org.monarchinitiative.svanna.model.landscape.enhancer.Enhancer;
import org.monarchinitiative.svanna.model.landscape.enhancer.EnhancerTissueSpecificity;
import org.junit.jupiter.api.Test;
import org.monarchinitiative.phenol.ontology.data.Term;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.monarchinitiative.svart.CoordinateSystem;
import org.monarchinitiative.svart.Strand;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class VistaEnhancerParserTest extends AbstractEnhancerParserTest {

    @Test
    public void parse() throws Exception {
        Path vistaPath = Paths.get("src/test/resources/enhancer/vista/vista.tsv");
        VistaEnhancerParser parser = new VistaEnhancerParser(GRCh38p13, vistaPath, UBERON_TO_HPO);
        List<? extends Enhancer> enhancers = parser.parseToList();

        assertThat(enhancers, hasSize(2));

        Enhancer enhancer = enhancers.get(0);
        assertThat(enhancer.contig(), equalTo(GRCh38p13.contigByName("chr16")));
        assertThat(enhancer.strand(), equalTo(Strand.POSITIVE));
        assertThat(enhancer.coordinateSystem(), equalTo(CoordinateSystem.oneBased()));
        assertThat(enhancer.start(), equalTo(86_396_481));
        assertThat(enhancer.end(), equalTo(86_397_120));

        assertThat(enhancer.id(), equalTo("element 1"));
        assertThat(enhancer.isDevelopmental(), equalTo(true));
        assertThat(enhancer.tissueSpecificity(), hasSize(2));
        assertThat(enhancer.tissueSpecificity().stream().map(EnhancerTissueSpecificity::tissueTerm).map(Term::id).collect(Collectors.toSet()),
                hasItems(TermId.of("UBERON:0001049"), TermId.of("UBERON:0007277")));
        assertThat(enhancer.tissueSpecificity().stream().map(EnhancerTissueSpecificity::hpoTerm).map(Term::getName).collect(Collectors.toSet()),
                hasItems("Morphological central nervous system abnormality", "Abnormality of hindbrain morphology"));
    }
}