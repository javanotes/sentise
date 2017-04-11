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
package org.reactivetechnologies.sentigrade.weka.handlers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.reactivetechnologies.sentigrade.dto.VectorRequestData;
import org.reactivetechnologies.sentigrade.dto.VectorRequestDataFactoryBean;
import org.reactivetechnologies.sentigrade.engine.IncrementalModelEngine;
import org.reactivetechnologies.sentigrade.engine.weka.AbstractIncrementalModelEngine;
import org.reactivetechnologies.sentigrade.files.DirectoryEventHandler;
import org.reactivetechnologies.sentigrade.files.DirectoryWatcher;
import org.reactivetechnologies.sentigrade.services.ModelExecutionService;
import org.reactivetechnologies.sentigrade.utils.ConfigUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import weka.core.Instances;
@Service
public class TrainingDirectoryLoaderHandler implements DirectoryEventHandler {

	private static final Logger log = LoggerFactory.getLogger(TrainingDirectoryLoaderHandler.class);
	public static final String TRIGGER_FILE_EXTN = "train";
	public static final String TRIGGER_FILE_PROC_EXTN = "proc";
	
	private DirectoryWatcher watchService;
	
	@Value("${weka.classifier.train.dirPath}")
	private String path;
	@Value("${weka.classifier.train.jobq.len:100}")
	private int queueLen;
	@Autowired
	private ModelExecutionService classifService;
	private ConcurrentLinkedQueue<File> backLog = new ConcurrentLinkedQueue<>();
	private boolean keepRunning;
	private ExecutorService workers, dirReaderPool;
	@Value("${weka.classifier.train.jobq.threads:4}")
	private int nThreads;
	@Value("${weka.classifier.train.fileio.threads:0}")
	private int readerThreads;
	@Value("${weka.classifier.train.fileio.maxAwaitMins:10}")
	private long maxAwait;
	@Autowired
	private VectorRequestDataFactoryBean dataFactory;
	/**
	 * This is an internal API. Kept public just for a quick testing purpose.
	 * Submit a new training dataset.
	 * @param i
	 * @throws Exception 
	 */
	public void submitTrainingData(Instances i, String domain) throws Exception
	{
		VectorRequestData data = dataFactory.getObject();
		data.setTextInstances(i, IncrementalModelEngine.getDomain(domain));
		classifService.buildClassifier(data);
	}
	private class LoaderTask implements Runnable
	{
		private ConcurrentTextDirectoryLoader newLoader(String dir) throws FileNotFoundException, IOException
		{
			ConcurrentTextDirectoryLoader loader = new ConcurrentTextDirectoryLoader();
			loader.setMaxAwaitDuration(maxAwait);
			loader.setMaxAwaitUnit(TimeUnit.MINUTES);
			loader.setReaderThreads(readerThreads > 0 ? readerThreads : Runtime.getRuntime().availableProcessors());
			loader.setDirectory(ConfigUtil.resolvePath(dir));
			
			return loader;
		}
		private void loadAsWekaDirLoader(String domain, String dir) throws Exception
		{
			ConcurrentTextDirectoryLoader loader = newLoader(dir);
			//Instances ins = loader.getDataSet(dirReaderPool, true);
			Instances ins = loader.getDataSet();//single reader thread
			
			submitTrainingData(ins, domain);
		}
		private void executeLoad(File f) 
		{
			try 
			{
				TriggerFile trigger = readTriggerFile(f.toPath());
				String dir = trigger.targetDir;
				String domain = trigger.domain;
				
				log.info("Begin training data loading ..");
				log.info(domain+"| Root dir -> "+dir);
				Path p = ConfigUtil.renameFileExtn(f, TRIGGER_FILE_PROC_EXTN);
				
				if (trigger.isTabbedLineDataset) {
					loadAsTabDelimitedText(domain, dir, trigger.tabFormat);
				}
				else
					loadAsWekaDirLoader(domain, dir);
				
				Files.delete(p);
				log.info("End data loading process");
			} 
			catch (IOException e) {
				log.error("Unable to execute load or read trigger file", e);
			} catch (Exception e) {
				log.error("Unable to build classifier with read instances ", e);
			}
			
		}
		/**
		 * @throws Exception 
		 * Tabbed format as '[text] \t [class]'
		 * @param domain
		 * @param dir
		 * @param tabFormat 
		 * @throws IOException 
		 * @throws  
		 */
		private void loadAsTabDelimitedText(String domain, String dir, String tabFormat) throws Exception {
			// default binary class neg,pos
			ConcurrentTextDirectoryLoader loader = newLoader(dir);
			boolean clsAtFirst;
			Map<Integer, String> scoreMap = new TreeMap<>();
			String [] split = tabFormat.split(" ");
			clsAtFirst = split.length >= 1 && split[0].toUpperCase().startsWith("SCORE");
			if(split.length >= 3)
			{
				String[] map = split[2].split(";"); //score map
				String[] k;
				for(String e: map)
				{
					k = e.split("=");
					if(k.length == 2)
					{
						try {
							scoreMap.put(Integer.valueOf(k[0]), k[1]);
						} catch (NumberFormatException e1) {
							
						}
					}
				}
			}
			
			if(scoreMap.isEmpty())
			{
				scoreMap.put(0, "neg");
				scoreMap.put(1, "pos");
			}
			log.info("Start processing tabbed line texts, with classAtFirst?"+clsAtFirst+" and score map "+scoreMap);
			Instances dataset = loader.getDataSet(scoreMap, clsAtFirst);
			
			submitTrainingData(dataset, domain);
		}
		@Override
		public void run() {
			log.debug("New loader worker started..");
			while(keepRunning)
			{
				try 
				{
					File f = requests.poll(1, TimeUnit.SECONDS);
					if(f == null)
					{
						f = backLog.poll();
						if(f != null)
						{
							executeLoad(f);
						}
					}
					else
					{
						executeLoad(f);
					}
				} 
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				catch(Exception e)
				{
					log.error("Unexpected error in loader thread", e);
				}
			}
			
		}
		
	}
	
	@PostConstruct
	private void init() throws FileNotFoundException
	{
		keepRunning = true;
		requests = new ArrayBlockingQueue<>(queueLen);
		workers = Executors.newFixedThreadPool(nThreads, new ThreadFactory() {
			int n = 0;
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "DirLoaderWorker-"+(n++));
				return t;
			}
		});
		for (int i = 0; i < nThreads; i++) {
			workers.submit(new LoaderTask());
		}
		dirReaderPool = Executors.newFixedThreadPool(readerThreads > 0 ? readerThreads : Runtime.getRuntime().availableProcessors(), new ThreadFactory() {
			int n = 0;
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "FileReaderWorker-"+(n++));
				return t;
			}
		});
		
		watchService = new DirectoryWatcher(ConfigUtil.resolvePath(path).toPath(), this);
		watchService.start();
	}
	/**
	 * Will read the first line only of the trigger file. Thus it is important to manage the newline char feeds
	 * correctly while writing text to the trigger file.
	 * @param p
	 * @return
	 * @throws IOException
	 */
	private TriggerFile readTriggerFile(Path p) throws IOException
	{
		List<String> lines = Files.readAllLines(p);
		if(lines.isEmpty())
			throw new IOException("trigger file content is empty");
		
		TriggerFile tf = new TriggerFile();
		tf.targetDir = lines.get(0);
		tf.domain = (lines.size() >= 2 && StringUtils.hasText(lines.get(1))) ? lines.get(1) : AbstractIncrementalModelEngine.DEFAULT_CLASSIFICATION_QUEUE;
		if(lines.size() >= 3)
		{
			tf.isTabbedLineDataset = Boolean.valueOf(lines.get(2));
		}
		if(lines.size() >= 4)
		{
			tf.tabFormat = lines.get(3);
		}
		return tf;
		
	}
	
	private static class TriggerFile
	{
		String targetDir;
		String domain;
		boolean isTabbedLineDataset;
		String tabFormat;
	}
	
	@PreDestroy
	private void destroy()
	{
		keepRunning = false;
		watchService.stop();
		workers.shutdown();
		try {
			log.info("stopping worker threads..");
			workers.awaitTermination(10, TimeUnit.MINUTES);
		} catch (InterruptedException e) {
			
		}
		dirReaderPool.shutdown();
		try {
			dirReaderPool.awaitTermination(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			
		}
	}
	private BlockingQueue<File> requests;
	@Override
	public void onFileTouched(File f) 
	{
		String fExtn = StringUtils.getFilenameExtension(f.getName());
		if(TRIGGER_FILE_EXTN.equals(fExtn))
		{
			offer(f);
		}
		else
		{
			if (log.isDebugEnabled()) {
				log.debug("Ignoring file " + f + ". Expecting the trigger file to have a '" + TRIGGER_FILE_EXTN
						+ "' extension");
			}
		}

	}

	private void offer(File f)
	{
		try 
		{
			boolean offrd = requests.offer(f, 1, TimeUnit.SECONDS);
			if(!offrd)
			{
				backLog.offer(f);
				log.warn("Job pushed to backlog.."+f);
			}
			
		}  catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		
	}
	@Override
	public void handleInitialFiles(Set<File> initialFiles) {
		if (!initialFiles.isEmpty()) 
		{
			log.info("Loading initial files on startup..");
			for (File f : initialFiles) {
				String fExtn = StringUtils.getFilenameExtension(f.getName());
				if(TRIGGER_FILE_EXTN.equals(fExtn))
				{
					offer(f);
				}
				else if(TRIGGER_FILE_PROC_EXTN.equals(fExtn))
				{
					log.info("Detected an in process trigger, processing it again. This should probably be okay");
					offer(f);
				}
				else
				{
					log.debug("Ignoring file "+f+". ");
				}
			}
		}
		else
			log.info("No pending files found for loading");
		
	}

}
