/* ============================================================================
*
* FILE: CachedIncrementalClassifierBean.java
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
package org.reactivetechnologies.sentise.engine.weka;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.reactivetechnologies.sentise.dto.RegressionModel;
import org.reactivetechnologies.sentise.engine.weka.dto.WekaRegressionModel;
import org.reactivetechnologies.sentise.files.ResourceLock;
import org.reactivetechnologies.sentise.files.ResourceLockedException;
import org.reactivetechnologies.ticker.utils.CommonHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

import weka.classifiers.Classifier;
import weka.core.Instances;

/**
 * @author esutdal
 *
 */
public class CachedIncrementalClassifierBean extends IncrementalClassifierBean {

	@Value("${weka.classifier.cache.path:user.dir}")
	private String cacheFilePath;
	@Value("${weka.classifier.cache.sync.intervalSecs:60}")
	private long syncDelay;
	private static final Logger log = LoggerFactory.getLogger(CachedIncrementalClassifierBean.class);
	public static final String CACHED_FILE_NAME = "__Weka_.model";
	public static final String CACHE_SUBDIR = "_supervised";
	public static final String CACHE_SUBDIR2 = "_domains";
	public static final String LOCK_FILE = ".lock";
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1643930081471580071L;
	private ScheduledExecutorService thread;
	@Autowired
	CommonHelper utils;
	/**
	 * 
	 * @param c
	 * @param size
	 */
	public CachedIncrementalClassifierBean(Classifier c) {
		super(c);
	}

	@Override
	public void buildClassifier(Instances data) throws Exception {
		super.buildClassifier(data);
	}

	@Override
	public WekaRegressionModel generateModelSnapshot() {
		if (modelUpdated.compareAndSet(true, false)) {
			dumpModelSnapshot();
		}
		try {
			return loadModel(false);
		} catch (IOException e) {
			log.warn("Unable to load a cached model! Will fetch transient. Ignoring error => "+ e);
			return super.generateModelSnapshot();
		}
	}
	/**
	 * Dump the built model to some media. This is done to refer the cached
	 * instance for performance improvement. This method is synchronized as there is a write operation involved, 
	 * and the invocation can originate from client as well as the background thread. Also this method will only
	 * log any IOException caught. This is done so that the client invocations do not get any exception, since the
	 * process should not break on failure at this point.
	 */
	private synchronized void dumpModelSnapshot() 
	{
		RegressionModel model = super.generateModelSnapshot();
		try {
			byte[] b = utils.marshall(model);
			saveBytes(b);
		} catch (IOException e) {
			log.error(domain+"| While trying to save model", e);
		}
	}
	private ResourceLock fileLock;
	private void lockCacheArea(File f) throws IOException
	{
		fileLock = new ResourceLock(f, LOCK_FILE);
		try {
			fileLock.lock();
		} catch (IllegalAccessException e) {
			throw new ResourceLockedException(
					"Cache path already in use by another process. Use a different 'weka.classifier.cache.path', or delete .lock file under ../_domains/ if no other process is running.");
		}
		
	}
	private Path filePath;
	private void prepareFilePath() throws IOException
	{
		String path = System.getProperty(cacheFilePath);
		if(path == null)
			path = cacheFilePath;
		
		File f = Paths.get(path, CACHE_SUBDIR, CACHE_SUBDIR2, domain).toFile();
		if(!f.exists())
			f.mkdirs();
		
		Assert.isTrue(f.isDirectory(), "Not a valid directory- "+path);
		lockCacheArea(f);
		
		filePath = Paths.get(f.getAbsolutePath(), classifierAlgorithm()+CACHED_FILE_NAME);
		
		log.debug(domain+"| Cached file path => "+filePath);
	}
	/**
	 * Save the serialized model file. Can be overriden to provide a different caching media. This method
	 * is being invoked from a synchronized method.
	 * @param b
	 * @throws IOException
	 */
	protected void saveBytes(byte[] b) throws IOException
	{
		try (OutputStream wr = Files.newOutputStream(filePath, StandardOpenOption.SYNC,
				StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

			wr.write(b);
			wr.flush();
		}
	}
	/**
	 * Check if there is any cached content that can be loaded via {@link #loadBytes()}.
	 * @return
	 */
	protected boolean hasCachedContent()
	{
		File f = filePath.toFile();
		return f.exists() && f.length() > 0;
	}
	/**
	 * Load the serialized model from caching media.
	 * @return
	 * @throws IOException
	 */
	protected byte[] loadBytes() throws IOException
	{
		try (InputStream wr = Files.newInputStream(filePath, StandardOpenOption.READ)) {

			int len = wr.available();
			byte[] b;
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			while(len > 0)
			{
				b = new byte[len];
				wr.read(b);
				out.write(b);
				len = wr.available();
			}
			
			return out.toByteArray();
		}
	}
	/**
	 * The properties which will need to be sync'd from file system.
	 * @param model
	 */
	private void unmarshallBuildState(WekaRegressionModel model)
	{
		clazzifier = model.getTrainedClassifier();
		lastBuildAt = model.getGeneratedOn();
		attribsInitialized = model.isAttribsInitialized();
		structure = model.getStructure();
	}
	/**
	 * 
	 * @return
	 * @throws IOException
	 */
	private WekaRegressionModel loadModel(boolean syncBuildState) throws IOException
	{
		byte[] b = loadBytes();
		WekaRegressionModel model = new WekaRegressionModel();
		utils.unmarshall(b, model);
		if (syncBuildState) {
			unmarshallBuildState(model);
		}
		if (log.isDebugEnabled()) {
			log.debug("lastBuildAt " + lastBuildAt + "; attribsInitialized " + attribsInitialized);
			log.debug("structure " + structure);
			log.debug("Classifier structure:: attribs# " + structure.numAttributes() + " classes# "
					+ structure.numClasses());
		}
		return model;
	}
	
	private boolean loadSavedModel()
	{
		if(hasCachedContent())
		{
			try 
			{
				WekaRegressionModel model = loadModel(true);
				log.warn(domain+"| Detected cached classifier present. Last built on, "+new Date(model.getGeneratedOn())+". Any configured classifier will be overridden.");
				log.debug(model.toXmlString());
				return true;
			} catch (IOException e) {
				log.warn(domain+"| Corrupted cached file found on startup. This data is irrecoverable so ignoring.", e);
			}
		}
		return false;
	}
	private void initSyncThread()
	{
		thread = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
			
			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "CachedClassifSyncThread-"+domain);
				t.setDaemon(true);
				return t;
			}
		});
		thread.scheduleWithFixedDelay(new Runnable() {
			
			@Override
			public void run() {
				try 
				{
					if (modelUpdated.compareAndSet(true, false)) {
						dumpModelSnapshot();
						log.info(domain + "| File sync task ran..");
					}
					else
						log.debug(domain + "| No build detected for file sync");
				} 
				catch (Exception e) {
					log.error("Exception in file sync task", e);
				}
			}
		}, 0, syncDelay, TimeUnit.SECONDS);
	}
	@Override
	protected boolean onInitialization() 
	{
		boolean loaded = false;
		try {
			prepareFilePath();
		} catch (IOException e) {
			log.error("Unable to initialize engine!", e);
			throw new BeanCreationException("Unable to initialize engine!", e);
		}
		loaded = loadSavedModel();
		//start the build thread
		super.onInitialization();
		//start the file sync thread
		initSyncThread();
		return loaded;

	}
	
	@Override
	protected void onDestruction() {
		dumpModelSnapshot();
		log.info(domain+"| File sync task run on stop");
		try {
			fileLock.close();
		} catch (IOException e) {
			
		}
	}
}
