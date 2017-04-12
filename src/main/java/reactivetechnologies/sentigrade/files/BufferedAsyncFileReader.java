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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.BlockingQueue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
/**
 * 
 * @author esutdal
 *
 */
public class BufferedAsyncFileReader extends AsyncFileReader 
{
	private static final Logger log = LoggerFactory.getLogger(BufferedAsyncFileReader.class);
	
	private BufferedReader reader;
	
	/**
	 * Stop signal.
	 * @param stopSignal
	 * @param queue
	 * @throws IOException 
	 */
	public BufferedAsyncFileReader(Path txtPath, BlockingQueue<FileContent> queue, String subdirPath)  {
		super(txtPath, queue, subdirPath);
	}

	private boolean opened;
	public void open() throws IOException
	{
		reader = new BufferedReader(new InputStreamReader(Files.newInputStream(txtPath, StandardOpenOption.READ), getCharset()));
		hasMore();
		opened = true;
	}
		
	/**
	 * By default this will read the full content of file. Override if this behavior is not intended.
	 * @return
	 * @throws IOException
	 */
	protected StringBuilder read() throws IOException
	{
		Assert.isTrue(opened, "File not opened!");
		if(readEx != null)
			throw readEx;
		if(nextLine == null)
			throw new IOException("End of file!");
			
		StringBuilder txtStr = new StringBuilder(nextLine);
		return txtStr;
	}
	/**
	 * If there is still more to be read. Override for large text file reading in chunks.
	 * @return
	 */
	protected boolean hasMore()
	{
		Assert.notNull(reader, "Reader not created!");
		try 
		{
			nextLine = reader.readLine();
			readEx = null;
		} 
		catch (IOException e) {
			nextLine = null;
			readEx = e;
			log.error("While reading next line ", e);
		}
		return nextLine != null;
	}
	private String nextLine = null;
	private IOException readEx;
	
	@Override
	public void close() throws IOException {
		if(reader != null)
			reader.close();
		
	}

}