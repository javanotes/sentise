/* ============================================================================
*
* FILE: ModelCombinerComponent.java
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.reactivetechnologies.ticker.datagrid.HazelcastOperations;
import org.reactivetechnologies.ticker.messaging.actors.MessageContainerSupport;
import org.reactivetechnologies.ticker.messaging.data.ext.TimeUIDMapData;
import org.reactivetechnologies.ticker.scheduler.Clock;
import org.reactivetechnologies.ticker.scheduler.TaskScheduler;
import org.reactivetechnologies.ticker.utils.ApplicationContextWrapper;
import org.reactivetechnologies.ticker.utils.TimeUIDSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.hazelcast.core.DuplicateInstanceNameException;
import com.hazelcast.core.ILock;
import com.hazelcast.core.ISet;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;

import reactivetechnologies.sentigrade.WekaConfiguration;
import reactivetechnologies.sentigrade.dto.CombinerResult;
import reactivetechnologies.sentigrade.dto.RegressionModel;
import reactivetechnologies.sentigrade.dto.Signal;
import reactivetechnologies.sentigrade.dto.VectorRequestData;
import reactivetechnologies.sentigrade.engine.ClassificationModelEngine;
import reactivetechnologies.sentigrade.engine.nlp.SentimentAnalyzer.BuildInstancesDelegate;
import reactivetechnologies.sentigrade.engine.weka.AbstractClassificationModelEngine;
import reactivetechnologies.sentigrade.engine.weka.EnsembleCombiner;
import reactivetechnologies.sentigrade.engine.weka.dto.WekaRegressionModel;
import reactivetechnologies.sentigrade.engine.weka.handlers.TrainingDataListenerHandler;
import reactivetechnologies.sentigrade.err.EngineException;
import reactivetechnologies.sentigrade.err.ModelNotFoundException;
import reactivetechnologies.sentigrade.err.ModelNotInitializedException;
import reactivetechnologies.sentigrade.services.ModelCombinerService;
import reactivetechnologies.sentigrade.utils.ConfigUtil;
import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Instances;


/**
 * A component class that performs the scheduled task of stacking classifiers,
 * as well as serve as a cluster communication channel. This class can be considered
 * as the driving class for the Sentise system.
 */
@Service
@ConfigurationProperties
public class WekaModelCombinerService implements MessageListener<Signal>, ModelCombinerService, Callable<RegressionModel> {

	private static final Logger log = LoggerFactory.getLogger(WekaModelCombinerService.class);
	static final byte DUMP_MODEL_REQ = 0b00000001;
	static final byte DUMP_MODEL_RES = 0b00000011;
	
	@Value("${weka.classifier.combiner:VOTING}")
	private String combiner;
	@Value("${weka.classifier.combiner.options:}")
	private String combinerOpts;
	@Value("${weka.classifier.combiner.snapAwaitSecs:600}")
	private int snapshotAwaitSecs;
	
	@Autowired
	private WekaConfiguration config;
	
	private Map<String, AbstractClassificationModelEngine> classifierBeans = new HashMap<>();
	private ConcurrentMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();
	
	@Autowired
	MessageContainerSupport msgContainer;
	
	@Autowired
	private TaskScheduler scheduler;
	@Autowired
	ApplicationContextWrapper ctxWrapper;
	@Autowired
	private ApplicationContext currentCtx;
	
	private void registerListener(ClassificationModelEngine<Classifier> eng, String domain)
	{
		TrainingDataListenerHandler listener = new TrainingDataListenerHandler(eng);
		listener.setDomain(domain);
		msgContainer.registerListener(listener);
	}
	private void registerEngine(String domain)
	{
		AbstractClassificationModelEngine eng = (AbstractClassificationModelEngine) currentCtx.getBean(WekaConfiguration.CACHED_INCR_CLASSIFIER_BEAN, domain);
		Assert.notNull(eng, "'"+WekaConfiguration.CACHED_INCR_CLASSIFIER_BEAN+"' bean is null!");
		classifierBeans.put(domain, eng);
		log.info(domain+"| Loaded engine for classifier ["+eng.classifierAlgorithm()+"] ");
		
		registerListener(eng, domain);
	}
	private void registerEngines()
	{
		String[] split = config.splitDomains();
		for(String s : split)
		{
			registerEngine(s);
		}
	}
	private void register()
	{
		registerEngines();
		msgContainer.start();
	}
	private ExecutorService threads;
	
	private CombinerResult doCombineModel(String domain) throws EngineException
	{
		CombinerResult result = CombinerResult.IGNORED;
		String modelId = "";
		try 
		{
			boolean isSnapshotDone = requestClusterSnapshot(domain, snapshotAwaitSecs, TimeUnit.SECONDS);
			if (isSnapshotDone) {
				log.info(domain+"| Starting ensembling using "+combiner);
				modelId = ensembleModels(domain);
				if (modelId != null) {
					result = CombinerResult.CREATED;
					result.setModelId(modelId);
				}

			} else {
				log.info(domain+"| Task ignored as another ensemble is running ");
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.debug("", e);
		} catch (TimeoutException e) {
			log.error(domain+"| Task timed out while waiting for cluster snapshot. Model not generated", e);
			result = CombinerResult.TIMEOUT;
		} catch (EngineException e) {

			if (e.getCause() instanceof DuplicateInstanceNameException) {
				log.warn(e.getMessage()); 
				result = CombinerResult.EXISTS;
				result.setModelId(e.getCause().getMessage());
			} else
				throw e;

		} 
		
	
		return result;
	}
	/**
	 * Runs a cluster wide model collection, and generates a combined
	 * (stacked/voted/evaluated) classifier model. The generated model is
	 * persisted in database, only if it is different than the ones already
	 * present.
	 * 
	 * @return Persisted model Id, or "" if not persisted in this run
	 * @throws EngineException
	 * @throws InterruptedException 
	 */
	private CombinerResult combineModel(String domain) throws EngineException {
		log.info(domain+"| ensembleModel Task starting..");
		ILock clusterLock = hzService.getClusterLock(domain);
		try 
		{
			boolean locked = clusterLock.tryLock(1, TimeUnit.SECONDS);
			if (locked) 
			{
				return doCombineModel(domain);
			}
			log.warn(domain+"| Ignoring task as another task is currently in execution");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.error(domain+"| InterruptedException while trying to acquire a cluster wide lock!");
		}
		finally
		{
			clusterLock.unlock();
		}
		return CombinerResult.IGNORED;
	}
	/**
	 * Save the ensemble to Hazelcast, only if it is not duplicate (by generated key).
	 * @param ensemble
	 * @param domain 
	 * @return
	 */
	protected boolean saveEnsemble(RegressionModel ensemble, String domain)
	{
		Clock c = scheduler.getClusterClock();
		UUID u = TimeUIDSupport.getTimeUUID(c.getTimestamp());
		
		TimeUIDMapData<RegressionModel> mapData = load(domain);
		for(RegressionModel m : mapData.values())
		{
			if(m.getStringId().equals(ensemble.getStringId()))
				return false;
		}
		mapData.put(u, ensemble);
		save(mapData, domain);
		
		return true;
	}
	
	private String getPersistId(String domain)
	{
		assertDomain(domain);
		String id = classifierBeans.get(domain).classifierAlgorithm();
		return id += "-"+domain+"-"+combiner;
	}
	private void save(TimeUIDMapData<RegressionModel> mapData, String domain)
	{
		String id = getPersistId(domain);
		hzService.set(id, mapData, ConfigUtil.WEKA_MODEL_PERSIST_MAP);
	}
	private boolean hasPersistModel(String domain)
	{
		String id = getPersistId(domain);
		return hzService.contains(id, ConfigUtil.WEKA_MODEL_PERSIST_MAP);
	}
	@SuppressWarnings("unchecked")
	private TimeUIDMapData<RegressionModel> load(String domain)
	{
		String id = getPersistId(domain);
		
		TimeUIDMapData<RegressionModel> mapData;
		if(!hzService.contains(id, ConfigUtil.WEKA_MODEL_PERSIST_MAP))
		{
			mapData = new TimeUIDMapData<>();
		}
		else
		{
			mapData = (TimeUIDMapData<RegressionModel>) hzService.get(id, ConfigUtil.WEKA_MODEL_PERSIST_MAP);
			
		}
		return mapData;
	}

	private String makeEnsemble(List<RegressionModel> models, String domain) throws EngineException
	{
		assertDomain(domain);
		WekaRegressionModel ensemble = classifierBeans.get(domain).ensembleBuiltModels(models, EnsembleCombiner.valueOf(combiner), null);
		log.debug(ensemble.getTrainedClassifier() + "");
		ensemble.generateId();
		
		boolean saved = saveEnsemble(ensemble, domain);
		if (!saved)
			throw new DuplicateInstanceNameException(ensemble.getStringId());

		log.info(domain+"| Saved ensemble. Combiner used: " + combiner);
		return ensemble.getStringId();
	}
	private String ensembleModels(String domain) throws EngineException 
	{
		ISet<WekaRegressionModel> modelSnapshots = hzService.hazelcastInstance().getSet(domain);
		if(modelSnapshots.isEmpty()){
			log.error(domain+"| snapshot collection is empty! will throw exception");
			throw new ModelNotFoundException(domain+"| Model snapshots collected across cluster was empty!");
		}
		
		List<RegressionModel> models = new LinkedList<>();
		for (Iterator<WekaRegressionModel> iterModel = modelSnapshots.iterator(); iterModel.hasNext();) 
		{
			WekaRegressionModel model = iterModel.next();
			if(model.isAttribsInitialized())
				models.add(model);
		}
		//clear the current snapshot
		modelSnapshots.clear();
		
		if(models.isEmpty())
			throw new ModelNotInitializedException(domain+"| All models collected across cluster are uninitialized. At least 1 need to be initialized with training data");
		try
		{
			String ensembleId = makeEnsemble(models, domain);
			return ensembleId;
		} 
		catch (EngineException e) {
			throw e;
		} 
		catch (DuplicateInstanceNameException e) {
			throw new EngineException(domain+"| Ignoring ensemble, as model already present in database", e);
		}
	}

	@Autowired
	private HazelcastOperations hzService;
	private ITopic<Signal> commChannel;
	
	@PreDestroy
	void destroy()
	{
		threads.shutdown();
		try {
			threads.awaitTermination(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	@PostConstruct
	void init() 
	{
		register();
		
		commChannel = hzService.hazelcastInstance().getTopic(ConfigUtil.WEKA_COMMUNICATION_TOPIC);
		commChannel.addMessageListener(this);
		//clusterLatch = hzService.hazelcastInstance().getCountDownLatch(ConfigUtil.WEKA_COMMUNICATION_TOPIC);
		
		threads = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
			
			private int n = 1;

			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "CombinerAsyncThread-"+(n++));
				return t;
			}
		});
		log.info("Execution engine initialization complete. Ensemble models will be saved to IMap '"+ConfigUtil.WEKA_MODEL_PERSIST_MAP+"'");
	}

	private void notifyIfProcessing(String domain) {
		if(latches.containsKey(domain))
			latches.get(domain).countDown();
	}
	
	private void assertDomain(String domain)
	{
		Assert.isTrue(classifierBeans.containsKey(domain), "Invalid domain snapshot request received! "+domain);
	}
	
	private void addToModelSnapshots(WekaRegressionModel model, String domain)
	{
		ISet<RegressionModel> modelSnapshots = hzService.hazelcastInstance().getSet(domain);
		boolean added = modelSnapshots.add(model);
		Assert.isTrue(!modelSnapshots.isEmpty(), domain+"| adding to ISet failed. isEmpty");
		
		if(added)
			log.info(domain+"| Dumped model successfully..");
		else
			log.warn(domain+"| * Model snapshot was not added *");
	}

	private void dumpClassifierSnapshot(String domain) 
	{
		assertDomain(domain);
		WekaRegressionModel model = classifierBeans.get(domain).generateModelSnapshot();
		log.debug(""+model.getTrainedClassifier());
		
		if(model.isAttribsInitialized())
		{
			addToModelSnapshots(model, domain);
		}
		else
			log.info(domain+"| Skipping dump since model is not built");
		
		sendMessage(new Signal(DUMP_MODEL_RES, domain));
	}

	
	public void sendMessage(Signal message) {
		commChannel.publish(message);
		log.debug("Message published:: [" + message + "] ");

	}

	/**
	 * Request a dump of classifier models from all cluster members.
	 * @param domain 
	 * 
	 * @param duration
	 * @param unit
	 * @return
	 * @throws InterruptedException
	 * @throws TimeoutException
	 * @throws EngineException 
	 */
	private boolean requestClusterSnapshot(String domain, long duration, TimeUnit unit) throws InterruptedException, TimeoutException, EngineException {
		boolean snapshotDone = false;
		snapshotDone = signalAndAwait(domain, duration, unit);
		if (!snapshotDone)
			throw new TimeoutException(domain+"| Operation timed out in [" + duration + " " + unit
					+ "] before getting response from all members");

		log.info(domain+"| Received ACK from all participants. Moving ahead..");
		return snapshotDone;
	}

	//private ICountDownLatch clusterLatch;
	private volatile boolean processing;
	
	/**
	 * 
	 * @param domain 
	 * @param duration
	 * @param unit
	 * @return
	 * @throws InterruptedException
	 */
	private boolean signalAndAwait(String domain, long duration, TimeUnit unit) throws InterruptedException {
		//resetLatch();
		setProcessing(true);
		boolean latched = false;
		CountDownLatch latch = null;
		try 
		{
			int size = hzService.hazelcastInstance().getCluster().getMembers().size();
			latch = new CountDownLatch(size);
			latches.put(domain, latch);
			sendMessage(new Signal(DUMP_MODEL_REQ, domain));
			
			log.info(domain+"| Waiting for dump response..");
			
			latched = latch.await(duration, unit);
			
			//cluster latch having race condition problem
			//clusterLatch.await(duration, unit);

		} finally {
			setProcessing(false);
			if(latch != null)
				latches.remove(domain, latch);
		}
		
		return latched;
		//return clusterLatch.getCount() == 0;

	}

	public boolean isProcessing() {
		return processing;
	}

	private void setProcessing(boolean processing) {
		this.processing = processing;
	}
	
	private RegressionModel combineIfThenLoad(String domain, boolean buildNow) throws EngineException
	{
		CombinerResult result = CombinerResult.IGNORED;
		if(buildNow || !hasPersistModel(domain))
		{
			try 
			{
				result = combineModel(domain);
				
			} catch (EngineException e) {
				log.error(domain+"| Unable to execute a combine task");
				throw e;
			}
		}
		try 
		{
			RegressionModel model =  loadLast(domain);
			model.setCombineStatus(result);
			return model;
		} 
		catch (Exception e) {
			throw new EngineException(domain+"| Unable to load latest saved model", e);
		}
	}
	
	private RegressionModel loadLast(String domain)
	{
		TimeUIDMapData<RegressionModel> md = load(domain);
		if(md != null && !md.isEmpty()){
			//System.out.println("WekaModelCombinerService.loadLast() "+ md.keySet().size());
			return md.entrySet().iterator().next().getValue();
		}
		
		throw new NoSuchElementException(domain+"| No model found!");
			
	}
	@Override
	public RegressionModel retrieveModel(String domain, boolean buildNow) throws EngineException  {
		return combineIfThenLoad(domain, buildNow);
	}

	@Override
	public Future<RegressionModel> retrieveModel(String domain, ExecutorService exec) {
		if(exec != null)
			return exec.submit(this);
		else
			return threads.submit(this);
		
	}

	@Override
	public RegressionModel call() throws Exception {
		return combineIfThenLoad("", true);
	}

	@Override
	public void onMessage(Message<Signal> message) {
		log.debug(
				"Message received from Member:: [" + message.getPublishingMember() + "] " + message.getMessageObject());
		switch (message.getMessageObject().getCommand()) 
		{
			case DUMP_MODEL_REQ:
				log.info(message.getMessageObject().getDomain()+"| Received DUMP_MODEL_REQ command ");
				dumpClassifierSnapshot(message.getMessageObject().getDomain());
				break;
			case DUMP_MODEL_RES:
				notifyIfProcessing(message.getMessageObject().getDomain());
				break;
			default:
				break;
		}
		
	}
	@Override
	public RegressionModel getLocalModel(String domain) throws EngineException {
		try 
		{
			assertDomain(domain);
			RegressionModel model = classifierBeans.get(domain).generateModelSnapshot();
			return model;
		} 
		catch (Exception e) {
			throw new EngineException(e);
		}
	}
	
	private void updateModelEngine(VectorRequestData data) throws EngineException
	{
		BuildInstancesDelegate bld = data.toInstancesAsync();
		Instance ins = null;
		int tenPct = bld.getCount() / 10;
		log.info(data.getDomain()+"| Starting analysis and build. This may take some time..");
		long start = System.currentTimeMillis();
		for(int i=0; i<bld.getCount(); i++)
		{
			try 
			{
				ins = bld.pollInstance();
				if ((i+1) % tenPct == 0) {
					log.info(data.getDomain()+"| Analyzed instance " + (i + 1) + " of " + bld.getCount());
				}
				classifierBeans.get(data.getDomain()).incrementModel(ins);
			} 
			catch (InterruptedException e1) {
				Thread.currentThread().interrupt();
				e1.printStackTrace();
			}
			catch (Exception e) {
				throw new EngineException(data.getDomain()+"| Exception while training model", e);
			}
			
		}
		long end = System.currentTimeMillis();
		log.info(data.getDomain()+"| End model build. Time taken: "+ConfigUtil.toTimeElapsedString((end-start)));
	}
	@Override
	public void buildVectorModel(VectorRequestData data) throws EngineException {
		if(!classifierBeans.containsKey(data.getDomain()))
			throw new EngineException("Invalid domain specified- "+data.getDomain());
		
		AbstractClassificationModelEngine engine = classifierBeans.get(data.getDomain());
		if(engine.isClassifierUpdatable())
		{
			updateModelEngine(data);
		}
		else
		{
			buildModelEngine(data);
		}
		
	}
	private void buildModelEngine(VectorRequestData data) throws EngineException {
		log.info(data.getDomain()+"| Starting analysis and build on a non-incremental classifier. This may take some time..");
		long start = System.currentTimeMillis();
		Instances ins = data.toInstances();
		try {
			classifierBeans.get(data.getDomain()).buildClassifier(ins);
		} catch (Exception e) {
			throw new EngineException(data.getDomain()+"| Exception while training model", e);
		}
		long end = System.currentTimeMillis();
		log.info(data.getDomain()+"| End model build. Time taken: "+ConfigUtil.toTimeElapsedString((end-start)));
	}

	
}
