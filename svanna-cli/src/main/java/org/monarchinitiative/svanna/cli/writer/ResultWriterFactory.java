package org.monarchinitiative.svanna.cli.writer;

import org.monarchinitiative.svanna.cli.writer.html.HtmlResultWriter;
import org.monarchinitiative.svanna.cli.writer.tabular.TabularResultWriter;
import org.monarchinitiative.svanna.cli.writer.vcf.VcfResultWriter;
import org.monarchinitiative.svanna.core.overlap.GeneOverlapper;
import org.monarchinitiative.svanna.core.service.AnnotationDataService;
import org.monarchinitiative.svanna.core.service.PhenotypeDataService;

public class ResultWriterFactory {

    private final GeneOverlapper overlapper;
    private final AnnotationDataService annotationDataService;
    private final PhenotypeDataService phenotypeDataService;

    public ResultWriterFactory(GeneOverlapper overlapper, AnnotationDataService annotationDataService, PhenotypeDataService phenotypeDataService) {
        this.overlapper = overlapper;
        this.annotationDataService = annotationDataService;
        this.phenotypeDataService = phenotypeDataService;
    }


    public ResultWriter resultWriterForFormat(OutputFormat outputFormat, boolean compress) {
        switch (outputFormat) {
            case HTML:
                return new HtmlResultWriter(overlapper, annotationDataService, phenotypeDataService);
            case TSV:
                return new TabularResultWriter(OutputFormat.TSV.fileSuffix(), '\t', compress);
            case CSV:
                return new TabularResultWriter(OutputFormat.CSV.fileSuffix(), ',', compress);
            case VCF:
                return new VcfResultWriter(compress);
            default:
                throw new RuntimeException("The output format " + outputFormat + " is not supported");
        }
    }

}
