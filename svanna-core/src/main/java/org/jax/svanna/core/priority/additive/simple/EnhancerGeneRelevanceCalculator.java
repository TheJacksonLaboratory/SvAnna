package org.jax.svanna.core.priority.additive.simple;

import org.jax.svanna.core.landscape.Enhancer;
import org.jax.svanna.core.reference.Gene;

/**
 * Assess the relevance of a potential change to the enhancer - gene interaction for an individual with given phenotype.
 * <p>
 * Disruption of a liver-specific enhancer is unlikely to be causal to an individual with e.g. autism.
 */
@FunctionalInterface
public interface EnhancerGeneRelevanceCalculator {

    double DEFAULT_ENHANCER_RELEVANCE = 1.f;

    static EnhancerGeneRelevanceCalculator defaultCalculator() {
        return (g, e) -> DEFAULT_ENHANCER_RELEVANCE;
    }

    double calculateRelevance(Gene gene, Enhancer enhancer);
}