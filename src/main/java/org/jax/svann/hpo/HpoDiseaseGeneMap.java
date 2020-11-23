package org.jax.svann.hpo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import org.jax.svann.except.SvAnnRuntimeException;
import org.monarchinitiative.phenol.annotations.assoc.HpoAssociationParser;
import org.monarchinitiative.phenol.annotations.formats.hpo.HpoDisease;
import org.monarchinitiative.phenol.annotations.obo.hpo.HpoDiseaseAnnotationParser;
import org.monarchinitiative.phenol.io.OntologyLoader;
import org.monarchinitiative.phenol.ontology.algo.OntologyAlgorithm;
import org.monarchinitiative.phenol.ontology.data.Ontology;
import org.monarchinitiative.phenol.ontology.data.TermId;

import java.io.File;
import java.util.*;

/**
 * SvAnn can optionally prioritize structural variants that affect genes associated with diseases that are
 * characterized by a list of HPO terms passed by the user. To do so, we first input HPO-information about
 * phenotypes and diseases and gene associations. Then, we extract data about genes and diseases that are relevant
 * for the HPO terms.
 * @author Peter N Robinson
 */
public class HpoDiseaseGeneMap {
    /** Human Phenotype Ontology */
    private final Ontology ontology;
    /** Multimap from a gene id, e.g., NCBIGene:2200 for FBN1, and corresponding disease ids. In the case of
     * FBN1, this includes Marfan syndrome (OMIM:154700), Acromicric dysplasia (OMIM:102370), and others.
     */
    private final Multimap<TermId, TermId> disease2geneIdMultiMap;
    /**
     * Map from disease IDs, e.g., OMIM:154700, to the corresponding HpoDisease object.
     */
    private final Map<TermId, HpoDisease> diseaseMap;

    private final Map<String, GeneWithId> geneSymbolMap;

    private HpoDiseaseGeneMap(String hpOboPath, String phenotypeHpoaPath, String mim2geneMedgenPath, String geneInfoPath) {
        this.ontology = OntologyLoader.loadOntology(new File(hpOboPath));
        List<String> desiredDatabasePrefixes= ImmutableList.of("OMIM");
        String orphaToGeneFile = null; // OK, this will not cause a crash, we will refactor in phenol
        HpoAssociationParser hap = new HpoAssociationParser(new File(geneInfoPath), new File(mim2geneMedgenPath), null, new File(phenotypeHpoaPath), ontology);
        this.disease2geneIdMultiMap = hap.getDiseaseToGeneIdMap();
        Map<TermId, String> tid2symbolMap = hap.getGeneIdToSymbolMap();
        this.geneSymbolMap = new HashMap<>();
        for (var e : tid2symbolMap.entrySet()) {
            geneSymbolMap.put(e.getValue(), new GeneWithId(e.getValue(), e.getKey()));
        }
        this.diseaseMap = HpoDiseaseAnnotationParser.loadDiseaseMap(phenotypeHpoaPath,ontology,desiredDatabasePrefixes);
    }

    /**
     * Create a map with key=GeneId, value=Set of {@link HpoDiseaseSummary} objects corresponding to the gene.
     * We create entries for diseases associated with the target term ids.
     *
     * @param targetHpoTermIds HPO ids entered by the user
     * @return map with key=GeneId, value=Set of {@link HpoDiseaseSummary} objects
     */
    public Map<TermId, Set<HpoDiseaseSummary>> getRelevantGenesAndDiseases(Collection<TermId> targetHpoTermIds) {
        Map<TermId, Set<HpoDiseaseSummary>> gene2diseaseMap = new HashMap<>();
        for (var disease : diseaseMap.values()) {
            Collection<TermId> associatedGenes = this.disease2geneIdMultiMap.get(disease.getDiseaseDatabaseId());
            if (associatedGenes.isEmpty()) {
                continue; // we are not interested in diseases with no associated genes for this prioritization
            }
            List<TermId> directAnnotations = disease.getPhenotypicAbnormalityTermIdList();
            Set<TermId> totalAnnotations =
                    OntologyAlgorithm.getAncestorTerms(ontology, new HashSet<>(directAnnotations), true);
            for (TermId tid : targetHpoTermIds) {
                if (totalAnnotations.contains(tid)) {
                    for (TermId geneId : associatedGenes) {
                        gene2diseaseMap.putIfAbsent(geneId, new HashSet<>());
                        gene2diseaseMap.get(geneId).add(new HpoDiseaseSummary(disease));
                    }
                }
            }
        }
        return Map.copyOf(gene2diseaseMap); // immutable
    }

    /**
     * Entry point to using this class.
     * @return HpoDiseaseGeneMap initialized with files from data subdirectory
     */
    public static HpoDiseaseGeneMap loadGenesAndDiseaseMap() {
        // TODO: 26. 10. 2020 externalize
        String hpoPath = String.format("%s%s%s", "data", File.separator, "hp.obo");
        String phenotypeHpoaPath = String.format("%s%s%s", "data", File.separator, "phenotype.hpoa");
        String mim2geneMedgenPath = String.format("%s%s%s", "data", File.separator, "mim2gene_medgen");
        String geneInfoPath = String.format("%s%s%s", "data", File.separator, "Homo_sapiens_gene_info.gz");
        File hpoFile = new File(hpoPath);
        File phenotypeFile = new File(phenotypeHpoaPath);
        File mim2geneFile = new File(mim2geneMedgenPath);
        File geneInfoFile = new File(geneInfoPath);
        if (! hpoFile.exists()) {
            throw new SvAnnRuntimeException("Could not find hp.obo. Did you run the download command?");
        }
        if (! phenotypeFile.exists()) {
            throw new SvAnnRuntimeException("Could not find phenotype.hpoa. Did you run the download command?");
        }
        if (! mim2geneFile.exists()) {
            throw new SvAnnRuntimeException("Could not find mim2gene_medgen Did you run the download command?");
        }
        if (! geneInfoFile.exists()) {
            throw new SvAnnRuntimeException("Could not find Homo_sapiens_gene_info.gz. Did you run the download command?");
        }
        HpoDiseaseGeneMap hdgmap = new HpoDiseaseGeneMap(hpoFile.getAbsolutePath(),
                phenotypeFile.getAbsolutePath(),
                mim2geneFile.getAbsolutePath(),
                geneInfoFile.getAbsolutePath());
        return hdgmap;
    }

    public Map<String, GeneWithId> getGeneSymbolMap() {
        return geneSymbolMap;
    }

    /**
     * The Enhancers are linked to HPO terms representing their tissue specificity. Here, we
     * return a set of the HPO terms that are equal to or ancestors of our target HPO terms, i.e., that
     * are clinicallly relevant.
     *
     * @param candidates       List of all HPO terms annotated to Enhancers by {@link org.jax.svann.genomicreg.TSpecParser}
     * @param targetHpoTermIds List of HPO terms annotated the proband (can be subterms of the candidates or equal)
     * @return set of terms from the candidate list that match the target terms, directly or indirectly
     */
    public Set<TermId> getRelevantAncestors(Set<TermId> candidates, Set<TermId> targetHpoTermIds) {
        Set<TermId> relevantSet = new HashSet<>();
        Set<TermId> ancs = OntologyAlgorithm.getAncestorTerms(ontology, targetHpoTermIds, true);
        for (TermId tid : candidates) {
            if (ancs.contains(tid))
                relevantSet.add(tid);
        }
        return Set.copyOf(relevantSet); // return immutable set
    }

    /**
     * This method can be used to get the labels for the terms that the user enters. If the user
     * enters a term that is not in the ontology, we throw a runtime error
     * @param relevantTerms Should be used for terms entered by the user as HP:1,HP:2, etc
     * @return map with the corresponding term labels
     */
    public Map<TermId, String> getTermLabelMap(Set<TermId> relevantTerms) {
        Map<TermId, String> labelmap = new HashMap<>();
        for (var t : relevantTerms) {
            if (this.ontology.getTermMap().containsKey(t)) {
                String label = this.ontology.getTermMap().get(t).getName();
                labelmap.put(t, label);
            } else {
                throw new SvAnnRuntimeException("Could not identify term " + t.getValue());
            }
        }
        return labelmap;
    }

    public Ontology getOntology() {
        return ontology;
    }
}
