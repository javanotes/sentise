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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.util.Assert;

import reactivetechnologies.sentigrade.files.AsyncFileReadProcessor;
import reactivetechnologies.sentigrade.files.FileContent;
import weka.core.DenseInstance;
import weka.core.Instances;

class TabbedFormatDirectoryLevelProcessor extends AsyncFileReadProcessor
{
	private final Instances data;
	private final AtomicInteger classIdx;
	private Map<Integer, String> scoreMap;
	public Map<Integer, String> getScoreMap() {
		return scoreMap;
	}

	public void setScoreMap(Map<Integer, String> scoreMap) {
		this.scoreMap = scoreMap;
	}

	private boolean isClassAtFirst = false;
	private ArrayList<Object> classAttribs;
	/**
	 * 
	 * @param queue
	 * @param struct
	 * @param classIdx
	 */
	public TabbedFormatDirectoryLevelProcessor(BlockingQueue<FileContent> queue, Instances struct, AtomicInteger classIdx) {
		super(queue, 1);
		this.data = struct;
		this.classIdx = classIdx;
		isOutputFilename = false;//force ignore
		
		classAttribs = Collections.list(struct.classAttribute().enumerateValues());
	}
	
	private String getClass(String cls)
	{
		try 
		{
			int clsNumber = Integer.valueOf(cls);
			if(scoreMap != null && scoreMap.containsKey(clsNumber))
			{
				return scoreMap.get(clsNumber);
			}
		} catch (NumberFormatException e) {
			
		}
		return cls;
	}
		
	@Override
	protected void updateInstance(FileContent fc) {
		double[] newInst = new double[2];
		String [] split = fc.getContent().split("\t");
		
		Assert.isTrue(split.length == 2, "Not a tab separated line '"+fc.getContent()+"'");
		int c = isClassAtFirst ? 0: 1;
		int t = c == 0 ? 1 : 0;
		
		newInst[t] = data.attribute(t).addStringValue(split[t]);
		String cls = getClass(split[c]);
		
		newInst[c] = classAttribs.indexOf(cls);
		Assert.isTrue(newInst[c] != -1, "Unable to map class attribute for '"+split[c]+"'");
		
		classIdx.getAndIncrement();
		data.add(new DenseInstance(1.0, newInst));
		
	}

	public boolean isClassAtFirst() {
		return isClassAtFirst;
	}

	public void setClassAtFirst(boolean isClassAtFirst) {
		this.isClassAtFirst = isClassAtFirst;
	}
	
}