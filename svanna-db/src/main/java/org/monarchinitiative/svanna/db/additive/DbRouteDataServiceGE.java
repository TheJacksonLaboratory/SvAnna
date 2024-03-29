package org.monarchinitiative.svanna.db.additive;

import org.monarchinitiative.svanna.core.priority.additive.RouteDataService;
import org.monarchinitiative.svanna.core.priority.additive.Routes;
import org.monarchinitiative.svanna.core.priority.additive.evaluator.ge.RouteDataGE;
import org.monarchinitiative.svanna.core.service.AnnotationDataService;
import org.monarchinitiative.svanna.core.service.GeneService;
import org.monarchinitiative.svanna.model.landscape.enhancer.Enhancer;
import org.monarchinitiative.svart.GenomicRegion;
import org.monarchinitiative.sgenes.model.Gene;
import org.monarchinitiative.sgenes.model.Located;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DbRouteDataServiceGE implements RouteDataService<RouteDataGE> {

    private final AnnotationDataService annotationDataService;
    private final GeneService geneService;

    public DbRouteDataServiceGE(AnnotationDataService annotationDataService, GeneService geneService) {
        this.annotationDataService = annotationDataService;
        this.geneService = geneService;
    }

    @Override
    public RouteDataGE getData(Routes route) {
        RouteDataGE.Builder builder = RouteDataGE.builder(route);

        for (GenomicRegion reference : route.references()) {
            Predicate<? super Located> isContainedInRoute = r -> reference.contains(r.location());
            List<Gene> genes = geneService.overlappingGenes(reference).overlapping().stream()
                    .filter(isContainedInRoute)
                    .collect(Collectors.toList());

            List<Enhancer> enhancers = annotationDataService.overlappingEnhancers(reference).stream()
                    .filter(isContainedInRoute)
                    .collect(Collectors.toList());

            builder.addGenes(genes).addEnhancers(enhancers);
        }

        return builder.build();
    }

}
