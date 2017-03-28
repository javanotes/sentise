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
package analytics;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.Enumeration;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivetechnologies.analytics.sentise.Sentise;
import org.reactivetechnologies.analytics.sentise.core.AbstractIncrementalModelEngine;
import org.reactivetechnologies.analytics.sentise.dto.RegressionModel;
import org.reactivetechnologies.analytics.sentise.dto.WekaData;
import org.reactivetechnologies.analytics.sentise.utils.ConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.ResourceUtils;

import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffLoader.ArffReader;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.StringToWordVector;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {Sentise.class})
public class ClassifierCoreTest {

	@Autowired
	AbstractIncrementalModelEngine classifier;
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
	@Test
	public void setup() throws FileNotFoundException, IOException
	{
		Assert.assertNotNull(classifier);
		Instances ins = readFile();
		Assert.assertNotNull("No instances found", ins);
		Assert.assertFalse("Instances is empty", ins.isEmpty());
		
	}
	@Test
	public void testUpdateableClassifierCanonical() throws Exception
	{
		ArffLoader loader = new ArffLoader();
	    loader.setFile(ResourceUtils.getFile("classpath:sms.arff"));
	    Instances structure = loader.getStructure();
	    structure.setClassIndex(0);
	    
	    StringToWordVector filter = new StringToWordVector();
	    filter.setInputFormat(structure);
	    //filter.setTokenizer(new LuceneWordTokenizer());
	    
	    Instances dataFiltered = Filter.useFilter(loader.getDataSet(), filter);

	    // train NaiveBayes
	    NaiveBayesUpdateable nb = new NaiveBayesUpdateable();
	    //nb.setDoNotCheckCapabilities(true);
	    nb.buildClassifier(dataFiltered.stringFreeStructure());
	    
	    Enumeration<Instance> e = dataFiltered.enumerateInstances();
	    
	    while (e.hasMoreElements())
	      nb.updateClassifier(e.nextElement());

	    // output generated model
	    System.out.println(nb);
	}
		
	@Test
	public void testBuildClassifierWithInstance() throws IOException 
	{
		Instances ins = readFile();
		Assert.assertNotNull("No instances found", ins);
		Assert.assertFalse("Instances is empty", ins.isEmpty());
		Enumeration<Instance> e = ins.enumerateInstances();
		for(int i=0; i<ITERATION; i++)
		{
			if (e.hasMoreElements()) {
				
				Instance in = e.nextElement();
				System.err.println("Submitting Instance==> " + i);
				try {
					classifier.incrementModel(new WekaData(in));
				} catch (Exception e1) {
					e1.printStackTrace();
					Assert.fail();
				} 
			}
			else
				break;
			
		}
		
		RegressionModel model = classifier.generateModelSnapshot();
		Assert.assertNotNull(model);
		String xml = model.toXmlString();
		System.out.println(ConfigUtil.prettyFormatXml(xml, 4));
		System.err.println("Printed model gen on.....\n"+new Date(model.getGeneratedOn()));
		
	}
}
