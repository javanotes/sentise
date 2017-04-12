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

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import reactivetechnologies.sentigrade.files.AsyncFileReadProcessor;
import reactivetechnologies.sentigrade.files.FileContent;
import weka.core.DenseInstance;
import weka.core.Instances;

class WekaFormatDirectoryLevelProcessor extends AsyncFileReadProcessor
{
	private final Instances data;
	private final AtomicInteger classIdx;
	
	public WekaFormatDirectoryLevelProcessor(BlockingQueue<FileContent> queue, Instances data, AtomicInteger classIdx, int dirCount) {
		super(queue, dirCount);
		this.data = data;
		this.classIdx = classIdx;
		isOutputFilename = false;//force ignore
	}
		
	@Override
	protected void updateInstance(FileContent fc) {
		double[] newInst = null;
		if (isOutputFilename) {
			newInst = new double[3];
		} else {
			newInst = new double[2];
		}

		newInst[0] = data.attribute(0).addStringValue(fc.getContent());
		
		if (isOutputFilename) {
			newInst[1] = data.attribute(1).addStringValue(fc.getSubdirPath() + File.separator + fc.getFileName());
		}

		classIdx.getAndIncrement();
		newInst[data.classIndex()] = fc.getkIndex();
		data.add(new DenseInstance(1.0, newInst));
		
	}
	
}