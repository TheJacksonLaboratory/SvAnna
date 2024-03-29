package org.monarchinitiative.svanna.benchmark;

import org.monarchinitiative.svanna.benchmark.cmd.benchmark_case.BenchmarkCaseCommand;
import org.monarchinitiative.svanna.benchmark.cmd.lift_coordinates.LiftCoordinatesCommand;
import org.monarchinitiative.svanna.benchmark.cmd.remap.RemapVariantsCommand;
import picocli.CommandLine;

import java.util.Locale;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Help.Ansi.Style.*;

@CommandLine.Command(name = "svanna-benchmark.jar",
        header = "Structural variant prioritization",
        mixinStandardHelpOptions = true,
        version = Main.VERSION,
        usageHelpWidth = Main.WIDTH,
        footer = Main.FOOTER)
public class Main implements Callable<Integer>  {

    public static final String VERSION = "svanna-benchmark v1.0.0-RC3-SNAPSHOT";

    public static final int WIDTH = 120;

    public static final String FOOTER = "See the full documentation at `https://svanna.readthedocs.io/en/latest`";

    private static final CommandLine.Help.ColorScheme COLOR_SCHEME = new CommandLine.Help.ColorScheme.Builder()
            .commands(bold, fg_blue, underline)
            .options(fg_yellow)
            .parameters(fg_yellow)
            .optionParams(italic)
            .build();

    private static CommandLine commandLine;

    public static void main(String [] args) {
        Locale.setDefault(Locale.US);
        commandLine = new CommandLine(new Main())
                .setColorScheme(COLOR_SCHEME)
                .addSubcommand("benchmark-case", new BenchmarkCaseCommand())
                .addSubcommand("remap-variants", new RemapVariantsCommand())
                .addSubcommand("lift-coordinates", new LiftCoordinatesCommand());
        commandLine.setToggleBooleanFlags(false);
        System.exit(commandLine.execute(args));
    }

    @Override
    public Integer call() {
        // work done in subcommands
        commandLine.usage(commandLine.getOut());
        return 0;
    }
}
