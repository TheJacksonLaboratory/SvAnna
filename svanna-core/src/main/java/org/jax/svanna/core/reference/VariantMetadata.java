package org.jax.svanna.core.reference;

public interface VariantMetadata {

    /**
     * Depth is set to -1 when the information is not available.
     */
    int MISSING_DEPTH_PLACEHOLDER = -1;

    /**
     * @return minimum number of reads covering the variant site
     * or {@link #MISSING_DEPTH_PLACEHOLDER} if the read depth data is missing
     */
    int minDepthOfCoverage();

    /**
     * @return number of reads that support presence of the <em>ref</em> allele at the variant site
     * or {@link #MISSING_DEPTH_PLACEHOLDER} if the read depth data is missing
     */
    int numberOfRefReads();

    /**
     * @return number of reads that support presence of the <em>alt</em> allele at the variant site
     * or {@link #MISSING_DEPTH_PLACEHOLDER} if the read depth data is missing
     */
    int numberOfAltReads();

    /**
     * @return variant zygosity
     */
    Zygosity zygosity();

}
