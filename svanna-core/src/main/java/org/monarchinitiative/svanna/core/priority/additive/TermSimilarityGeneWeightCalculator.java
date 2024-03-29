package org.monarchinitiative.svanna.core.priority.additive;

import org.monarchinitiative.svanna.core.hpo.SimilarityScoreCalculator;
import org.monarchinitiative.svanna.core.service.PhenotypeDataService;
import org.monarchinitiative.svanna.model.HpoDiseaseSummary;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.monarchinitiative.sgenes.model.Gene;

import java.util.*;
import java.util.stream.Collectors;

public class TermSimilarityGeneWeightCalculator implements GeneWeightCalculator {

    private final PhenotypeDataService phenotypeDataService;
    private final SimilarityScoreCalculator similarityScoreCalculator;
    private final Collection<TermId> patientFeatures;

    public TermSimilarityGeneWeightCalculator(PhenotypeDataService phenotypeDataService,
                                              SimilarityScoreCalculator similarityScoreCalculator,
                                              Collection<TermId> patientTerms) {
        this.phenotypeDataService = phenotypeDataService;
        this.similarityScoreCalculator = similarityScoreCalculator;
        this.patientFeatures = patientTerms;
    }


    @Override
    public double calculateRelevance(Gene gene) {
        double maxSimilarity = 0.;

        Optional<String> hgncIdOptional = gene.id().hgncId();
        if (hgncIdOptional.isEmpty())
            return maxSimilarity;

        List<String> diseaseIds = phenotypeDataService.getDiseasesForGene(hgncIdOptional.get()).stream()
                .map(HpoDiseaseSummary::getDiseaseId)
                .collect(Collectors.toUnmodifiableList());

        for (String diseaseId : diseaseIds) {
            List<TermId> diseaseHpoIds = phenotypeDataService.phenotypicAbnormalitiesForDiseaseId(diseaseId);
            double resnikSimilarity = similarityScoreCalculator.computeSimilarityScore(patientFeatures, diseaseHpoIds);
            maxSimilarity = Math.max(maxSimilarity, resnikSimilarity);
        }

        return maxSimilarity;
    }
}
