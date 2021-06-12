package org.jax.svanna.cli.writer.html.svg;


import org.jax.svanna.core.exception.SvAnnRuntimeException;
import org.jax.svanna.core.landscape.RepetitiveRegion;
import org.monarchinitiative.svart.Strand;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

import static org.jax.svanna.cli.writer.html.svg.Constants.REPEAT_HEIGHT;

/**
 * Write a UCSC-style track for each of the repeat families in our SVG. Write each repeat as a region in the
 * corresponding track.
 */
public class SvgRepeatWriter {
    private final Map<String, List<RepetitiveRegion>> repeatFamilyMap;
    private final int paddedGenomicMinPos;
    private final double paddedGenomicSpan;



    public SvgRepeatWriter(List<RepetitiveRegion> repeats,
                           int paddedpaddedGenomicMinPos,
                           double paddedGenomicSpan) {
        this.paddedGenomicMinPos = paddedpaddedGenomicMinPos;
        this.paddedGenomicSpan = paddedGenomicSpan;
        repeatFamilyMap = new TreeMap<>();
        for (var repeat : repeats) {
            repeatFamilyMap.putIfAbsent(repeat.repeatFamily().name(), new ArrayList<>());
            repeatFamilyMap.get(repeat.repeatFamily().name()).add(repeat);
        }
    }

    /**
     * @return the vertical space that will be taken up by the repeat tracks
     */
    public double verticalSpace() {
        int numberOfTracks = this.repeatFamilyMap.size();
        return REPEAT_HEIGHT * numberOfTracks + 20;
    }


    /**
     * @return the SVG x coordinate that corresponds to a given genomic position
     */
    protected double translateGenomicToSvg(int genomicCoordinate) {
        double pos = genomicCoordinate - paddedGenomicMinPos;
        if (pos < 0) {
            throw new SvAnnRuntimeException("Bad left boundary (genomic coordinate-"); // should never happen
        }
        double prop = pos / paddedGenomicSpan;
        return prop * Constants.SVG_WIDTH;
    }



    public void write(Writer writer, double ystart) throws IOException {
        double y = ystart;
        double minx = translateGenomicToSvg(this.paddedGenomicMinPos);
        double trackwidth = translateGenomicToSvg((int)this.paddedGenomicSpan);
        for (var e : this.repeatFamilyMap.entrySet()) {
            String family = e.getKey();
            writer.write(SvgUtil.svgbox(minx, y, trackwidth, REPEAT_HEIGHT, "black") + "\n");
            writer.write(SvgUtil.svgtext(minx + trackwidth + 10, y, "black", family) + "\n");
            List<RepetitiveRegion> repeatList = e.getValue();
            for (var repeat : repeatList) {
                int start = repeat.startOnStrand(Strand.POSITIVE);
                double x_repeat = translateGenomicToSvg(start);
                double repeat_width = translateGenomicToSvg(repeat.length());
                writer.write(SvgUtil.svgbox(x_repeat, y, repeat_width, REPEAT_HEIGHT, "black", "red") + "\n");
            }
            y += REPEAT_HEIGHT;
        }
    }
}
