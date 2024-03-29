package org.monarchinitiative.svanna.cli.cmd;

import org.monarchinitiative.svanna.cli.writer.OutputFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class Utils {

    private static final Logger LOGGER = LoggerFactory.getLogger(Utils.class);

    /**
     * Parse input argument that specifies the desired output formats into a collection of {@link OutputFormat}s.
     *
     * @return a collection of output formats
     */
    public static Collection<OutputFormat> parseOutputFormats(String outputFormats) {
        Set<OutputFormat> formats = new HashSet<>(2);
        for (String format : outputFormats.split(",")) {
            try {
                formats.add(OutputFormat.valueOf(format.toUpperCase()));
            } catch (IllegalArgumentException e) {
                LOGGER.warn("Ignoring invalid output format `{}`", format);
            }
        }
        return formats;
    }

}
