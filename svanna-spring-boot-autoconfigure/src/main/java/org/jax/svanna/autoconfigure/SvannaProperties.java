package org.jax.svanna.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

@ConfigurationProperties(prefix = "svanna")
public class SvannaProperties {

    /**
     * Path to directory with SvAnna files.
     */
    private String dataDirectory;
    private String jannovarCachePath;

    @NestedConfigurationProperty
    private DataParameters dataParameters;

    public DataParameters dataParameters() {
        return dataParameters;
    }

    public void setDataParameters(DataParameters dataParameters) {
        this.dataParameters = dataParameters;
    }


    public String jannovarCachePath() {
        return jannovarCachePath;
    }

    public void setJannovarCachePath(String jannovarCachePath) {
        this.jannovarCachePath = jannovarCachePath;
    }

    public String dataDirectory() {
        return dataDirectory;
    }

    public void setDataDirectory(String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    public static class DataParameters {

        private double tadStabilityThreshold = .25;

        public double tadStabilityThreshold() {
            return tadStabilityThreshold;
        }

        public void setTadStabilityThreshold(double tadStabilityThreshold) {
            this.tadStabilityThreshold = tadStabilityThreshold;
        }

    }
}
