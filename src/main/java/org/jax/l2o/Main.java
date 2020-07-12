package org.jax.l2o;


import picocli.CommandLine;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.Callable;

@CommandLine.Command(name = "l2o", mixinStandardHelpOptions = true, version = "l2o 0.0.1",
        description = "LIRICAL to overlapping SV")
public class Main implements Callable<Integer>  {
    @CommandLine.Option(names = {"-o","--out"})
    protected String outname = "l2o.bed";
    @CommandLine.Option(names = {"-v", "--vcf"})
    protected String vcfFile;
    @CommandLine.Option(names = {"-l", "--lirical"})
    protected String liricalFile;



    public static void main(String [] args) {

    }

    @Override
    public Integer call() throws Exception {
        Main main = new Main();
        Lirical2Overlap l2o = new Lirical2Overlap(this.liricalFile, this.vcfFile, this.outname);

        return 0;
    }
}
