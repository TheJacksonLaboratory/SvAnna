package org.jax.svann;

import com.google.common.collect.Multimap;
import org.jax.svann.lirical.LiricalHit;
import org.jax.svann.reference.Position;
import org.jax.svann.reference.genome.Contig;
import org.jax.svann.reference.genome.GenomeAssembly;
import org.jax.svann.reference.genome.GenomeAssemblyProvider;
import org.jax.svann.tspec.Enhancer;
import org.jax.svann.tspec.GencodeParser;
import org.jax.svann.tspec.TSpecParser;
import org.jax.svann.tspec.TssPosition;
import org.monarchinitiative.phenol.annotations.assoc.HpoAssociationParser;
import org.monarchinitiative.phenol.io.OntologyLoader;
import org.monarchinitiative.phenol.ontology.data.Ontology;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class SvAnnAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(SvAnnAnalysis.class);
    final static double THRESHOLD = 1;
    private final static int DISTANCE_THRESHOLD = 500_000;
    private final List<LiricalHit> hitlist;
    private final Map<TermId, Set<String>> diseaseId2GeneSymbolMap;
    private final String outputfile;
    private final List<TermId> targetHpoIdList;
    private final  Map<String, List<TssPosition>> symbolToTranscriptListMap;
    private final Set<Enhancer> phenotypicallyRelevantEnhancerSet = new HashSet<>();
    /**
     * TODO allow as parameter to CTOR
     */
    private final GenomeAssembly assembly = GenomeAssemblyProvider.getGrch38Assembly();

    public SvAnnAnalysis(String liricalPath, String Vcf, String outfile, List<TermId> tidList, String enhancerPath, String gencode) {

        TSpecParser tparser = new TSpecParser(enhancerPath);
        Map<TermId, List<Enhancer>> id2enhancerMap = tparser.getId2enhancerMap();
        Map<TermId, String> hpoId2LabelMap = tparser.getId2labelMap();
        GencodeParser gparser = new GencodeParser(gencode);
        this.symbolToTranscriptListMap = gparser.getSymbolToTranscriptListMap();
        this.diseaseId2GeneSymbolMap = initDiseaseMap();
        this.hitlist = new ArrayList<>();
        this.outputfile = outfile;
        this.targetHpoIdList = tidList;
        for (TermId t : tidList) {
            List<Enhancer> enhancers = id2enhancerMap.getOrDefault(t, new ArrayList<>());
            phenotypicallyRelevantEnhancerSet.addAll(enhancers);
        }
        List<LiricalHit> hitlist = getLiricalHitList(liricalPath);
        for (var hit : hitlist) {
            Set<String> geneSymbols = hit.getGeneSymbols();
            Set<Enhancer> enhancers = getRelevantEnhancers(geneSymbols);
            hit.setEnhancerSet(enhancers);
        }
    }


    private Set<Enhancer>  getRelevantEnhancers(Set<String> geneSymbols) {
        Set<Enhancer> relevant = new HashSet<>();

        for (var gene : geneSymbols) {
            List<TssPosition> tssList = this.symbolToTranscriptListMap.getOrDefault(gene, List.of());
            for (var tss : tssList) {
                String chr = tss.getGenomicPosition().getChromosome();
                final Optional<Contig> contigOptional = assembly.getContigByName(chr);
                if (contigOptional.isEmpty()) {
                    LOGGER.warn("Unknown contig `{}` for {}", chr, tss);
                    continue;
                }
                final Contig contig = contigOptional.get();
                int pos = tss.getGenomicPosition().getPosition();
                for (var e : phenotypicallyRelevantEnhancerSet) {
                    // TODO Do we need the TSS class and should it return a Position?
                    if (e.matchesPos(contig, Position.precise( pos ), DISTANCE_THRESHOLD)) {
                        relevant.add(e);
                    }
                }
            }
        }

        return relevant;
    }


    public SvAnnAnalysis(String liricalPath, String Vcf, String outfile) {
        this.diseaseId2GeneSymbolMap = initDiseaseMap();
        symbolToTranscriptListMap = new HashMap<>();
        System.out.printf("We retrieved %d disease to gene annotations.\n", diseaseId2GeneSymbolMap.size());
        this.outputfile = outfile;
        hitlist = new ArrayList<>();

        System.out.println(Vcf);
        this.targetHpoIdList = null;

    }


    private List<LiricalHit> getLiricalHitList(String liricalPath) {
        String line;
        File liricalFile = new File(liricalPath);
        if (! liricalFile.exists()) {
            System.err.printf("[ERROR] Could not find LIRICAL input file at %s\n", liricalPath);
            return List.of();
        }
        try (BufferedReader br = new BufferedReader(new FileReader(liricalPath))) {
            //rank    diseaseName     diseaseCurie    pretestprob     posttestprob    compositeLR     entrezGeneId    variants
            while ((line=br.readLine()) != null) {
                if (line.startsWith("rank")) break;
            }
            while ((line=br.readLine()) != null) {
                String []fields = line.split("\t");
                if (fields.length < 8) {
                    System.err.println("[ERROR] Bad line, less than 8 fields: " + line);
                    continue;
                }
                String dname = fields[1];
                String dcurie = fields[2];
                String posttestprob = fields[4].replace("%","");
                try {
                    double prob = Double.parseDouble(posttestprob);
                    double lr = Double.parseDouble(fields[5].replaceAll(",",""));
                    if (prob > THRESHOLD) {
                        LiricalHit hit = new LiricalHit(dname, dcurie, prob, lr);
                        TermId diseaseId = TermId.of(dcurie);
                        if (this.diseaseId2GeneSymbolMap.containsKey(diseaseId)) {
                            //System.out.println("found genes for " + dname);
                            hit.setGeneSymbols(diseaseId2GeneSymbolMap.get(diseaseId));
                        }
                        hitlist.add(hit);
                    }
                } catch (NumberFormatException e) {
                    System.err.println("[ERROR] " + e.getLocalizedMessage());
                    System.err.println("[ERROR] " + line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
        System.out.printf("[INFO] We got %d above threshold candidates.\n", hitlist.size());
        return hitlist;
    }

    public List<LiricalHit> getHitlist() {
        return hitlist;
    }

    private Map<TermId, Set<String>> initDiseaseMap() {
        String geneinfo = "data/Homo_sapiens_gene_info.gz";
        String mimgene = "data/mim2gene_medgen";
        String hpo = "data/hp.obo";
        Ontology ontology = OntologyLoader.loadOntology(new File(hpo));
        HpoAssociationParser  parser = new HpoAssociationParser(geneinfo, mimgene, ontology);
        Map<TermId, String> id2sym = parser.getGeneIdToSymbolMap();
        Map<TermId, Set<String>> dis2symmap =  new HashMap<>();
        Multimap<TermId, TermId> mm = parser.getDiseaseToGeneIdMap();
        for (TermId diseaseId : mm.keys()) {
            dis2symmap.putIfAbsent(diseaseId, new HashSet<>());
            for (TermId geneId : mm.get(diseaseId)) {
                if (id2sym.containsKey(geneId)) {
                    dis2symmap.get(diseaseId).add(id2sym.get(geneId));
                }
            }
        }
        return dis2symmap;
    }
}
