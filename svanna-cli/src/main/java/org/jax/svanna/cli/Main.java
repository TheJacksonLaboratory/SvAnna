package org.jax.svanna.cli;


import org.jax.svanna.cli.cmd.AnnotateCommand;
import picocli.CommandLine;

import java.util.Locale;
import java.util.concurrent.Callable;

import static picocli.CommandLine.Help.Ansi.Style.*;

@CommandLine.Command(name = "svanna-cli.jar",
        header = "Structural variant annotation",
        mixinStandardHelpOptions = true,
        version = Main.VERSION,
        usageHelpWidth = Main.WIDTH,
        footer = Main.FOOTER)
public class Main implements Callable<Integer>  {

    public static final String VERSION = "svanna v0.2.9-SNAPSHOT";

    public static final int WIDTH = 120;

    public static final String FOOTER = "See the full documentation at `https://github.com/TheJacksonLaboratory/svann`";

    private static final CommandLine.Help.ColorScheme COLOR_SCHEME = new CommandLine.Help.ColorScheme.Builder()
            .commands(bold, fg_blue, underline)
            .options(fg_yellow)
            .parameters(fg_yellow)
            .optionParams(italic)
            .build();

    public static void main(String [] args) {
        Locale.setDefault(Locale.US);
        CommandLine cline = new CommandLine(new Main())
                .setColorScheme(COLOR_SCHEME)
//                .addSubcommand("download", new DownloadCommand())
//                .addSubcommand("download", null)
                .addSubcommand("annotate", new AnnotateCommand());
        cline.setToggleBooleanFlags(false);
        int exitCode = cline.execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        // work done in subcommands
        return 0;
    }
}