package de.intranda.goobi.plugins;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * This file is part of a plugin for Goobi - a Workflow tool for the support of mass digitization.
 *
 * Visit the websites for more information.
 *          - https://goobi.io
 *          - https://www.intranda.com
 *          - https://github.com/intranda/goobi
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 2 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59
 * Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 */

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.configuration.SubnodeConfiguration;
import org.apache.commons.lang.mutable.MutableInt;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Request;
import org.goobi.beans.Step;
import org.goobi.production.enums.PluginGuiType;
import org.goobi.production.enums.PluginReturnValue;
import org.goobi.production.enums.PluginType;
import org.goobi.production.enums.StepReturnValue;
import org.goobi.production.plugin.interfaces.IStepPluginVersion2;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.Namespace;
import org.jdom2.filter.Filters;
import org.jdom2.input.SAXBuilder;
import org.jdom2.xpath.XPathExpression;
import org.jdom2.xpath.XPathFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.sub.goobi.config.ConfigPlugins;
import de.sub.goobi.helper.exceptions.DAOException;
import de.sub.goobi.helper.exceptions.SwapException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.xeoh.plugins.base.annotations.PluginImplementation;

@PluginImplementation
@Log4j2
public class GeonamescorrectionStepPlugin implements IStepPluginVersion2 {
    private static XPathFactory xFactory = XPathFactory.instance();
    private static Namespace altoNs = Namespace.getNamespace("alto", "http://www.loc.gov/standards/alto/ns-v2#");
    private static XPathExpression<Element> tagXpath = xFactory.compile("//alto:NamedEntityTag", Filters.element(), null, altoNs);

    private static Pattern geonameIdExtractor = Pattern.compile("\\/(\\d+)");

    @Getter
    private String title = "intranda_step_geonamescorrection";
    @Getter
    private Step step;
    private String geonamesAccount;
    private String geonamesApiUrl;
    @Getter
    private boolean allowTaskFinishButtons;
    private String returnPath;

    @Getter
    private Map<String, List<NEREntry>> nerEntryMap;
    @Getter
    @Setter
    private String editMode = "all";
    @Getter
    @Setter
    private NEREntry editEntry;
    @Getter
    @Setter
    private String searchString;

    @Override
    public void initialize(Step step, String returnPath) {
        this.returnPath = returnPath;
        this.step = step;

        // read parameters from correct block in configuration file
        SubnodeConfiguration myconfig = ConfigPlugins.getProjectAndStepConfig(title, step);
        geonamesAccount = myconfig.getString("geonamesAccount", "testuser");
        geonamesApiUrl = myconfig.getString("geonamesApiUrl", "http://api.geonames.org");
        allowTaskFinishButtons = myconfig.getBoolean("allowTaskFinishButtons", false);

        try {
            this.nerEntryMap = readAllNEREntries();
        } catch (SwapException | DAOException | IOException | InterruptedException e) {
            //TODO: error in GUI
            log.error(e);
        }

        log.info("Geonamescorrection step plugin initialized");
    }

    private Map<String, List<NEREntry>> readAllNEREntries() throws SwapException, DAOException, IOException, InterruptedException {
        Path altoFolder = Paths.get(this.step.getProzess().getOcrAltoDirectory());
        final Map<String, List<NEREntry>> nerEntryMap = new LinkedHashMap<String, List<NEREntry>>();

        try (Stream<Path> dirStream = Files.list(altoFolder)) {
            MutableInt position = new MutableInt(1);
            dirStream
                    .forEachOrdered(p -> {
                        String filename = p.getFileName().toString();
                        try {
                            nerEntryMap.put(filename, readNEREntriesFromAltoFile(p, position.intValue(), filename, filename));
                            position.increment();
                        } catch (JDOMException | IOException e) {
                            // TODO Auto-generated catch block
                            log.error(e);
                        }
                    });
        }

        return nerEntryMap;
    }

    private List<NEREntry> readNEREntriesFromAltoFile(Path p, int position, String title, String id) throws JDOMException, IOException {
        List<NEREntry> foundEntries = new ArrayList<>();
        SAXBuilder sax = new SAXBuilder();
        Document doc = sax.build(p.toFile());
        List<Element> tags = tagXpath.evaluate(doc);
        for (Element tag : tags) {
            if ("LOCATION".equals(tag.getAttributeValue("TYPE"))) {
                String doc_vocab = tag.getAttributeValue("LABEL");
                String text_snippet = extractTextSnippet(doc, tag);
                String geonames_uri = tag.getAttributeValue("URI");
                String geonames_feature_code = null;
                String geonames_vocab = null;
                String lat = null;
                String lng = null;
                if (geonames_uri != null) {
                    JsonNode geonamesJson = requestGeonames(geonames_uri);
                    geonames_feature_code = geonamesJson.get("fcode").asText();
                    geonames_vocab = geonamesJson.get("name").asText();
                    lat = geonamesJson.get("lat").asText();
                    lng = geonamesJson.get("lng").asText();
                }
                String verification = "UNVERIFIED";
                NEREntry entry = new NEREntry(id, title, doc_vocab, text_snippet, position, geonames_uri,
                        geonames_feature_code, geonames_vocab, lat, lng, verification, 0);
                foundEntries.add(entry);
            }
        }
        return foundEntries;
    }

    private String extractTextSnippet(Document doc, Element tag) {
        String tagref = tag.getAttributeValue("ID");
        String xpathString = String.format("//alto:TextLine[./alto:String[@TAGREFS='%s']]", tagref);
        XPathExpression<Element> lineXpath = xFactory.compile(xpathString, Filters.element(), null, altoNs);
        Element line = lineXpath.evaluateFirst(doc);
        return line.getChildren("String", altoNs)
                .stream()
                .map(str -> str.getAttributeValue("CONTENT"))
                .collect(Collectors.joining(" "));
    }

    private JsonNode requestGeonames(String geonames_uri) throws ClientProtocolException, IOException {
        Matcher matcher = geonameIdExtractor.matcher(geonames_uri);
        String geonamesId = "";
        if (matcher.find()) {
            geonamesId = matcher.group(1);
        } else {
            return null;
        }
        String geonamesJSON = Request.Get(String.format("%s/getJSON?geonameId=%s&username=%s", geonamesApiUrl, geonamesId, geonamesAccount))
                .execute()
                .returnContent()
                .asString();
        JsonNode respJson = new ObjectMapper().readTree(geonamesJSON);
        return respJson;
    }

    public String doEditEntry() {
        System.out.println(this.editMode + " " + this.searchString + " " + this.editEntry);
        return "";
    }

    @Override
    public PluginGuiType getPluginGuiType() {
        return PluginGuiType.FULL;
        // return PluginGuiType.PART;
        // return PluginGuiType.PART_AND_FULL;
        // return PluginGuiType.NONE;
    }

    @Override
    public String getPagePath() {
        return "/uii/plugin_step_geonamescorrection.xhtml";
    }

    @Override
    public PluginType getType() {
        return PluginType.Step;
    }

    @Override
    public String cancel() {
        return "/uii" + returnPath;
    }

    @Override
    public String finish() {
        return "/uii" + returnPath;
    }

    @Override
    public int getInterfaceVersion() {
        return 0;
    }

    @Override
    public HashMap<String, StepReturnValue> validate() {
        return null;
    }

    @Override
    public boolean execute() {
        PluginReturnValue ret = run();
        return ret != PluginReturnValue.ERROR;
    }

    @Override
    public PluginReturnValue run() {
        boolean successful = true;
        // your logic goes here

        log.info("Geonamescorrection step plugin executed");
        if (!successful) {
            return PluginReturnValue.ERROR;
        }
        return PluginReturnValue.FINISH;
    }
}
