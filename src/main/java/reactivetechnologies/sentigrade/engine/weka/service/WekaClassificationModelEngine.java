/* ============================================================================
*
* FILE: IncrementalClassifier.java
*
The MIT License (MIT)

Copyright (c) 2016 Sutanu Dalui

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*
* ============================================================================
*/
package reactivetechnologies.sentigrade.engine.weka.service;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.reactivetechnologies.ticker.datagrid.HazelcastOperations;
import org.reactivetechnologies.ticker.messaging.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import reactivetechnologies.sentigrade.dto.RegressionModel;
import reactivetechnologies.sentigrade.engine.weka.AbstractClassificationModelEngine;
import reactivetechnologies.sentigrade.engine.weka.EnsembleCombiner;
import reactivetechnologies.sentigrade.engine.weka.dto.WekaData;
import reactivetechnologies.sentigrade.engine.weka.dto.WekaRegressionModel;
import reactivetechnologies.sentigrade.engine.weka.service.WordVectorPreprocessor.ArgSwitch;
import reactivetechnologies.sentigrade.err.EngineException;
import reactivetechnologies.sentigrade.err.OperationFailedUnexpectedly;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.UpdateableClassifier;
import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;

/**
 * A extension of Weka updateable classifier.
 *  @see http://wiki.pentaho.com/display/DATAMINING/Handling+Large+Data+Sets+with+Weka
 */

public class WekaClassificationModelEngine extends AbstractClassificationModelEngine {

	/*
	 * 
	    Naive Bayes
	    Naive Bayes multinomial (naive Bayes for text categorization)
	    DMNBtext (discriminitive multinomial naive bayes for text categoriziation)
	    AODE and AODEsr (averaged one dependence estimators)
	    SPegasos (the Pegasos stochasitc gradient descent algorithm for learning linear support vector machines and logistic regression for binary class problems)
	    SGD (stochastic gradient descent for linear regression, binary class logistic regression and linear support vector machines)
	    IB1, IBk and KStar (nearest neighbor learners for classification and regression using a sliding window on the data)
	    locally weighted learning (locally weighted models using a sliding window on the data)
	    RacedIncrementalLogitBoost (ensembles of boosted base learners applied to data "chunks")
	    Cobweb (incremental clustering)

	 */
	
	protected boolean onInitialization() {
		thread = Executors.newSingleThreadExecutor(new ThreadFactory() {
			
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "IncrClassifBuildThread-"+domain);
				t.setDaemon(true);
				return t;
			}
		});
		keepConsuming = true;
		buildTask = new BuildClassifierTask();
		thread.submit(buildTask);
		return false;
	}
	
	protected final AtomicBoolean modelUpdated = new AtomicBoolean();
	protected boolean isUpdateable() {
		return clazzifier != null && (clazzifier instanceof UpdateableClassifier);
	}

	@Autowired
	protected HazelcastOperations hzService;
	protected String domain = DEFAULT_CLASSIFIER_DOMAIN;
	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	private static final Logger log = LoggerFactory.getLogger(WekaClassificationModelEngine.class);

	/*@Value("${weka.classifier.tokenize:true}")
	private boolean filterDataset;*/
	@Value("${weka.classifier.tokenize.options:}")
	private String filterOpts;
	@Value("${weka.classifier.tokenize.useLucene:true}")
	private boolean lucene;
	@Value("${weka.classifier.request.backlog:1000}")
	private int queueBacklog;
	@Value("${weka.classifier.flushSync.enable:false}")
	private boolean flush;
	@Value("${weka.classifier.flushSync.sleepMillis:10}")
	private long flushSleep;
	@Value("${weka.classifier.flushSync.sleepLoop:100}")
	private int flushSleepMax;
	
	protected Classifier clazzifier;
	protected volatile long lastBuildAt = 0;

	private ExecutorService thread;
	/**
	 * Name of the classifier algorithm used. By default returns the
	 * Weka classifier class name.
	 * @return
	 */
	@Override
	public String classifierAlgorithm()
	{
		return clazzifier != null ? clazzifier.getClass().getSimpleName() : "n/a";
	}
	@PostConstruct
	void initialize() 
	{
		instanceQ = new ArrayBlockingQueue<>(queueBacklog);
		onInitialization();
		if (log.isDebugEnabled()) {
			log.info(domain+"| ** Weka Classifier loaded [" + clazzifier.getClass()+ "] **");
			//log.debug("weka.classifier.tokenize? " + filterDataset);
			log.debug("weka.classifier.tokenize.options: " + filterOpts);
			log.debug(clazzifier.toString());
		}

	}

	protected volatile boolean keepConsuming;
	private BuildClassifierTask buildTask;
	/**
	 * 
	 * @param c
	 *            Base classifier
	 * @param size
	 *            size of blocking queue
	 */
	public WekaClassificationModelEngine(Classifier c) 
	{
		//printIncrAlgoValid();
		clazzifier = c;
		
	}
	
	/**
	 * Force a build with all queued instances. The async thread
	 * will be paused while the flush is in progress. <p><b>Warning:</b> There are a couple of things to note here, 
	 * flush might be missed most of the time, if queue remains empty; and again if flush is acquired
	 * when a continuous ingestion is happening, it might fall into an unbreakable loop. So use it with caution!
	 * @return 
	 */
	private boolean flush()
	{
		boolean keepReading = true;
		boolean intr = false;
		int retry = 0;
		while(!(keepReading = buildTask.pause()) && retry++<flushSleepMax){
			try {
				Thread.sleep(flushSleep);
			} catch (InterruptedException e) {
				intr = true;
			}
		}
		if(intr)
			Thread.currentThread().interrupt();
		if (keepReading) 
		{
			try 
			{
				while (keepReading) 
				{
					try 
					{
						Instances ins = instanceQ.poll();
						if (ins == null || ins instanceof Poison)
							keepReading = false;
						else
							buildClassifier(ins);

					} catch (Exception e) {
						log.error("", e);
					}
				}
				
				return true;
			} 
			finally {
				buildTask.resume();
			} 
		}
		else
			log.warn("Giving up! Flush was skipped since unable to acquire mutex on build thread");
		
		return false;
	}

	private final class BuildClassifierTask implements Runnable {
		private final AtomicBoolean acquiredMutex = new AtomicBoolean();
		
		private void onBuild() throws Exception
		{
			try 
			{
				Instances ins = instanceQ.take();
				if (ins instanceof Poison)
					keepConsuming = false;
				else
					buildClassifier(ins);
			} 
			finally {
				acquiredMutex.compareAndSet(true, false);
			}
		}
		
		private void onPause() throws InterruptedException
		{
			log.warn("Try pause build on signal");
			synchronized (acquiredMutex) {
				if(!acquiredMutex.compareAndSet(false, true))
				{
					log.info("Pausing build task..");
					acquiredMutex.wait();
					log.info("Resumed build task..");
				}
			}
		}
		private void build()
		{
			try 
			{
				if(acquiredMutex.compareAndSet(false, true))
				{
					onBuild();
				}
				else
				{
					onPause();
					
				}
				
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				log.error("Error caught in build thread", e);
			}
		}
		private boolean pause()
		{
			//busy spin!
			return acquiredMutex.compareAndSet(false, true);
		}
		private void resume()
		{
			synchronized (acquiredMutex) {
				if(acquiredMutex.compareAndSet(true, false))
					acquiredMutex.notify();
			}
		}
		
		@Override
		public void run() 
		{
			log.info(domain+"| Starting classifier update worker");
			while (keepConsuming) 
			{
				build();
			}
			log.debug(domain+"| Stopped classifier executor thread");
		}
	}

	private static class Poison extends Instances
	{

		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public Poison(String name, ArrayList<Attribute> attInfo, int capacity) {
			super(name, attInfo, capacity);
		}

		private Poison() {
			this("poison", new ArrayList<>(), 0);
		}
		
	}
	@PreDestroy
	void destroy() {
		onDestruction();
		instanceQ.offer(new Poison());
		thread.shutdown();
		
	}
	/**
	 * To be invoked on destroy. Subclasses to override.
	 */
	protected void onDestruction() {
		
	}
	/**
	 * Filter if required. Expects the training string to be the first attribute in the instance.
	 * @param data
	 * @return
	 * @throws Exception
	 */
	protected Instances filterInstances(WekaData data) throws Exception {
		if (data.isEnableFilter()) {
			ArgSwitch args = new ArgSwitch();
			args.setUseLucene(lucene);
			args.setUseNominalAttrib(clazzifier instanceof NaiveBayesUpdateable);
			return WordVectorPreprocessor.process(data.getInstances(), args);
		}
		return data.getInstances();
	}
	protected boolean attribsInitialized;
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private BlockingQueue<Instances> instanceQ;
	protected Instances structure;
	void setStructure(Instances data)
	{
		structure = getStructure(data);
	}
	private synchronized void initAttribs(Instances data) throws Exception
	{
		if (!attribsInitialized) {
			log.debug(data.toSummaryString());
			log.debug(data.toString());
			setStructure(data);
			if (isUpdateable()) {
				clazzifier.buildClassifier(structure);
			}
			attribsInitialized = true;
			log.info(domain + "| Classifier attributes initialized");
		}
	}
	private void updateClassifier(Instances data) throws Exception
	{
		//setStructure(data);//not needed 
		for (Enumeration<Instance> e = data.enumerateInstances(); e.hasMoreElements();) {
			Instance i = e.nextElement();
			updateWithInstance(i, false);
		}
		//this is a volatile variable. updating only once to reduce cost of cpu cache flushes.
		lastBuildAt = System.currentTimeMillis();
		log.info(domain+"| Classifier model updated. Attrib count: "+structure.numAttributes());
	}
	/**
	 * This might be invoked concurrently.
	 * @param i
	 * @throws Exception
	 */
	private synchronized void updateWithInstance(Instance i, boolean updateTimestamp) throws Exception
	{
		i.setDataset(structure);
		((UpdateableClassifier) clazzifier).updateClassifier(i);
		if(updateTimestamp)
			lastBuildAt = System.currentTimeMillis();
		if(modelUpdated.compareAndSet(false, true)){
			log.info(domain+"| Classifier model updated with instance ..");
		}
	}
	
	@Override
	public void buildClassifier(Instances data) throws Exception {
		if(!attribsInitialized)
		{
			initAttribs(data);
		}
		if (isUpdateable()) 
		{
			updateClassifier(data);
		} 
		else
		{
			log.warn(domain+"| '" + classifierAlgorithm()
					+ "' is not an incremental algorithm. Expecting a complete dataset for training. Any previous model built will be overriden");
			clazzifier.buildClassifier(data);
			if(modelUpdated.compareAndSet(false, true)){
				lastBuildAt = System.currentTimeMillis();
			}
			log.info(domain+"| Classifier model built. Attrib count: "+structure.numAttributes());
		}

	}

	/**
	 * Classifies the given test instance. The instance has to belong to a
	 * dataset when it's being classified. Note that a classifier MUST implement
	 * either this or distributionForInstance().
	 * 
	 * @param instance
	 *            the instance to be classified
	 * @return the predicted most likely class for the instance or
	 *         Instance.missingValue() if no prediction is made
	 * @exception Exception
	 *                if an error occurred during the prediction
	 */
	public double classifyInstance(Instance instance) throws Exception {

		return clazzifier.classifyInstance(instance);
	}

	/**
	 * Predicts the class memberships for a given instance. If an instance is
	 * unclassified, the returned array elements must be all zero. If the class
	 * is numeric, the array must consist of only one element, which contains
	 * the predicted value. Note that a classifier MUST implement either this or
	 * classifyInstance().
	 * 
	 * @param instance
	 *            the instance to be classified
	 * @return an array containing the estimated membership probabilities of the
	 *         test instance in each class or the numeric prediction
	 * @exception Exception
	 *                if distribution could not be computed successfully
	 */
	public double[] distributionForInstance(Instance instance) throws Exception {

		return clazzifier.distributionForInstance(instance);
	}

	@Override
	public void incrementModel(Data nextInstance) throws Exception 
	{
		Instances filtered = filterInstances((WekaData) nextInstance);
		filtered.setClassIndex(1);//for text classification, there will only be 2 attributes with the class being the last
		
		while(!instanceQ.offer(filtered, 100, TimeUnit.MILLISECONDS));
	}
	@Override
	public WekaRegressionModel generateModelSnapshot() {
		WekaRegressionModel m = new WekaRegressionModel();
		try 
		{
			if (flush) {
				log.info(domain+"| Flushing before model generation..");
				flush();
			}
			m.setGeneratedOn(lastBuildAt);
			m.setAttribsInitialized(attribsInitialized);
			m.setTrainedClassifier(AbstractClassifier.makeCopy(clazzifier));
			m.setStructure(structure);
		} 
		catch (Exception e) {
			throw new OperationFailedUnexpectedly(domain+"| System Error! Unable to copy built classifier", e);
		}
		
		m.generateId();
		return m;
	}

	@Override
	public WekaRegressionModel ensembleBuiltModels(List<RegressionModel> models, EnsembleCombiner combiner, WekaData evaluationSet) throws EngineException {
		Classifier[] classifiers = new Classifier[models.size()];
		int i = 0;
		for (RegressionModel model : models) {
			classifiers[i++] = ((WekaRegressionModel) model).getTrainedClassifier();
		}

		Classifier bestFit = null;
		bestFit = combiner.getEnsembleClassifier(classifiers, evaluationSet != null ? evaluationSet.getInstances() : null,
				evaluationSet != null ? evaluationSet.getOptions() : null);
		log.info(domain+"| Model combination generated.. ");
		WekaRegressionModel m = new WekaRegressionModel();
		m.setTrainedClassifier(bestFit);
		m.setStructure(structure);
		return m;
	}
	@Override
	public Classifier classifierInstance() {
		return clazzifier;
	}

	@Override
	public void incrementModel(Instance nextInstance) throws Exception {
		if(!attribsInitialized)
		{
			initAttribs(nextInstance.dataset());
		}
		updateWithInstance(nextInstance, true);
	}

	@Override
	public boolean isClassifierUpdatable() {
		return isUpdateable();
	}

}
