package org.jax.svanna.core.priority;

import org.monarchinitiative.phenol.ontology.data.TermId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;

public interface SvPrioritizerFactory {

     Logger LOGGER = LoggerFactory.getLogger(SvPrioritizerFactory.class);

    SvPrioritizer<SvPriority> getPrioritizer(SvPrioritizerType type, Collection<TermId> patientTerms);


}