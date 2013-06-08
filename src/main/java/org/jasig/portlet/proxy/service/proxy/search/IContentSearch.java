package org.jasig.portlet.proxy.service.proxy.search;

import javax.portlet.PortletRequest;

import org.jasig.portal.search.SearchRequest;
import org.jasig.portal.search.SearchResults;

public interface IContentSearch {

	public SearchResults search(SearchRequest searchQuery, PortletRequest request);
	
}
