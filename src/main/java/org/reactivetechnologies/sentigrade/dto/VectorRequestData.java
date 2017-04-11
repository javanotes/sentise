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
package org.reactivetechnologies.sentigrade.dto;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.reactivetechnologies.sentigrade.engine.IncrementalModelEngine;
import org.reactivetechnologies.sentigrade.err.OperationFailedUnexpectedly;
import org.reactivetechnologies.sentigrade.nlp.SentimentAnalyzer;
import org.reactivetechnologies.sentigrade.nlp.SentimentAnalyzer.BuildInstancesDelegate;
import org.reactivetechnologies.sentigrade.utils.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

public class VectorRequestData extends RequestData {
	VectorRequestData() {
		super();
		setUseSentimentVector(true);
	}
	/**
	 * Get the nominal options for a class attribute.
	 * @param texts
	 * @return
	 */
	public static List<String> classAttrNominals(Instances texts)
	{
		List<String> l = new LinkedList<>();
		Attribute a = texts.classAttribute();
		if(a.isNominal())
		{
			for(Object o : Collections.list(a.enumerateValues())){
				l.add(o.toString());
			}
			
		}
		return l;
	}
	public void setTextInstances(Instances texts, String domain)
	{
		for(Instance text: Collections.list(texts.enumerateInstances()))
		{
			getDataSet().add(new Tuple(text.stringValue(0), text.stringValue(1)));
			
		}
		getClasses().addAll(classAttrNominals(texts));
		setDomain(domain);
	}
	public void setTuples(RequestData data)
	{
		setDataSet(data.getDataSet());
		setClasses(data.getClasses());
		setDomain(data.getDomain());
	}
	
	//injected through factory bean
	SentimentAnalyzer analyzer;
	private static final Logger log = LoggerFactory.getLogger(VectorRequestData.class);
	//Expensive!
	@Override
	public Instances toInstances() 
	{
		Assert.notEmpty(getDataSet(), "'dataSet' is empty or null");
		final Instances data = getStructure();
		BuildInstancesDelegate builder = analyzer.newInstancesBuilder();
		int count=0;
		log.info("Start transforming to vector. This may take some time ..");
		long start = System.currentTimeMillis();
		for (Tuple t : getDataSet()) {
			if (StringUtils.isEmpty(t.textClass) || t.text == null)
				continue;

			builder.submitInstance(data, t);
			count++;
			
		}
		for (int i = 0; i < count; i++) {
			try {
				data.add(builder.pollInstance());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new OperationFailedUnexpectedly(e);
			}
		}
		long time = System.currentTimeMillis() - start;
		log.info("End transformation. Time taken: "+ConfigUtil.toTimeElapsedString(time));
		return data;
	}
	
	public BuildInstancesDelegate toInstancesAsync() 
	{
		Assert.notEmpty(getDataSet(), "'dataSet' is empty or null");
		final Instances data = getStructure();
		BuildInstancesDelegate builder = analyzer.newInstancesBuilder();
		for (Tuple t : getDataSet()) {
			if (StringUtils.isEmpty(t.textClass) || t.text == null)
				continue;

			builder.submitInstance(data, t);
			
		}
				
		return builder;
	}

	
	@Override
	protected void buildStructure() {
		Assert.notEmpty(getClasses(), "'class' is empty or null");
		Attribute attr0 = new Attribute(IncrementalModelEngine.CLASSIFIER_ATTRIB_ST_ADJ, IncrementalModelEngine.CLASSIFIER_ATTRIB_ST_ADJ_IDX);
		Attribute attr1 = new Attribute(IncrementalModelEngine.CLASSIFIER_ATTRIB_ST_ADV, IncrementalModelEngine.CLASSIFIER_ATTRIB_ST_ADV_IDX);
		Attribute attr2 = new Attribute(IncrementalModelEngine.CLASSIFIER_ATTRIB_ST_NOUN, IncrementalModelEngine.CLASSIFIER_ATTRIB_ST_NOUN_IDX);
		Attribute attr3 = new Attribute(IncrementalModelEngine.CLASSIFIER_ATTRIB_ST_VERB, IncrementalModelEngine.CLASSIFIER_ATTRIB_ST_VERB_IDX);
		Attribute attr4 = new Attribute(IncrementalModelEngine.CLASSIFIER_ATTRIB_ST_ALL, IncrementalModelEngine.CLASSIFIER_ATTRIB_ST_ALL_IDX);
		Attribute attr5 = new Attribute(IncrementalModelEngine.CLASSIFIER_ATTRIB_CLASS, getClasses(),
				IncrementalModelEngine.CLASSIFIER_ATTRIB_ST_CLASS_IDX);
		
		structure = new Instances(getDomain(), new ArrayList<>(Arrays.asList(attr0, attr1, attr2, attr3, attr4, attr5)), getDataSet().size());
		structure.setClass(attr5);
	}
}
