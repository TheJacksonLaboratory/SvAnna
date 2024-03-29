package org.monarchinitiative.svanna.ingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "svanna.ingest.tad")
public class TadProperties {

    private String mcArthur2021Supplement;

    public String mcArthur2021Supplement() {
        return mcArthur2021Supplement;
    }

    public void setMcArthur2021Supplement(String mcArthur2021Supplement) {
        this.mcArthur2021Supplement = mcArthur2021Supplement;
    }

}
