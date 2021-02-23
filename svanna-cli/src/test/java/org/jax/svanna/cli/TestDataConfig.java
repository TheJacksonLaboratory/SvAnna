package org.jax.svanna.cli;

import de.charite.compbio.jannovar.data.JannovarData;
import de.charite.compbio.jannovar.data.JannovarDataSerializer;
import org.jax.svanna.core.overlap.Overlapper;
import org.jax.svanna.core.overlap.SvAnnOverlapper;
import org.jax.svanna.core.reference.TranscriptService;
import org.jax.svanna.core.reference.transcripts.JannovarTranscriptService;
import org.jax.svanna.test.TestVariants;
import org.monarchinitiative.svart.GenomicAssemblies;
import org.monarchinitiative.svart.GenomicAssembly;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class TestDataConfig {

    private static final Path JANNOVAR_DATA = Paths.get("src/test/resources/hg38_refseq_small.ser");


    @Bean
    public GenomicAssembly genomicAssembly() {
        return GenomicAssemblies.GRCh38p13();
    }

    @Bean
    public TestVariants testVariants(GenomicAssembly genomicAssembly) {
        return new TestVariants(genomicAssembly);
    }

    /**
     * Small Jannovar cache that contains RefSeq transcripts of the following genes:
     * <ul>
     *     <li><em>SURF1</em></li>
     *     <li><em>SURF2</em></li>
     *     <li><em>FBN1</em></li>
     *     <li><em>ZNF436</em></li>
     *     <li><em>ZBTB48</em></li>
     *     <li><em>HNF4A</em></li>
     *     <li><em>GCK</em></li>
     *     <li><em>BRCA2</em></li>
     *     <li><em>COL4A5</em></li> (on <code>chrX</code>)
     *     <li><em>SRY</em></li> (on <code>chrY</code>)
     * </ul>
     */
    @Bean
    public JannovarData jannovarData() throws Exception {
        return new JannovarDataSerializer(JANNOVAR_DATA.toString()).load();
    }

    @Bean
    public TranscriptService transcriptService(GenomicAssembly assembly, JannovarData jannovarData) {
        return JannovarTranscriptService.of(assembly, jannovarData);
    }

    @Bean
    public Overlapper overlapper(TranscriptService transcriptService) {
        return new SvAnnOverlapper(transcriptService.getChromosomeMap());
    }

}