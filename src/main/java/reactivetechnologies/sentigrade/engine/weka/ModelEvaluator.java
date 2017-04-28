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
package reactivetechnologies.sentigrade.engine.weka;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import reactivetechnologies.sentigrade.dto.ClassifiedModel;
import reactivetechnologies.sentigrade.dto.VectorRequestData;
import reactivetechnologies.sentigrade.dto.VectorRequestDataFactoryBean;
import reactivetechnologies.sentigrade.engine.IncrementalModelEngine;
import reactivetechnologies.sentigrade.err.EngineException;
import reactivetechnologies.sentigrade.services.ModelCombinerService;
import reactivetechnologies.sentigrade.services.ModelExecutionService;
import reactivetechnologies.sentigrade.utils.ConfigUtil;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Instances;

@Component
public class ModelEvaluator implements CommandLineRunner, Runnable{

	@Value("${weka.classifier.eval:true}")
	private boolean enabled;
	@Value("${weka.classifier.eval.ensemble:true}")
	private boolean enableCluster;
	@Value("${weka.classifier.eval.path:_eval}")
	private String path;
	@Value("${weka.classifier.eval.domain:}")
	private String domain;
	@Value("${weka.classifier.eval.format:score sentence}")
	private String format;
	@Autowired
	ModelCombinerService combiner;
	@Autowired
	ModelExecutionService executor;
	@Autowired
	TrainingDataLoader loader;
	@Autowired
	private VectorRequestDataFactoryBean dataFactory;
	
	private static final Logger log = LoggerFactory.getLogger(ModelEvaluator.class);
	public ModelEvaluator() {
	}

	@Override
	public void run() {
		if(loadEvalFile())
		{
			log.info("Starting model evaluation run with model level "+(enableCluster ? "CLUSTER" : "LOCAL"));
			log.info("---------------------------------------------------------");
			evaluateModel();
			log.info("* End model evaluation *");
		}
		else
			log.info("* No evaluation file found on classpath. Exit task *");
	}

	private void evaluateModel() {
		Set<String> domains = new HashSet<>();
		domains.add(IncrementalModelEngine.DEFAULT_CLASSIFIER_DOMAIN);
		if (StringUtils.hasText(domain)) {
			for (String s : domain.split(",")) {
				domains.add(s);
			} 
		}
		
		for(String d: domains)
		{
			evaluateDomainModel(d);
		}
	}

	private void evaluateDomainModel(String domain) {
		log.info("Evaluating local model for domain "+domain);
		try 
		{
			Instances ins = loader.loadFromFormattedText(domain, dataDir.getAbsolutePath(), format);
			ClassifiedModel<Classifier> model = enableCluster ? executor.gatherClassifier(domain) : executor.getClassifier(domain);
			log.info("Using model: "+model.model);
			if (log.isDebugEnabled()) {
				log.debug(ConfigUtil.toPrettyXml(model.model));
			}
			VectorRequestData vector = dataFactory.getObject();
			vector.setTextInstances(ins, IncrementalModelEngine.getDomain(domain));
			ins = vector.toInstances();//will take time
			
			Evaluation e = new Evaluation(ins);
			e.useNoPriors();
			e.evaluateModel(model.model, ins);
			log.info("-- Matrix --");
			System.out.println(e.toMatrixString(domain));
			log.info("-- Summary --");
			System.out.println(e.toSummaryString(domain, false));
		} 
		catch (IOException e) {
			log.warn("Unable to read evaluation file/dir", e);
		} catch (EngineException e) {
			log.warn("Unable to extract local model", e);
		} catch (Exception e) {
			log.warn("Exception in evaluation", e);
		}
	}

	private File dataDir;
	private boolean loadEvalFile() {
		try 
		{
			if (StringUtils.hasText(path)) {
				dataDir = ConfigUtil.resolvePath(path);
				return true;
			}
		} catch (FileNotFoundException e) {
			log.error("", e);
			return false;
		}
		return false;
		
	}

	@Override
	public void run(String... args) throws Exception {
		if (enabled) {
			new Thread(this, "EvaluatorThread").start();
		}
	}

}
