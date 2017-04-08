/* ============================================================================
*
* FILE: BootstrapConfigurator.java
*
The MIT License (MIT)

Copyright (c) 2016 Sutanu Dalui

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*
* ============================================================================
*/
package org.reactivetechnologies.sentigrade;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Arrays;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.reactivetechnologies.sentigrade.dto.CombinerResult;
import org.reactivetechnologies.sentigrade.engine.weka.AbstractIncrementalModelEngine;
import org.reactivetechnologies.sentigrade.engine.weka.CachedIncrementalClassifierBean;
import org.reactivetechnologies.ticker.messaging.actors.MessageContainerSupport;
import org.reactivetechnologies.ticker.utils.ApplicationContextWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.core.Utils;

@Configuration
public class WekaConfiguration {

	public static final String INCR_CLASSIFIER_BEAN = "classifier";
	public static final String CACHED_INCR_CLASSIFIER_BEAN = "cachedClassifier";
	
	@Autowired
	MessageContainerSupport container;
	
	@Value("${weka.classifier:weka.classifiers.bayes.NaiveBayesUpdateable}")
	private String wekaClassifier;

	@Value("${weka.classifier.domains:}")
	private String domains;
	@Value("${weka.classifier.options: }")
	private String options;
	
	private String[] domainSplits;
	public String[] splitDomains()
	{
		if(domainSplits == null)
			splitDomains0();
		
		return Arrays.copyOf(domainSplits, domainSplits.length);
	}
	private void splitDomains0()
	{
		if(!StringUtils.hasText(domains)){
			domains = AbstractIncrementalModelEngine.DEFAULT_CLASSIFICATION_QUEUE;
		}
		else
			domains += ","+AbstractIncrementalModelEngine.DEFAULT_CLASSIFICATION_QUEUE;
		
		domainSplits = domains.split(",");
	}
	
	@Bean(name = CACHED_INCR_CLASSIFIER_BEAN)
	@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
	public AbstractIncrementalModelEngine getClassifier(String domain) throws Exception {
		
		Classifier c = null;
		try {
			c = AbstractClassifier.forName(wekaClassifier,
					StringUtils.hasText(options) ? Utils.splitOptions(options) : null);
		} catch (Exception e) {
			c = (Classifier) ApplicationContextWrapper.newInstance(wekaClassifier);
			if(StringUtils.hasText(options))
			{
				Method m = ReflectionUtils.findMethod(c.getClass(), "setOptions");
				if(m != null)
					ReflectionUtils.invokeMethod(m, c, (Object[])Utils.splitOptions(options));
			}
			
		}

		CachedIncrementalClassifierBean cached = new CachedIncrementalClassifierBean(c);
		cached.setDomain(domain);
		return cached;
	}
	
	@Bean
	ObjectMapper jacksonMapper()
	{
		ObjectMapper objMapper = new ObjectMapper();
		SimpleModule mod = new SimpleModule();
		mod.addSerializer(CombinerResult.class, new JsonSerializer<CombinerResult>() {

			@Override
			public void serialize(CombinerResult value, JsonGenerator gen, SerializerProvider serializers)
					throws IOException, JsonProcessingException {
				gen.writeStartObject();
				gen.writeStringField("result", value.name());
				gen.writeStringField("model_id", value.getModelId());
				gen.writeEndObject();
			}
		});
		return objMapper;
	}
	
	@PostConstruct
	void onLoad() {
		splitDomains0();
	}

	@PreDestroy
	void onUnload() {
		
	}
}
