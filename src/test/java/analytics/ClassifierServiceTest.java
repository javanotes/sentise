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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.reactivetechnologies.analytics.sentise.EngineException;
import org.reactivetechnologies.analytics.sentise.Sentise;
import org.reactivetechnologies.analytics.sentise.dto.ClassifiedModel;
import org.reactivetechnologies.analytics.sentise.facade.ModelExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.ResourceUtils;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.converters.ArffLoader.ArffReader;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = {Sentise.class})
public class ClassifierServiceTest {

	@Autowired
	ModelExecutionService classifier;
	
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
	public void testBuildClassifierWithInstances() throws Exception 
	{
		Instances ins = readFile();
		Assert.assertNotNull("No instances found", ins);
		Assert.assertFalse("Instances is empty", ins.isEmpty());
		
		try {
			classifier.buildClassifier(ins, null);
		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail();
		}
		Thread.sleep(5000);
		System.err.println("gathering classifier now..........");
		ClassifiedModel model = null;
		try {
			model = classifier.gatherClassifier(null);
		} catch (EngineException e) {
			e.printStackTrace();
			Assert.fail();
		}
		Assert.assertNotNull(model.model);
		//String xml = XStream.serialize(model.model);
		//System.out.println(xml);
	}
	
	@Test
	public void testClassifyUsingBuiltClassifier() throws Exception 
	{
		
		testBuildClassifierWithInstances();

		
		Attribute attribute2 = new Attribute("class", Arrays.asList("neg", "pos"));
		Attribute attribute1 = new Attribute("text", true);
		
		Instances instances = new Instances("Test relation", new ArrayList<>(Arrays.asList(attribute1, attribute2)), 1);
		// Set class index
		instances.setClassIndex(1);
		// Create and add the instance
		DenseInstance instance = new DenseInstance(2);
		instance.setValue(attribute1, "jackie brown ( miramax - 1997 ) starring pam grier , samuel l . jackson , robert forster , bridget fonda , michael keaton , robert de niro , michael bowen , chris tucker screenplay by quentin tarantino , based on the novel rum punch by elmore leonard produced by lawrence bender directed by quentin tarantino running time : 155 minutes note : some may consider portions of the following text to be spoilers . be forewarned . ------------------------------------------------------------- during the three years since the release of the groundbreaking success pulp fiction , the cinematic output from its creator , quentin tarantino , has been surprisingly low . oh , he\'s been busy -- doing the talk show circuit , taking small roles in various films , overseeing the production of his screenplay from dusk till dawn , making cameo appearances on television shows , providing a vignette for the ill-fated anthology four rooms -- everything , it seems , except direct another feature-length film . it\'s been the long intermission between projects as well as the dizzying peak which pulp fiction reached which has made mr . tarantino\'s new feature film , jackie brown , one of the most anticipated films of the year , and his third feature film cements his reputation as the single most important new american filmmaker to emerge from the 1990s . things aren\'t going well for jackie brown ( pam grier ) . she\'s 44 years old , stuck at a dead-end job ( \" $16 , 000 a year , plus retirement benefits that aren\'t worth a damn \" ) as a flight attendant for the worst airline in north america -- and she\'s just been caught at the airport by atf agent ray nicolette ( portrayed with terrific childlike enthusiasm by michael keaton ) and police officer mark dargus ( michael bowen ) smuggling $50 000 from mexico for gun-runner ordell robbie ( samuel l . jackson ) , who has her bailed out by unassuming bail bondsman max cherry ( robert forster ) . the loquacious ordell , based out of a hermosa beach house where his horny , bong-hitting surfer girl melanie ( bridget fonda ) and agreeable crony louis gara ( robert de niro ) hang out , operates under the policy that the best rat is a dead rat , and he\'s soon out to silence jackie brown . meanwhile , the authorities\' target is ordell , and they want jackie to help them by arranging a sting to the tune of a half-million dollars . only through a series of clever twists , turns , and double-crosses will jackie be able to gain the upper hand on both of her nemeses . although jackie brown marks mr . tarantino\'s first produced screenplay adaptation ( based on the elmore leonard novel \" rum punch \" ) , there\'s no mistaking his distinctive fingerprints all over this film . while he\'s adhered closely to the source material in a narrative sense , the setting has been relocated to los angeles and the lead character\'s now black . in terms of ambiance , the film harkens back to the 1970s , from the wall-to-wall funk and soul music drowning the soundtrack to the nondescript look of the sets -- even the opening title credit sequence has the echo of vintage 1970s productions . the opening sequence featuring ms . grier wordlessly striding through the lax , funky music blaring away on the speakers , is emblematic of films of that era . the timeframe for the film is in fact 1995 , but the atmosphere of jackie brown is decidedly retro . of course , nothing in the film screams 1970s more than the casting of pam grier and robert forster as the two leads , and although the caper intrigue is fun to watch as the plot twists , backstabbing , and deceptions deliciously unfold , the strength of jackie brown is the quiet , understated relationship developed between jackie and max ; when they kiss , it\'s perhaps the most tender scene of the year . tenderness ? in a quentin tarantino film ? sure , there\'ve been moments of sweetness in his prior films -- the affectionate exchanges between the bruce willis and maria de madeiros characters in pulp fiction and the unflagging dedication shared by the characters of tim roth and amanda plummer , or even in reservoir dogs , where a deep , unspoken bond develops between the harvey keitel and tim roth characters -- but for the most part , mr . tarantino\'s films are typified by manic energy , unexpected outbursts of violence , and clever , often wordy , banter . these staples of his work are all present in jackie brown , but what\'s new here is a different facet of his storytelling -- a willingness to imbue the film with a poignant emotional undercurrent , and a patience to draw out several scenes with great deliberation . this effective demonstration of range prohibits the pigeonholing of mr . tarantino as simply a helmer of slick , hip crime dramas with fast-talking lowlifes , and heralds him as a bonafide multifaceted talent ; he\'s the real deal . this new aspect of mr . tarantino\'s storytelling is probably best embodied in a single character -- that of the world-weary , sensitive , and exceedingly-professional max cherry , whose unspoken attraction to jackie is touching . mr . forster\'s nuanced , understated performance is the best in the film ; he creates an amiable character of such poignancy that when he gazes at jackie , we smile along with him . much press has been given about the casting of blaxploitation-era icon pam grier in the lead , with the wags buzzing that mr . tarantino may do for her what his pulp fiction did to bolster john travolta\'s then-sagging career . as it turns out , ms . grier is solid in the film\'s title role , although nothing here forces her to test her range . i do have to take exception to the claim that this film marks her career resurrection , though -- she\'s been working steadily over the years , often in direct-to-video action flicks , but also in such recent theatrical releases as tim burton\'s mars attacks ! and larry cohen\'s original gangstas ( where she first teamed up with mr . forster . ) of course , it\'s true that her role here was a godsend -- a meaty a part as this is rarity for * any * actress , let alone one of her age and current status in the industry . while jackie brown may disappoint those looking for another pulp fiction clone , it marks tremendous growth of mr . tarantino as a director whose horizons are rapidly expanding , and whose characterizations have never been better . and while the film\'s narrative doesn\'t really warrant a running time of 155 minutes , it\'s filled with such sumptuous riches , ranging from the brashness of the vivid soundtrack to entertaining , inconsequential conversations between the characters , that there wasn\'t an unengaging moment . with an impressive trio of feature films under his belt , it\'ll be interesting to see what he tries next . \r\n"
				+ "");
		// instance.setValue((Attribute)fvWekaAttributes.elementAt(1), text);
		instances.add(instance);
		
		double pred = classifier.classifyInstance(instances.instance(0), null);
		System.err.println("Class predicted: " + instances.classAttribute().value((int) pred));
	}
	
	//@Test
	public void testEvaluateBuiltClassifier() throws Exception 
	{
		
		testBuildClassifierWithInstances();
		Instances data = readFile();//make this a different eval dataset
		data.setClassIndex(1);
		Evaluation eval = new Evaluation(data);
		Classifier classif = classifier.gatherClassifier(null).model;
		eval.crossValidateModel(classif, data, 10, new Random());
		
		System.out.println(eval.toSummaryString());
	}
	
}
