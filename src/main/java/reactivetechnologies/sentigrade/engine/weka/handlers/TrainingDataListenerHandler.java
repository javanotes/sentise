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
package reactivetechnologies.sentigrade.engine.weka.handlers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.reactivetechnologies.ticker.messaging.base.AbstractQueueListener;
import org.reactivetechnologies.ticker.messaging.base.QueueListener;
import org.reactivetechnologies.ticker.messaging.data.TextData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactivetechnologies.sentigrade.engine.IncrementalModelEngine;
import reactivetechnologies.sentigrade.engine.weka.AbstractIncrementalModelEngine;
import reactivetechnologies.sentigrade.engine.weka.dto.WekaData;
import reactivetechnologies.sentigrade.err.EngineException;
import weka.classifiers.Classifier;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.CSVLoader;
import weka.core.converters.JSONLoader;
/**
 * The {@linkplain QueueListener} which listens for training data and submit it
 * (with preprocessing, if needed) to the {@linkplain IncrementalModelEngine} to incrementally update the Weka classifier model. 
 * <p>The listener would be expecting {@linkplain WekaData} type, as event data. If not, it will try on a best effort basis to parse the 
 * input raw text as ARFF or CSV formatted, if possible.
 * @author esutdal
 *
 */
public class TrainingDataListenerHandler extends AbstractQueueListener<TextData> {

	public TrainingDataListenerHandler(IncrementalModelEngine<Classifier> engine) {
		super();
		this.engine = engine;
	}

	private final IncrementalModelEngine<Classifier> engine;
	protected static final Logger LOG = LoggerFactory.getLogger(TrainingDataListenerHandler.class);
	
	private String domain = AbstractIncrementalModelEngine.DEFAULT_CLASSIFIER_DOMAIN;
	@Override
	public Class<TextData> dataType() {
		return TextData.class;
	}

	private static Instances parseArrfInput(String input) throws IOException
	{
		ArffLoader arl = new ArffLoader();
		arl.setSource(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
		return arl.getDataSet();
		
	}
	private static Instances parseCsvInput(String input) throws IOException
	{
		CSVLoader arl = new CSVLoader();
		arl.setSource(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
		return arl.getDataSet();
		
	}
	private static Instances parseJsonInput(String input) throws IOException
	{
		JSONLoader arl = new JSONLoader();
		arl.setSource(new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)));
		return arl.getDataSet();
		
	}
	private static Instances parseInput(String input) throws Throwable
	{
		Throwable nested = new Exception();
		try 
		{
			return parseArrfInput(input);
		} catch (Exception e) {
			nested = nested.initCause(e);
			nested = new Exception(nested);
			try {
				return parseCsvInput(input);
			} catch (Exception e1) {
				nested = nested.initCause(e);
				nested = new Exception(nested);
				try {
					return parseJsonInput(input);
				} catch (IOException e2) {
					nested = nested.initCause(e);
					nested = new Exception(nested);
					
					throw nested;
				}
			}
		}
	}
	private static String asString(TextData m)
	{
		if(m instanceof WekaData)
			return m.toString();
		else
			return m.getPayload();
	}
	@Override
	public void onMessage(TextData m) throws EngineException  {
		try 
		{
			if(m instanceof WekaData)
				engine.incrementModel((WekaData) m);
			else
			{
				LOG.warn("Event not an instanceof InstancesData. Trying on a best effort basis to parse!");
				Instances ins = parseInput(m.getPayload());
				engine.incrementModel(new WekaData(ins));
			}
			
		} 
		catch (Throwable e) {
			LOG.error("Error while parsing input training data!", e);
			throw new EngineException("Unparseable training data. Expecting ARFF/CSV format. '"+asString(m)+"'");
		}

	}
	@Override
	public int parallelism() {
		return 10;
	}

	@Override
	public void destroy() {
		
	}

	@Override
	public void init() {
		LOG.info(domain+"| Started training data receiver");
	}

	@Override
	public String routing() {
		return domain;
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}
}
