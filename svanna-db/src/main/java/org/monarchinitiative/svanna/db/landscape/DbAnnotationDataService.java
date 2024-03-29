package org.monarchinitiative.svanna.db.landscape;

import org.monarchinitiative.svanna.core.service.AnnotationDataService;
import org.monarchinitiative.svanna.core.service.GeneDosageDataService;
import org.monarchinitiative.svanna.model.landscape.dosage.Dosage;
import org.monarchinitiative.svanna.model.landscape.dosage.DosageRegion;
import org.monarchinitiative.svanna.model.landscape.enhancer.Enhancer;
import org.monarchinitiative.svanna.model.landscape.repeat.RepetitiveRegion;
import org.monarchinitiative.svanna.model.landscape.tad.TadBoundary;
import org.monarchinitiative.svanna.model.landscape.variant.PopulationVariant;
import org.monarchinitiative.svanna.model.landscape.variant.PopulationVariantOrigin;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.monarchinitiative.svart.GenomicRegion;

import java.util.List;
import java.util.Set;

public class DbAnnotationDataService implements AnnotationDataService {

    private final EnhancerAnnotationDao enhancerAnnotationDao;
    private final AnnotationDao<RepetitiveRegion> repetitiveRegionDao;
    private final PopulationVariantDao populationVariantDao;
    private final AnnotationDao<TadBoundary> tadBoundaryDao;
    private final GeneDosageDataService geneDosageDataService;

    public DbAnnotationDataService(EnhancerAnnotationDao enhancerAnnotationDao,
                                   AnnotationDao<RepetitiveRegion> repetitiveRegionDao,
                                   PopulationVariantDao populationVariantDao,
                                   AnnotationDao<TadBoundary> tadBoundaryDao,
                                   GeneDosageDataService geneDosageDataService) {
        this.enhancerAnnotationDao = enhancerAnnotationDao;
        this.repetitiveRegionDao = repetitiveRegionDao;
        this.populationVariantDao = populationVariantDao;
        this.tadBoundaryDao = tadBoundaryDao;
        this.geneDosageDataService = geneDosageDataService;
    }

    @Override
    public List<Enhancer> overlappingEnhancers(GenomicRegion query) {
        return enhancerAnnotationDao.getOverlapping(query);
    }

    @Override
    public Set<TermId> enhancerPhenotypeAssociations() {
        return enhancerAnnotationDao.getPhenotypeAssociations();
    }

    @Override
    public List<RepetitiveRegion> overlappingRepetitiveRegions(GenomicRegion query) {
        return repetitiveRegionDao.getOverlapping(query);
    }

    @Override
    public Set<PopulationVariantOrigin> availableOrigins() {
        return populationVariantDao.availableOrigins();
    }

    @Override
    public List<PopulationVariant> overlappingPopulationVariants(GenomicRegion query, Set<PopulationVariantOrigin> origins) {
        return populationVariantDao.getOverlapping(query, origins);
    }

    @Override
    public List<TadBoundary> overlappingTadBoundaries(GenomicRegion query) {
        return tadBoundaryDao.getOverlapping(query);
    }

    @Override
    public List<DosageRegion> dosageElements(GenomicRegion query) {
        return geneDosageDataService.dosageElements(query);
    }

    @Override
    public List<Dosage> geneDosageDataForHgncId(String hgncId) {
        return geneDosageDataService.geneDosageDataForHgncId(hgncId);
    }

    @Override
    public List<Dosage> geneDosageDataForHgncIdAndRegion(String hgncId, GenomicRegion query) {
        return geneDosageDataService.geneDosageDataForHgncIdAndRegion(hgncId, query);
    }
}
