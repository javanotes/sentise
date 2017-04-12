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

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.BlockJUnit4ClassRunner;
import org.springframework.util.ResourceUtils;

import reactivetechnologies.sentigrade.engine.weka.AbstractIncrementalModelEngine;
import reactivetechnologies.sentigrade.engine.weka.Preprocessor;
import reactivetechnologies.sentigrade.engine.weka.Preprocessor.ArgSwitch;
import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffLoader.ArffReader;

@RunWith(BlockJUnit4ClassRunner.class)
//@SpringBootTest(classes = {Sentise.class})
public class ClassifierCoreNoSpringTest {

	//@Autowired
	final static int ITERATION = 1000;
	
	private Instances readFile() throws FileNotFoundException, IOException
	{
		try(BufferedReader br = new BufferedReader(new FileReader(ResourceUtils.getFile("classpath:tokens.arff") )))
		{
			ArffReader ad = new ArffReader(br);
			Instances ins = ad.getData();
			return ins;
		}
	}
	@Before
	public void setup() throws FileNotFoundException, IOException
	{
		Instances ins = readFile();
		Assert.assertNotNull("No instances found", ins);
		Assert.assertFalse("Instances is empty", ins.isEmpty());
		
	}
	private NaiveBayesUpdateable bayes;
	private Attribute attribute2 = new Attribute("class", Arrays.asList("neg", "pos"));
	private Attribute attribute1 = new Attribute("text", true);
	@Test
	public void testUpdateableClassifierCanonical() throws Exception
	{
		ArffLoader loader = new ArffLoader();
	    loader.setFile(ResourceUtils.getFile("classpath:tokens.arff"));
	    
	    Instances dataFiltered = loader.getDataSet();
	    dataFiltered.setClassIndex(1);
	    dataFiltered = Preprocessor.process(dataFiltered, new ArgSwitch());//Filter.useFilter(loader.getDataSet(), filter);

	    // train NaiveBayes
	    bayes = new NaiveBayesUpdateable();
	    //nb.setDoNotCheckCapabilities(true);
	    System.err.println("building model.............");
	    bayes.buildClassifier(AbstractIncrementalModelEngine.getStructure(dataFiltered));
	    
	    Enumeration<Instance> e = dataFiltered.enumerateInstances();
	    
	    
	    while (e.hasMoreElements())
	      bayes.updateClassifier(e.nextElement());

	    // output generated model
	    System.out.println(bayes);
	    
	    positiveSenti();
	    
	    negativeSenti();
	}
	
	private void positiveSenti() throws Exception
	{
		Instances instances = new Instances("Test relation", new ArrayList<>(Arrays.asList(attribute1, attribute2)), 1);
		// Set class index
		instances.setClassIndex(1);
		System.err.println("------- testClassifyPositiveUsingBuiltClassifier --------");
		// Create and add the instance
		DenseInstance instance = new DenseInstance(2);
		instance.setValue(attribute1, " probably i have never watched a better one than this"
				+ "");
		// instance.setValue((Attribute)fvWekaAttributes.elementAt(1), text);
		instances.add(instance);
		
		double pred = bayes.classifyInstance(instances.instance(0));
		System.err.println("Class predicted: " + instances.classAttribute().value((int) pred));
		instances.clear();
		
		instance = new DenseInstance(2);
		instance.setValue(attribute1, " it is a very good movie. the best i have ever seen!"
				+ "");
		instances.add(instance);
		
		pred = bayes.classifyInstance(instances.instance(0));
		System.err.println("Class predicted: " + instances.classAttribute().value((int) pred));
		instances.clear();
		
		instance = new DenseInstance(2);
		instance.setValue(attribute1, " it will be extremely difficult to produce something like this again ever in history"
				+ "");
		// instance.setValue((Attribute)fvWekaAttributes.elementAt(1), text);
		instances.add(instance);
		
		pred = bayes.classifyInstance(instances.instance(0));
		System.err.println("Class predicted: " + instances.classAttribute().value((int) pred));
		instances.clear();
		
		instance = new DenseInstance(2);
		instance.setValue(attribute1, " i have never seen something like this before. please keep on entertaining us like this forever"
				+ "");
		// instance.setValue((Attribute)fvWekaAttributes.elementAt(1), text);
		instances.add(instance);
		
		pred = bayes.classifyInstance(instances.instance(0));
		System.err.println("Class predicted: " + instances.classAttribute().value((int) pred));
		instances.clear();
		
	}
	
	private void negativeSenti() throws Exception
	{
		Instances instances = new Instances("Test relation", new ArrayList<>(Arrays.asList(attribute1, attribute2)), 1);
		// Set class index
		instances.setClassIndex(1);
		System.err.println("------- testClassifyNegativesUsingBuiltClassifier --------");
		// Create and add the instance
		DenseInstance instance = new DenseInstance(2);
		instance.setValue(attribute1, " it is a sad affair  that ben affleck needs to work with maddison. otherwise it could have been better."
				+ "");
		// instance.setValue((Attribute)fvWekaAttributes.elementAt(1), text);
		instances.add(instance);
		
		double pred = bayes.classifyInstance(instances.instance(0));
		System.err.println("Class predicted: " + instances.classAttribute().value((int) pred));
		instances.clear();
		
		instance = new DenseInstance(2);
		instance.setValue(attribute1, " it is a very bad movie. the worst i have ever seen!"
				+ "");
		// instance.setValue((Attribute)fvWekaAttributes.elementAt(1), text);
		instances.add(instance);
		
		pred = bayes.classifyInstance(instances.instance(0));
		System.err.println("Class predicted: " + instances.classAttribute().value((int) pred));
		instances.clear();
		
		instance = new DenseInstance(2);
		instance.setValue(attribute1, " well i do not mean to sound rude, but go get a life than watching this film"
				+ "");
		// instance.setValue((Attribute)fvWekaAttributes.elementAt(1), text);
		instances.add(instance);
		
		pred = bayes.classifyInstance(instances.instance(0));
		System.err.println("Class predicted: " + instances.classAttribute().value((int) pred));
		instances.clear();
		
		
	}
		
	
}
