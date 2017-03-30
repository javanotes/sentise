/**
 * Copyright 2017 esutdal

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */
package org.reactivetechnologies.analytics.sentise.listener;

import org.reactivetechnologies.analytics.sentise.dto.ClassifiedModel;
import org.reactivetechnologies.analytics.sentise.dto.RequestData;
import org.reactivetechnologies.analytics.sentise.facade.ModelExecutionService;
import org.reactivetechnologies.analytics.sentise.utils.ConfigUtil;
import org.reactivetechnologies.ticker.rest.AbstractRestHandler;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ClassificationRequestHandler extends AbstractRestHandler {

	private static final Logger log = LoggerFactory.getLogger(ClassificationRequestHandler.class);
	@Autowired
	ModelExecutionService service;

	static final String URL = "/classify/{domain}";
	@Override
	public String url() {
		return URL;
	}
	
	@Override
	protected void doPost(Request request, Response response) throws Exception {
		RequestBody req = parse(request, response, "domain");
		RequestData data = RequestData.fromJson(req.body);
		data.setDomain(req.queue);
		
		service.buildClassifier(data);
		log.info("Submitted request for build classifier: "+req.body);
	}
	@Override
	protected void doGet(Request request, Response response) throws Exception {
		String domain = request.getHeader("domain");
		
		ClassifiedModel model = service.getClassifier(domain);
		response.setContentType("text/xml");
		response.addHeader( "Content-Disposition", "filename=" + "model.xml" );
		response.setBody(ConfigUtil.toPrettyXml(model.model));
		response.setResponseCreated();
		
		log.info("Fetched classifier for domain: "+domain);
	}
	
	public static String escapeHtml(String xml) {
	    String xmlContent = xml.trim().replaceAll("<","&lt;").replaceAll(">","&gt;").replaceAll("\n", "<br />").replaceAll(" ", "&nbsp;");
	    StringBuilder s = new StringBuilder();
	    s.append("<html><body>");
	    s.append("<pre id=\"content\">");
	    s.append(xmlContent);
	    s.append("</pre>");
	    s.append("</body></html>");
	    
	    return s.toString();
	}

}
