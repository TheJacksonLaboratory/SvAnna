package org.monarchinitiative.svanna.ingest.cmd;

import org.monarchinitiative.svanna.ingest.Main;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.Callable;

/**
 * @author Daniel Danis
 */
@CommandLine.Command(name = "generate-config",
        aliases = {"G"},
        header = "Generate a configuration YAML file",
        mixinStandardHelpOptions = true,
        version = Main.VERSION,
        usageHelpWidth = Main.WIDTH,
        footer = Main.FOOTER)
public class GenerateConfigCommand implements Callable<Integer> {

    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateConfigCommand.class);

    @CommandLine.Parameters(arity = "1",
            description = "Configuration file path",
            defaultValue = "svanna-ingest-config.yml")
    public Path outputPath;

    @Override
    public Integer call() throws Exception {
        LOGGER.info("Generating config template to `{}`", outputPath.toAbsolutePath());
        try (InputStream is = GenerateConfigCommand.class.getResourceAsStream("/application-template.yml")) {
            Files.copy(is, outputPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            LOGGER.warn("Error: {} ", e.getMessage());
            return 1;
        }
        return 0;
    }
}
