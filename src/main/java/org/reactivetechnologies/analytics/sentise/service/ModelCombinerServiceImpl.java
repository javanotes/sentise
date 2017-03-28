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
package org.reactivetechnologies.analytics.sentise.service;

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

import org.reactivetechnologies.analytics.sentise.ClassifierConfiguration;
import org.reactivetechnologies.analytics.sentise.EngineException;
import org.reactivetechnologies.analytics.sentise.core.AbstractIncrementalModelEngine;
import org.reactivetechnologies.analytics.sentise.core.CombinerType;
import org.reactivetechnologies.analytics.sentise.core.IncrementalModelEngine;
import org.reactivetechnologies.analytics.sentise.dto.CombinerResult;
import org.reactivetechnologies.analytics.sentise.dto.RegressionModel;
import org.reactivetechnologies.analytics.sentise.dto.Signal;
import org.reactivetechnologies.analytics.sentise.facade.ModelCombinerService;
import org.reactivetechnologies.analytics.sentise.listener.TrainingDataListener;
import org.reactivetechnologies.analytics.sentise.utils.ConfigUtil;
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
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.hazelcast.core.DuplicateInstanceNameException;
import com.hazelcast.core.ILock;
import com.hazelcast.core.ISet;
import com.hazelcast.core.ITopic;
import com.hazelcast.core.Message;
import com.hazelcast.core.MessageListener;


/**
 * A component class that performs the scheduled task of stacking classifiers,
 * as well as serve as a cluster communication channel. This class can be considered
 * as the driving class for the Sentise system.
 */
@Service
@ConfigurationProperties
public class ModelCombinerServiceImpl implements MessageListener<Signal>, ModelCombinerService, Callable<RegressionModel> {

	private static final Logger log = LoggerFactory.getLogger(ModelCombinerServiceImpl.class);
	static final byte DUMP_MODEL_REQ = 0b00000001;
	static final byte DUMP_MODEL_RES = 0b00000011;
	
	@Value("${weka.classifier.combiner:VOTING}")
	private String combiner;
	@Value("${weka.classifier.combiner.options:}")
	private String combinerOpts;
	@Value("${weka.classifier.combiner.snapAwaitSecs:600}")
	private int snapshotAwaitSecs;
	
	@Autowired
	private ClassifierConfiguration config;
	
	private Map<String, AbstractIncrementalModelEngine> classifierBeans = new HashMap<>();
	private ConcurrentMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();
	
	@Autowired
	MessageContainerSupport msgContainer;
	
	@Autowired
	private TaskScheduler scheduler;
	@Autowired
	ApplicationContextWrapper ctxWrapper;
	
	private void registerListener(IncrementalModelEngine eng, String domain)
	{
		TrainingDataListener listener = new TrainingDataListener(eng);
		listener.setDomain(domain);
		msgContainer.registerListener(listener);
	}
	private void registerEngine(String domain)
	{
		@SuppressWarnings("static-access")
		AbstractIncrementalModelEngine eng = (AbstractIncrementalModelEngine) ctxWrapper.getInstance(ClassifierConfiguration.CACHED_INCR_CLASSIFIER_BEAN, domain);
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
				log.info(domain+"| Starting ensembling..");
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
	
	@Override
	public CombinerResult combineModel(String domain) throws EngineException {
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
		return id += "-"+domain;
	}
	private void save(TimeUIDMapData<RegressionModel> mapData, String domain)
	{
		String id = getPersistId(domain);
		hzService.set(id, mapData, ConfigUtil.WEKA_MODEL_PERSIST_MAP);
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
		RegressionModel ensemble = classifierBeans.get(domain).ensembleBuiltModels(models, CombinerType.valueOf(combiner), null);
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
		ISet<RegressionModel> modelSnapshots = hzService.hazelcastInstance().getSet(domain);
		if(modelSnapshots.isEmpty()){
			log.error(domain+"| snapshot collection is empty! will throw exception");
			throw new EngineException(domain+"| Model snapshots collected across cluster was empty!");
		}
		
		List<RegressionModel> models = new LinkedList<>();
		for (Iterator<RegressionModel> iterModel = modelSnapshots.iterator(); iterModel.hasNext();) 
		{
			RegressionModel model = iterModel.next();
			models.add(model);
		}
		try
		{
			String ensembleId = makeEnsemble(models, domain);
			return ensembleId;
		} 
		catch (EngineException e) {
			throw e;
		} 
		catch (DuplicateInstanceNameException e) {
			throw new EngineException(domain+"| Ignoring model already present in database", e);
		}
		finally
		{
			//clear the current snapshot
			modelSnapshots.clear();
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
		log.info("Model combiner initialized..");
	}

	private void notifyIfProcessing(String domain) {
		if(latches.containsKey(domain))
			latches.get(domain).countDown();
	}
	
	private void assertDomain(String domain)
	{
		Assert.isTrue(classifierBeans.containsKey(domain), "Invalid domain snapshot request received! "+domain);
	}

	private void dumpClassifierSnapshot(String domain) 
	{
		assertDomain(domain);
		RegressionModel model = classifierBeans.get(domain).generateModelSnapshot();
		log.debug(""+model.getTrainedClassifier());
		
		ISet<RegressionModel> modelSnapshots = hzService.hazelcastInstance().getSet(domain);
		boolean added = modelSnapshots.add(model);
		Assert.isTrue(!modelSnapshots.isEmpty(), domain+"| adding to ISet failed. isEmpty");
		
		if(added)
			log.info(domain+"| Dumped model successfully..");
		else
			log.warn(domain+"| * Model snapshot was not added *");
		
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
		if(buildNow)
		{
			try 
			{
				result = combineModel(domain);
				
			} catch (Exception e) {
				throw new EngineException(domain+"| Unable to execute a combine task", e);
			}
		}
		try 
		{
			RegressionModel model =  loadLast(domain);
			model.setCombineStatus(result);
			return model;
		} catch (Exception e) {
			throw new EngineException(domain+"| Unable to load latest saved model", e);
		}
	}
	
	private RegressionModel loadLast(String domain)
	{
		TimeUIDMapData<RegressionModel> md = load(domain);
		if(md != null && !md.isEmpty())
			return md.entrySet().iterator().next().getValue();
		
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

	
}