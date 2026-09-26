package ro.ridelance.anafvalidator.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.springframework.stereotype.Component;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Verifică doar că XML-ul e bine format, înainte să pornim un proces DUKIntegrator.
 * DTD-urile și entitățile externe sunt interzise (protecție XXE); o declarație ANAF nu are DOCTYPE.
 */
@Component
public class XmlWellFormednessChecker {

    public void check(byte[] xml) {
        try {
            newFactory().newSAXParser().parse(new ByteArrayInputStream(xml), new DefaultHandler());
        } catch (SAXParseException e) {
            throw new MalformedXmlException("XML invalid (linia " + e.getLineNumber() + ", coloana "
                    + e.getColumnNumber() + "): " + e.getMessage());
        } catch (SAXException e) {
            throw new MalformedXmlException("XML invalid: " + e.getMessage());
        } catch (ParserConfigurationException | IOException e) {
            throw new IllegalStateException("Nu pot verifica XML-ul", e);
        }
    }

    /** O fabrică nouă per verificare: {@link SAXParserFactory} nu e garantat thread-safe. */
    private static SAXParserFactory newFactory() throws ParserConfigurationException, SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setValidating(false);
        factory.setXIncludeAware(false);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        return factory;
    }
}
