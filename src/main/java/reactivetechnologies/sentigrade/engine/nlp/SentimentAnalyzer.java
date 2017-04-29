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
package reactivetechnologies.sentigrade.engine.nlp;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.ling.LabeledWord;
import edu.stanford.nlp.neural.rnn.RNNCoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;
import edu.stanford.nlp.util.CoreMap;
import reactivetechnologies.sentigrade.dto.RequestData.Tuple;
import reactivetechnologies.sentigrade.engine.ClassificationModelEngine;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
/**
 * Sentiment analysis using Stanford Core NLP package and SentiNet word weights.
 * @author esutdal
 *
 */
@Component
public class SentimentAnalyzer {

	private static final Logger LOG = LoggerFactory.getLogger(SentimentAnalyzer.class);
	// http://www.comp.leeds.ac.uk/amalgam/tagsets/upenn.html -- tagger codes

	private StanfordCoreNLP sentimentPipeline, tokenizerPipeline;
	private SWN3 sentiNet;
	private volatile boolean initialized;
	@Value("${snlp.analyzer.enablePrint:false}")
	private boolean printNormalized;
	@Value("${snlp.analyzer.coreThreads:0}")
	private int threads;
	private TimeUnit unit = TimeUnit.MILLISECONDS;
	@Value("${snlp.analyzer.coreThreads.timeoutSecs:0}")
	private long timeout;
	@Value("${snlp.analyzer.sentinet.path:}")
	private String sentiFile;
	@PostConstruct
	private void initialize()
	{
		Properties pipelineProps = new Properties();
		Properties tokenizerProps = new Properties();

		pipelineProps.setProperty("annotators", "parse, sentiment");
		pipelineProps.setProperty("enforceRequirements", "false");

		tokenizerProps.setProperty("annotators", "tokenize, ssplit, pos");
		// tokenizerProps.setProperty(StanfordCoreNLP.NEWLINE_SPLITTER_PROPERTY,
		// "true");

		sentimentPipeline = new StanfordCoreNLP(pipelineProps);
		tokenizerPipeline = new StanfordCoreNLP(tokenizerProps);

		sentiNet = StringUtils.hasText(sentiFile) ? new SWN3(sentiFile) : new SWN3();
		try {
			sentiNet.load();
		} catch (IOException e) {
			throw new BeanInitializationException("Unable to load SentiNet dictionary! ", e);
		}
		threads = threads > 0 ? threads : Runtime.getRuntime().availableProcessors();
		annotatorThreads = Executors.newFixedThreadPool(threads, new ThreadFactory() {
			int n = 0;
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "AnnotatorThread-"+(n++));
				t.setDaemon(true);
				return t;
			}
		});
		
		builderThreads = Executors.newSingleThreadExecutor(new ThreadFactory() {
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "AnnotBuilderThread");
				t.setDaemon(true);
				return t;
			}
		});
		
		initialized = true;
		LOG.info("SentimentAnalyzer initialization complete");
	}
	@PreDestroy
	private void onDestroy()
	{
		annotatorThreads.shutdown();
		builderThreads.shutdown();
	}

	private static int normalizedPrediction(Tree t) {
		int n = RNNCoreAnnotations.getPredictedClass(t);
		return n == -1 ? 0 : (n-2);
	}

	private static void outputPredictedClass(Tree tree) {
		Label label = tree.label();
		if (!(label instanceof CoreLabel)) {
			throw new IllegalArgumentException("Required a tree with CoreLabels");
		}
		CoreLabel cl = (CoreLabel) label;
		cl.setValue(Integer.toString(RNNCoreAnnotations.getPredictedClass(tree)));


	}

	private static void print(Tree tree) {
		if (LOG.isDebugEnabled()) {
			Tree t = tree.deepCopy();
			//setSentimentLabels(t);
			LOG.debug("leaf?" + t.isLeaf() + " phrrasal?" + t.isPhrasal() + " preterminal?"
					+ t.isPreTerminal() + " class:" + RNNCoreAnnotations.getPredictedClass(t));
			LOG.debug("" + t + "" );
			
		}

	}
	
	//copied
	/**
	   * Sets the labels on the tree to be the indices of the nodes.
	   * Starts counting at the root and does a postorder traversal.
	   */
	  static int setIndexLabels(Tree tree, int index) {
	    if (tree.isLeaf()) {
	      return index;
	    }

	    tree.label().setValue(Integer.toString(index));
	    index++;
	    for (Tree child : tree.children()) {
	      index = setIndexLabels(child, index);
	    }
	    return index;
	  }
	  //copied
	static void setSentimentLabels(Tree tree) {

		if (tree.isLeaf()) {
			return;
		}

		for (Tree child : tree.children()) {
			setSentimentLabels(child);
		}

		outputPredictedClass(tree);
		// System.err.println("end '"+tree.label().value()+"'");
	}
	
	private SentimentVector calculateScore(CoreMap sentence)
	{
		final Tree sentiTree = sentence.get(SentimentCoreAnnotations.SentimentAnnotatedTree.class);
		print(sentiTree);
		
		SentimentVector weights = calculatePOSScores(sentiTree);
		weights.overallScore = normalizedPrediction(sentiTree);

		String senti = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
		if (printNormalized) {
			LOG.info(normalizedPrediction(sentiTree) + " [" + senti + "] " + sentence);
		}
		LOG.debug(weights+"");
		
		return weights;
	}

	private Sentiments annotateSentence(Annotation annotation) {
		sentimentPipeline.annotate(annotation);
		Sentiments sentiments = new Sentiments();
		for (CoreMap sentence : annotation.get(CoreAnnotations.SentencesAnnotation.class)) {
			
			SentimentVector weights = calculateScore(sentence);
			sentiments.add(weights);
			
		}
		sentiments.normalize();
		return sentiments;
	}
	/**
	 * @deprecated
	 * @param document
	 * @return
	 */
	Sentiments annotateSentiments(String document) {
		Annotation annot = sentimentPipeline.process(document);
		Sentiments sentiments = new Sentiments();
		for (CoreMap sentence : annot.get(CoreAnnotations.SentencesAnnotation.class)) {
			
			SentimentVector weights = calculateScore(sentence);
			sentiments.add(weights);
			
		}
		sentiments.normalize();
		return sentiments;
	}
	
	/**
	 * Get a vectorized form of sentiment/opinion.
	 * @param text
	 * @return {@linkplain SentimentVector}
	 */
	public SentimentVector getSentiment(String text) {
		Assert.isTrue(initialized, "Not initialized!");
		LOG.debug("Analyzing text > '"+text+"'");
		Sentiments vector = calculate(text);
		if (LOG.isDebugEnabled()) {
			LOG.debug("Analysis complete. Final Score: " + vector);
		}
		return vector;
	}
	private double calcPOSNoun(Tree parse) {

		TregexPattern pattern = TregexPattern.compile("@NN");
		TregexMatcher matcher = pattern.matcher(parse);
		double score = 0.0;
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();

			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractNoun(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("Noun Labels: " + labeledYield);
		}
		
		LOG.debug("Sentinet: " + score);

		return score;
	}
	
	private double calcPOSAdv(Tree parse) {

		TregexPattern pattern = TregexPattern.compile("@RB");
		TregexMatcher matcher = pattern.matcher(parse);
		double score = 0.0;
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();

			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractAdv(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("Adv Labels: " + labeledYield);
		}

		pattern = TregexPattern.compile("@RBR");
		matcher = pattern.matcher(parse);
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();
			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractAdv(t + "");

			}
			// System.out.println("Adj: " + leaves);
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("Adv Labels: " + labeledYield);
		}

		pattern = TregexPattern.compile("@RBS");
		matcher = pattern.matcher(parse);
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();
			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractAdv(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("Adv Labels: " + labeledYield);
		}
		
		pattern = TregexPattern.compile("@ADVP");
		matcher = pattern.matcher(parse);
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();
			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractAdv(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("Adv Labels: " + labeledYield);
		}
		
		LOG.debug("Sentinet: " + score);

		return score;
	}
	
	private double calcPOSVerb(Tree parse) {

		TregexPattern pattern = TregexPattern.compile("@VB");
		TregexMatcher matcher = pattern.matcher(parse);
		double score = 0.0;
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();

			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractVerb(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("Verb Labels: " + labeledYield);
		}

		pattern = TregexPattern.compile("@VBD");
		matcher = pattern.matcher(parse);
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();
			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractVerb(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("verb Labels: " + labeledYield);
		}

		pattern = TregexPattern.compile("@VBG");
		matcher = pattern.matcher(parse);
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();
			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractVerb(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("verb Labels: " + labeledYield);
		}
		
		pattern = TregexPattern.compile("@VBZ");
		matcher = pattern.matcher(parse);
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();
			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractVerb(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("verb Labels: " + labeledYield);
		}
		
		pattern = TregexPattern.compile("@VBN");
		matcher = pattern.matcher(parse);
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();
			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractVerb(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("verb Labels: " + labeledYield);
		}
		
		pattern = TregexPattern.compile("@VBP");
		matcher = pattern.matcher(parse);
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();
			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractVerb(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("verb Labels: " + labeledYield);
		}
		
		LOG.debug("Sentinet: " + score);

		return score;
	}
	
	private double calcPOSAdj(Tree parse) {

		TregexPattern pattern = TregexPattern.compile("@JJ");
		TregexMatcher matcher = pattern.matcher(parse);
		double score = 0.0;
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();

			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractAdj(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("Adj Labels: " + labeledYield);
		}

		pattern = TregexPattern.compile("@JJR");
		matcher = pattern.matcher(parse);
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();
			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractAdj(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("Adj Labels: " + labeledYield);
		}

		pattern = TregexPattern.compile("@JJS");
		matcher = pattern.matcher(parse);
		while (matcher.find()) {
			Tree match = matcher.getMatch();
			List<Tree> leaves = match.getLeaves();
			if (sentiNet.isLoaded()) {
				for (Tree t : leaves)
					score += sentiNet.extractAdj(t + "");

			}
			List<LabeledWord> labeledYield = match.labeledYield();
			LOG.debug("Adj Labels: " + labeledYield);
		}

		LOG.debug("Sentinet: " + score);

		return score;
	}

	private SentimentVector calculatePOSScores(Tree parse) {
		SentimentVector vector = new SentimentVector();
		if (parse.isPhrasal()) {
			// TregexPattern pattern = TregexPattern.compile("@NP");//noun
			// TregexPattern pattern = TregexPattern.compile("@VP");
			// TregexPattern pattern = TregexPattern.compile("@JJS");//adjective
			// superlative
			// TregexPattern pattern = TregexPattern.compile("@CD");//numeric
			TregexPattern pattern = TregexPattern.compile("@ADJP");
			TregexMatcher matcher = pattern.matcher(parse);
			
			while (matcher.find()) {
				Tree match = matcher.getMatch();
				vector.adjScore += calcPOSAdj(match.deepCopy());
				vector.advScore += calcPOSAdv(match.deepCopy());
				vector.nounScore += calcPOSNoun(match.deepCopy());
				vector.verbScore += calcPOSVerb(match.deepCopy());
			}
			
			pattern = TregexPattern.compile("@VP");
			matcher = pattern.matcher(parse);
			while (matcher.find()) {
				Tree match = matcher.getMatch();
				vector.adjScore += calcPOSAdj(match.deepCopy());
				vector.nounScore += calcPOSNoun(match.deepCopy());
				vector.verbScore += calcPOSVerb(match.deepCopy());
				vector.advScore += calcPOSAdv(match.deepCopy());
			}
		}
		return vector;
	}
	private ExecutorService annotatorThreads, builderThreads;
	
	private Sentiments annotateSentences(Annotation tokenized)
	{
		Sentiments sentiments = new Sentiments();
		List<CoreMap> sentences = tokenized.get(CoreAnnotations.SentencesAnnotation.class);
		CountDownLatch latch = new CountDownLatch(sentences.size());
		for (CoreMap sentence : sentences) {
			
			annotatorThreads.submit(new SentimentTask(sentence, latch, sentiments));
		}
		boolean done = false;
		try {
			done = latch.await(timeout > 0 ? TimeUnit.SECONDS.toMillis(timeout) : Long.MAX_VALUE, unit);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		
		sentiments.normalize();
		if(!done)
			LOG.warn("Analyzer did not complete in stipulated amount of time!");
		
		return sentiments;
	}
	
	private class SentimentTask implements Callable<Sentiments>
	{

		public SentimentTask(CoreMap sentence, CountDownLatch latch, Sentiments total) {
			super();
			this.sentence = sentence;
			this.latch = latch;
			this.total = total;
		}
		final CoreMap sentence;
		final CountDownLatch latch;
		final Sentiments total;
		@Override
		public Sentiments call() throws Exception {
			Annotation nextAnnotation = new Annotation(sentence.get(CoreAnnotations.TextAnnotation.class));
			nextAnnotation.set(CoreAnnotations.SentencesAnnotation.class, Collections.singletonList(sentence));
			total.add(annotateSentence(nextAnnotation));
			latch.countDown();
			return total;
		}
		
	}

	private Annotation tokenizeText(String text)
	{
		return tokenizerPipeline.process(text.replaceAll("[-+^:]", ""));
	}
	private Sentiments calculate(String text) {
		LOG.debug("start tokenization ..");
		Annotation tokenized = tokenizeText(text);
		LOG.debug("tokenization done");
		Sentiments sentiments = annotateSentences(tokenized);
		return sentiments;
	}

	
	private class BuildInstanceTask implements Runnable
	{
		private final BlockingQueue<Instance> instanceQ;
		public BuildInstanceTask(Instances struct, Tuple t, BlockingQueue<Instance> instanceQ) {
			super();
			this.struct = struct;
			this.t = t;
			this.instanceQ = instanceQ;
		}
		Instances struct;
		Tuple t;
		
		private Instance newWekaInstance()
		{
			Instance i = new DenseInstance(6);
			i.setDataset(struct);
			SentimentVector vector = getSentiment(t.getText());//this invocation can take time, depending on the text size and complexity.
			i.setValue(struct.attribute(ClassificationModelEngine.CLASSIFIER_ATTRIB_ST_ADJ), vector.getAdjScore());
			i.setValue(struct.attribute(ClassificationModelEngine.CLASSIFIER_ATTRIB_ST_ADV), vector.getAdvScore());
			i.setValue(struct.attribute(ClassificationModelEngine.CLASSIFIER_ATTRIB_ST_NOUN), vector.getNounScore());
			i.setValue(struct.attribute(ClassificationModelEngine.CLASSIFIER_ATTRIB_ST_VERB), vector.getVerbScore());
			i.setValue(struct.attribute(ClassificationModelEngine.CLASSIFIER_ATTRIB_ST_ALL), vector.getOverallScore());
			if (t.getTextClass() != null) {
				//not a test instance
				i.setValue(struct.attribute(ClassificationModelEngine.CLASSIFIER_ATTRIB_ST_CLASS_IDX),
						t.getTextClass());
			}
			return i;
		}
		@Override
		public void run() {
			try 
			{
				Instance i = newWekaInstance();
				try {
					instanceQ.put(i);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			} catch (Exception e) {
				LOG.error("", e);
			}
		}
		
	}
	public BuildInstancesDelegate newInstancesBuilder()
	{
		return new BuildInstancesDelegate();
	}
	public class BuildInstancesDelegate
	{
		private volatile int count = 0;
		private BuildInstancesDelegate() {
		}
		private final BlockingQueue<Instance> instanceQ = new LinkedBlockingQueue<>();
		public void submitInstance(Instances struct, Tuple t) {
			builderThreads.submit(new BuildInstanceTask(struct, t, instanceQ));
			count++;
		}
		
		public Instance pollInstance() throws InterruptedException {
			return instanceQ.take();
		}

		public int getCount() {
			return count;
		}

	}
	

	public SentimentAnalyzer() {
		
	}

	@SuppressWarnings("unused")
	public static void main(String[] args) {
		//LoggerFactory.getLogger(LOG.ROOT_LOGGER_NAME).
		//LOG.ROOT_LOGGER_NAME
		String text0 = "jackie brown ( miramax - 1997 ) starring pam grier , samuel l . jackson , robert forster , bridget fonda , michael keaton , robert de niro , michael bowen , chris tucker screenplay by quentin tarantino , based on the novel rum punch by elmore leonard produced by lawrence bender directed by quentin tarantino running time : 155 minutes note : some may consider portions of the following text to be spoilers . be forewarned . ------------------------------------------------------------- during the three years since the release of the groundbreaking success pulp fiction , the cinematic output from its creator , quentin tarantino , has been surprisingly low . oh , he\'s been busy -- doing the talk show circuit , taking small roles in various films , overseeing the production of his screenplay from dusk till dawn , making cameo appearances on television shows , providing a vignette for the ill-fated anthology four rooms -- everything , it seems , except direct another feature-length film . it\'s been the long intermission between projects as well as the dizzying peak which pulp fiction reached which has made mr . tarantino\'s new feature film , jackie brown , one of the most anticipated films of the year , and his third feature film cements his reputation as the single most important new american filmmaker to emerge from the 1990s . things aren\'t going well for jackie brown ( pam grier ) . she\'s 44 years old , stuck at a dead-end job ( \" $16 , 000 a year , plus retirement benefits that aren\'t worth a damn \" ) as a flight attendant for the worst airline in north america -- and she\'s just been caught at the airport by atf agent ray nicolette ( portrayed with terrific childlike enthusiasm by michael keaton ) and police officer mark dargus ( michael bowen ) smuggling $50 000 from mexico for gun-runner ordell robbie ( samuel l . jackson ) , who has her bailed out by unassuming bail bondsman max cherry ( robert forster ) . the loquacious ordell , based out of a hermosa beach house where his horny , bong-hitting surfer girl melanie ( bridget fonda ) and agreeable crony louis gara ( robert de niro ) hang out , operates under the policy that the best rat is a dead rat , and he\'s soon out to silence jackie brown . meanwhile , the authorities\' target is ordell , and they want jackie to help them by arranging a sting to the tune of a half-million dollars . only through a series of clever twists , turns , and double-crosses will jackie be able to gain the upper hand on both of her nemeses . although jackie brown marks mr . tarantino\'s first produced screenplay adaptation ( based on the elmore leonard novel \" rum punch \" ) , there\'s no mistaking his distinctive fingerprints all over this film . while he\'s adhered closely to the source material in a narrative sense , the setting has been relocated to los angeles and the lead character\'s now black . in terms of ambiance , the film harkens back to the 1970s , from the wall-to-wall funk and soul music drowning the soundtrack to the nondescript look of the sets -- even the opening title credit sequence has the echo of vintage 1970s productions . the opening sequence featuring ms . grier wordlessly striding through the lax , funky music blaring away on the speakers , is emblematic of films of that era . the timeframe for the film is in fact 1995 , but the atmosphere of jackie brown is decidedly retro . of course , nothing in the film screams 1970s more than the casting of pam grier and robert forster as the two leads , and although the caper intrigue is fun to watch as the plot twists , backstabbing , and deceptions deliciously unfold , the strength of jackie brown is the quiet , understated relationship developed between jackie and max ; when they kiss , it\'s perhaps the most tender scene of the year . tenderness ? in a quentin tarantino film ? sure , there\'ve been moments of sweetness in his prior films -- the affectionate exchanges between the bruce willis and maria de madeiros characters in pulp fiction and the unflagging dedication shared by the characters of tim roth and amanda plummer , or even in reservoir dogs , where a deep , unspoken bond develops between the harvey keitel and tim roth characters -- but for the most part , mr . tarantino\'s films are typified by manic energy , unexpected outbursts of violence , and clever , often wordy , banter . these staples of his work are all present in jackie brown , but what\'s new here is a different facet of his storytelling -- a willingness to imbue the film with a poignant emotional undercurrent , and a patience to draw out several scenes with great deliberation . this effective demonstration of range prohibits the pigeonholing of mr . tarantino as simply a helmer of slick , hip crime dramas with fast-talking lowlifes , and heralds him as a bonafide multifaceted talent ; he\'s the real deal . this new aspect of mr . tarantino\'s storytelling is probably best embodied in a single character -- that of the world-weary , sensitive , and exceedingly-professional max cherry , whose unspoken attraction to jackie is touching . mr . forster\'s nuanced , understated performance is the best in the film ; he creates an amiable character of such poignancy that when he gazes at jackie , we smile along with him . much press has been given about the casting of blaxploitation-era icon pam grier in the lead , with the wags buzzing that mr . tarantino may do for her what his pulp fiction did to bolster john travolta\'s then-sagging career . as it turns out , ms . grier is solid in the film\'s title role , although nothing here forces her to test her range . i do have to take exception to the claim that this film marks her career resurrection , though -- she\'s been working steadily over the years , often in direct-to-video action flicks , but also in such recent theatrical releases as tim burton\'s mars attacks ! and larry cohen\'s original gangstas ( where she first teamed up with mr . forster . ) of course , it\'s true that her role here was a godsend -- a meaty a part as this is rarity for * any * actress , let alone one of her age and current status in the industry . while jackie brown may disappoint those looking for another pulp fiction clone , it marks tremendous growth of mr . tarantino as a director whose horizons are rapidly expanding , and whose characterizations have never been better . and while the film\'s narrative doesn\'t really warrant a running time of 155 minutes , it\'s filled with such sumptuous riches , ranging from the brashness of the vivid soundtrack to entertaining , inconsequential conversations between the characters , that there wasn\'t an unengaging moment . with an impressive trio of feature films under his belt , it\'ll be interesting to see what he tries next . \r\n";
		
		String text1 = "I love the summer in New York, but I hate the winter.";
		String text2 = "There are slow and repetitive parts, but it has just enough spice to keep it interesting.";
		String text3 = "Those who find ugly meanings in beautiful things are corrupt without being charming.";

		String ce = "Sentiment analysis has never been this good";
		String ce2 = "Sentiment analysis has never been good";
		
		String text = "The movie was exceedingly slow, dangerously living though visually brilliant and maybe a trendsetter";
		
		String airtelNeg = "Hii, airtel 4g provide very low data in very high price.And the network is very solw in our area, "
				+ "and the costumer care service also is very bad, they are not responding on any complain.in my view jio is best."
				+ "airtel sending me fake msg like 2gb data free on downloading my airtel app, I have downloaded the app but I did not find any data on my mobile.";
		
		String airtelPos = "Hi every body I am using Airtel for like 12 years and I have to say that it is the best. "
				+ "I have never had a problem with airtel network. Airtel coverage is very good, signal strength is good in almost every area. "
				+ "I remember on the new year night when everyone was trying to call their relatives all no were going not reachable some were "
				+ "not responsive but I was using airtel and I have to say that I didnt feel any problem while calling or messaging. "
				+ "As the advertisemnt says airtel is the best";

		// processAnnotations(text1);

		SentimentAnalyzer sa = new SentimentAnalyzer();
		sa.printNormalized = true;
		sa.initialize();
		System.out.println(sa.getSentiment(ce));
		System.out.println(sa.getSentiment(ce2));
		System.out.println(sa.getSentiment(airtelNeg));
		System.out.println(sa.getSentiment(airtelPos));
	}
	
}
