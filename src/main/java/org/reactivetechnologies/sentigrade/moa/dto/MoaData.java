/* ============================================================================
*
* FILE: InstanceModel.java
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
package org.reactivetechnologies.sentigrade.moa.dto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.reactivetechnologies.sentigrade.utils.ConfigUtil;
import org.reactivetechnologies.ticker.messaging.Data;
import org.reactivetechnologies.ticker.messaging.data.TextData;
import org.springframework.util.Assert;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;



/**
 * A {@linkplain Data} wrapper for Weka {@linkplain Instances}.
 */
public class MoaData extends TextData {

	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private String summaryString()
	{
		StringBuilder s = new StringBuilder();
		s.append("instance_count=").append(instances.numInstances())
		.append("; ").append("attrib_count=").append(instances.numAttributes()).append("; ").append("class_idx=").append(instances.classIndex())
		.append("; ").append("attribs=").append(enumerateAttributes());
		
		return s.toString();
	}
	private List<Attribute> enumerateAttributes() {
		ArrayList<Attribute> eList = new ArrayList<>(instances.numAttributes());
		for (int i = 0; i < instances.numAttributes(); i++) {
			eList.add(instances.attribute(i));
		}
		return eList;
	}
	@Override
	public String toString() {
		try {
			
			return "[" + (instances == null ? "nil" : ConfigUtil.truncate(summaryString(), 2000, " ..trunc*")) + "]";
		} catch (Exception e) {
			e.printStackTrace();
			return "WekaData_tostring_err!";
		}
	}

	private String options;
	private Instances instances;
	/**
	 * Data with multiple {@linkplain Instances}.
	 * @param instances
	 */
	public MoaData(Instances instances) {
		this();
		Assert.notNull(instances);
		this.instances = instances;
	}
	private boolean isInstanceOnly;
	public boolean isSingleInstance() {
		return isInstanceOnly;
	}
	/**
	 * Data with single {@linkplain Instance}.
	 * @param instance
	 */
	public MoaData(Instance instance) 
	{
		Assert.notNull(instance);
		Assert.isTrue(instance.numAttributes() == 0, "Attributes not set for Instance");
		
		ArrayList<Attribute> eList = new ArrayList<>(instance.numAttributes());
		for (int i = 0; i < instance.numAttributes(); i++) {
			eList.add(instance.attribute(i));
		}
			
		instances = new Instances("MoaData", eList, 1);
		instances.add(instance);
		Assert.isTrue(instances.numAttributes() == 0, "Attributes not set for Instance");
		isInstanceOnly = true;
	}
	public MoaData() {
	}
	/**
	 * Return the underlying {@linkplain Instances} object.
	 * @return
	 */
	public Instances getInstances() {
		return instances;
	}
	/**
	 * Get the instance, in a single {@linkplain Instance}.
	 * @return IllegalArgumentException if the underlying Instances contain more than one Instance.
	 * @throws 
	 */
	public Instance getInstance() {
		Assert.isTrue(isInstanceOnly, "Not a single Instance");
		return getInstances().instance(0);
	}

	public String getOptions() {
		return options;
	}

	public void setOptions(String options) {
		this.options = options;
	}

	@Override
	public void writeData(ObjectDataOutput out) throws IOException {
		super.writeData(out);
		out.writeUTF(options);
		out.writeBoolean(isInstanceOnly);
		if (instances != null) {
			try 
			{
				String xml = ConfigUtil.toXml(instances);
				out.writeUTF(xml);
			} catch (Exception e) {
				throw new IOException(e);
			}
			
		}
		else
			out.writeUTF("");
	}

	/**
	 * Split this instance to number of instances if possible.
	 * @param maxLen the max length of each part
	 * @return
	 */
	public MoaData[] split(int maxLen)
	{
		Assert.isTrue(maxLen > 0, "'maxLen' has to be a positive number");
		if(instances == null)
			return new MoaData[]{};
		
		int partCount = instanceSize() / maxLen;
		if(partCount > 0)
		{
			final int rem = instanceSize() % maxLen;
			partCount = partCount + (rem > 0 ? 1 : 0);
			
			final MoaData[] partsArray = new MoaData[partCount];
			
			int idx = 0;
			Instances partIns = null;
			int len = maxLen;
			
			for (; idx < partCount; idx++) 
			{
				if(idx == partCount - 1 && rem > 0)
				{
					//last part for the remainder
					len = rem;
				}
				partIns = new Instances(instances, len);
				for (int j = 0; j < len; j++) 
				{
					partIns.add(instances.get((maxLen*idx) + j));
				}
				partsArray[idx] = new MoaData(partIns);
			}
			
			return partsArray;
		}
		return new MoaData[]{this};
		
	}
	public int instanceSize()
	{
		Assert.notNull(instances);
		return instances.numInstances();
	}
	public int attributeSize()
	{
		Assert.notNull(instances);
		return instances.numAttributes();
	}
	@Override
	public void readData(ObjectDataInput in) throws IOException {
		super.readData(in);
		options = in.readUTF();
		isInstanceOnly = in.readBoolean();
		
		String xml = in.readUTF();
		if(!"".equals(xml.trim()))
		{
			try {
				instances = (Instances) ConfigUtil.fromXml(xml);
			} catch (Exception e) {
				throw new IOException(e);
			}
		}
		
	}
}
