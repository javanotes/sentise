/* ============================================================================
*
* FILE: JarModuleLoader.java
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
package reactivetechnologies.sentigrade.files;

import java.io.File;
import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanInitializationException;

import reactivetechnologies.sentigrade.engine.weka.CachedIncrementalClassifierBean;

/**
 * {@linkplain WatchService} utility.
 */
public class DirectoryWatcher implements Runnable {

	private static final Logger log = LoggerFactory.getLogger(DirectoryWatcher.class);

	private final Path root;
	/**
	 * 
	 * @param path
	 * @param handler
	 */
	public DirectoryWatcher(Path path, DirectoryEventHandler handler) {
		root = path;
		this.handler = handler;
	}

	private WatchService watcher;
	private ResourceLock fileLock;
	
	private void acquireWatchArea() throws IllegalAccessException, IOException
	{
		fileLock = new ResourceLock(root.toFile(), CachedIncrementalClassifierBean.LOCK_FILE);
		fileLock.lock();
	}
	
	private void initialize() throws IllegalAccessException, IOException {
		
		acquireWatchArea();
		try 
		{
			watcher = root.getFileSystem().newWatchService();
		} catch (Exception e) {
			throw new BeanInitializationException("Unable to register file watcher", e);
		}
		setInitialFiles(walkDirectory(root));
		handler.handleInitialFiles(getInitialFiles());
		log.info("Registered trigger file listener on dir [" + root+"]");
	}

	/**
	 * 
	 */
	public void start()
	{
		try 
		{
			initialize();
			new Thread(this, "DirectoryWatcher").start();
		} 
		catch (IllegalAccessException e) {
			log.warn("Trigger directory is already being watched by another process. Skipping for this instance!");
		} catch (IOException e) {
			log.error("Exception while trying to acquire lock. Watch service is being skipped!", e);
		}
		
	}
	private Set<File> initialFiles = new HashSet<>();

	private void registerWatch(Path dir) throws IOException {
		if (log.isDebugEnabled()) {
			log.debug("registering: " + dir + " for file events");
		}
		dir.register(watcher, StandardWatchEventKinds.ENTRY_CREATE/*, StandardWatchEventKinds.ENTRY_MODIFY*/);
	}

	private Set<File> walkDirectory(Path directory) {
		final Set<File> walkedFiles = new LinkedHashSet<File>();
		try 
		{
			registerWatch(directory);
			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
					FileVisitResult fileVisitResult = super.preVisitDirectory(dir, attrs);
					registerWatch(dir);
					return fileVisitResult;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					FileVisitResult fileVisitResult = super.visitFile(file, attrs);
					walkedFiles.add(file.toFile());
					return fileVisitResult;
				}

			});
		} 
		catch (IOException e) {
			log.error("Failed to walk directory: " + directory.toString(), e);
		}
		return walkedFiles;
	}

	private final DirectoryEventHandler handler;
	private volatile boolean stopRequested;

	private void fireOnFileTouched(File f) {
		try {
			handler.onFileTouched(f);
			log.info("Loaded file: " + f.getName());
		} catch (Exception e) {
			log.error("Unable to load file", e);
		}
	}

	@Override
	public void run() {
		while (!stopRequested) 
		{
			try {
				Set<File> files = filesFromEvents();
				for (File f : files) {
					fireOnFileTouched(f);
				}
			} catch (ClosedWatchServiceException e) {
				if (!stopRequested)
					log.error("Watch service closed unexpectedly!", e);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}

		}

	}

	/**
	 * 
	 */
	public void stop() {
		stopRequested = true;
		if (watcher != null) {
			try {
				watcher.close();
			} catch (IOException e) {
				log.debug(e.getMessage());
			} 
		}
		if(fileLock != null)
			try {
				fileLock.close();
			} catch (IOException e) {
				
			}
	}

	private Set<File> filesFromEvents() throws InterruptedException {
		WatchKey key = watcher.take();
		Set<File> files = new LinkedHashSet<File>();
		if (key != null && key.isValid()) {
			for (WatchEvent<?> event : key.pollEvents()) {
				if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
						|| event.kind() == StandardWatchEventKinds.ENTRY_MODIFY) {
					Path item = (Path) event.context();
					File file = new File(
							((Path) key.watchable()).toAbsolutePath() + File.separator + item.getFileName());
					if (log.isDebugEnabled()) {
						log.debug("Watch Event: " + event.kind() + ": " + file);
					}
					files.add(file);
				}

			}
			key.reset();

		}
		return files;
	}

	public Set<File> getInitialFiles() {
		return initialFiles;
	}

	private void setInitialFiles(Set<File> initialFiles) {
		this.initialFiles.addAll(initialFiles);
	}

}
