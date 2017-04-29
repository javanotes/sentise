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
package reactivetechnologies.sentigrade.engine.weka.service;

import javax.annotation.PostConstruct;

import org.reactivetechnologies.ticker.messaging.base.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import reactivetechnologies.sentigrade.dto.ClassifiedModel;
import reactivetechnologies.sentigrade.dto.CombinerResult;
import reactivetechnologies.sentigrade.dto.RequestData;
import reactivetechnologies.sentigrade.dto.VectorRequestData;
import reactivetechnologies.sentigrade.dto.VectorRequestDataFactoryBean;
import reactivetechnologies.sentigrade.engine.ClassificationModelEngine;
import reactivetechnologies.sentigrade.engine.weka.dto.WekaData;
import reactivetechnologies.sentigrade.engine.weka.dto.WekaRegressionModel;
import reactivetechnologies.sentigrade.err.EngineException;
import reactivetechnologies.sentigrade.services.ModelCombinerService;
import reactivetechnologies.sentigrade.services.ModelExecutionService;
import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Instances;

@Service
public class WekaModelExecutionService implements ModelExecutionService {

	@Autowired
	private Publisher publisher;
	@Autowired
	private ModelCombinerService combiner;
	@Autowired
	private VectorRequestDataFactoryBean dataFactory;
	
	@Value("${weka.classifier.train.dataset.batch.size:100}")
	private int maxDataLen;
	@Value("${weka.classifier.train.dataset.batch:false}")
	private boolean batchEnabled;
	private static final Logger log = LoggerFactory.getLogger(WekaModelExecutionService.class);
	
	public static String getDomain(String domain)
	{
		return ClassificationModelEngine.getDomain(domain);
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
	
	private void buildClassifier(Instances data, String domain, boolean enableFilter) throws Exception {
		WekaData aData = new WekaData(data);
		aData.setEnableFilter(enableFilter);
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

	public double classifyInstance(Instance instance, String domain) throws Exception {
		WekaRegressionModel model = (WekaRegressionModel) combiner.retrieveModel(getDomain(domain), false);
		instance.setDataset(model.getStructure());
		return model.getTrainedClassifier().classifyInstance(instance);
	}

	@SuppressWarnings("unchecked")
	@Override
	public ClassifiedModel<Classifier> gatherClassifier(String domain) throws EngineException {
		WekaRegressionModel model = (WekaRegressionModel) combiner.retrieveModel(getDomain(domain), true);
		return new ClassifiedModel<>(model.getCombineStatus(), model.getTrainedClassifier());
	}
	@Override
	public void buildClassifier(RequestData request) throws EngineException {
		try 
		{
			if(request instanceof VectorRequestData)
			{
				combiner.buildVectorModel((VectorRequestData) request);
			}
			else
			{
				buildClassifier(request.toInstances(), request.getDomain(), true);
			}
		} 
		catch (Exception e) {
			throw new EngineException(e);
		}
		
	}
	
	private RequestData parseTestRequest(RequestData request) throws Exception
	{
		if(request.isUseSentimentVector() && !(request instanceof VectorRequestData))
		{
			VectorRequestData vectorReq = dataFactory.getObject();
			vectorReq.setTuples(request);
			return vectorReq;
		}
		else
			return request;
	}
	@Override
	public String classifyInstance(RequestData request) throws Exception {
		try 
		{
			RequestData parsedRequest = parseTestRequest(request);
			return classifyInstanceAsString(parsedRequest.toInstance(), parsedRequest.getDomain());
			
		} 
		catch (Exception e) 
		{
			throw new EngineException(e);
		}
	}
	
	private String classifyInstanceAsString(Instance instance, String domain) throws Exception {
		WekaRegressionModel model = (WekaRegressionModel) combiner.retrieveModel(getDomain(domain), false);
		instance.setDataset(model.getStructure());
		double d = model.getTrainedClassifier().classifyInstance(instance);
		return model.getStructure().classAttribute().value((int) d);
	}
	
	@PostConstruct
	private void init()
	{
		if (log.isDebugEnabled()) {
			log.debug("request json struct:\n" + RequestData.sampleJson());
		}
	}
	@SuppressWarnings("unchecked")
	@Override
	public ClassifiedModel<Classifier> getClassifier(String domain) throws EngineException {
		WekaRegressionModel model = (WekaRegressionModel) combiner.getLocalModel(getDomain(domain));
		return new ClassifiedModel<>(CombinerResult.IGNORED, model.getTrainedClassifier());
	}

}
