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

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 
 * @author esutdal
 *
 */
public class AsyncFileReader implements Runnable, Closeable {
	private static final Logger log = LoggerFactory.getLogger(AsyncFileReader.class);
	protected Path txtPath;
	protected final BlockingQueue<FileContent> queue;
	protected String subdirPath;
	private Charset charset = StandardCharsets.UTF_8;
	public final static int BLOCK = 8092;
	/**
	 * New asynchronous file read constructor.
	 * @param txtPath
	 * @param queue
	 * @param fileCount
	 * @param subdirPath
	 */
	public AsyncFileReader(Path txtPath, BlockingQueue<FileContent> queue, String subdirPath) {
		super();
		this.txtPath = txtPath;
		this.queue = queue;
		this.subdirPath = subdirPath;
	}
	protected boolean stopSignal = false;
	private int kIndex;
	/**
	 * Stop signal.
	 * @param stopSignal
	 * @param queue
	 */
	public AsyncFileReader(boolean stopSignal, BlockingQueue<FileContent> queue) {
		this.stopSignal = stopSignal;
		this.queue = queue;
	}

	private void poisonPill()
	{
		FileContent fc = new FileContent();
		fc.setStop(true);
		try {
			queue.put(fc);
			log.debug("offered stop signal...");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			e.printStackTrace();
		}
	}
	private void submit(String txtStr) throws InterruptedException
	{
		FileContent fc = new FileContent(txtStr, txtPath.getFileName().toString(), subdirPath);
		fc.setkIndex(this.getkIndex());
		queue.put(fc);
		log.debug("submitted text- "+fc.getContent());
		
	}
	/**
	 * By default this will read the full content of file. Override if this behavior is not intended.
	 * @return
	 * @throws IOException
	 */
	protected StringBuilder read() throws IOException
	{
		StringBuilder txtStr = new StringBuilder();

		try (BufferedInputStream is = new BufferedInputStream(Files.newInputStream(txtPath, StandardOpenOption.READ))) {

			byte[] b;
			int read = -1;
			while (true) {
				b = new byte[BLOCK];
				read = is.read(b);
				if(read == -1)
					break;
				
				b = Arrays.copyOfRange(b, 0, read);
				txtStr.append(new String(b, charset));
			}
			
		}
		
		return txtStr;
	}
	/**
	 * If there is still more to be read. Override for large text file reading in chunks.
	 * @return
	 */
	protected boolean hasMore()
	{
		return false;//read content fully
	}
	/**
	 * Can be overriden by subclasses
	 * @param content
	 */
	protected void handleReadContent(String content)
	{
		try {
			submit(content);
		} catch (InterruptedException e) {
			e.printStackTrace();
			Thread.currentThread().interrupt();
		}
	}
	@Override
	public void run() {

		if(stopSignal)
		{
			poisonPill();
			return;
		}
		
		StringBuilder textContent = null;

		do 
		{
			try 
			{
				textContent = read();
				handleReadContent(textContent.toString());
			} 
			catch (IOException e) {
				log.error("Error while trying to read text file " + txtPath, e);
			}
			
		} while (hasMore());

		try {
			close();
		} catch (IOException e) {
			
		}
	}

	public Charset getCharset() {
		return charset;
	}

	public void setCharset(Charset charset) {
		this.charset = charset;
	}

	public int getkIndex() {
		return kIndex;
	}

	public void setkIndex(int kIndex) {
		this.kIndex = kIndex;
	}

	@Override
	public void close() throws IOException {
		// noop
		
	}

}