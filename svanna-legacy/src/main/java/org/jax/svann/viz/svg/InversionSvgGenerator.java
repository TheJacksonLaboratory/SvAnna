package org.jax.svann.viz.svg;

import org.jax.svann.except.SvAnnRuntimeException;
import org.jax.svann.genomicreg.Enhancer;
import org.jax.svann.reference.CoordinatePair;
import org.jax.svann.reference.Strand;
import org.jax.svann.reference.SvType;
import org.jax.svann.reference.transcripts.SvAnnTxModel;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public class InversionSvgGenerator extends SvSvgGenerator {

    CoordinatePair inversionCoordinates;
    int inversionGenomicStart;
    int inversionGenomicEnd;
    private final double ARROWHEAD_SIZE = 8.0;

    public InversionSvgGenerator(List<SvAnnTxModel> transcripts, List<Enhancer> enhancers, List<CoordinatePair> coordinatePairs) {
        super(SvType.INVERSION, transcripts, enhancers, coordinatePairs);
        if (coordinatePairs.size() != 1) {
            throw new SvAnnRuntimeException("Malformed initialization of DeletionSvgGenerator -- we expect one CoordinatePair but got " +
                    coordinatePairs.size());
        }
        inversionCoordinates = coordinatePairs.get(0);
        int pos1 = inversionCoordinates.getStart().withStrand(Strand.FWD).getPosition();
        int pos2 = inversionCoordinates.getEnd().withStrand(Strand.FWD).getPosition();
        this.inversionGenomicStart = Math.min(pos1, pos2);
        this.inversionGenomicEnd = Math.max(pos1, pos2); // the positions are inverted but we want start < end!
    }

    @Override
    public void write(Writer writer) throws IOException {
        int starty = 50;
        int y = starty;
        String inversionLength = getSequenceLengthString(inversionCoordinates.getLength());
        String inversionDescription = String.format("%s inversion", inversionLength);
        writeInversion(starty, inversionDescription, writer);
        y += 100;
        for (var e : this.affectedEnhancers) {
            writeEnhancer(e, y, writer);
            y += HEIGHT_PER_DISPLAY_ITEM;
        }
        for (var tmod : this.affectedTranscripts) {
            writeTranscript(tmod, y, writer);
            y += HEIGHT_PER_DISPLAY_ITEM;
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
    private void writeInversion(int ypos, String msg, Writer writer) throws IOException {
        double start = translateGenomicToSvg(this.inversionGenomicStart);
        double end = translateGenomicToSvg(this.inversionGenomicEnd);
        double width = end - start;
        double Y = ypos + 0.5 * SV_HEIGHT;
        String color = RED;
        String rect = String.format("<rect x=\"%f\" y=\"%f\" width=\"%f\" height=\"%f\" " +
                        "style=\"stroke:%s; fill: %s\" />\n",
                start, Y, width, SV_HEIGHT, color, color);
        writer.write(rect);
        double overhang = Math.min(0.1*SVG_WIDTH, 2*width);
        writeRightwardsArrow(start, Y, start+overhang, Y, color, 2 , writer);
        writeLeftwardsArrow(end-overhang, Y+SV_HEIGHT, end, Y+SV_HEIGHT, color, 2, writer);
        Y += 1.75*SV_HEIGHT;
        writer.write(String.format("<text x=\"%f\" y=\"%f\"  fill=\"%s\">%s</text>\n",start -10,Y, BLACK, msg));
    }


    private void writeRightwardsArrow(double x1,
                                      double y1,
                                      double x2,
                                      double y2,
                                      String color,
                                      int lineThickness,
                                      Writer writer) throws IOException {
        String line = String.format("<line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" stroke=\"%s\" stroke-width=\"%d\"/>\n",
                x1, y1, x2, y2, color, lineThickness);
        writer.write(line);
        String polygon = String.format("<polygon points=\"%f,%f %f,%f %f,%f\" style=\"fill:%s;stroke:%s;stroke-width:1\" />",
                x2-1,y2+ARROWHEAD_SIZE,x2-1,y2-ARROWHEAD_SIZE,x2+ARROWHEAD_SIZE-1.0,y2, color, color);
        writer.write(polygon);
    }

    private void writeLeftwardsArrow(double x1,
                                      double y1,
                                      double x2,
                                      double y2,
                                      String color,
                                      int lineThickness,
                                      Writer writer) throws IOException {
        String line = String.format("<line x1=\"%f\" y1=\"%f\" x2=\"%f\" y2=\"%f\" stroke=\"%s\" stroke-width=\"%d\"/>\n",
                x1, y1, x2, y2, color, lineThickness);
        writer.write(line);
        String polygon = String.format("<polygon points=\"%f,%f %f,%f %f,%f\" style=\"fill:%s;stroke:%s;stroke-width:1\" />",
                x1+1,y2+ARROWHEAD_SIZE,x1+1,y2-ARROWHEAD_SIZE,x1-ARROWHEAD_SIZE+1.0,y2, color, color);
        writer.write(polygon);
    }


}