package gov.ncbi.pmc.cite;

import gov.ncbi.pmc.ids.IdResolver;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.xml.resolver.tools.CatalogResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Container for some singleton-type objects that are instantiated and shared, regardless
 * of the context we're running in (i.e. webapp vs. unit test).
 */
public class App {
    public static final String apiVersion = "v1";

    private Logger log = LoggerFactory.getLogger(App.class);
    private IdResolver idResolver;
    // Jackson ObjectMapper should be thread-safe, see
    // http://wiki.fasterxml.com/JacksonFAQThreadSafety
    private ObjectMapper mapper;
    private ItemSource itemSource;
    private TransformEngine transformEngine;
    private DocumentBuilderFactory dbf;
    private CatalogResolver catalogResolver;
    private CiteprocPool citeprocPool;



    /**
     * Create a new App object.
     * @throws IOException
     */
    public App() throws Exception {
        idResolver = new IdResolver();
        mapper = new ObjectMapper();

        // Controlled by system property item_provider (default is "test")
        String itemSourceProp = System.getProperty("item_source");
        String itemSourceStr = itemSourceProp != null ? itemSourceProp : "test";
        if (itemSourceStr.equals("test")) {
            itemSource = new TestItemSource(getClass().getClassLoader().getResource("samples/"), this);
        }

        // PubOne from stcache:
        else if (itemSourceStr.equals("stcache-pub-one")) {
            itemSource = new StcachePubOneItemSource(this);
        }
        // NXML from stcache
        else if (itemSourceStr.equals("stcache-nxml")) {
            itemSource = new StcacheNxmlItemSource(this);
        }
        // NXML from Vladimir's conversion service
        else if (itemSourceStr.equals("conv-app-nxml")) {
            itemSource = new ConvAppNxmlItemSource(this);
        }
      /* TBD:
        else if (itemSourceStr.equals("eutils")){
            itemSource = new EutilsItemSource(itemSourceStr, this);
        }
      */
        else {
            throw new Exception("Bad value for item_source; can't continue");
        }


        transformEngine = new TransformEngine(getClass().getClassLoader().getResource("xslt/"), mapper);
        dbf = DocumentBuilderFactory.newInstance();
        catalogResolver = new CatalogResolver();
        citeprocPool = new CiteprocPool(itemSource);
    }


    /**
     * Utility function for getting an XML DocumentBuilder that uses catalogs
     */
    public DocumentBuilder newDocumentBuilder() throws ParserConfigurationException {
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver(catalogResolver);
        return db;
    }


    public IdResolver getIdResolver() {
        return idResolver;
    }

    public ObjectMapper getMapper() {
        return mapper;
    }

    public ItemSource getItemSource() {
        return itemSource;
    }

    public TransformEngine getTransformEngine() {
        return transformEngine;
    }

    public void setTransformEngine(TransformEngine transformEngine) {
        this.transformEngine = transformEngine;
    }

    /**
     * Convenience method that delegates to TransformEngine.
     */
    public Object doTransform(Document src, String transform)
        throws IOException
    {
        return transformEngine.doTransform(src, transform);
    }

    public CiteprocPool getCiteprocPool() {
        return citeprocPool;
    }

}
