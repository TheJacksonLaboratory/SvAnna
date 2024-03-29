package org.monarchinitiative.svanna.cli.writer.html.template;

import freemarker.cache.ClassTemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;
import org.monarchinitiative.svanna.cli.writer.OutputOptions;
import org.monarchinitiative.svanna.cli.writer.html.AnalysisParameters;
import org.monarchinitiative.svanna.core.LogUtils;
import org.monarchinitiative.phenol.ontology.data.Term;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HtmlTemplate {
    /** Map of data that will be used for the FreeMark template. */
    protected final Map<String, Object> templateData= new HashMap<>();
    /** FreeMarker configuration object. */
    protected final Configuration cfg;

    protected static final String NOT_AVAILABLE = "n/a";

    private static final Logger LOGGER = LoggerFactory.getLogger(HtmlTemplate.class);

    public HtmlTemplate(List<String> htmlList,
                        Map<String, String> infoMap,
                        Collection<Term> originalHpoTerms,
                        AnalysisParameters analysisParameters,
                        OutputOptions outputOptions) {

        this.cfg = new Configuration(new Version(String.valueOf(Configuration.VERSION_2_3_0)));
        cfg.setDefaultEncoding("UTF-8");
        cfg.setLocalizedLookup(false);
        cfg.setOutputFormat(HTMLOutputFormat.INSTANCE);
        cfg.setIncompatibleImprovements(new Version(2, 3, 20));
        ClassTemplateLoader templateLoader = new ClassTemplateLoader(HtmlTemplate.class, "");
        cfg.setTemplateLoader(templateLoader);
        cfg.setClassForTemplateLoading(HtmlTemplate.class, "");
        templateData.putIfAbsent("svalist", htmlList);
        templateData.put("counts_table", infoMap.getOrDefault("counts_table", NOT_AVAILABLE));
        templateData.put("n_unparsable", infoMap.getOrDefault("unparsable", NOT_AVAILABLE));
        templateData.put("vcf_file", infoMap.getOrDefault("vcf_file", NOT_AVAILABLE));
        templateData.put("phenopacket_file", infoMap.getOrDefault("phenopacket_file", NOT_AVAILABLE));
        templateData.put("n_affectedGenes", infoMap.getOrDefault("n_affectedGenes", NOT_AVAILABLE));
        templateData.put("n_affectedEnhancers", infoMap.getOrDefault("n_affectedEnhancers", NOT_AVAILABLE));
        MetaDataHtmlComponent metaDataHtmlComponent = new MetaDataHtmlComponent(originalHpoTerms, analysisParameters, outputOptions);
        templateData.put("analysisMetadata", metaDataHtmlComponent.getHtml());
    }


    public void outputFile(Path outPath) {
        try (BufferedWriter out = Files.newBufferedWriter(outPath)) {
            Template template = cfg.getTemplate("svannHTML.ftl");
            template.process(templateData, out);
        } catch (TemplateException | IOException te) {
            LogUtils.logWarn(LOGGER, "Error writing out HTML results: {}", te.getMessage());
        }
    }
}
