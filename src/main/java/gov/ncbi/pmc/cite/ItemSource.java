package gov.ncbi.pmc.cite;

import gov.ncbi.pmc.ids.IdGlob;
import gov.ncbi.pmc.ids.RequestIdList;
import gov.ncbi.pmc.ids.Identifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spaceprogram.kittycache.KittyCache;

/**
 * This fetches item data in either PubOne or citeproc-json format, given an IdSet.
 * One of these is instantiated per servlet.
 */
public abstract class ItemSource {
    protected Logger log;
    protected App app;
    // Implement a small-lightweight cache for the retrieved JSON items, to support requests
    // for multiple styles of the same id (for example)
    private KittyCache<String, JsonNode> jsonCache;
    private static final int jsonCacheSize = 100;
    private static final int jsonCacheTtl = 10;


    public ItemSource(App app)
    {
        log = LoggerFactory.getLogger(this.getClass());
        this.app = app;
        jsonCache = new KittyCache<String, JsonNode>(jsonCacheSize);
    }

    /**
     * Get the NXML for a given ID.
     */
    public abstract Document retrieveItemNxml(IdGlob idg)
        throws BadParamException, NotFoundException, IOException;

    /**
     * Get the PubOne XML, given an ID.  The default implementation of this produces the PubOne by
     * transforming the NXML.
     *
     * @throws IOException - if something goes wrong with the transformation
     */
    public Document retrieveItemPubOne(IdGlob idg)
        throws BadParamException, NotFoundException, IOException
    {
        Document nxml = retrieveItemNxml(idg);
        // Prepare id parameters that get passed to the xslt
        Map<String, String> params = new HashMap<String, String>();
        Identifier pmid = idg.getIdByType("pmid");
        if (pmid != null) params.put("pmid", pmid.getValue());
        Identifier pmcid = idg.getIdByType("pmcid");
        if (pmcid != null) params.put("pmcid", pmcid.getValue());

        return (Document) app.doTransform(nxml, "pub-one", params);
    }

    /**
     * Get the item as a json object, as defined by citeproc-json.  This generates the JSON from the PubOne
     * format, and then modifies the results slightly, adding the id field.
     *
     * @throws IOException - if there's some problem retrieving the PubOne or transforming it
     */
    public JsonNode retrieveItemJson(IdGlob idg)
        throws BadParamException, NotFoundException, IOException
    {
        String curie = idg.getIdByType("aiid").getCurie();
        JsonNode cached = jsonCache.get(curie);
        if (cached != null) {
            log.debug("JSON for " + curie + ": kitty-cache hit");
            return cached;
        }
        log.debug("JSON for " + curie + ": kitty-cache miss");

        Document pub_one = retrieveItemPubOne(idg);
        String jsonStr = (String) app.doTransform(pub_one, "pub-one2json");
        ObjectNode json = (ObjectNode) app.getMapper().readTree(jsonStr);
        json.put("id", curie);
        jsonCache.put(curie, json, jsonCacheTtl);
        return json;
    }

    public ObjectMapper getMapper() {
        return app.getMapper();
    }}
