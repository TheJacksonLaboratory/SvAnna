package org.jax.svanna.db.additive.dispatch;

import org.jax.svanna.core.LogUtils;
import org.jax.svanna.core.priority.additive.IntrachromosomalBreakendException;
import org.monarchinitiative.svart.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

class RouteAssembly {

    /**
     * Sort variants located in a single contig and adjust variant strands to the same strand. If there is a breakend
     * among the variants, variant strands of the other variants are adjusted to align with the breakend.
     * <p>
     * The method supports max 1 breakend to be present.
     *
     * @throws RouteAssemblyException if not possible to arrange the variants in a meaningful way
     */
    static VariantArrangement assemble(List<Variant> variants) throws RouteAssemblyException {
        if (variants.isEmpty()) throw new RouteAssemblyException("Variant list must not be empty");

        List<Variant> breakends = variants.stream()
                .filter(v -> v instanceof BreakendVariant)
                .collect(Collectors.toList());
        if (breakends.isEmpty())
            return assembleIntrachromosomal(variants);
        else if (breakends.size() == 1)
            return assembleInterchromosomal(variants, breakends.get(0));
        else
            throw new RouteAssemblyException("Unable to assemble a list of " + breakends.size() + "(>1) breakend variants");
    }

    private static VariantArrangement assembleIntrachromosomal(List<Variant> variants) {
        long contigCount = variants.stream().map(Variant::contig).distinct().count();
        if (contigCount > 1)
            throw new RouteAssemblyException("Unable to assemble variants on " + contigCount + "(>1) contigs without knowing the breakend");
        if (variants.size() == 1)
            return VariantArrangement.intrachromosomal(variants);

        List<Variant> startSorted = variants.stream()
                .map(v -> v.withStrand(Strand.POSITIVE)) // this might fail if V does not override `withStrand`
                .sorted(Comparator.comparingInt(v -> v.startOnStrandWithCoordinateSystem(Strand.POSITIVE, CoordinateSystem.zeroBased())))
                .collect(Collectors.toList());

        Variant previous = startSorted.get(0);
        for (Variant current : startSorted) {
            if (previous == current) continue;
            if (previous.overlapsWith(current))
                throw new RouteAssemblyException("Unable to assemble overlapping variants: "
                        + LogUtils.variantSummary(previous) + " " + LogUtils.variantSummary(current));
            previous = current;
        }

        return VariantArrangement.intrachromosomal(startSorted);
    }

    private static VariantArrangement assembleInterchromosomal(List<? extends Variant> variants, Variant breakendVariant) {
        BreakendVariant breakend = (BreakendVariant) breakendVariant;

        Breakend left = breakend.left();
        Breakend right = breakend.right();
        if (left.contig().equals(right.contig()))
            // TODO - evaluate
            throw new IntrachromosomalBreakendException("Intrachromosomal breakends are not currently supported: " + LogUtils.variantSummary(breakend));

        List<Variant> leftSorted = variants.stream()
                .filter(v -> v.contig().equals(left.contig()) && !v.equals(breakend))
                .sorted(Comparator.comparingInt(left::distanceTo))
                .map(v -> (Variant) v.withStrand(left.strand())) // this might fail if V does not override `withStrand`
                .collect(Collectors.toList());
        for (Variant variant : leftSorted) {
            if (left.distanceTo(variant) > 0)
                throw new RouteAssemblyException("Variant " + LogUtils.variantSummary(variant) + " is not upstream of the breakend " + LogUtils.breakendSummary(left));
        }


        List<Variant> rightSorted = variants.stream()
                .filter(v -> v.contig().equals(right.contig()) && !v.equals(breakend))
                .sorted(Comparator.comparing(v -> right.distanceTo((Region<?>) v)).reversed())
                .map(v -> (Variant) v.withStrand(right.strand()))
                .collect(Collectors.toList());
        for (Variant variant : rightSorted) {
            if (right.distanceTo(variant) < 0)
                throw new RouteAssemblyException("Variant " + LogUtils.variantSummary(variant) + " is not downstream of the breakend " + LogUtils.breakendSummary(right));
        }


        List<Variant> sortedVariants = new ArrayList<>();
        sortedVariants.addAll(leftSorted);
        sortedVariants.add(breakendVariant);
        sortedVariants.addAll(rightSorted);

        return VariantArrangement.interchromosomal(sortedVariants, leftSorted.size());
    }

}
