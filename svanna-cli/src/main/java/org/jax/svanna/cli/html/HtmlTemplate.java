package org.jax.svanna.cli.html;

import freemarker.cache.ClassTemplateLoader;
import freemarker.core.HTMLOutputFormat;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.Version;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
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
                        Map<TermId, String> topLevelHpoTerms,
                        Map<TermId, String> originalHpoTerms) {
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
        HpoHtmlComponent hpoHtmlComponent = new HpoHtmlComponent(topLevelHpoTerms, originalHpoTerms);
        templateData.put("hpoterms", hpoHtmlComponent.getHtml());
    }




    public void outputFile(String prefix) {
        String outpath = String.format("%s.html", prefix);
        LOGGER.info("Writing HTML results to `{}`", outpath);
        try (BufferedWriter out = new BufferedWriter(new FileWriter(outpath))) {
            Template template = cfg.getTemplate("svannHTML.ftl");
            template.process(templateData, out);
        } catch (TemplateException | IOException te) {
            te.printStackTrace();
        }
    }
}
