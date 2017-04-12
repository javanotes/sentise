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
package reactivetechnologies.sentigrade.dto;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import reactivetechnologies.sentigrade.engine.nlp.SentimentAnalyzer;
@Component
public class VectorRequestDataFactoryBean implements FactoryBean<VectorRequestData> {

	@Autowired
	private SentimentAnalyzer analyzer;
	public VectorRequestDataFactoryBean() {
	}

	@Override
	public VectorRequestData getObject() throws Exception {
		VectorRequestData v = new VectorRequestData();
		v.analyzer = analyzer;
		return v;
	}

	@Override
	public Class<?> getObjectType() {
		return VectorRequestData.class;
	}

	@Override
	public boolean isSingleton() {
		return false;
	}

}
