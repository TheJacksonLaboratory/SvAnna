package org.jax.svanna.benchmark.cmd.remap.lift;

import htsjdk.samtools.liftover.LiftOver;
import htsjdk.samtools.util.Interval;
import org.jax.svanna.core.reference.VariantCallAttributes;
import org.jax.svanna.io.FullSvannaVariant;
import org.jax.svanna.io.parse.BreakendedSvannaVariant;
import org.jax.svanna.io.parse.DefaultSvannaVariant;
import org.monarchinitiative.svart.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

public class LiftFullSvannaVariant {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiftFullSvannaVariant.class);

    private final GenomicAssembly genomicAssembly;
    private final LiftOver liftOver;

    public LiftFullSvannaVariant(GenomicAssembly genomicAssembly,
                                 Path liftoverChain) {
        this.genomicAssembly = Objects.requireNonNull(genomicAssembly);
        this.liftOver = new LiftOver(liftoverChain.toFile());
    }

    public Optional<? extends FullSvannaVariant> lift(FullSvannaVariant variant) {
        VariantCallAttributes attributes = createVariantCallAttributes(variant);
        if (variant.isBreakend()) {

            BreakendedSvannaVariant bv = (BreakendedSvannaVariant) variant;
            // Liftover cannot lift interval with length zero. Since all breakends have zero length,
            // we must artificially make the breakend region to have length 1 (hence -1).

            // LEFT
            Breakend left = bv.left();
            int leftStart = left.startWithCoordinateSystem(CoordinateSystem.oneBased()) - 1;
            Interval leftInput = new Interval(left.contig().ucscName(),
                    leftStart,
                    left.endWithCoordinateSystem(CoordinateSystem.oneBased()),
                    left.strand().isNegative(),
                    null);
            Interval leftLifted = liftOver.liftOver(leftInput);
            if (leftLifted == null) return Optional.empty();
            Contig leftContig = genomicAssembly.contigByName(leftLifted.getContig());
            if (leftContig.isUnknown()) {
                LOGGER.warn("Unknown contig {} after lifting the variant {}", leftLifted.getContig(), variant);
                return Optional.empty();
            }
            Coordinates leftCoordinates = Coordinates.of(CoordinateSystem.oneBased(),
                    leftLifted.getStart() + 1,
                    left.startConfidenceInterval(),
                    leftLifted.getEnd(),
                    left.endConfidenceInterval());
            Breakend liftedLeft = Breakend.of(leftContig,
                    left.id(),
                    leftLifted.isPositiveStrand() ? Strand.POSITIVE : Strand.NEGATIVE,
                    leftCoordinates);


            // RIGHT
            Breakend right = bv.right();
            int rightStart = right.startWithCoordinateSystem(CoordinateSystem.oneBased()) - 1;
            Interval rightInput = new Interval(right.contig().ucscName(),
                    rightStart,
                    right.endWithCoordinateSystem(CoordinateSystem.oneBased()),
                    right.strand().isNegative(),
                    null);
            Interval rightLifted = liftOver.liftOver(rightInput);
            if (rightLifted == null) return Optional.empty();
            Contig rightContig = genomicAssembly.contigByName(rightLifted.getContig());
            if (rightContig.isUnknown()) {
                LOGGER.warn("Unknown contig {} after lifting the variant {}", rightLifted.getContig(), variant);
                return Optional.empty();
            }
            Coordinates rightCoordinates = Coordinates.of(CoordinateSystem.oneBased(),
                    rightLifted.getStart() + 1,
                    right.startConfidenceInterval(),
                    rightLifted.getEnd(),
                    right.endConfidenceInterval());
            Breakend liftedRight = Breakend.of(rightContig,
                    right.id(),
                    rightLifted.isPositiveStrand() ? Strand.POSITIVE : Strand.NEGATIVE,
                    rightCoordinates);

            BreakendVariant bvLifted = BreakendVariant.of(bv.eventId(), liftedLeft, liftedRight, bv.ref(), bv.alt());


            return Optional.of(BreakendedSvannaVariant.builder()
                    .with(bvLifted)
                    .variantCallAttributes(attributes)
                    .variantContext(variant.variantContext())
                    .build());

        } else {
            String contigName = variant.contig().ucscName();
            boolean isZeroLength = variant.length() == 0;

            int start = variant.startOnStrandWithCoordinateSystem(Strand.POSITIVE, CoordinateSystem.oneBased());
            if (isZeroLength) {
                // liftover cannot lift interval with length zero. We must artificially make it a region of length 1.
                start = start - 1;
            }
            Interval liftoverInput = new Interval(contigName, start, variant.endOnStrandWithCoordinateSystem(Strand.POSITIVE, CoordinateSystem.oneBased()));

            Interval lifted = liftOver.liftOver(liftoverInput);
            if (lifted == null) {
                // cannot be lifted over
                return Optional.empty();
            } else {
                Contig contig = genomicAssembly.contigByName(lifted.getContig());
                if (contig.isUnknown()) {
                    LOGGER.warn("Unknown contig {} after lifting the variant {}", lifted.getContig(), variant);
                    return Optional.empty();
                } else {
                    Coordinates coordinates = Coordinates.of(CoordinateSystem.oneBased(),
                            isZeroLength ? lifted.getStart() + 1 : lifted.getStart(),
                            variant.startConfidenceInterval(),
                            lifted.getEnd(),
                            variant.endConfidenceInterval());

                    Variant v;
                    if (variant.isSymbolic()) {
                        // symbolic variant
                        v = Variant.of(contig, variant.id(), lifted.isPositiveStrand() ? Strand.POSITIVE : Strand.NEGATIVE,
                                coordinates,
                                variant.ref(),
                                variant.alt(),
                                calculateChangeLength(variant.startWithCoordinateSystem(CoordinateSystem.zeroBased()), variant.endWithCoordinateSystem(CoordinateSystem.zeroBased()), variant.variantType(), variant.changeLength()));
                    } else {
                        // sequence variant
                        v = Variant.of(contig,
                                variant.id(),
                                lifted.isPositiveStrand() ? Strand.POSITIVE : Strand.NEGATIVE,
                                coordinates,
                                variant.ref(),
                                variant.alt());
                    }

                    return Optional.of(DefaultSvannaVariant.builder()
                            .with(v)
                            .variantCallAttributes(attributes)
                            .variantContext(variant.variantContext())
                            .build());
                }
            }
        }
    }

    private static VariantCallAttributes createVariantCallAttributes(FullSvannaVariant variant) {
        return VariantCallAttributes.builder()
                .dp(variant.minDepthOfCoverage())
                .refReads(variant.numberOfRefReads())
                .altReads(variant.numberOfAltReads())
                .zygosity(variant.zygosity())
                .copyNumber(variant.copyNumber())
                .build();
    }


    private static int calculateChangeLength(int start,
                                             int end,
                                             VariantType variantType,
                                             int changeLengthForIns) {
        switch (variantType.baseType()) {
            case DEL:
                return -(end - start);
            case INV:
                return 0;
            case INS:
                // the change length is unchanged for INS
                return changeLengthForIns;
            case DUP:
            case SNV:
            case CNV:
            default:
                return end - start;
        }
    }

}
