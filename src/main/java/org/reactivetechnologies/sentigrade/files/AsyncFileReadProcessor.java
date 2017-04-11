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
package org.reactivetechnologies.sentigrade.files;

import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AsyncFileReadProcessor implements Runnable {
	private final BlockingQueue<FileContent> queue;
	
	public BlockingQueue<FileContent> getQueue() {
		return queue;
	}

	private int dirCount;
	protected boolean isOutputFilename;
	protected static final Logger log = LoggerFactory.getLogger(AsyncFileReadProcessor.class);
	/**
	 * 
	 * @param queue
	 * @param data
	 * @param classIdx
	 */
	public AsyncFileReadProcessor(BlockingQueue<FileContent> queue, int dirCount) {
		super();
		this.queue = queue;
		this.dirCount = dirCount;
	}

	@Override
	public void run() {
		while (true) 
		{
			try 
			{
				FileContent fc = queue.take();
				log.debug("got content.."+fc.getContent());
				if (fc.isStop())
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

		log.info("Async file processing completed successfully");
	}
	/**
	 * 
	 * @param fc
	 */
	protected abstract void updateInstance(FileContent fc);

	public boolean isOutputFilename() {
		return isOutputFilename;
	}

	public void setOutputFilename(boolean isOutputFilename) {
		this.isOutputFilename = isOutputFilename;
	}

}