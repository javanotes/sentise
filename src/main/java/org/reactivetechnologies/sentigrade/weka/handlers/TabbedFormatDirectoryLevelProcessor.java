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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.reactivetechnologies.sentigrade.files.AsyncFileReadProcessor;
import org.reactivetechnologies.sentigrade.files.FileContent;
import org.springframework.util.Assert;

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
	public TabbedFormatDirectoryLevelProcessor(BlockingQueue<FileContent> queue, Instances data, AtomicInteger classIdx) {
		super(queue, 1);
		this.data = data;
		this.classIdx = classIdx;
		isOutputFilename = false;//force ignore
		
		classAttribs = Collections.list(data.classAttribute().enumerateValues());
	}
	
	private String getClass(String [] split)
	{
		String cls = isClassAtFirst ? split[0] : split[1];
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
		double[] newInst = new double[2];;
		String [] split = fc.getContent().split("\t");
		Assert.isTrue(split.length == 2, "Not a tab separated line '"+fc.getContent()+"'");
		
		newInst[0] = data.attribute(0).addStringValue(isClassAtFirst ? split[1] : split[0]);
		String cls = getClass(split);
		newInst[1] = classAttribs.indexOf(cls);
		
		Assert.isTrue(newInst[1] != -1, "Unable to map class attribute '"+cls+"'");
		
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