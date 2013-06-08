/**
 * Licensed to Jasig under one or more contributor license
 * agreements. See the NOTICE file distributed with this work
 * for additional information regarding copyright ownership.
 * Jasig licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a
 * copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jasig.portlet.proxy.mvc.portlet.proxy;

import javax.annotation.Resource;
import javax.portlet.Event;
import javax.portlet.EventRequest;
import javax.portlet.EventResponse;

import org.jasig.portal.search.SearchConstants;
import org.jasig.portal.search.SearchRequest;
import org.jasig.portal.search.SearchResults;
import org.jasig.portlet.proxy.service.proxy.search.IContentSearch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.portlet.bind.annotation.EventMapping;

/**
 * SearchController is the main search controller for web proxy portlets.
 * 
 * @author Anthony Colebourne
 */
@Controller
@RequestMapping("VIEW")
public class SearchController {
   
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    
    private IContentSearch searchService;
    @Required
    @Resource(name="contentSearchProvider")
    public void setSearchService(IContentSearch searchService) {
		this.searchService = searchService;
	}

	@EventMapping(SearchConstants.SEARCH_REQUEST_QNAME_STRING)
    public void searchRequest(EventRequest request, EventResponse response) {   
        log.debug("EVENT HANDLER -- START");
        
        final Event event = request.getEvent();
        final SearchRequest searchQuery = (SearchRequest)event.getValue();
        
        SearchResults searchResults = searchService.search(searchQuery, request);
        
        response.setEvent(SearchConstants.SEARCH_RESULTS_QNAME, searchResults);
        log.debug("EVENT HANDLER -- END");
    }

}
