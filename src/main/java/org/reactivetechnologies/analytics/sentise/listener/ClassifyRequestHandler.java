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

import org.reactivetechnologies.analytics.sentise.dto.RequestData;
import org.reactivetechnologies.analytics.sentise.dto.ResponseData;
import org.reactivetechnologies.analytics.sentise.facade.ModelExecutionService;
import org.reactivetechnologies.ticker.rest.AbstractRestHandler;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ClassifyRequestHandler extends AbstractRestHandler {

	private static final Logger log = LoggerFactory.getLogger(ClassifyRequestHandler.class);
	@Autowired
	ModelExecutionService service;

	static final String URL = "/class/{domain}";
	@Override
	public String url() {
		return URL;
	}
	
	@Override
	protected void doGet(Request request, Response response) throws Exception {
		RequestBody req = parse(request, response, "domain");
		RequestData data = RequestData.fromJson(req.body);
		data.setDomain(req.queue);
		
		String result = service.classifyInstance(data);
		ResponseData res = new ResponseData();
		res.setClassification(result);
		response.setContentType("text/json");
		response.setBody(res);
		response.setResponseCode(200);
		log.info("Classification found: "+result);
	}

	@Override
	protected void doPost(Request request, Response response) throws Exception {
		response.setResponseNoContent();
		
	}
	
}
