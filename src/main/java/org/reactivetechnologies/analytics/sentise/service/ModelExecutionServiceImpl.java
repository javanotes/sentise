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
package org.reactivetechnologies.analytics.sentise.service;

import javax.annotation.PostConstruct;

import org.reactivetechnologies.analytics.sentise.dto.ClassifiedModel;
import org.reactivetechnologies.analytics.sentise.dto.CombinerResult;
import org.reactivetechnologies.analytics.sentise.dto.RegressionModel;
import org.reactivetechnologies.analytics.sentise.dto.RequestData;
import org.reactivetechnologies.analytics.sentise.dto.WekaData;
import org.reactivetechnologies.analytics.sentise.engine.IncrementalModelEngine;
import org.reactivetechnologies.analytics.sentise.err.EngineException;
import org.reactivetechnologies.analytics.sentise.facade.ModelCombinerService;
import org.reactivetechnologies.analytics.sentise.facade.ModelExecutionService;
import org.reactivetechnologies.ticker.messaging.base.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import weka.core.Instance;
import weka.core.Instances;

@Service
public class ModelExecutionServiceImpl implements ModelExecutionService {

	@Autowired
	private Publisher publisher;
	@Autowired
	private ModelCombinerService combiner;
		
	@Value("${weka.classifier.train.dataset.batch.size:100}")
	private int maxDataLen;
	@Value("${weka.classifier.train.dataset.batch:false}")
	private boolean batchEnabled;
	private static final Logger log = LoggerFactory.getLogger(ModelExecutionServiceImpl.class);
	
	private static String getDomain(String domain)
	{
		return StringUtils.hasText(domain) ? domain : IncrementalModelEngine.DEFAULT_CLASSIFICATION_QUEUE;
	}
	private void buildClassifierBatch(WekaData aData, String domain)
	{
		WekaData[] wData = aData.split(maxDataLen);
		for (int i = 0; i < wData.length; i++) {
			WekaData wekaData = wData[i];
			wekaData.setDestination(getDomain(domain));
			publisher.ingest(wekaData);
			log.info(getDomain(domain) + "| Submitted training data chunk of size " + wekaData.instanceSize());
		}
	}
	@Override
	public void buildClassifier(Instances data, String domain) {
		WekaData aData = new WekaData(data);
		if (batchEnabled) {
			buildClassifierBatch(aData, domain); 
		}
		else
		{
			aData.setDestination(getDomain(domain));
			publisher.ingest(aData);
			log.info(getDomain(domain) + "| Submitted training data of size " + aData.instanceSize());
		}
		
	}

	@Override
	public double classifyInstance(Instance instance, String domain) throws Exception {
		RegressionModel model = combiner.retrieveModel(getDomain(domain), false);
		return model.getTrainedClassifier().classifyInstance(instance);
	}

	@Override
	public ClassifiedModel gatherClassifier(String domain) throws EngineException {
		RegressionModel model = combiner.retrieveModel(getDomain(domain), true);
		return new ClassifiedModel(model.getCombineStatus(), model.getTrainedClassifier());
	}
	@Override
	public void buildClassifier(RequestData request) throws EngineException {
		try 
		{
			Instances ins = request.toInstances();
			buildClassifier(ins, request.getDomain());
		} 
		catch (Exception e) {
			throw new EngineException(e);
		}
		
	}
	@Override
	public String classifyInstance(RequestData request) throws Exception {
		try 
		{
			Instance ins = request.toInstance();
			double d = classifyInstance(ins, request.getDomain());
			return request.getStructure().classAttribute().value((int) d);
		} catch (Exception e) {
			throw new EngineException(e);
		}
	}
	
	@PostConstruct
	private void init()
	{
		if (log.isDebugEnabled()) {
			log.debug("request json struct:\n" + RequestData.sampleJson());
		}
	}
	@Override
	public ClassifiedModel getClassifier(String domain) throws EngineException {
		RegressionModel model = combiner.getLocalModel(getDomain(domain));
		ClassifiedModel cm = new ClassifiedModel(CombinerResult.IGNORED, model.getTrainedClassifier());
		return cm;
	}

}
