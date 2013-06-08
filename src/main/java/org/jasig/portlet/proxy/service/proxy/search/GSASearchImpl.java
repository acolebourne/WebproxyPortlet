package org.jasig.portlet.proxy.service.proxy.search;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.portlet.PortletMode;
import javax.portlet.PortletPreferences;
import javax.portlet.PortletRequest;
import javax.portlet.PortletSession;
import javax.portlet.WindowState;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DecompressingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jasig.portal.search.PortletUrl;
import org.jasig.portal.search.PortletUrlParameter;
import org.jasig.portal.search.PortletUrlType;
import org.jasig.portal.search.SearchRequest;
import org.jasig.portal.search.SearchResult;
import org.jasig.portal.search.SearchResults;
import org.jasig.portlet.proxy.service.GenericContentRequestImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.portlet.util.PortletUtils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

public class GSASearchImpl implements IContentSearch {

    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    
	@Override
	public SearchResults search(SearchRequest searchQuery, PortletRequest request) {
    
		String searchTerms = "";
		try {
			searchTerms = URLEncoder.encode(searchQuery.getSearchTerms(), "UTF-8");
		} catch (UnsupportedEncodingException e) {
			log.warn("Search term cannot be converted to UTF-8",e);
		}
		
		final SearchResults searchResults = new SearchResults();
	        
		searchResults.setQueryId(searchQuery.getQueryId());
		searchResults.setWindowId(request.getWindowID());
		
        String gsa = request.getPreferences().getValue("gsa.host", "");
        String collection = request.getPreferences().getValue("gsa.collection", "");
        String frontend = request.getPreferences().getValue("gsa.frontend", "");
        if (gsa.equals("") || collection.equals("") || frontend.equals("")) {
            log.info("NOT Configured for search -- GSA:"+gsa+" -- COLLECTION:"+collection+" -- frontend:"+frontend);
        }
        
        String baseUrl = request.getPreferences().getValue(GenericContentRequestImpl.CONTENT_LOCATION_KEY, "/");
        log.debug(baseUrl);
        
        Pattern pat = Pattern.compile("^(https?://[^/]+)", Pattern.CASE_INSENSITIVE);      
        Matcher match = pat.matcher(baseUrl);
        if (!match.find()) {
        	//TODO This is a bad idea
            throw new RuntimeException("sBaseUrl cannot be pared for search");
        }
               
        String matchDomain = match.group(1);
        log.debug("DOMAIN :: "+matchDomain);
        
        String searchBaseURL = "http://"+gsa+"/search?q="+searchTerms+"&site="+ collection +"&client="+frontend+"&output=xml_no_dtd";
        log.debug(searchBaseURL);
        
        HttpClient client = new DecompressingHttpClient(new DefaultHttpClient()); 
        
        HttpGet get = new HttpGet(searchBaseURL);

        try {
            log.debug("ABOUT TO DO REQUEST");
            
            HttpResponse httpResponse = client.execute(get);
            log.debug("STATUS CODE :: "+httpResponse.getStatusLine().getStatusCode());
            
            InputStream in = httpResponse.getEntity().getContent();
                        
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = docFactory.newDocumentBuilder();
            
            Document doc = builder.parse(in);
            
            log.debug(("GOT InputSource"));
            XPathFactory  factory=XPathFactory.newInstance();
            XPath xPath=factory.newXPath();
                    
            Integer maxCount = Integer.parseInt(xPath.evaluate("count(/GSP/RES/R)", doc));

            log.debug(maxCount + " -- Results");
            for (int count = 1; count <= maxCount; count++ ) {
            
                //String u = xPath.evaluate("/GSP/RES/R["+count+"]/U/text()", doc).replaceFirst(matchDomain, "");
            	String u = xPath.evaluate("/GSP/RES/R["+count+"]/U/text()", doc);
                String t = xPath.evaluate("/GSP/RES/R["+count+"]/T/text()", doc);
                String s = xPath.evaluate("/GSP/RES/R["+count+"]/S/text()", doc);
                
                log.debug("RESULT -- "+t);
                
                SearchResult result = new SearchResult();
                result.setTitle(t);
                result.setSummary(s);
                PortletUrl pUrl = new PortletUrl();
                pUrl.setPortletMode(PortletMode.VIEW.toString());
                pUrl.setType(PortletUrlType.RENDER);
                pUrl.setWindowState(WindowState.MAXIMIZED.toString());
                PortletUrlParameter param = new PortletUrlParameter();
                param.setName("proxy.url");
                param.getValue().add(u);
                pUrl.getParam().add(param);

                
                this.updateUrls(u, request);
                
                result.setPortletUrl(pUrl);

                searchResults.getSearchResult().add(result);
            }
        
        } catch (IOException ex) {
            log.error(ex.getMessage(),ex);
        } catch (XPathExpressionException ex) {
            log.error(ex.getMessage(),ex);
        } catch (ParserConfigurationException ex) {
            log.error(ex.getMessage(),ex);
        } catch (SAXException ex) {
            log.error(ex.getMessage(),ex);
        }
        
        return searchResults;
	}
	
	protected void updateUrls(final String searchResultUrl, final PortletRequest request) {
		final String REWRITTEN_URLS_KEY = "rewrittenUrls";
		final String WHITELIST_REGEXES_KEY = "whitelistRegexes";
		
        // attempt to retrieve the list of rewritten URLs from the session
        final PortletSession session = request.getPortletSession();
        ConcurrentMap<String, String> rewrittenUrls;
        synchronized (PortletUtils.getSessionMutex(session)) {
            rewrittenUrls = (ConcurrentMap<String, String>) session.getAttribute(REWRITTEN_URLS_KEY);

            // if the rewritten URLs list doesn't exist yet, create it
            if (rewrittenUrls == null) {
                rewrittenUrls = new ConcurrentHashMap<String, String>();
                session.setAttribute(REWRITTEN_URLS_KEY, rewrittenUrls);
            }
        }

        // get the list of configured whitelist regexes
        final PortletPreferences preferences = request.getPreferences();
        final String[] whitelistRegexes = preferences.getValues(WHITELIST_REGEXES_KEY, new String[] {});
       
        // if this URL matches our whitelist regex, rewrite it 
        // to pass through this portlet
        for(String regex : whitelistRegexes) {
        	if(StringUtils.isNotBlank(regex)) {
        		final Pattern pattern = Pattern.compile(regex);  // TODO share compiled regexes
        		
        		if(pattern.matcher(searchResultUrl).find()) {
        			//record that we've rewritten this URL
        			rewrittenUrls.put(searchResultUrl, searchResultUrl);
        		}
        	}
        }
    }

}
