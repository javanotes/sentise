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
package org.reactivetechnologies.sentise.files;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import org.springframework.boot.ApplicationPid;
/**
 * A file lock utility using {@linkplain FileLock}.
 * @author esutdal
 *
 */
public class ResourceLock implements Closeable{

	private FileChannel channel;
	private volatile boolean locked;
	private final File lockFile;
	@Override
	public void close() throws IOException {
		if (channel != null) {
			channel.close();
		}
		lockFile.delete();
	}
	/**
	 * New instance for lock to be created in 'dir' with the given name.
	 * @param dir
	 * @param lockFileName
	 */
	public ResourceLock(File dir, String lockFileName) {
		lockFile = new File(dir, lockFileName);
	}
	/**
	 * Try to lock on a {@linkplain FileLock}, if not already locked by some other process/thread.
	 * @throws IllegalAccessException if unable to acquire lock
	 * @throws IOException if IO exception occurs
	 */
	public synchronized void lock() throws IllegalAccessException, IOException 
	{
		if(!lockFile.createNewFile())
		{
			boolean alive = checkIfProcessAlive(lockFile);
			if(alive)
				throw new IllegalAccessException();
		}
		lockFile.deleteOnExit();
		lockFile(lockFile);
		
		setLocked(true);
	}
	
	@SuppressWarnings("resource")
	private void lockFile(File lockFile) throws IOException, IllegalAccessException
	{
		channel = new RandomAccessFile(lockFile, "rw").getChannel();
		if (channel.tryLock() == null) {
			channel.close();
			throw new IllegalAccessException();
		}
		
		writePid();
	}
	
	private void writePid() throws IOException
	{
		String pid = new ApplicationPid().toString();
		channel.position(0);
		channel.write(ByteBuffer.wrap(pid.getBytes(StandardCharsets.UTF_8)));
		channel.force(true);
	}
	
	private static boolean checkIfProcessAlive(File lockFile) {
		List<String> lines = null;
		try {
			lines = Files.readAllLines(lockFile.toPath());
		} catch (IOException e) {
			return true;
		}
		if(lines.isEmpty())
			return false;
		
		return isStillAllive(lines.get(0));
	}
	static boolean isStillAllive(String pidStr) {
	    String OS = System.getProperty("os.name").toLowerCase();
	    String command = null;
	    if (OS.indexOf("win") >= 0) {
	        //log.debug("Check alive Windows mode. Pid: [{}]", pidStr);
	        command = "cmd /c tasklist /FI \"PID eq " + pidStr + "\"";
	        return isProcessIdRunning(pidStr, command);
	    } else if (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0) {
	        //log.debug("Check alive Linux/Unix mode. Pid: [{}]", pidStr);
	        command = "ps -p " + pidStr;
	        return isProcessIdRunning(pidStr, command);
	    }
	    //log.debug("Default Check alive for Pid: [{}] is false", pidStr);
	    return false;
	}


	static boolean isProcessIdRunning(String pid, String command) {
	    //log.debug("Command [{}]",command );
	    try 
	    {
	        Runtime rt = Runtime.getRuntime();
	        Process pr = rt.exec(command);

	        try(BufferedReader bReader = new BufferedReader(new InputStreamReader(pr.getInputStream())))
	        {
		        String strLine = null;
		        while ((strLine= bReader.readLine()) != null) {
		            if (strLine.contains(" " + pid + " ")) {
		                return true;
		            }
		        }
	        }

	        return false;
	    } catch (Exception ex) {
	        //log.warn("Got exception using system command [{}].", command, ex);
	        return true;
	    }
	}

	public boolean isLocked() {
		return locked;
	}

	private void setLocked(boolean locked) {
		this.locked = locked;
	}

}
