package org.monarchinitiative.svanna.ingest.cmd;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.MessageDigestAlgorithms;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.monarchinitiative.phenol.annotations.assoc.GeneInfoGeneType;
import org.monarchinitiative.phenol.annotations.formats.hpo.*;
import org.monarchinitiative.phenol.annotations.io.hpo.HpoDiseaseLoaders;
import org.monarchinitiative.svanna.core.LogUtils;
import org.monarchinitiative.svanna.core.SvAnnaRuntimeException;
import org.monarchinitiative.svanna.core.hpo.TermPair;
import org.monarchinitiative.svanna.db.IngestDao;
import org.monarchinitiative.svanna.db.gene.GeneDiseaseDao;
import org.monarchinitiative.svanna.db.landscape.*;
import org.monarchinitiative.svanna.db.phenotype.MicaDao;
import org.monarchinitiative.svanna.ingest.Main;
import org.monarchinitiative.svanna.ingest.config.*;
import org.monarchinitiative.svanna.ingest.hpomap.HpoMapping;
import org.monarchinitiative.svanna.ingest.hpomap.HpoTissueMapParser;
import org.monarchinitiative.svanna.ingest.io.ZipCompressionWrapper;
import org.monarchinitiative.svanna.ingest.parse.GencodeGeneProcessor;
import org.monarchinitiative.svanna.ingest.parse.IngestRecordParser;
import org.monarchinitiative.svanna.ingest.parse.RepetitiveRegionParser;
import org.monarchinitiative.svanna.ingest.parse.dosage.ClingenGeneCurationParser;
import org.monarchinitiative.svanna.ingest.parse.dosage.ClingenRegionCurationParser;
import org.monarchinitiative.svanna.ingest.parse.enhancer.fantom.FantomEnhancerParser;
import org.monarchinitiative.svanna.ingest.parse.enhancer.vista.VistaEnhancerParser;
import org.monarchinitiative.svanna.ingest.parse.population.DbsnpVcfParser;
import org.monarchinitiative.svanna.ingest.parse.population.DgvFileParser;
import org.monarchinitiative.svanna.ingest.parse.population.GnomadSvVcfParser;
import org.monarchinitiative.svanna.ingest.parse.population.HgSvc2VcfParser;
import org.monarchinitiative.svanna.ingest.parse.tad.McArthur2021TadBoundariesParser;
import org.monarchinitiative.svanna.ingest.similarity.IcMicaCalculator;
import org.monarchinitiative.svanna.model.HpoDiseaseSummary;
import org.monarchinitiative.svanna.model.landscape.dosage.DosageRegion;
import org.monarchinitiative.svanna.model.landscape.enhancer.Enhancer;
import org.monarchinitiative.svanna.model.landscape.tad.TadBoundary;
import org.monarchinitiative.svanna.model.landscape.variant.PopulationVariant;
import org.monarchinitiative.phenol.annotations.io.hpo.DiseaseDatabase;
import org.monarchinitiative.phenol.annotations.io.hpo.HpoDiseaseLoader;
import org.monarchinitiative.phenol.annotations.io.hpo.HpoDiseaseLoaderOptions;
import org.monarchinitiative.phenol.base.PhenolRuntimeException;
import org.monarchinitiative.phenol.io.OntologyLoader;
import org.monarchinitiative.phenol.ontology.data.Ontology;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.monarchinitiative.sgenes.gtf.model.GencodeGene;
import org.monarchinitiative.svart.assembly.GenomicAssemblies;
import org.monarchinitiative.svart.assembly.GenomicAssembly;
import org.monarchinitiative.svart.GenomicRegion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import picocli.CommandLine;
import org.monarchinitiative.sgenes.io.GeneParser;
import org.monarchinitiative.sgenes.io.GeneParserFactory;
import org.monarchinitiative.sgenes.io.SerializationFormat;
import org.monarchinitiative.sgenes.model.Gene;
import org.monarchinitiative.sgenes.model.GeneIdentifier;
import org.monarchinitiative.sgenes.model.Located;

import javax.sql.DataSource;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@CommandLine.Command(name = "build-db",
        aliases = "B",
        header = "ingest the annotation files into H2 database",
        mixinStandardHelpOptions = true,
        version = Main.VERSION,
        usageHelpWidth = Main.WIDTH,
        footer = Main.FOOTER)
@EnableAutoConfiguration
@EnableConfigurationProperties(value = {
        IngestProperties.class,
        EnhancerProperties.class,
        VariantProperties.class,
        PhenotypeProperties.class,
        TadProperties.class,
        GeneDosageProperties.class,
        GeneProperties.class
})
public class BuildDb implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(BuildDb.class);

    private static final Set<DiseaseDatabase> DISEASE_DATABASES = Set.of(DiseaseDatabase.DECIPHER,
            DiseaseDatabase.OMIM,
            DiseaseDatabase.ORPHANET);
    private static final NumberFormat NF = NumberFormat.getNumberInstance();

    static {
        NF.setMaximumFractionDigits(2);
    }

    private static final Pattern NCBI_GENE_PATTERN = Pattern.compile("NCBIGene:(?<value>\\d+)");
    private static final Pattern HGNC_GENE_PATTERN = Pattern.compile("HGNC:(?<value>\\d+)");

    private static final String LOCATIONS = "classpath:db/migration";

    @CommandLine.Option(names = {"-o", "--overwrite"},
            description = "remove existing database (default: ${DEFAULT-VALUE})")
    public boolean overwrite = true;

    @CommandLine.Parameters(index = "0",
            paramLabel = "svanna-ingest-config.yml",
            description = "Path to configuration file generated by the `generate-config` command")
    public Path configFile;

    @CommandLine.Parameters(index = "1",
            description = "path to directory where the database will be built (default: ${DEFAULT-VALUE})")
    public Path buildDir = Path.of("data");

    @CommandLine.Option(names = {"--db-version"},
            required = true,
            paramLabel = "2102",
            description = "Database version, e.g. `2102` for database built in Feb 2021")
    public String version;

    @CommandLine.Option(names = {"--assembly"},
            required = true,
            paramLabel = "{hg19, hg38}",
            description = "Genomic assembly version")
    public String assembly;

    public static HikariDataSource initializeDataSource(Path dbPath) {
        HikariDataSource dataSource = makeDataSourceAt(dbPath);

        int migrations = applyMigrations(dataSource);
        LOGGER.info("Applied {} migration(s)", migrations);
        return dataSource;
    }

    private static HikariDataSource makeDataSourceAt(Path databasePath) {
        String absolutePath = databasePath.toFile().getAbsolutePath();
        if (absolutePath.endsWith(".mv.db"))
            absolutePath = absolutePath.substring(0, absolutePath.length() - 6);

        String jdbcUrl = String.format("jdbc:h2:file:%s", absolutePath);
        HikariConfig config = new HikariConfig();
        config.setUsername("sa");
        config.setPassword("sa");
        config.setDriverClassName("org.h2.Driver");
        config.setJdbcUrl(jdbcUrl);

        return new HikariDataSource(config);
    }

    private static int applyMigrations(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations(LOCATIONS)
                .load();
        MigrateResult migrate = flyway.migrate();
        return migrate.migrationsExecuted;
    }

    private static String getVersionedAssembly(String assembly, String version) {
        assembly = normalizeAssemblyString(assembly);
        // a string like `1902_hg19`
        return version + "_" + assembly;
    }

    private static String normalizeAssemblyString(String assembly) {
        switch (assembly.toLowerCase()) {
            case "hg19":
            case "grch37":
                return "hg19";
            case "hg38":
            case "grch38":
                return "hg38";
            default:
                throw new SvAnnaRuntimeException(String.format("Unknown assembly string '%s'", assembly));
        }
    }

    private static PhenotypeData downloadPhenotypeFiles(PhenotypeProperties properties,
                                                        DataSource dataSource,
                                                        Path buildDir,
                                                        Path tmpDir,
                                                        List<? extends GencodeGene> genes,
                                                        Map<Integer, Integer> ncbiGeneToHgnc) throws IOException {
        // JSON ontology belongs to the buildDir
        URL hpoJsonUrl = new URL(properties.hpoJsonUrl());
        Path hpoOboPath = downloadUrl(hpoJsonUrl, buildDir);

        // other files are temporary
        // HPOA
        URL hpoAnnotationsUrl = new URL(properties.hpoAnnotationsUrl());
        Path hpoAnnotationsPath = downloadUrl(hpoAnnotationsUrl, tmpDir);
        // mim2geneMedgen
        URL mim2geneMedgenUrl = new URL(properties.mim2geneMedgenUrl());
        Path mim2geneMedgenPath = downloadUrl(mim2geneMedgenUrl, tmpDir);
        // geneInfoPath
        URL geneInfoPathUrl = new URL(properties.geneInfoUrl());
        Path geneInfoPath = downloadUrl(geneInfoPathUrl, tmpDir);
        // Download is done

        GeneDiseaseDao geneDiseaseDao = new GeneDiseaseDao(dataSource);

        // Ingest geneIdentifiers
        int updatedGeneIdentifiers = insertGeneIdentifiers(genes, geneDiseaseDao, ncbiGeneToHgnc);
        LOGGER.info("Ingest of gene identifiers updated {} rows", NF.format(updatedGeneIdentifiers));


        // Read phenotype data
        LOGGER.debug("Reading HPO file from {}", hpoOboPath);
        Ontology hpo = OntologyLoader.loadOntology(hpoOboPath.toFile());

        LOGGER.debug("Parsing HPO disease associations at {}", hpoAnnotationsPath);
        LOGGER.debug("Parsing gene info file at {}", geneInfoPath.toAbsolutePath());
        LOGGER.debug("Parsing MIM to gene medgen file at {}", mim2geneMedgenPath.toAbsolutePath());
        HpoDiseaseLoaderOptions loaderOptions = HpoDiseaseLoaderOptions.of(DISEASE_DATABASES, true, HpoDiseaseLoaderOptions.DEFAULT_COHORT_SIZE);
        HpoDiseaseLoader loader = HpoDiseaseLoaders.defaultLoader(hpo, loaderOptions);
        HpoDiseases diseases = loader.load(hpoAnnotationsPath);
        HpoAssociationData hpoAssociationData = HpoAssociationData.builder(hpo)
                .hpoDiseases(diseases)
                .mim2GeneMedgen(mim2geneMedgenPath)
                .homoSapiensGeneInfo(geneInfoPath, GeneInfoGeneType.DEFAULT)
                .build();

        // Ingest geneToDisease
        int updatedGeneToDisease = ingestGeneToDiseaseMap(hpoAssociationData, ncbiGeneToHgnc, diseases, geneDiseaseDao);
        LOGGER.info("Ingest of gene to disease associations updated {} rows", NF.format(updatedGeneToDisease));

        // Ingest disease to phenotypes
        int updatedDiseaseToPhenotypes = ingestDiseaseToPhenotypes(geneDiseaseDao, diseases);
        LOGGER.info("Ingest of disease to phenotypes updated {} rows", NF.format(updatedDiseaseToPhenotypes));

        // Return the PhenotypeData so that we don't have to re-read the files
        return new PhenotypeData(hpo, diseases, hpoAssociationData);
    }

    private static int insertGeneIdentifiers(List<? extends GencodeGene> genes,
                                             GeneDiseaseDao geneDiseaseDao,
                                             Map<Integer, Integer> ncbiGeneToHgnc) {
        Map<Integer, Integer> hgncToNcbiGene = ncbiGeneToHgnc.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getValue, Map.Entry::getKey));

        List<GeneIdentifier> geneIdentifiers = new ArrayList<>(genes.size());
        for (GencodeGene gene : genes) {
            Optional<String> hgncIdOpt = gene.id().hgncId();
            Optional<String> ncbiGeneOpt = gene.id().ncbiGeneId();

            String hgncId = null, ncbiGeneId = null;

            if (hgncIdOpt.isPresent()) {
                // We have the ID, this was easy
                hgncId = hgncIdOpt.get();
            } else {
                // Let's try to get the ID from the corresponding NCBIGene id
                if (ncbiGeneOpt.isPresent()) {
                    Matcher matcher = NCBI_GENE_PATTERN.matcher(ncbiGeneOpt.get());
                    if (matcher.matches()) {
                        int ncbiGeneInt = Integer.parseInt(matcher.group("value"));
                        Integer hgncIdInt = ncbiGeneToHgnc.get(ncbiGeneInt);
                        if (hgncIdInt != null)
                            hgncId = "HGNC:" + hgncIdInt;
                    }
                }
            }

            if (ncbiGeneOpt.isPresent()) {
                // We have the ID, this was easy
                ncbiGeneId = ncbiGeneOpt.get();
            } else {
                // Let's try to get the ID from corresponding HGNC id
                if (hgncIdOpt.isPresent()) {
                    Matcher matcher = HGNC_GENE_PATTERN.matcher(hgncIdOpt.get());
                    if (matcher.matches()) {
                        int hgncIdInt = Integer.parseInt(matcher.group("value"));
                        Integer ncbiGeneInt = hgncToNcbiGene.get(hgncIdInt);
                        if (ncbiGeneInt != null)
                            ncbiGeneId = "NCBIGene:" + ncbiGeneInt;
                    }
                }
            }
            geneIdentifiers.add(GeneIdentifier.of(gene.accession(), gene.symbol(), hgncId, ncbiGeneId));
        }

        return geneDiseaseDao.insertGeneIdentifiers(geneIdentifiers);
    }

    private static int ingestGeneToDiseaseMap(HpoAssociationData hpoAssociationData,
                                              Map<Integer, Integer> ncbiGeneToHgnc,
                                              HpoDiseases diseases,
                                              GeneDiseaseDao geneDiseaseDao) {

        Map<Integer, List<HpoDiseaseSummary>> geneToDisease = new HashMap<>();

        // extract relevant bits and pieces for diseases, and map NCBIGene to HGNC
        Map<TermId, Collection<TermId>> geneToDiseaseIdMap = hpoAssociationData.associations().geneIdToDiseaseIds();

        Map<TermId, HpoDisease> diseaseMap = diseases.diseaseById();
        for (TermId ncbiGeneTermId : geneToDiseaseIdMap.keySet()) {
            Matcher matcher = NCBI_GENE_PATTERN.matcher(ncbiGeneTermId.getValue());
            if (matcher.matches()) {
                int ncbiGeneId = Integer.parseInt(matcher.group("value"));
                Integer hgncId = ncbiGeneToHgnc.get(ncbiGeneId);
                if (hgncId != null) {
                    for (TermId diseaseId : geneToDiseaseIdMap.get(ncbiGeneTermId)) {
                        HpoDisease hpoDisease = diseaseMap.get(diseaseId);
                        if (hpoDisease != null) {
                            geneToDisease.computeIfAbsent(hgncId, k -> new LinkedList<>())
                                    .add(HpoDiseaseSummary.of(diseaseId.getValue(), hpoDisease.diseaseName()));
                        }
                    }
                }

            }
        }

        return geneDiseaseDao.insertGeneToDisease(geneToDisease);
    }

    private static int ingestDiseaseToPhenotypes(GeneDiseaseDao geneDiseaseDao, HpoDiseases diseases) {

        int updated = 0;
        for (HpoDisease disease : diseases) {
            List<TermId> presentPhenotypeTermIds = disease.presentAnnotationsStream()
                    .map(HpoDiseaseAnnotation::id)
                    .collect(Collectors.toList());
            updated += geneDiseaseDao.insertDiseaseToPhenotypes(disease.id().getValue(), presentPhenotypeTermIds);
        }
        return updated;
    }

    private static List<? extends GencodeGene> downloadAndPreprocessGenes(GeneProperties properties,
                                                                          GenomicAssembly assembly,
                                                                          Path buildDir,
                                                                          Path tmpDir) throws IOException {
        // download Gencode GTF
        URL url = new URL(properties.gencodeGtfUrl());
        Path localGencodeGtfPath = downloadUrl(url, tmpDir);

        // Load the Gencode GTF into the "silent gene" format
        LOGGER.info("Reading Gencode GTF file at {}", localGencodeGtfPath.toAbsolutePath());
        GencodeGeneProcessor gencodeGeneProcessor = new GencodeGeneProcessor(localGencodeGtfPath, assembly);
        List<? extends GencodeGene> genes = gencodeGeneProcessor.process();
        LOGGER.info("Read {} genes", NF.format(genes.size()));

        // dump the transformed genes to compressed JSON file in the build directory
        GeneParserFactory parserFactory = GeneParserFactory.of(assembly);
        GeneParser jsonParser = parserFactory.forFormat(SerializationFormat.JSON);
        Path destination = buildDir.resolve("gencode.v38.genes.json.gz");
        LOGGER.info("Serializing the genes to {}", destination.toAbsolutePath());
        try (OutputStream os = new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(destination)))) {
            jsonParser.write(genes, os);
        }

        return genes;
    }

    private static Path downloadLiftoverChain(IngestProperties properties, Path tmpDir) throws IOException {
        URL chainUrl = new URL(properties.hg19toHg38ChainUrl()); // download hg19 to hg38 liftover chain
        return downloadUrl(chainUrl, tmpDir);
    }

    private static void ingestEnhancers(EnhancerProperties properties,
                                        GenomicAssembly assembly,
                                        DataSource dataSource) throws IOException {
        Map<TermId, HpoMapping> uberonToHpoMap;
        try (InputStream is = BuildDb.class.getResourceAsStream("/uberon_tissue_to_hpo_top_level.csv")) {
            HpoTissueMapParser hpoTissueMapParser = new HpoTissueMapParser(is);
            uberonToHpoMap = hpoTissueMapParser.getOtherToHpoMap();
        }

        IngestRecordParser<? extends Enhancer> vistaParser = new VistaEnhancerParser(assembly, Path.of(properties.vista()), uberonToHpoMap);
        IngestDao<Enhancer> ingestDao = new EnhancerAnnotationDao(dataSource, assembly);
        int updated = ingestTrack(vistaParser, ingestDao);
        LOGGER.info("Ingest of Vista enhancers affected {} rows", NF.format(updated));

        IngestRecordParser<? extends Enhancer> fantomParser = new FantomEnhancerParser(assembly, Path.of(properties.fantomMatrix()), Path.of(properties.fantomSample()), uberonToHpoMap);
        updated = ingestTrack(fantomParser, ingestDao);
        LOGGER.info("Ingest of FANTOM5 enhancers affected {} rows", NF.format(updated));
    }

    private static void ingestPopulationVariants(VariantProperties properties, GenomicAssembly assembly, DataSource dataSource, Path tmpDir,
                                                 Path hg19Hg38chainPath) throws IOException {
        // DGV
        URL dgvUrl = new URL(properties.dgvUrl());
        Path dgvLocalPath = downloadUrl(dgvUrl, tmpDir);
        LOGGER.info("Ingesting DGV data");
        DbPopulationVariantDao ingestDao = new DbPopulationVariantDao(dataSource, assembly);
        int dgvUpdated = ingestTrack(new DgvFileParser(assembly, dgvLocalPath), ingestDao);
        LOGGER.info("DGV ingest updated {} rows", NF.format(dgvUpdated));

        // GNOMAD SV
        URL gnomadUrl = new URL(properties.gnomadSvVcfUrl());
        Path gnomadLocalPath = downloadUrl(gnomadUrl, tmpDir);
        LOGGER.info("Ingesting GnomadSV data");
        IngestRecordParser<PopulationVariant> gnomadParser = new GnomadSvVcfParser(assembly, gnomadLocalPath, hg19Hg38chainPath);
        int gnomadUpdated = ingestTrack(gnomadParser, ingestDao);
        LOGGER.info("GnomadSV ingest updated {} rows", NF.format(gnomadUpdated));

        // HGSVC2
        URL hgsvc2 = new URL(properties.hgsvc2VcfUrl());
        Path hgsvc2Path = downloadUrl(hgsvc2, tmpDir);
        LOGGER.info("Ingesting HGSVC2 data");
        IngestRecordParser<PopulationVariant> hgSvc2VcfParser = new HgSvc2VcfParser(assembly, hgsvc2Path);
        int hgsvc2Updated = ingestTrack(hgSvc2VcfParser, ingestDao);
        LOGGER.info("HGSVC2 ingest updated {} rows", NF.format(hgsvc2Updated));

        // dbSNP
        URL dbsnp = new URL(properties.dbsnpVcfUrl());
        Path dbSnpPath = downloadUrl(dbsnp, tmpDir);
        LOGGER.info("Ingesting dbSNP data");
        IngestRecordParser<PopulationVariant> dbsnpVcfParser = new DbsnpVcfParser(assembly, dbSnpPath);
        int dbsnpUpdated = ingestTrack(dbsnpVcfParser, ingestDao);
        LOGGER.info("dbSNP ingest updated {} rows", NF.format(dbsnpUpdated));
    }

    private static void ingestRepeats(IngestProperties properties, GenomicAssembly assembly, DataSource dataSource, Path tmpDir) throws IOException {
        URL repeatsUrl = new URL(properties.getRepetitiveRegionsUrl());
        Path repeatsLocalPath = downloadUrl(repeatsUrl, tmpDir);

        LOGGER.info("Ingesting repeats data");
        int repetitiveUpdated = ingestTrack(new RepetitiveRegionParser(assembly, repeatsLocalPath), new RepetitiveRegionDao(dataSource, assembly));
        LOGGER.info("Repeats ingest updated {} rows", NF.format(repetitiveUpdated));
    }

    private static void ingestTads(TadProperties properties, GenomicAssembly assembly, DataSource dataSource, Path tmpDir, Path chain) throws IOException {
        // McArthur2021 supplement
        URL mcArthurSupplement = new URL(properties.mcArthur2021Supplement());
        Path localPath = downloadUrl(mcArthurSupplement, tmpDir);

        try (ZipFile zipFile = new ZipFile(localPath.toFile())) {
            // this is the single file from the entire ZIP that we're interested in
            String entryName = "emcarthur-TAD-stability-heritability-184f51a/data/boundariesByStability/100kbBookendBoundaries_mainText/100kbBookendBoundaries_byStability.bed";
            ZipArchiveEntry entry = zipFile.getEntry(entryName);
            InputStream is = zipFile.getInputStream(entry);
            IngestRecordParser<TadBoundary> parser = new McArthur2021TadBoundariesParser(assembly, is, chain);
            IngestDao<TadBoundary> dao = new TadBoundaryDao(dataSource, assembly);
            int updated = ingestTrack(parser, dao);
            LOGGER.info("Ingest of TAD boundaries affected {} rows", NF.format(updated));
        }
    }

    private static void precomputeIcMica(DataSource dataSource,
                                         Ontology hpo,
                                         HpoDiseases diseases) {
        Map<TermPair, Double> similarityMap = IcMicaCalculator.precomputeIcMicaValues(hpo, diseases);

        MicaDao dao = new MicaDao(dataSource);
        similarityMap.forEach(dao::insertItem);
    }

    private static Map<TermId, GenomicRegion> readGeneRegions(List<? extends GencodeGene> genes) {
        Map<TermId, GenomicRegion> regionsByHgncId = new HashMap<>(genes.size());
        for (Gene gene : genes) {
            Optional<String> hgncIdOptional = gene.id().hgncId();
            if (hgncIdOptional.isEmpty())
                continue;

            try {
                TermId hgncId = TermId.of(hgncIdOptional.get());
                regionsByHgncId.put(hgncId, gene.location());
            } catch (PhenolRuntimeException e) {
                LOGGER.warn("Invalid HGNC id `{}` in gene {}", hgncIdOptional.get(), gene);
            }
        }

        return regionsByHgncId;
    }

    private static void ingestGeneDosage(GeneDosageProperties properties,
                                         GenomicAssembly assembly,
                                         DataSource dataSource,
                                         Path tmpDir,
                                         Map<TermId, ? extends GenomicRegion> geneRegions,
                                         Map<Integer, Integer> ncbiGeneToHgnc) throws IOException {
        ClingenDosageElementDao clingenDosageElementDao = new ClingenDosageElementDao(dataSource, assembly);

        // dosage sensitive genes
        URL geneUrl = new URL(properties.getGeneUrl());
        Path geneLocalPath = downloadUrl(geneUrl, tmpDir);
        ClingenGeneCurationParser geneParser = new ClingenGeneCurationParser(geneLocalPath, assembly, geneRegions, ncbiGeneToHgnc);
        try (Stream<? extends DosageRegion> geneStream = geneParser.parse()) {
            int geneUpdated = geneStream
                    .mapToInt(clingenDosageElementDao::insertItem)
                    .sum();
            LOGGER.info("Ingest of dosage sensitive genes affected {} rows", NF.format(geneUpdated));
        }

        // dosage sensitive regions
        URL regionUrl = new URL(properties.getRegionUrl());
        Path regionLocalPath = downloadUrl(regionUrl, tmpDir);
        ClingenRegionCurationParser regionParser = new ClingenRegionCurationParser(regionLocalPath, assembly);
        try (Stream<? extends DosageRegion> regionStream = regionParser.parse()) {
            int regionsUpdated = regionStream
                    .mapToInt(clingenDosageElementDao::insertItem)
                    .sum();
            LOGGER.info("Ingest of dosage sensitive regions affected {} rows", NF.format(regionsUpdated));
        }
    }

    private static Map<Integer, Integer> parseNcbiToHgncTable(String ncbiGeneToHgnc) throws IOException {
        Path tablePath = Path.of(ncbiGeneToHgnc);
        if (Files.notExists(tablePath)) {
            throw new IOException("Table for mapping NCBIGene to HGNC does not exist at " + tablePath.toAbsolutePath());
        }

        Map<Integer, Integer> results = new HashMap<>();
        try (BufferedReader reader = openForReading(tablePath);
             CSVParser parser = CSVFormat.TDF.withFirstRecordAsHeader().parse(reader)) {
            Pattern hgncPattern = Pattern.compile("HGNC:(?<payload>\\d+)");
            // HGNC ID	NCBI gene ID	Approved symbol
            // HGNC:13666	8086	AAAS
            for (CSVRecord record : parser) {
                // parse NCBIGene. Should be a number, but may be missing.
                String ncbiGene = record.get("NCBI gene ID");
                if (ncbiGene.isBlank())
                    // missing NCBI gene ID for this gene
                    continue;

                int ncbiGeneId;
                try {
                    ncbiGeneId = Integer.parseInt(ncbiGene);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Skipping non-numeric NCBIGene id `{}` on line #{}: `{}`", ncbiGene, record.getRecordNumber(), record);
                    continue;
                }

                // parse HGNC id
                Matcher hgncMatcher = hgncPattern.matcher(record.get("HGNC ID"));
                if (!hgncMatcher.matches()) {
                    LOGGER.warn("Skipping HGNC id `{}` on line #{}: `{}`", record.get("HGNC ID"), record.getRecordNumber(), record);
                    continue;
                }
                Integer hgncId = Integer.parseInt(hgncMatcher.group("payload"));

                // store the results
                results.put(ncbiGeneId, hgncId);
            }
        }
        return results;
    }

    private static BufferedReader openForReading(Path tablePath) throws IOException {
        return (tablePath.toFile().getName().endsWith(".gz"))
                ? new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(tablePath))))
                : Files.newBufferedReader(tablePath);

    }

    private static <T extends Located> int ingestTrack(IngestRecordParser<? extends T> ingestRecordParser, IngestDao<? super T> ingestDao) throws IOException {
        return ingestRecordParser.parse()
                .mapToInt(ingestDao::insertItem)
                .sum();
    }

    private static Path downloadUrl(URL url, Path downloadDir) throws IOException {
        String file = url.getFile();
        String urlFileName = file.substring(file.lastIndexOf('/') + 1);
        Path localPath = downloadDir.resolve(urlFileName);
        LOGGER.info("Downloading data from `{}` to {}", url, localPath);
        downloadFile(url, localPath.toFile());
        return localPath;
    }

    private static void downloadFile(URL source, File target) throws IOException {
        if (target.isFile()) return;
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs())
            throw new IOException("Unable to create parent directory " + parent.getAbsolutePath() + " for downloading " + target.getAbsolutePath());

        URLConnection connection;
        if ("http".equals(source.getProtocol()))
            connection = openHttpConnectionHandlingRedirects(source);
        else
            connection = source.openConnection();

        try (BufferedInputStream is = new BufferedInputStream(connection.getInputStream())) {
            FileOutputStream os = new FileOutputStream(target);
            IOUtils.copyLarge(is, os);
        }
    }

    private static URLConnection openHttpConnectionHandlingRedirects(URL source) throws IOException {
        LogUtils.logDebug(LOGGER, "Opening HTTP connection to `{}`", source);
        HttpURLConnection connection = (HttpURLConnection) source.openConnection();
        int status = connection.getResponseCode();

        if (status != HttpURLConnection.HTTP_OK) {
            LogUtils.logDebug(LOGGER, "Received response `{}`", status);
            if (status == HttpURLConnection.HTTP_MOVED_PERM || status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_SEE_OTHER) {
                String location = connection.getHeaderField("Location");
                LogUtils.logDebug(LOGGER, "Redirecting to `{}`", location);
                return new URL(location).openConnection();
            }
        }
        return connection;
    }

    @Override
    public Integer call() throws Exception {
        try (ConfigurableApplicationContext context = getContext()) {
            IngestProperties properties = context.getBean(IngestProperties.class);

            GenomicAssembly assembly = GenomicAssemblies.GRCh38p13();
            if (buildDir.toFile().exists()) {
                if (!buildDir.toFile().isDirectory()) {
                    LOGGER.error("Not a directory: {}", buildDir);
                    return 1;
                }
            } else {
                if (!buildDir.toFile().mkdirs()) {
                    LOGGER.error("Unable to create build directory");
                    return 1;
                }
            }

            Path dbPath = buildDir.resolve("svanna_db.mv.db");
            if (dbPath.toFile().isFile()) {
                if (overwrite) {
                    LOGGER.info("Removing the old database");
                    Files.delete(dbPath);
                } else {
                    LOGGER.info("Abort since the database already exists at {}. ", dbPath);
                    return 0;
                }
            }

            LOGGER.info("Creating database at {}", dbPath);
            HikariDataSource dataSource = initializeDataSource(dbPath);

            Path tmpDir = buildDir.resolve("build");
            List<? extends GencodeGene> genes = downloadAndPreprocessGenes(properties.getGenes(), assembly, buildDir, tmpDir);
            Map<Integer, Integer> ncbiGeneToHgncId = parseNcbiToHgncTable(properties.ncbiGeneToHgnc());

            PhenotypeData phenotypeData = downloadPhenotypeFiles(properties.phenotype(),
                    dataSource,
                    buildDir,
                    tmpDir,
                    genes,
                    ncbiGeneToHgncId);

            ingestEnhancers(properties.enhancers(), assembly, dataSource);

            Path hg19ToHg38Chain = downloadLiftoverChain(properties, tmpDir);
            ingestPopulationVariants(properties.variants(), assembly, dataSource, tmpDir, hg19ToHg38Chain);
            ingestRepeats(properties, assembly, dataSource, tmpDir);
            ingestTads(properties.tad(), assembly, dataSource, tmpDir, hg19ToHg38Chain);

            precomputeIcMica(dataSource, phenotypeData.hpo(), phenotypeData.hpoDiseases());
            Map<TermId, GenomicRegion> geneMap = readGeneRegions(genes);
            ingestGeneDosage(properties.getDosage(), assembly, dataSource, tmpDir, geneMap, ncbiGeneToHgncId);
            dataSource.close();
        }

        // Calculate SHA256 digest for the resource files
        List<File> resources = Arrays.stream(Objects.requireNonNull(buildDir.toFile().listFiles()))
                .filter(File::isFile)
                .collect(Collectors.toList());
        LOGGER.info("Calculating SHA256 digest for resource files in `{}`", buildDir.toAbsolutePath());
        Map<File, String> fileToDigest = new HashMap<>();
        {
            DigestUtils digest = new DigestUtils(MessageDigestAlgorithms.SHA_256);

            for (File resource : resources) {
                if (LOGGER.isDebugEnabled()) LOGGER.debug("Calculating SHA256 digest for `{}`", resource);
                String hexDigest = digest.digestAsHex(resource);
                fileToDigest.put(resource, hexDigest);
            }
        }

        Path digestFilePath = buildDir.resolve("checksum.sha256");
        LOGGER.info("Storing the digest into `{}`", digestFilePath);
        try (BufferedWriter digestWriter = Files.newBufferedWriter(digestFilePath)) {
            for (File resource : fileToDigest.keySet()) {
                String line = String.format("%s  %s", fileToDigest.get(resource), resource.getName());
                digestWriter.write(line);
                digestWriter.write(System.lineSeparator());
            }
        }

        {
            List<File> resourcesToCompress = new ArrayList<>(resources);
            resourcesToCompress.add(digestFilePath.toFile());
            Path zipPath = buildDir.resolve(getVersionedAssembly(assembly, version) + ".svanna.zip");
            LOGGER.info("Compressing the resource files into a single ZIP file `{}`", zipPath);
            try (ZipCompressionWrapper wrapper = new ZipCompressionWrapper(zipPath.toFile())) {
                for (File resource : resourcesToCompress) {
                    LOGGER.info("Compressing `{}`", resource);
                    wrapper.addResource(resource, resource.getName());
                }
            }
        }

        LOGGER.info("The ingest is complete");
        return 0;
    }

    protected ConfigurableApplicationContext getContext() {
        // bootstrap Spring application context
        return new SpringApplicationBuilder(BuildDb.class)
                .properties(Map.of("spring.config.location", configFile.toString()))
                .run();
    }

    private static class PhenotypeData {
        private final Ontology hpo;
        private final HpoDiseases hpoDiseases;
        private final HpoAssociationData hpoAssociationData;

        private PhenotypeData(Ontology hpo, HpoDiseases hpoDiseases, HpoAssociationData hpoAssociationData) {
            this.hpo = hpo;
            this.hpoDiseases = hpoDiseases;
            this.hpoAssociationData = hpoAssociationData;
        }

        public Ontology hpo() {
            return hpo;
        }

        public HpoDiseases hpoDiseases() {
            return hpoDiseases;
        }

        public HpoAssociationData hpoAssociationData() {
            return hpoAssociationData;
        }

    }
}
