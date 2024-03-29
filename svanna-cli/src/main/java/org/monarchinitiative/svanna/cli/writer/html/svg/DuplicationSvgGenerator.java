package org.monarchinitiative.svanna.cli.writer.html.svg;

import org.monarchinitiative.svanna.model.landscape.dosage.DosageRegion;
import org.monarchinitiative.svanna.model.landscape.enhancer.Enhancer;
import org.monarchinitiative.svanna.model.landscape.repeat.RepetitiveRegion;
import org.monarchinitiative.svart.GenomicVariant;
import org.monarchinitiative.sgenes.model.Gene;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class DuplicationSvgGenerator extends SvSvgGenerator {

    private final int duplicationStart;
    private final int duplicationEnd;
    private final int duplicationLength;

    /**
     * @param variant a structural variant (SV)
     * @param genes gene or genes that overlap with the SV
     * @param enhancers enhancers that overlap with the SV
     * @param repeats repeat regions that overlap with the SV
     * @param dosageRegions triplo/haplosensitive regions that overlap with the SV
     */
    public DuplicationSvgGenerator(GenomicVariant variant,
                                   List<Gene> genes,
                                   List<Enhancer> enhancers,
                                   List<RepetitiveRegion> repeats,
                                   List<DosageRegion> dosageRegions) {
        super(variant, genes, enhancers, repeats, dosageRegions);

        duplicationStart = Math.min(variant.start(), variant.end());
        duplicationEnd = Math.max(variant.start(), variant.end());
        duplicationLength = duplicationEnd - duplicationStart + 1;
    }


    /**
     * Write an SVG (without header) representing this SV. Not intended to be used to create a stand-alone
     * SVG (for this, user {@link #getSvg()}
     * @param writer a file handle
     * @throws IOException if we cannot write.
     */
    @Override
    public void write(Writer writer) throws IOException {
        int starty = 50;
        int y = starty;
        String deletionLength = getSequenceLengthString(duplicationLength);
        String deletionDescription = String.format("%s duplication", deletionLength);
        writeDuplication(starty, deletionDescription, writer);
        y += 100;
        y = writeDosage(writer, y);
        y = writeRepeats(writer, y);
        for (var gene : affectedGenes) {
            writeGene(gene, y, writer);
            y += gene.transcriptCount() * Constants.HEIGHT_PER_DISPLAY_ITEM;
        }
        writeScale(writer, y);
    }

    /**
     * PROTOTYPE -- THIS MAYBE NOT BE THE BEST WAY TO REPRESENT OTHER TUPES OF SV
     * @param ypos  The y position where we will write the cartoon
     * @param msg A String describing the SV
     * @param writer a file handle
     * @throws IOException if we can't write
     */
    private void writeDuplication(int ypos, String msg, Writer writer) throws IOException {
        double start = translateGenomicToSvg(duplicationStart);
        double end = translateGenomicToSvg(duplicationEnd);
        double width = end - start;
        double Y = ypos + 0.5 * SV_HEIGHT;
        String rect = String.format("<rect x=\"%f\" y=\"%f\" width=\"%f\" height=\"%f\" rx=\"2\" " +
                        "style=\"stroke:%s; fill: %s\" />\n",
                start, Y, width, SV_HEIGHT, DARKGREEN, ORANGE);
        writer.write(rect);
        Y += 1.75*SV_HEIGHT;
        writer.write(String.format("<text x=\"%f\" y=\"%f\"  fill=\"%s\">%s</text>\n",start -10,Y, PURPLE, msg));
    }



}
