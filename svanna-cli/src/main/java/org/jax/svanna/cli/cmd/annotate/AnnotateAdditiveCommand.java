package org.jax.svanna.cli.cmd.annotate;

import org.jax.svanna.autoconfigure.SvannaProperties;
import org.jax.svanna.cli.Main;
import org.jax.svanna.cli.cmd.Utils;
import org.jax.svanna.cli.cmd.*;
import org.jax.svanna.cli.writer.AnalysisResults;
import org.jax.svanna.cli.writer.OutputFormat;
import org.jax.svanna.cli.writer.ResultWriter;
import org.jax.svanna.cli.writer.ResultWriterFactory;
import org.jax.svanna.cli.writer.html.AnalysisParameters;
import org.jax.svanna.cli.writer.html.HtmlResultWriter;
import org.jax.svanna.core.exception.LogUtils;
import org.jax.svanna.core.filter.PopulationFrequencyAndCoverageFilter;
import org.jax.svanna.core.hpo.HpoDiseaseSummary;
import org.jax.svanna.core.hpo.ModeOfInheritance;
import org.jax.svanna.core.hpo.PhenotypeDataService;
import org.jax.svanna.core.landscape.AnnotationDataService;
import org.jax.svanna.core.landscape.PopulationVariantOrigin;
import org.jax.svanna.core.priority.*;
import org.jax.svanna.core.reference.SvannaVariant;
import org.jax.svanna.core.reference.Zygosity;
import org.jax.svanna.io.parse.VariantParser;
import org.jax.svanna.io.parse.VcfVariantParser;
import org.monarchinitiative.phenol.ontology.data.Term;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.monarchinitiative.svart.GenomicAssembly;
import org.phenopackets.schema.v1.Phenopacket;
import org.phenopackets.schema.v1.core.HtsFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.stream.Collectors;

@CommandLine.Command(name = "annotate-additive",
        aliases = {"AAV"},
        header = "Prioritize the variants with additive prioritizer",
        mixinStandardHelpOptions = true,
        version = Main.VERSION,
        usageHelpWidth = Main.WIDTH,
        footer = Main.FOOTER)
public class AnnotateAdditiveCommand extends SvAnnaCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnnotateAdditiveCommand.class);

    protected static final NumberFormat NF = NumberFormat.getNumberInstance();

    static {
        NF.setMaximumFractionDigits(2);
    }

    /**
     * Gene contribution is multiplied by this factor if variant is heterozygous and the gene is not associated with
     * a dominant disease.
     */
    private static final double MODE_OF_INHERITANCE_FACTOR = .5;

    /*
     * -------------- INPUT OPTIONS -------------
     */

    @CommandLine.Option(names = {"-p", "--phenopacket"}, description = "Path to phenopacket")
    public Path phenopacketPath = null;

    @CommandLine.Option(names = {"--vcf"}, description = "Path to input VCF file")
    public Path vcfFile = null;

    @CommandLine.Option(names = {"-t", "--term"}, description = "HPO term ID(s)")
    public List<String> hpoTermIdList = List.of();

    /*
     * ------------ ANALYSIS OPTIONS ------------
     */
    @CommandLine.Option(names = {"--n-threads"}, paramLabel = "2", description = "Process variants using n threads (default: ${DEFAULT-VALUE})")
    public int parallelism = 2;

    /*
     * ------------ FILTERING OPTIONS ------------
     */
    @CommandLine.Option(names = {"--similarity-threshold"}, description = "percentage threshold for determining variant's region is similar enough to database entry (default: ${DEFAULT-VALUE})")
    public float similarityThreshold = 80.F;

    @CommandLine.Option(names = {"--frequency-threshold"}, description = "frequency threshold as a percentage [0-100] (default: ${DEFAULT-VALUE})")
    public float frequencyThreshold = 1.F;

    @CommandLine.Option(names={"--min-read-support"}, description="Minimum number of ALT reads to prioritize (default: ${DEFAULT-VALUE})")
    public int minAltReadSupport = 3;

    @CommandLine.Option(names = {"--mode-of-inheritance"}, description = "reassign priority of heterozygous variants if at least one affected gene is not associated with AD disease (default: ${DEFAULT-VALUE})")
    public boolean modeOfInheritance = false;

    @CommandLine.Option(names = {"max-length"}, description = "Do not prioritize variants longer than this (default: ${DEFAULT-VALUE})")
    public int maxLength = 100_000;

    /*
     * ------------  OUTPUT OPTIONS  ------------
     */
    @CommandLine.Option(names = {"-x", "--prefix"}, description = "prefix for output files (default: ${DEFAULT-VALUE})")
    public String outPrefix = null;

    @CommandLine.Option(names = {"-f", "--output-format"},
            paramLabel = "html",
            description = "Comma separated list of output formats to use for writing the results (default: ${DEFAULT-VALUE})")
    public String outputFormats = "html";

    @CommandLine.Option(names = {"-n", "--report-top-variants"}, paramLabel = "50", description = "Report top n variants (default: ${DEFAULT-VALUE})")
    public int reportNVariants = 100;

    @CommandLine.Option(names = {"--no-breakends"}, description = "do not include breakend variants into HTML report (default: ${DEFAULT-VALUE})")
    public boolean doNotReportBreakends = false;

    @Override
    public Integer call(){
        int status = checkArguments();
        if (status!=0)
            return status;

        Set<TermId> phenotypeTermIds;
        if (vcfFile != null) { // VCF & CLI
            phenotypeTermIds = hpoTermIdList.stream()
                    .map(TermId::of)
                    .collect(Collectors.toSet());
        } else { // phenopacket
            try {
                Phenopacket phenopacket = PhenopacketImporter.readPhenopacket(phenopacketPath);
                phenotypeTermIds = phenopacket.getPhenotypicFeaturesList().stream()
                        .map(pf -> TermId.of(pf.getType().getId()))
                        .collect(Collectors.toSet());

                Optional<Path> vcfFilePathOptional = getVcfFilePath(phenopacket);
                if (vcfFilePathOptional.isEmpty())
                    // complaint is logged within the function
                    return 1;

                vcfFile = vcfFilePathOptional.get();
            } catch (IOException e) {
                LogUtils.logError(LOGGER, "Error reading phenopacket at `{}`: {}", phenopacketPath, e.getMessage());
                return 1;
            }
        }

        try {
            runAnalysis(phenotypeTermIds, vcfFile);
        } catch (InterruptedException | ExecutionException | IOException e) {
            LogUtils.logError(LOGGER, "Error: {}", e.getMessage());
            return 1;
        }

        LogUtils.logInfo(LOGGER, "The analysis has completed successfully. Bye");
        return 0;
    }

    protected int checkArguments() {
        if ((vcfFile == null) == (phenopacketPath == null)) {
            LogUtils.logError(LOGGER,"Path to a VCF file or to a phenopacket must be supplied");
            return 1;
        }

        if (phenopacketPath != null && !hpoTermIdList.isEmpty()) {
            LogUtils.logError(LOGGER, "Passing HPO terms both through CLI and Phenopacket is not supported");
            return 1;
        }

        if (parallelism < 1) {
            LogUtils.logError(LOGGER, "Thread number must be positive: {}", parallelism);
            return 1;
        }
        int processorsAvailable = Runtime.getRuntime().availableProcessors();
        if (parallelism > processorsAvailable) {
            LogUtils.logWarn(LOGGER, "You asked for more threads ({}) than processors ({}) available on the system", parallelism, processorsAvailable);
        }

        if (outputFormats.isEmpty()) {
            LogUtils.logError(LOGGER, "Aborting the analysis since no valid output format was provided");
            return 1;
        }
        return 0;
    }

    private String resolveOutPrefix(Path vcfFile) {
        if (outPrefix != null)
            return outPrefix;

        String vcfPath = vcfFile.toAbsolutePath().toString();
        String prefixBase;
        if (vcfPath.endsWith(".vcf.gz"))
            prefixBase = vcfPath.substring(0, vcfPath.length() - 7);
        else if (vcfPath.endsWith(".vcf"))
            prefixBase = vcfPath.substring(0, vcfPath.length() - 4);
        else
            prefixBase = vcfPath;
        return prefixBase + ".SVANNA";
    }

    private void runAnalysis(Collection<TermId> patientTerms, Path vcfFile) throws IOException, ExecutionException, InterruptedException {
        Collection<OutputFormat> outputFormats = Utils.parseOutputFormats(this.outputFormats);
        try (ConfigurableApplicationContext context = getContext()) {
            GenomicAssembly genomicAssembly = context.getBean(GenomicAssembly.class);

            // check that the HPO terms entered by the user (if any) are valid
            PhenotypeDataService phenotypeDataService = context.getBean(PhenotypeDataService.class);

            LogUtils.logDebug(LOGGER, "Validating the provided phenotype terms");
            Set<Term> validatedPatientTerms = phenotypeDataService.validateTerms(patientTerms);
            LogUtils.logDebug(LOGGER, "Preparing the top-level phenotype terms for the input terms");
            Set<Term> topLevelHpoTerms = phenotypeDataService.getTopLevelTerms(validatedPatientTerms);

            LogUtils.logInfo(LOGGER, "Reading variants from `{}`", vcfFile);
            VariantParser<SvannaVariant> parser = new VcfVariantParser(genomicAssembly, false);
            List<SvannaVariant> variants = parser.createVariantAlleleList(vcfFile);
            LogUtils.logInfo(LOGGER, "Read {} variants", NF.format(variants.size()));

            // Filter
            LogUtils.logInfo(LOGGER, "Filtering out the variants with reciprocal overlap >{}% occurring in more than {}% probands", similarityThreshold, frequencyThreshold);
            LogUtils.logInfo(LOGGER, "Filtering out the variants where ALT allele is supported by less than {} reads", minAltReadSupport);
            AnnotationDataService annotationDataService = context.getBean(AnnotationDataService.class);
            PopulationFrequencyAndCoverageFilter filter = new PopulationFrequencyAndCoverageFilter(annotationDataService, similarityThreshold, frequencyThreshold, minAltReadSupport, maxLength);
            List<SvannaVariant> filteredVariants = filter.filter(variants);

            // Prioritize
            SvPrioritizerFactory svPrioritizerFactory = context.getBean(SvPrioritizerFactory.class);
            SvPrioritizerType svPrioritizerType = SvPrioritizerType.ADDITIVE;
            SvPrioritizer<SvPriority> prioritizer = svPrioritizerFactory.getPrioritizer(svPrioritizerType, patientTerms);

            LogUtils.logInfo(LOGGER, "Prioritizing variants");
            ProgressReporter priorityProgress = new ProgressReporter(5_000);
            Function<SvannaVariant, SvannaVariant> prioritizationFunction = v -> {
                priorityProgress.logItem(v);
                SvPriority priority = prioritizer.prioritize(v);
                v.setSvPriority(priority);
                return v;
            };

            List<SvannaVariant> filteredPrioritizedVariants = TaskUtils.executeBlocking(filteredVariants, prioritizationFunction, parallelism);

            if (modeOfInheritance)
                performModeOfInheritanceReassignment(filteredPrioritizedVariants, phenotypeDataService);

            AnalysisResults results = new AnalysisResults(vcfFile.toAbsolutePath().toString(), validatedPatientTerms, topLevelHpoTerms, filteredPrioritizedVariants);


            ResultWriterFactory resultWriterFactory = context.getBean(ResultWriterFactory.class);
            String prefix = resolveOutPrefix(vcfFile);
            for (OutputFormat outputFormat : outputFormats) {
                ResultWriter writer = resultWriterFactory.resultWriterForFormat(outputFormat);
                if (writer instanceof HtmlResultWriter) {
                    SvannaProperties svannaProperties = context.getBean(SvannaProperties.class);
                    // TODO - is there a more elegant way to pass the HTML specific parameters into the writer?
                    HtmlResultWriter wrt = (HtmlResultWriter) writer;
                    wrt.setAnalysisParameters(getAnalysisParameters(svannaProperties));
                    wrt.setDoNotReportBreakends(doNotReportBreakends);
                }
                writer.write(results, prefix);
            }
        }
    }

    private AnalysisParameters getAnalysisParameters(SvannaProperties properties) {
        AnalysisParameters analysisParameters = new AnalysisParameters();

        analysisParameters.setDataDirectory(properties.dataDirectory());
        analysisParameters.setJannovarCachePath(properties.jannovarCachePath());
        analysisParameters.setPhenopacketPath(phenopacketPath == null ? null : phenopacketPath.toAbsolutePath().toString());
        analysisParameters.setVcfPath(vcfFile.toAbsolutePath().toString());
        analysisParameters.setSimilarityThreshold(similarityThreshold);
        analysisParameters.setFrequencyThreshold(frequencyThreshold);
        analysisParameters.addAllPopulationVariantOrigins(PopulationVariantOrigin.benign());
        analysisParameters.setMinAltReadSupport(minAltReadSupport);
        analysisParameters.setTopNVariantsReported(reportNVariants);
        analysisParameters.setTadStabilityThreshold(properties.dataParameters().tadStabilityThresholdAsPercentage());
        analysisParameters.setUseVistaEnhancers(properties.dataParameters().enhancers().useVista());
        analysisParameters.setUseFantom5Enhancers(properties.dataParameters().enhancers().useFantom5());
        analysisParameters.setPhenotypeTermSimilarityMeasure(properties.prioritizationParameters().termSimilarityMeasure().toString());

        return analysisParameters;
    }

    private static Optional<Path> getVcfFilePath(Phenopacket phenopacket) {
        // There should be exactly one VCF file
        LinkedList<HtsFile> vcfFiles = phenopacket.getHtsFilesList().stream()
                .filter(htsFile -> htsFile.getHtsFormat().equals(HtsFile.HtsFormat.VCF))
                .distinct()
                .collect(Collectors.toCollection(LinkedList::new));
        if (vcfFiles.size() != 1) {
            LogUtils.logWarn(LOGGER, "Expected to find 1 VCF file, found {}", vcfFiles.size());
            return Optional.empty();
        }

        // The VCF file should have a proper URI
        HtsFile vcf = vcfFiles.getFirst();
        try {
            URI uri = new URI(vcf.getUri());
            return Optional.of(Path.of(uri));
        } catch (URISyntaxException e) {
            LogUtils.logWarn(LOGGER, "Invalid URI `{}`: {}", vcf.getUri(), e.getMessage());
            return Optional.empty();
        }
    }

    private static void performModeOfInheritanceReassignment(List<SvannaVariant> filteredPrioritizedVariants, PhenotypeDataService phenotypeDataService) {
        // TODO - find a better place for this code
        LogUtils.logInfo(LOGGER, "Reassigning priorities");
        for (SvannaVariant variant : filteredPrioritizedVariants) {
            if (variant.svPriority() instanceof GeneAwareSvPriority) {
                Zygosity zygosity = variant.zygosity();
                if (zygosity.equals(Zygosity.UNKNOWN))
                    continue;

                GeneAwareSvPriority priority = (GeneAwareSvPriority) variant.svPriority();
                Map<String, Double> map = new HashMap<>();
                for (String geneId : priority.geneIds()) {
                    double geneContribution = priority.geneContribution(geneId);
                    if (geneContribution < 1E-8)
                        continue; // no contribution, the gene is not affected by the variant

                    Set<HpoDiseaseSummary> diseases = phenotypeDataService.getDiseasesForGene(geneId);
                    if (zygosity.equals(Zygosity.HETEROZYGOUS)) {
                        boolean noDominantDisease = true;
                        for (HpoDiseaseSummary disease : diseases) {
                            if (disease.isCompatibleWithInheritance(ModeOfInheritance.AUTOSOMAL_DOMINANT)
                                    || disease.isCompatibleWithInheritance(ModeOfInheritance.X_DOMINANT)) {
                                noDominantDisease = false;
                                break;
                            }
                        }

                        if (noDominantDisease && !diseases.isEmpty())
                            geneContribution *= MODE_OF_INHERITANCE_FACTOR;
                    }
                    map.put(geneId, geneContribution);
                }
                variant.setSvPriority(GeneAwareSvPriority.of(map));
            }
        }
    }
}
