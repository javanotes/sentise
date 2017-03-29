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
package org.reactivetechnologies.analytics.sentise.files;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
/**
 * A file lock utility using {@linkplain FileLock}.
 * @author esutdal
 *
 */
public class ResourceLock implements Closeable{

	private FileChannel channel;
	private volatile boolean locked;
	@Override
	public void close() throws IOException {
		if (channel != null) {
			channel.close();
		}
	}
	/**
	 * Try to lock on a {@linkplain FileLock}, if not already locked by some other process/thread.
	 * @param dir
	 * @param lockFileName
	 * @throws IllegalAccessException
	 * @throws IOException
	 */
	@SuppressWarnings("resource")
	public synchronized void lock(File dir, String lockFileName) throws IllegalAccessException, IOException 
	{
		File lockFile = new File(dir, lockFileName);
		lockFile.createNewFile();
		lockFile.deleteOnExit();
		
		channel = new RandomAccessFile(lockFile, "rw").getChannel();
		if (channel.tryLock() == null) {
			channel.close();
			throw new IllegalAccessException();
		}
		
		String pid = ManagementFactory.getRuntimeMXBean().getName();
		if(pid.contains("@"))
		{
			pid = pid.substring(0, pid.indexOf("@"));
			channel.write(ByteBuffer.wrap(pid.getBytes(StandardCharsets.UTF_8)));
			channel.force(true);
		}
		
		setLocked(true);
	}

	public boolean isLocked() {
		return locked;
	}

	private void setLocked(boolean locked) {
		this.locked = locked;
	}

}
