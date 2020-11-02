package org.jax.svann.viz;

import org.jax.svann.reference.genome.Contig;

/**
 * A POJO that records the locations of chromosomal breakends or locations in an easily displayable fashion
 */
public class HtmlLocation {

    private final String chrom;
    private final int begin;
    private final int end;

    public HtmlLocation(Contig chrom, int begin, int end) {
        this.chrom = chrom.getPrimaryName();
        this.begin = begin;
        this.end = end;
    }

    public String getChrom() {
        return chrom;
    }

    public int getBegin() {
        return begin;
    }

    public int getEnd() {
        return end;
    }
}