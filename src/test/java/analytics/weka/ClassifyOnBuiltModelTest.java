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
package analytics.weka;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivetechnologies.sentigrade.Sentise;
import org.reactivetechnologies.sentigrade.dto.ClassifiedModel;
import org.reactivetechnologies.sentigrade.dto.RequestData;
import org.reactivetechnologies.sentigrade.err.EngineException;
import org.reactivetechnologies.sentigrade.services.ModelExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import weka.classifiers.Classifier;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {Sentise.class})
public class ClassifyOnBuiltModelTest {

	@Autowired
	ModelExecutionService classifier;
	
	@Before
	public void setup() throws FileNotFoundException, IOException, EngineException
	{
		Assert.assertNotNull(classifier);
		classifier.gatherClassifier(null);
	}
	
		
	@Test
	public void testClassifyPositivesUsingBuiltClassifier() throws Exception 
	{
		RequestData train = new RequestData("that was a one time watch movie. but i liked the way he acted", Arrays.asList("neg", "pos"));
		train.setUseSentimentVector(true);
		String pred = classifier.classifyInstance(train);
		System.err.println("Class predicted: " + pred);
		
		train = new RequestData("that was a one time watch movie. but i liked the way he acted", Arrays.asList("neg", "pos"));
		train.setUseSentimentVector(true);
		pred = classifier.classifyInstance(train);
		System.err.println("Class predicted: " + pred);
		
		train = new RequestData("that was a one time watch movie. but i liked the way he acted", Arrays.asList("neg", "pos"));
		train.setUseSentimentVector(true);
		pred = classifier.classifyInstance(train);
		System.err.println("Class predicted: " + pred);
		
		
	}
	@Test
	public void testClassifyNegativesUsingBuiltClassifier() throws Exception 
	{
		RequestData train = new RequestData("That was an extremely bad movie", Arrays.asList("neg", "pos"));
		train.setUseSentimentVector(true);
		String pred = classifier.classifyInstance(train);
		System.err.println("Class predicted: " + pred);
		
		train = new RequestData("The worst network coverage i have ever got", Arrays.asList("neg", "pos"));
		train.setUseSentimentVector(true);
		pred = classifier.classifyInstance(train);
		System.err.println("Class predicted: " + pred);
		
		train = new RequestData("all other operators are better than XYZ operatorator", Arrays.asList("neg", "pos"));
		train.setUseSentimentVector(true);
		pred = classifier.classifyInstance(train);
		System.err.println("Class predicted: " + pred);
	}
	
	@Test
	public void testFetchLocalIncrementalClassifier() throws Exception 
	{
		ClassifiedModel<Classifier> model = classifier.getClassifier(null);
		Assert.assertNotNull(model);
		Assert.assertNotNull(model.model);
		
	}
	
	//@Test
	/*public void testEvaluateBuiltClassifier() throws Exception 
	{
		
		testBuildClassifierWithInstances();
		Instances data = readFile();//make this a different eval dataset
		data.setClassIndex(1);
		Evaluation eval = new Evaluation(data);
		Classifier classif = classifier.gatherClassifier(null).model;
		eval.crossValidateModel(classif, data, 10, new Random());
		
		System.out.println(eval.toSummaryString());
	}*/
	
}
