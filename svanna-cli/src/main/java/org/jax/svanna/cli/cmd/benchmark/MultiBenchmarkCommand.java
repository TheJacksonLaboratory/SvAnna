package org.jax.svanna.cli.cmd.benchmark;

import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.jax.svanna.cli.Main;
import org.jax.svanna.core.exception.LogUtils;
import org.jax.svanna.core.filter.PopulationFrequencyAndRepetitiveRegionFilter;
import org.jax.svanna.core.hpo.PhenotypeDataService;
import org.jax.svanna.core.landscape.AnnotationDataService;
import org.jax.svanna.core.priority.SvPrioritizerFactory;
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

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@CommandLine.Command(name = "multi-benchmark",
        aliases = {"MB"},
        header = "Run benchmark with multiple phenopackets against multiple background VCF files",
        mixinStandardHelpOptions = true,
        version = Main.VERSION,
        usageHelpWidth = Main.WIDTH,
        footer = Main.FOOTER)
public class MultiBenchmarkCommand extends BaseBenchmarkCommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiBenchmarkCommand.class);

    @CommandLine.Option(
            names = {"-b", "--background-vcf"},
            arity = "1..*",
            description = "path to VCF file with variants to be used as neutral background")
    public List<Path> vcfPaths;

    @CommandLine.Option(
            names = {"-p", "--phenopacket"},
            arity = "1..*",
            description = "path(s) to JSON files with case reports curated by Hpo Case Annotator")
    public List<Path> caseReports;

    @Override
    public Integer call() {
        LogUtils.logInfo(LOGGER, "Analyzing `{}` phenopackets against `{}` background VCF files resulting in `{}` analyses", caseReports.size(), vcfPaths.size(), caseReports.size() * vcfPaths.size());

        List<CaseReport> cases = CaseReportImporter.readCasesProvidedAsPositionalArguments(caseReports);

        try (ConfigurableApplicationContext context = getContext()) {
            GenomicAssembly genomicAssembly = context.getBean(GenomicAssembly.class);
            VariantParser<SvannaVariant> parser = new VcfVariantParser(genomicAssembly, false);

            LogUtils.logInfo(LOGGER, "Filtering out the variants with reciprocal overlap >{}% occurring in more than {}% probands", similarityThreshold, frequencyThreshold);
            LogUtils.logInfo(LOGGER, "Filtering out the variants where at least >{}% of variant's region occurs in a repetitive region", similarityThreshold);
            AnnotationDataService annotationDataService = context.getBean(AnnotationDataService.class);
            PopulationFrequencyAndRepetitiveRegionFilter filter = new PopulationFrequencyAndRepetitiveRegionFilter(annotationDataService, similarityThreshold, frequencyThreshold);

            SvPrioritizerFactory priorityFactory = context.getBean(SvPrioritizerFactory.class);
            PrioritizationRunner prioritizationRunner = new PrioritizationRunner(priorityFactory, nThreads);

            PhenotypeDataService phenotypeDataService = context.getBean(PhenotypeDataService.class);

            Map<String, Map<String, List<VariantPriority>>> results = new HashMap<>();
            Map<String, Set<String>> causalVariantIds = prepareCausalVariantIds(cases);
            int vcfsProcessed = 1;
            for (Path vcfPath : vcfPaths) {
                LogUtils.logInfo(LOGGER, "({}/{}) Processing background VCF `{}`", vcfsProcessed, vcfPaths.size(), vcfPath.toAbsolutePath());
                HashMap<String, List<VariantPriority>> vcfResults = new HashMap<>();

                List<SvannaVariant> variants = parser.createVariantAlleleList(vcfPath);
                LogUtils.logInfo(LOGGER, "Read {} variants", NF.format(variants.size()));

                List<SvannaVariant> allVariants = filter.filter(variants);

                List<SvannaVariant> filteredVariants = allVariants.stream()
                        .filter(SvannaVariant::passedFilters)
                        .collect(Collectors.toList());


                int nFilteredOut = variants.size() - filteredVariants.size();
                String percentRemoved = NF.format((double) nFilteredOut * 100 / variants.size());
                LogUtils.logInfo(LOGGER, "Removed {} ({}%) variants that failed the filtering", nFilteredOut, percentRemoved);

                int casesProcessed = 1;
                for (CaseReport caseReport : cases) {
                    LogUtils.logInfo(LOGGER, "({}/{}) Processing case `{}`", casesProcessed, cases.size(), caseReport.caseName());

                    // get and validate patient terms
                    Collection<TermId> patientTerms = caseReport.patientTerms();
                    Set<Term> validatedPatientTerms = phenotypeDataService.validateTerms(patientTerms);
                    Set<TermId> validatedPatientTermIds = validatedPatientTerms.stream().map(Term::getId).collect(Collectors.toSet());

                    // prepare the variants
                    List<SvannaVariant> caseVariants = new LinkedList<>(filteredVariants);
                    Collection<SvannaVariant> targetVariants = caseReport.variants();
                    List<SvannaVariant> filteredTargetVariants = filter.filter(targetVariants);
                    for (SvannaVariant filteredTargetVariant : filteredTargetVariants) {
                        if (filteredTargetVariant.passedFilters())
                            caseVariants.add(filteredTargetVariant);
                        else
                            LogUtils.logWarn(LOGGER, "Variant {} in {} did not pass the filters!", LogUtils.variantSummary(filteredTargetVariant), caseReport.caseName());
                    }

                    List<VariantPriority> priorities = prioritizationRunner.prioritize(validatedPatientTermIds, caseVariants);
                    vcfResults.put(caseReport.caseName(), priorities);

                    casesProcessed++;
                }

                results.put(vcfPath.toFile().getName(), vcfResults);
                vcfsProcessed++;
            }

            try {
                writeOutTheResults(results, causalVariantIds);
            } catch (IOException e) {
                LogUtils.logError(LOGGER, "Error: {}", e);
                return 1;
            }

            return 0;

        } catch (Exception e) {
            LogUtils.logError(LOGGER, "Error occurred: {}", e.getMessage());
            return 1;
        }

    }

    private static Map<String, Set<String>> prepareCausalVariantIds(List<CaseReport> cases) {
        Map<String, Set<String>> causalVariantIds = new HashMap<>();

        for (CaseReport aCase : cases) {
            Set<String> ids = aCase.variants().stream()
                    .map(Variant::id)
                    .collect(Collectors.toSet());
            causalVariantIds.put(aCase.caseName(), ids);
        }
        return causalVariantIds;
    }


    private void writeOutTheResults(Map<String, Map<String, List<VariantPriority>>> results, Map<String, Set<String>> causalVariantIds) throws IOException {
        Path outputPath = Path.of(outPrefix + ".csv.gz");
        LogUtils.logInfo(LOGGER, "Writing out the results to `{}`", outputPath.toAbsolutePath());
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new GzipCompressorOutputStream(new FileOutputStream(outputPath.toFile()))))) {
            CSVPrinter printer = CSVFormat.DEFAULT
                    .withHeader("BACKGROUND_VCF", "CASE_NAME", "VARIANT_ID", "VTYPE", "IS_CAUSAL", "PRIORITY")
                    .print(writer);

            for (Map.Entry<String, Map<String , List<VariantPriority>>> vcfResults : results.entrySet()) {
                String backgroundVcfName = vcfResults.getKey();
                LogUtils.logTrace(LOGGER, "Writing the results for `{}`", backgroundVcfName);

                for (Map.Entry<String, List<VariantPriority>> caseResults : vcfResults.getValue().entrySet()) {
                    String caseName = caseResults.getKey();
                    Set<String> variantIds = causalVariantIds.get(caseName);

                    for (VariantPriority variantPriority : caseResults.getValue()) {
                        Variant variant = variantPriority.variant();

                        printer.print(backgroundVcfName); // BACKGROUND_VCF
                        printer.print(caseName); // CASE_NAME
                        printer.print(variant.id()); // VARIANT_ID
                        printer.print(variant.variantType()); // VTYPE
                        printer.print(variantIds.contains(variant.id())); // IS_CAUSAL
                        printer.print(variantPriority.priority().getPriority()); // PRIORITY

                        printer.println();
                    }
                }
            }
        }
    }
}