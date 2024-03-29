package org.monarchinitiative.svanna.db.additive;

import org.monarchinitiative.svanna.core.priority.additive.RouteData;
import org.monarchinitiative.svanna.core.priority.additive.RouteDataService;
import org.monarchinitiative.svanna.core.priority.additive.evaluator.ge.RouteDataGE;
import org.monarchinitiative.svanna.core.priority.additive.evaluator.getad.RouteDataGETad;
import org.monarchinitiative.svanna.core.service.AnnotationDataService;
import org.monarchinitiative.svanna.core.service.GeneService;

public class RouteDataServiceFactory {

    private final AnnotationDataService annotationDataService;
    private final GeneService geneService;

    public RouteDataServiceFactory(AnnotationDataService annotationDataService, GeneService geneService) {
        this.annotationDataService = annotationDataService;
        this.geneService = geneService;
    }

    @SuppressWarnings("unchecked")
    public <T extends RouteData> RouteDataService<T> getService(Class<?> routeData) {
        if (routeData.equals(RouteDataGE.class)) {
            return (RouteDataService<T>) new DbRouteDataServiceGE(annotationDataService, geneService);
        } else if (routeData.equals(RouteDataGETad.class)) {
            return (RouteDataService<T>) new DbRouteDataServiceGETad(annotationDataService, geneService);
        } else {
            throw new RuntimeException("Unknown route data");
        }
    }
}
