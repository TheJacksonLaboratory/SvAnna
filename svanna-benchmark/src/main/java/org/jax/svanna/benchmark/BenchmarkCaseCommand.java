package org.jax.svanna.benchmark;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jax.svanna.benchmark.util.ProgressReporter;
import org.jax.svanna.benchmark.util.TaskUtils;
import org.jax.svanna.core.LogUtils;
import org.jax.svanna.core.filter.PopulationFrequencyAndCoverageFilter;
import org.jax.svanna.core.hpo.PhenotypeDataService;
import org.jax.svanna.core.landscape.AnnotationDataService;
import org.jax.svanna.core.priority.SvPrioritizer;
import org.jax.svanna.core.priority.SvPrioritizerFactory;
import org.jax.svanna.core.priority.SvPrioritizerType;
import org.jax.svanna.core.priority.SvPriority;
import org.jax.svanna.core.reference.SvannaVariant;
import org.jax.svanna.io.parse.VariantParser;
import org.jax.svanna.io.parse.VcfVariantParser;
import org.monarchinitiative.phenol.ontology.data.Term;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.monarchinitiative.svart.GenomicAssembly;
import org.monarchinitiative.svart.Variant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@CommandLine.Command(name = "benchmark-case",
        aliases = {"BC"},
        header = "Annotate case with additive prioritizer features",
        mixinStandardHelpOptions = true,
        version = Main.VERSION,
        usageHelpWidth = Main.WIDTH,
        footer = Main.FOOTER)
public class BenchmarkCaseCommand extends BaseBenchmarkCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(BenchmarkCaseCommand.class);

    @CommandLine.Option(names = {"--similarity-threshold"}, description = "percentage threshold for determining variant's region is similar enough to database entry (default: ${DEFAULT-VALUE})")
    public float similarityThreshold = 80.F;

    @CommandLine.Option(names = {"--frequency-threshold"}, description = "frequency threshold as a percentage [0-100] (default: ${DEFAULT-VALUE})")
    public float frequencyThreshold = 1.F;

    @CommandLine.Option(names = {"--min-read-support"}, description = "Minimum number of ALT reads to prioritize (default: ${DEFAULT-VALUE})")
    public int minAltReadSupport = 3;

    @CommandLine.Option(names = {"--max-length"}, description = "Do not prioritize variants longer than this (default: ${DEFAULT-VALUE})")
    public int maxLength = 100_000;

    @CommandLine.Option(names = {"-x", "--prefix"}, description = "prefix for output files (default: ${DEFAULT-VALUE})")
    public String outPrefix = "svanna-benchmark";

    @CommandLine.Option(names = {"--n-threads"}, paramLabel = "2", description = "Process variants using n threads (default: ${DEFAULT-VALUE})")
    public int nThreads = 2;

    @CommandLine.Option(names = {"--report-all-variants"}, description = "report rank and priority of all variants (default: ${DEFAULT-VALUE})")
    public boolean reportAllVariants = false;

    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "Where to write the output")
    public Path outputPath;

    @CommandLine.Parameters(
            index = "0",
            description = "path to VCF file with variants to be used as neutral background")
    public Path vcfFile;

    @CommandLine.Parameters(
            index = "1",
            description = "path to Phenopacket JSON file with case reports curated by Hpo Case Annotator")
    public Path caseReport;

    private static String stripVcfSuffixes(Path vcfFile) {
        String name = vcfFile.toFile().getName();
        if (name.endsWith(".vcf")) {
            return name.substring(0, name.length() - 4);
        } else if (name.endsWith(".vcf.gz")) {
            return name.substring(0, name.length() - 7);
        }
        return name;
    }

    @Override
    public Integer call() {
        List<CaseReport> cases = CaseReportImporter.readCasesProvidedAsPositionalArguments(List.of(caseReport));

        if (cases.isEmpty()) {
            LogUtils.logWarn(LOGGER, "Unable to continue with no cases `{}`");
            return 1;
        }

        CaseReport caseReport = cases.get(0);

        try (ConfigurableApplicationContext context = getContext()) {
            GenomicAssembly genomicAssembly = context.getBean(GenomicAssembly.class);

            LogUtils.logInfo(LOGGER, "Reading variants from `{}`", vcfFile);
            VariantParser<? extends SvannaVariant> parser = new VcfVariantParser(genomicAssembly);
            List<? extends SvannaVariant> variants = parser.createVariantAlleleList(vcfFile);
            LogUtils.logInfo(LOGGER, "Read {} variants", NF.format(variants.size()));

            LogUtils.logInfo(LOGGER, "Filtering out the variants with reciprocal overlap >{}% occurring in more than {}% probands", similarityThreshold, frequencyThreshold);
            LogUtils.logInfo(LOGGER, "Filtering out the variants where ALT allele is supported by less than {} reads", minAltReadSupport);
            AnnotationDataService annotationDataService = context.getBean(AnnotationDataService.class);
            PopulationFrequencyAndCoverageFilter filter = new PopulationFrequencyAndCoverageFilter(annotationDataService, similarityThreshold, frequencyThreshold, minAltReadSupport, maxLength);
            List<? extends SvannaVariant> allVariants = filter.filter(variants);

            List<SvannaVariant> filteredVariants = allVariants.stream()
                    .filter(SvannaVariant::passedFilters)
                    .collect(Collectors.toList());

            LogUtils.logInfo(LOGGER, "Removed {} variants that failed the filtering", variants.size() - filteredVariants.size());

            PhenotypeDataService phenotypeDataService = context.getBean(PhenotypeDataService.class);
            SvPrioritizerFactory svPrioritizerFactory = context.getBean(SvPrioritizerFactory.class);

            // ---------------------------------------------

            // get and validate patient terms
            Collection<TermId> patientTerms = caseReport.patientTerms();
            Set<Term> validatedPatientTerms = phenotypeDataService.validateTerms(patientTerms);
            Set<TermId> validatedPatientTermIds = validatedPatientTerms.stream().map(Term::getId).collect(Collectors.toSet());

            // create the prioritizer seeded by the phenotype terms and prioritize the variants
            SvPrioritizer<SvPriority> prioritizer = svPrioritizerFactory.getPrioritizer(SvPrioritizerType.ADDITIVE, validatedPatientTermIds);

            // prepare the variants
            List<Variant> caseVariants = new LinkedList<>(filteredVariants);
            Collection<SvannaVariant> targetVariants = caseReport.variants();
            List<SvannaVariant> filteredTargetVariants = filter.filter(targetVariants);
            for (SvannaVariant filteredTargetVariant : filteredTargetVariants) {
                if (filteredTargetVariant.passedFilters()) {
                    caseVariants.add(filteredTargetVariant);
                } else {
                    LogUtils.logWarn(LOGGER, "Variant {}-{} did not pass the filters! {}", caseReport.caseSummary(), LogUtils.variantSummary(filteredTargetVariant), filteredTargetVariant);
                }
            }

            ProgressReporter progressReporter = new ProgressReporter(5_000);
            Stream<VariantPriority> annotationStream = caseVariants.parallelStream()
                    .onClose(progressReporter.summarize())
                    .peek(progressReporter::logItem)
                    .map(v -> new VariantPriority(v, prioritizer.prioritize(v)));
            List<VariantPriority> priorities = TaskUtils.executeBlocking(() -> annotationStream.collect(Collectors.toList()), nThreads);

            Set<String> causalIds = targetVariants.stream()
                    .map(Variant::id)
                    .collect(Collectors.toSet());

            // ---------------------------------------------

            String vcfName = stripVcfSuffixes(vcfFile);
            BenchmarkResults results = new BenchmarkResults(caseReport.caseSummary().phenopacketId(), vcfName, priorities);

            try {
                Path output = (outputPath != null)
                        ? outputPath
                        : Path.of(outPrefix + ".csv.gz");
                writeOutResults(output.toFile(), results, causalIds);
            } catch (IOException e) {
                LogUtils.logError(LOGGER, "Error: {}", e);
                return 1;
            }

            return 0;
        } catch (Exception e) {
            return 1;
        }

    }

    /**
     * Write a CSV file that represent results of one simulation.
     */
    private void writeOutResults(File output, BenchmarkResults results, Set<String> causalVariantIds) throws IOException {
        LogUtils.logDebug(LOGGER, "Ranking variants");
        List<VariantPriority> prioritized = results.priorities().stream()
                .filter(vp -> causalVariantIds.contains(vp.variant().id()) || reportAllVariants)
                .sorted(Comparator.<VariantPriority>comparingDouble(p -> p.priority().getPriority()).reversed())
                .collect(Collectors.toUnmodifiableList());

        // "case_name", "background_vcf", "variant_id", "rank", "vtype", "is_causal", "priority"
        LogUtils.logInfo(LOGGER, "Writing the results for `{}`", results.caseName());
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GzipCompressorOutputStream(new FileOutputStream(output))))) {
            CSVPrinter printer = CSVFormat.DEFAULT
                    .withHeader("case_name", "background_vcf", "variant_id", "rank", "vtype", "is_causal", "priority")
                    .print(writer);

            int rank = 1;
            for (VariantPriority priority : prioritized) {
                Variant variant = priority.variant();
                printer.print(results.caseName());
                printer.print(results.backgroundVcfName());
                printer.print(variant.id());
                printer.print(rank);
                printer.print(variant.variantType());
                printer.print(causalVariantIds.contains(variant.id()));
                printer.print(priority.priority().getPriority());
                printer.println();

                rank++;
            }
        }
    }

}
