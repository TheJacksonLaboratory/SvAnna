package org.monarchinitiative.svanna.ingest.parse.enhancer.fantom;

import org.monarchinitiative.svanna.ingest.hpomap.HpoMapping;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Parse the two files in resources, Supplemental Tables S10 and S11 from
 * Andersson R, Gebhard C, Miguel-Escalada I, et al. An atlas of active
 * enhancers across human cell types and tissues. Nature. 2014;507(7493):455-461.
 * Note that these files do not have uberon/CL ids for all of the samples that are
 * listed in the Human.sample_name2library_id.txt file; it seems we are able to
 * get about 249 for CL and 387 for uberon.
 * @author Peter N Robinson
 */
public class SupplementParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(SupplementParser.class);

    /** Key is a sample id such as CNhs12597. Value is a term id such as CL:0000359. */
    Map<String, TermId> fantomSampleToTermIdMap;
    Map<String, TermId> fantomSampleToHpoIdMap;

    Map<String, HpoMapping> fantomSampleToHpoMappingMap;


    public SupplementParser(Map<TermId, HpoMapping> hpoMappingMap) {
        fantomSampleToHpoMappingMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(SupplementParser.class.getResourceAsStream("/andersson-2014-table_s10.tsv")))) {
            String line;
            br.readLine(); // discard header
            while ((line=br.readLine()) != null) {
                String[] fields = line.split("\t");
                String sampleId = fields[1];
                String clId = fields[3];
                TermId cellOntologyId = TermId.of(clId);
                // fantomSampleToTermIdMap.put(sampleId, cellOntologyId);
                if (hpoMappingMap.containsKey(cellOntologyId)) {
                    //fantomSampleToHpoIdMap.put(sampleId, cl2hpo.get(cellOntologyId));
                    fantomSampleToHpoMappingMap.put(sampleId, hpoMappingMap.get(cellOntologyId));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(SupplementParser.class.getResourceAsStream("/andersson-2014-table_s11.tsv")))) {
            String line;
            br.readLine(); // discard header
            while ((line=br.readLine()) != null) {
                String[] fields = line.split("\t");
                String sampleId = fields[1];
                String uberId = fields[3];
                TermId uberonId = TermId.of(uberId);
                if (hpoMappingMap.containsKey(uberonId)) {
                    fantomSampleToHpoMappingMap.put(sampleId, hpoMappingMap.get(uberonId));
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        LOGGER.info("Imported {} samples with CL/UBERON ids.", fantomSampleToHpoMappingMap.size());

    }

    public Map<String, HpoMapping> getFantomSampleToHpoMappingMap() { return this.fantomSampleToHpoMappingMap; }
}
