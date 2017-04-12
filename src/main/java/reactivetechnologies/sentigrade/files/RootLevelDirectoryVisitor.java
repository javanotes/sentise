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
package reactivetechnologies.sentigrade.files;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class RootLevelDirectoryVisitor
{
	private static final Logger log = LoggerFactory.getLogger(RootLevelDirectoryVisitor.class);
	private List<String> classes = new LinkedList<>();
	private final String directoryPath;
	private BlockingQueue<FileContent> queue;
	private Charset fileCharset;
	private AtomicInteger fileCount = new AtomicInteger();
	private boolean useBufferedReader;
	private boolean logFilename;
	/**
	 * 
	 * @param directoryPath
	 */
	public RootLevelDirectoryVisitor(String directoryPath) {
		super();
		this.directoryPath = directoryPath;
	}

	public List<String> getClasses() {
		return classes;
	}

	public void setClasses(List<String> classes) {
		this.classes = classes;
	}

	public String getDirectoryPath() {
		return directoryPath;
	}

	public Charset getFileCharset() {
		return fileCharset;
	}

	public void setFileCharset(Charset fileCharset) {
		this.fileCharset = fileCharset;
	}

	public AtomicInteger getFileCount() {
		return fileCount;
	}

	public void setFileCount(AtomicInteger fileCount) {
		this.fileCount = fileCount;
	}

	/**
	 * Visit the files at root level and read files with {@linkplain AsyncFileReader}, then process them with {@linkplain AsyncFileReadProcessor}.
	 * @param readerThreads reader threads, preferably 1
	 * @param processorThreads processor threads, preferably 1
	 * @param processor the processor
	 * @throws IOException
	 */
	public void doVisit(ExecutorService readerThreads, ExecutorService processorThreads, AsyncFileReadProcessor processor) throws IOException
	{
		this.queue = processor.getQueue();
		boolean processorAsync = false;
		if (processorThreads != null) {
			processorThreads.submit(processor);
			processorAsync = true;
		}
		if (classes != null && !classes.isEmpty()) {
			visitClassDirectories(readerThreads); 
		}
		else
		{
			visitAsRootDirectory(readerThreads);
		}
		
		if(!processorAsync)
			processor.run();
		
	}
	
	private void visitClassDirectories(ExecutorService readerThreads) throws IOException
	{
		for (String subdirPath : classes) {
			Path dirToVisit = Paths.get(directoryPath, subdirPath);
			walkDirectory(dirToVisit, readerThreads, subdirPath);

		}
	}
	private void visitAsRootDirectory(ExecutorService readerThreads) throws IOException
	{
		Path dirToVisit = Paths.get(directoryPath);
		walkDirectory(dirToVisit, readerThreads, "");
	}
	/**
	 * 
	 * @param dirToVisit
	 * @param reader
	 * @param subdirPath
	 * @throws IOException
	 */
	private void walkDirectory(Path dirToVisit, ExecutorService reader, String subdirPath) throws IOException
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
				if (logFilename) {
					log.info("File visited.. " + file.getFileName());
				}
				if(file.toFile().isDirectory())
					return FileVisitResult.CONTINUE;
				
				AsyncFileReader fileReader = useBufferedReader ? new BufferedAsyncFileReader(file, queue, subdirPath)
						: new AsyncFileReader(file, queue, subdirPath);
				if(fileCharset != null)
					fileReader.setCharset(fileCharset);
				
				if(k == -1)
					k = classes.indexOf(subdirPath);
				
				fileReader.setkIndex(k);
				if(useBufferedReader)
					((BufferedAsyncFileReader) fileReader).open();
				
				reader.submit(fileReader);
				fileCount.incrementAndGet();
				log.debug("processing " + fileCount + " : " + file);
				return FileVisitResult.CONTINUE;
			}

		});
		
	}

	public boolean isUseBufferedReader() {
		return useBufferedReader;
	}

	public void setUseBufferedReader(boolean useBufferedReader) {
		this.useBufferedReader = useBufferedReader;
	}

	public boolean isLogFilename() {
		return logFilename;
	}

	public void setLogFilename(boolean logFilename) {
		this.logFilename = logFilename;
	}
}