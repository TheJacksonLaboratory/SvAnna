package org.jax.svanna.ingest.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "svanna.ingest.genes")
public class GeneProperties {

    private String gencodeGtfUrl;

    public String getGencodeGtfUrl() {
        return gencodeGtfUrl;
    }

    public void setGencodeGtfUrl(String gencodeGtfUrl) {
        this.gencodeGtfUrl = gencodeGtfUrl;
    }

}
