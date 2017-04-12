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
package reactivetechnologies.sentigrade.engine.weka.handlers;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import reactivetechnologies.sentigrade.dto.VectorRequestData;
import reactivetechnologies.sentigrade.files.RootLevelDirectoryVisitor;
import weka.core.Attribute;
import weka.core.Instances;
import weka.core.converters.TextDirectoryLoader;
/**
 * An extension of {@linkplain TextDirectoryLoader} with NIO operations and multithreaded
 * implementation of {@link #getDataSet()}.
 * @author esutdal
 *
 */
class ConcurrentTextDirectoryLoader extends TextDirectoryLoader {

	static final Logger log = LoggerFactory.getLogger(ConcurrentTextDirectoryLoader.class);

	/**
	 * No of file reader threads to be used for each invocation to {@linkplain TextDirectoryLoader#getDataSet()}.
	 * There will be another single processor thread as well.
	 */
	private int readerThreads = Runtime.getRuntime().availableProcessors();

	/**
	 * 
	 */
	private static final long serialVersionUID = -223325297095026130L;
	
	private String getDirPath() throws IOException
	{
		if (getDirectory() == null) {
			throw new IOException("No directory/source has been specified");
		}

		return getDirectory().getAbsolutePath();
	}
	
	private List<String> getClassDirs() throws IOException {
		return VectorRequestData.classAttrNominals(getStructure());
	}
	
	private void visitWekaDirectory(ExecutorService reader, ExecutorService processor, AtomicInteger classIdx, AtomicInteger fileCount) throws IOException
	{
		RootLevelDirectoryVisitor visitor = new RootLevelDirectoryVisitor(getDirPath());
		visitor.setClasses(getClassDirs());
		if (StringUtils.hasText(m_charSet)) {
			visitor.setFileCharset(Charset.forName(m_charSet));
		}
		visitor.setFileCount(fileCount);
		visitor.doVisit(reader, processor, new WekaFormatDirectoryLevelProcessor(new LinkedBlockingQueue<>(),
				getStructure(), classIdx, getClassDirs().size()));

	}
	
	/**
	 * Overridden version of {@link #getDataSet()}, to use a thread pool for reading training files concurrently.
	 * @param reader
	 * @param useProcessorThread
	 * @return
	 * @throws IOException
	 */
	public Instances getDataSet(ExecutorService reader, boolean useProcessorThread) throws IOException {

		final AtomicInteger fileCount = new AtomicInteger();
		final AtomicInteger classIdx = new AtomicInteger();
		
		ExecutorService processor = null;
		if (useProcessorThread) {
			processor = Executors.newSingleThreadExecutor();
		}
		log.info("WekaFormat| Starting executors with "+readerThreads+" reader threads "+(useProcessorThread ? "and 1 processor thread" : ""));
		
		visitWekaDirectory(reader, processor, classIdx, fileCount);
		
		if (useProcessorThread) {
			processor.shutdown();
			try {
				processor.awaitTermination(maxAwaitDuration, maxAwaitUnit);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} 
		}
		if (!ignoreResultCount) {
			try {
				Assert.state(fileCount.get() == classIdx.get(),
						"Files read " + fileCount + ", but instance processed " + classIdx+"! This warning can be skipped by setting ignoreResultCount=true");
			} catch (IllegalStateException e) {
				log.warn(e.getMessage());
			} 
		}
		log.info("Total files processed: "+fileCount);
		log.info("Total instances processed: "+classIdx);
		
		return getStructure();
		//return data;
	}
	/**
	 * Uses a single reader thread to avoid concurrency issues in missing data file.
	 */
	@Override
	public Instances getDataSet() throws IOException {
		
		ExecutorService reader = Executors.newSingleThreadExecutor();
		readerThreads = 1;
		final Instances data = getDataSet(reader, true);
			
		reader.shutdown();
		try {
			reader.awaitTermination(maxAwaitDuration, maxAwaitUnit);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		
		return data;
	}
	/**
	 * Overridden version of {@link #getDataSet()}. Get tabbed line dataset, of the format [sentence score]. Score is either 1 (for positive) or 0 (for negative) in default case.
	 * @param scoreMap
	 * @return
	 * @throws IOException
	 */
	public Instances getDataSet(Map<Integer, String> scoreMap, boolean isClassFirst) throws IOException {
		
		ExecutorService reader = Executors.newSingleThreadExecutor();
		readerThreads = 1;
		
		final AtomicInteger fileCount = new AtomicInteger();
		final AtomicInteger classIdx = new AtomicInteger();
		
		ExecutorService processor = Executors.newSingleThreadExecutor();
		
		log.info("TabbedFormat starting executors with "+readerThreads+" reader threads and 1 processor thread");
		
		final Instances structure = getStructure(scoreMap, isClassFirst);
		
		//visit using buffered reader dir only
		RootLevelDirectoryVisitor visitor = new RootLevelDirectoryVisitor(getDirPath());
		visitor.setFileCount(fileCount);
		TabbedFormatDirectoryLevelProcessor proc = new TabbedFormatDirectoryLevelProcessor(new LinkedBlockingQueue<>(),
				structure, classIdx);
		proc.setScoreMap(scoreMap);
		proc.setClassAtFirst(isClassFirst);
		
		visitor.setUseBufferedReader(true);
		visitor.setLogFilename(true);
		visitor.doVisit(reader, processor, proc);
		
		processor.shutdown();
		try {
			processor.awaitTermination(maxAwaitDuration, maxAwaitUnit);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} 
		
		log.info("Total files processed: "+fileCount);
		log.info("Total instances processed: "+classIdx);
		
			
		reader.shutdown();
		try {
			reader.awaitTermination(maxAwaitDuration, maxAwaitUnit);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		
		return structure;
	}
	
	private Instances getStructure(Map<Integer, String> scoreMap, boolean isClassFirst) throws IOException
	{
		ArrayList<Attribute> atts = new ArrayList<Attribute>(2);
		atts.add(null);
		atts.add(null);
		Attribute text = new Attribute("text", (ArrayList<String>) null, isClassFirst ? 1 : 0);
		

		// make sure that the name of the class attribute is unlikely to
		// clash with any attribute created via the StringToWordVector filter
		Attribute cls = new Attribute("@@class@@", new ArrayList<>(scoreMap.values()), isClassFirst ? 0 : 1);
		
		atts.set(cls.index(), cls);
		atts.set(text.index(), text);

		String relName = getDirectory().getName().replaceAll("/", "_");
		relName = relName.replaceAll("\\\\", "_").replaceAll(":", "_");
		Instances ins = new Instances(relName, atts, 0);
		ins.setClass(cls);
		
		return ins;
		
	}
	private boolean ignoreResultCount;
	private long maxAwaitDuration = 1;
	private TimeUnit maxAwaitUnit = TimeUnit.HOURS;

	public int getReaderThreads() {
		return readerThreads;
	}

	public void setReaderThreads(int readerThreads) {
		this.readerThreads = readerThreads;
	}

	public long getMaxAwaitDuration() {
		return maxAwaitDuration;
	}

	public void setMaxAwaitDuration(long maxAwaitDuration) {
		this.maxAwaitDuration = maxAwaitDuration;
	}

	public TimeUnit getMaxAwaitUnit() {
		return maxAwaitUnit;
	}

	public void setMaxAwaitUnit(TimeUnit maxAwaitUnit) {
		this.maxAwaitUnit = maxAwaitUnit;
	}

	public boolean isIgnoreResultCount() {
		return ignoreResultCount;
	}

	public void setIgnoreResultCount(boolean ignoreResultCount) {
		this.ignoreResultCount = ignoreResultCount;
	}

}
