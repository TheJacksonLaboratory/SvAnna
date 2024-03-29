package org.monarchinitiative.svanna.db.service;

import org.monarchinitiative.svanna.core.service.GeneDosageDataService;
import org.monarchinitiative.svanna.db.landscape.ClingenDosageElementDao;
import org.monarchinitiative.svanna.model.landscape.dosage.Dosage;
import org.monarchinitiative.svanna.model.landscape.dosage.DosageRegion;
import org.monarchinitiative.svart.GenomicRegion;

import java.util.List;

/**
 * This gene dosage data service consults ClinGen data to assert a genes as haploinsufficient or triplosensitive.
 */
public class ClinGenGeneDosageDataService implements GeneDosageDataService {

    private final ClingenDosageElementDao clingenDosageElementDao;

    public ClinGenGeneDosageDataService(ClingenDosageElementDao clingenDosageElementDao) {
        this.clingenDosageElementDao = clingenDosageElementDao;
    }

    @Override
    public List<DosageRegion> dosageElements(GenomicRegion query) {
        return clingenDosageElementDao.getOverlapping(query);
    }

    @Override
    public List<Dosage> geneDosageDataForHgncId(String hgncId) {
        return clingenDosageElementDao.geneDosageDataForHgncId(hgncId);
    }

    @Override
    public List<Dosage> geneDosageDataForHgncIdAndRegion(String hgncId, GenomicRegion query) {
        return clingenDosageElementDao.geneDosageDataForHgncIdAndRegion(hgncId, query);
    }
}
