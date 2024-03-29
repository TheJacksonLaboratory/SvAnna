package org.monarchinitiative.svanna.db.additive;

import org.monarchinitiative.svanna.core.priority.additive.RouteDataService;
import org.monarchinitiative.svanna.core.priority.additive.Routes;
import org.monarchinitiative.svanna.core.priority.additive.evaluator.getad.RouteDataGETad;
import org.monarchinitiative.svanna.core.service.AnnotationDataService;
import org.monarchinitiative.svanna.core.service.GeneService;
import org.monarchinitiative.svanna.model.landscape.enhancer.Enhancer;
import org.monarchinitiative.svanna.model.landscape.tad.TadBoundary;
import org.monarchinitiative.svart.GenomicRegion;
import org.monarchinitiative.sgenes.model.Gene;
import org.monarchinitiative.sgenes.model.Located;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class DbRouteDataServiceGETad implements RouteDataService<RouteDataGETad> {

    private final AnnotationDataService annotationDataService;
    private final GeneService geneService;

    public DbRouteDataServiceGETad(AnnotationDataService annotationDataService, GeneService geneService) {
        this.annotationDataService = annotationDataService;
        this.geneService = geneService;
    }

    @Override
    public RouteDataGETad getData(Routes route) {
        RouteDataGETad.Builder builder = RouteDataGETad.builder(route);

        for (GenomicRegion reference : route.references()) {
            Predicate<? super Located> isContainedInRoute = r -> reference.contains(r.location());
            List<Gene> genes = geneService.overlappingGenes(reference).overlapping().stream()
                    .filter(isContainedInRoute)
                    .collect(Collectors.toList());

            List<Enhancer> enhancers = annotationDataService.overlappingEnhancers(reference).stream()
                    .filter(isContainedInRoute)
                    .collect(Collectors.toList());

            List<TadBoundary> boundaries = annotationDataService.overlappingTadBoundaries(reference).stream()
                    .filter(isContainedInRoute)
                    .filter(notOverlappingWithGene(genes))
                    .collect(Collectors.toList());

            builder.addGenes(genes)
                    .addEnhancers(enhancers)
                    .addTadBoundaries(boundaries);
        }

        return builder.build();
    }

    private static Predicate<? super TadBoundary> notOverlappingWithGene(Collection<Gene> genes) {
        return tad -> genes.stream().noneMatch(g -> g.location().overlapsWith(tad.location()));
    }

}
