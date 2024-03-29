package org.monarchinitiative.svanna.ingest.parse.population;

import org.monarchinitiative.svanna.model.landscape.variant.PopulationVariant;
import org.junit.jupiter.api.Test;
import org.monarchinitiative.svart.assembly.GenomicAssemblies;
import org.monarchinitiative.svart.assembly.GenomicAssembly;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class GnomadSVFileParserTest {

    private static final Path testFile = Paths.get("src/test/resources/population/variants_for_nstd166.5lines.csv.gz");

    private static final GenomicAssembly genomicAssembly = GenomicAssemblies.GRCh38p13();

    @Test
    public void parseToList() throws Exception {
        GnomadSVFileParser instance = new GnomadSVFileParser(genomicAssembly, testFile);
        List<? extends PopulationVariant> variants = instance.parseToList();

        // TODO - finish or remove
//        variants.forEach(System.err::println);
//        assertThat(variants, hasSize(0));
    }
}