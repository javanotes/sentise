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

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.codec.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import weka.core.DenseInstance;
import weka.core.Instances;
import weka.core.SerializedObject;
import weka.core.converters.TextDirectoryLoader;
/**
 * An extension of {@linkplain TextDirectoryLoader} with NIO operations and multithreaded
 * implementation of {@link #getDataSet()}.
 * @author esutdal
 *
 */
class ConcurrentTextDirectoryLoader extends TextDirectoryLoader {

	private static final Logger log = LoggerFactory.getLogger(ConcurrentTextDirectoryLoader.class);

	private static class FileContent {
		public FileContent() {

		}
		/**
		 * 
		 * @param content
		 * @param fileName
		 * @param subdirPath
		 */
		public FileContent(String content, String fileName, String subdirPath) {
			super();
			this.content = content;
			this.fileName = fileName;
			this.subdirPath = subdirPath;
		}

		private String fileName;
		private String content;
		private boolean stop;
		private String subdirPath;
		private int kIndex;
	}

	private class InstanceProcessor implements Runnable {
		private BlockingQueue<FileContent> queue;
		private Instances data;
		private AtomicInteger classIdx;
		private int dirCount;
		/**
		 * 
		 * @param queue
		 * @param data
		 * @param classIdx
		 */
		public InstanceProcessor(BlockingQueue<FileContent> queue, Instances data,	AtomicInteger classIdx, int dirCount) {
			super();
			this.queue = queue;
			this.data = data;
			this.classIdx = classIdx;
			this.dirCount = dirCount;
		}

		@Override
		public void run() {
			while (true) {
				try 
				{
					FileContent fc = queue.take();
					log.debug("got content.."+fc.content);
					if (fc.stop)
					{
						log.debug("stop signalled at count="+dirCount);
						if(--dirCount == 0)
							break;
					}
					else
						updateInstance(fc);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				catch (Exception e) {
					log.error("Exception in processor thread", e);
				}
			}

			log.info("Processing terminated successfully..");
		}

		private void updateInstance(FileContent fc) {
			double[] newInst = null;
			if (m_OutputFilename) {
				newInst = new double[3];
			} else {
				newInst = new double[2];
			}

			newInst[0] = data.attribute(0).addStringValue(fc.content);
			if (m_OutputFilename) {
				newInst[1] = data.attribute(1).addStringValue(fc.subdirPath + File.separator + fc.fileName);
			}

			classIdx.getAndIncrement();
			newInst[data.classIndex()] = fc.kIndex;
			data.add(new DenseInstance(1.0, newInst));
		}

	}

	
	private class AsyncFileReader implements Runnable {
		private Path txtPath;
		private AtomicInteger fileCount;
		private BlockingQueue<FileContent> queue;
		private String subdirPath;
		final static int BLOCK = 8092;
		/**
		 * 
		 * @param txtPath
		 * @param queue
		 * @param fileCount
		 * @param subdirPath
		 */
		public AsyncFileReader(Path txtPath, BlockingQueue<FileContent> queue, AtomicInteger fileCount,
				String subdirPath) {
			super();
			this.txtPath = txtPath;
			this.fileCount = fileCount;
			this.queue = queue;
			this.subdirPath = subdirPath;
		}
		private boolean stopSignal = false;
		private int kIndex;
		public AsyncFileReader(boolean stopSignal, BlockingQueue<FileContent> queue) {
			this.stopSignal = stopSignal;
			this.queue = queue;
		}

		@Override
		public void run() {

			if(stopSignal)
			{
				FileContent fc = new FileContent();
				fc.stop = true;
				try {
					queue.put(fc);
					log.debug("offered stop signal...");
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				
				return;
			}
			
			fileCount.incrementAndGet();
			log.debug("processing " + fileCount + " : " + txtPath);

			StringBuilder txtStr = new StringBuilder();
			boolean err = false;

			try (BufferedInputStream is = new BufferedInputStream(Files.newInputStream(txtPath, StandardOpenOption.READ))) {

				byte[] b;
				int read = -1;
				while (true) {
					b = new byte[BLOCK];
					read = is.read(b);
					if(read == -1)
						break;
					
					b = Arrays.copyOfRange(b, 0, read);
					txtStr.append((StringUtils.hasText(m_charSet) ? new String(b, Charsets.toCharset(m_charSet)) : new String(b)));
				}
				
			} 
			catch (IOException e) {
				log.error("Error while trying to read text file " + txtPath, e);
				err = true;
			}
			if (!err) {
				try {
					FileContent fc = new FileContent(txtStr.toString(), txtPath.getFileName().toString(), subdirPath);
					fc.kIndex = this.kIndex;
					queue.put(fc);
					log.debug("submitted text- "+fc.content);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			}

		}

	}
	/**
	 * No of file reader threads to be used for each invocation to {@linkplain TextDirectoryLoader#getDataSet()}.
	 * There will be another single processor thread as well.
	 */
	private int readerThreads = Runtime.getRuntime().availableProcessors();

	/**
	 * 
	 */
	private static final long serialVersionUID = -223325297095026130L;
	
	private List<String> getClassDirs() throws IOException
	{
		final List<String> classes = new LinkedList<String>();
		
		Enumeration<Object> enm = getStructure().classAttribute().enumerateValues();
		while (enm.hasMoreElements()) {
			Object oo = enm.nextElement();
			if (oo instanceof SerializedObject) {
				classes.add(((SerializedObject) oo).getObject().toString());
			} else {
				classes.add(oo.toString());
			}
		}
		return classes;
	}

	private String getDirPath() throws IOException
	{
		if (getDirectory() == null) {
			throw new IOException("No directory/source has been specified");
		}

		return getDirectory().getAbsolutePath();
	}
	/**
	 * 
	 * @param dirToVisit
	 * @param reader
	 * @param queue
	 * @param fileCount
	 * @param classes
	 * @param subdirPath
	 * @throws IOException
	 */
	private void visitClassDirectory(Path dirToVisit, ExecutorService reader, BlockingQueue<FileContent> queue, AtomicInteger fileCount, final List<String> classes, String subdirPath) throws IOException
	{
		Files.walkFileTree(dirToVisit, EnumSet.noneOf(FileVisitOption.class), 1, new SimpleFileVisitor<Path>() {

			int k = -1;
			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException ex) throws IOException {
				log.info("End visit dir..."+dir.getFileName());
				reader.submit(new AsyncFileReader(true, queue));
				return FileVisitResult.TERMINATE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				log.debug("visit file.."+file.getFileName());
				if(file.toFile().isDirectory())
					return FileVisitResult.CONTINUE;
				
				AsyncFileReader ar = new AsyncFileReader(file, queue, fileCount, subdirPath);
				if(k == -1)
					k = classes.indexOf(subdirPath);
				
				ar.kIndex = k;
				reader.submit(ar);
				return FileVisitResult.CONTINUE;
			}

		});
	}
	/**
	 * Visit class directories using a {@linkplain Files#wa}
	 * @param reader
	 * @param processor
	 * @param classes
	 * @param fileCount
	 * @param classIdx
	 * @return 
	 * @throws IOException
	 */
	private Instances visitClassDirectories(ExecutorService reader, ExecutorService processor, AtomicInteger fileCount, AtomicInteger classIdx) throws IOException
	{
		final Instances data = getStructure();
		final List<String> classes = getClassDirs();
		final BlockingQueue<FileContent> queue = new LinkedBlockingQueue<>();
		final String directoryPath = getDirPath();
		boolean processorAsync = false;
		if (processor != null) {
			processor.submit(new InstanceProcessor(queue, data, classIdx, classes.size()));
			processorAsync = true;
		}
		for (String subdirPath : classes) 
		{
			Path dirToVisit = Paths.get(directoryPath, subdirPath);
			visitClassDirectory(dirToVisit, reader, queue, fileCount, classes, subdirPath);
			
		}
		
		if(!processorAsync)
			new InstanceProcessor(queue, data, classIdx, classes.size()).run();
		
		return data;
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
		
		log.info("Starting executors with "+readerThreads+" reader threads "+(useProcessorThread ? "and 1 processor thread" : ""));
		final Instances data = visitClassDirectories(reader, processor, fileCount, classIdx);
		
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
						"Files read: " + fileCount + ", but instance processed: " + classIdx+". This warning can be skipped by setting ignoreResultCount=true");
			} catch (IllegalStateException e) {
				log.warn(e.getMessage());
			} 
		}
		log.info("Total files processed: "+fileCount);
		log.info("Total instances processed: "+classIdx);
		
		return data;
	}
	
	@Override
	public Instances getDataSet() throws IOException {
		
		ExecutorService reader = Executors.newFixedThreadPool(readerThreads);
		final Instances data = getDataSet(reader, true);
			
		reader.shutdown();
		try {
			reader.awaitTermination(maxAwaitDuration, maxAwaitUnit);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		
		return data;
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
