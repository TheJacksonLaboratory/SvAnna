package org.monarchinitiative.svanna.io.parse;

import htsjdk.variant.variantcontext.Genotype;
import org.monarchinitiative.svanna.core.LogUtils;
import org.monarchinitiative.svanna.core.reference.VariantCallAttributes;
import org.monarchinitiative.svanna.core.reference.Zygosity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

class VariantCallAttributeParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(VariantCallAttributeParser.class);

    private VariantCallAttributeParser() {
        // private no-op
    }

    static VariantCallAttributes parseAttributes(Map<String, Object> attributes, Genotype genotype) {
        VariantCallAttributes.Builder builder = VariantCallAttributes.builder();

        // first, parse zygosity
        builder.zygosity(parseZygosity(genotype));

        // then read depth
        if (genotype.hasDP()) {
            builder.dp(genotype.getDP());
        } else {
            if (attributes.containsKey("RE")) {
                // Sniffles: ##INFO=<ID=RE,Number=1,Type=Integer,Description="read support">
                try {
                    builder.dp(Integer.parseInt((String) attributes.get("RE")));
                } catch (ClassCastException e) {
                    LOGGER.warn("Unable to parse `RE` attribute: {}", attributes.get("RE"));
                }
            }
        }

        // copy number
        if (genotype.hasExtendedAttribute("CN")) {
            // PBSV and SVIM record number of copies in FORMAT field in applicable cases
            try {
                builder.copyNumber(Integer.parseInt((String) genotype.getExtendedAttribute("CN", "-1")));
            } catch (ClassCastException e) {
                LOGGER.warn("Unable to cast `CN` attribute: {}", genotype.getExtendedAttribute("CN"));
            } catch (NumberFormatException e) {
                LOGGER.warn("`CN` attribute is not an integer: {}", genotype.getExtendedAttribute("CN"));
            }
        }

        // finally allelic depths
        if (genotype.hasAD()) {
            int[] ads = genotype.getAD();
            if (ads.length == 1) // hemizygous, we assume ALT allele
                builder.refReads(0).altReads(ads[0]);
            else if (ads.length == 2)
                builder.refReads(ads[0]).altReads(ads[1]);
            else
                LogUtils.logWarn(LOGGER, "Unexpected AD field in genotype {}", genotype.toBriefString());

        } else if (genotype.hasExtendedAttribute("DR") && genotype.hasExtendedAttribute("DV")) {
            // Sniffles:
            //  - ##FORMAT=<ID=DR,Number=1,Type=Integer,Description="# high-quality reference reads">
            //  - ##FORMAT=<ID=DV,Number=1,Type=Integer,Description="# high-quality variant reads">
            try {
                builder.refReads(Integer.parseInt((String) genotype.getExtendedAttribute("DR", "-1")));
            } catch (ClassCastException e) {
                LOGGER.warn("Unable to cast `DR` attribute: {}", genotype.getExtendedAttribute("DR"));
            } catch (NumberFormatException e) {
                LOGGER.warn("`DR` attribute is not an integer: {}", genotype.getExtendedAttribute("DR"));
            }
            try {
                builder.altReads(Integer.parseInt((String) genotype.getExtendedAttribute("DV", "-1")));
            } catch (ClassCastException e) {
                LOGGER.warn("Unable to cast `DV` attribute: {}", genotype.getExtendedAttribute("DV"));
            } catch (NumberFormatException e) {
                LOGGER.warn("`DV` attribute is not an integer: {}", genotype.getExtendedAttribute("DV"));
            }
        } else if (attributes.containsKey("SUPPORT")) {
            // SVIM: ##INFO=<ID=SUPPORT,Number=1,Type=Integer,Description="Number of reads supporting this variant">
            try {
                builder.altReads(Integer.parseInt((String) attributes.get("SUPPORT")));
            } catch (ClassCastException e) {
                LOGGER.warn("Unable to cast `SUPPORT` attribute: {}", attributes.get("SUPPORT"));
            } catch (NumberFormatException e) {
                LOGGER.warn("`SUPPORT` attribute is not an integer: {}", attributes.get("SUPPORT"));
            }
        }

        return builder.build();
    }

    private static Zygosity parseZygosity(Genotype gt) {
        switch (gt.getType()) {
            case HET:
                return Zygosity.HETEROZYGOUS;
            case HOM_VAR:
            case HOM_REF:
                return Zygosity.HOMOZYGOUS;
            case NO_CALL:
            case UNAVAILABLE:
            default:
                return Zygosity.UNKNOWN;
        }
    }
}
