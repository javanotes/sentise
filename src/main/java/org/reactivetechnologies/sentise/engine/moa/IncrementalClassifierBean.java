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
package org.reactivetechnologies.sentise.engine.moa;

import java.util.ArrayList;
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

import org.reactivetechnologies.sentise.dto.RegressionModel;
import org.reactivetechnologies.sentise.engine.moa.dto.MoaData;
import org.reactivetechnologies.sentise.engine.moa.dto.MoaRegressionModel;
import org.reactivetechnologies.sentise.engine.weka.dto.WekaData;
import org.reactivetechnologies.sentise.err.EngineException;
import org.reactivetechnologies.sentise.err.OperationFailedUnexpectedly;
import org.reactivetechnologies.ticker.datagrid.HazelcastOperations;
import org.reactivetechnologies.ticker.messaging.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;

import moa.classifiers.Classifier;
import moa.core.Utils;

/**
 * A extension of Weka updateable classifier.
 *  @see http://wiki.pentaho.com/display/DATAMINING/Handling+Large+Data+Sets+with+Weka
 */

class IncrementalClassifierBean extends AbstractMOAModelEngine {

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
	
	@Autowired
	protected HazelcastOperations hzService;
	protected String domain = DEFAULT_CLASSIFICATION_QUEUE;
	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	private static final Logger log = LoggerFactory.getLogger(IncrementalClassifierBean.class);

	@Value("${weka.classifier.tokenize:true}")
	private boolean filterDataset;
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
			log.debug("weka.classifier.tokenize? " + filterDataset);
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
	 * @throws ReflectiveOperationException 
	 */
	public IncrementalClassifierBean(String className) throws ReflectiveOperationException 
	{
		super(className);
		
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
	public void buildClassifier(Instances ins) {
		// TODO Auto-generated method stub
		
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
	protected Instances filterInstances(MoaData data) throws Exception {
		return structure;/*
		if (filterDataset) {
			ArgSwitch args = new ArgSwitch();
			args.setUseLucene(lucene);
			args.setUseNominalAttrib(clazzifier instanceof NaiveBayesUpdateable);
			return Preprocessor.process(data.getInstances(), args);
		}
		return data.getInstances();
	*/}
	protected boolean attribsInitialized;
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private BlockingQueue<Instances> instanceQ;
	protected Instances structure;
	void setStructure(Instances data)
	{
		//structure = getStructure(data);
	}
	private void initAttribs(Instances data) throws Exception
	{/*
		log.debug(data.toString());
		setStructure(data);
		clazzifier.trainOnInstance(structure);
		attribsInitialized = true;
		
		log.info(domain+"| Classifier attributes initialized..");
	*/}
	private void updateClassifier(Instances data) throws Exception
	{/*
		setStructure(data);
		for (Enumeration<Instance> e = data.enumerateInstances(); e.hasMoreElements();) {
			Instance i = e.nextElement();
			i.setDataset(structure);
			u.updateClassifier(i);
			if(modelUpdated.compareAndSet(false, true)){
				lastBuildAt = System.currentTimeMillis();
			}
		}
		//this is a volatile variable. updating only once to reduce cost of cpu cache flushes.
		lastBuildAt = System.currentTimeMillis();
		log.info(domain+"| Classifier build updated. Attrib count: "+structure.numAttributes());
	*/}
	
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

		return Utils.maxIndex(distributionForInstance(instance));
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

		return clazzifier.getVotesForInstance(instance);
	}

	@Override
	public void incrementModel(Data nextInstance) throws Exception 
	{
		Instances filtered = filterInstances((MoaData) nextInstance);
		filtered.setClassIndex(1);//for text classification, there will only be 2 attributes with the class being the last
		
		while(!instanceQ.offer(filtered, 100, TimeUnit.MILLISECONDS));
	}
	@Override
	public MoaRegressionModel generateModelSnapshot() {
		MoaRegressionModel m = new MoaRegressionModel();
		try 
		{
			if (flush) {
				log.info(domain+"| Flushing before model generation..");
				flush();
			}
			m.setGeneratedOn(lastBuildAt);
			m.setAttribsInitialized(attribsInitialized);
			m.setTrainedClassifier(clazzifier.copy());
			m.setStructure(structure);
		} 
		catch (Exception e) {
			throw new OperationFailedUnexpectedly(domain+"| System Error! Unable to copy built classifier", e);
		}
		
		m.generateId();
		return m;
	}

	@Override
	public MoaRegressionModel ensembleBuiltModels(List<RegressionModel> models, MoaData evaluationSet) throws EngineException {
		Classifier[] classifiers = new Classifier[models.size()];
		int i = 0;
		for (RegressionModel model : models) {
			classifiers[i++] = ((MoaRegressionModel) model).getTrainedClassifier();
		}

		Classifier bestFit = null;
		//new Vote(vote, error)
		/*bestFit = combiner.getEnsembleClassifier(classifiers, evaluationSet != null ? evaluationSet.getInstances() : null,
				evaluationSet != null ? evaluationSet.getOptions() : null);*/
		log.info(domain+"| Model combination generated.. ");
		MoaRegressionModel m = new MoaRegressionModel();
		m.setTrainedClassifier(bestFit);
		return m;
	}
	@Override
	public Classifier classifierInstance() {
		return clazzifier;
	}

}
