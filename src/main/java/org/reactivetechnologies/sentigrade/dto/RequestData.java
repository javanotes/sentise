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
package org.reactivetechnologies.sentigrade.dto;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.reactivetechnologies.sentigrade.engine.IncrementalModelEngine;
import org.reactivetechnologies.sentigrade.services.ModelExecutionService;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;

import weka.core.Attribute;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
/**
 * A request DTO corresponding to Weka Instance/s.
 * @author esutdal
 *
 */
public class RequestData implements DataSerializable {
	
	private static ObjectWriter jsonWriter;
	private static ObjectReader jsonReader;
	static
	{
		ObjectMapper OMAPPER = new ObjectMapper();
		jsonWriter = OMAPPER.writerFor(RequestData.class).with(new DefaultPrettyPrinter());
		jsonReader = OMAPPER.readerFor(RequestData.class);
	}
	public RequestData() {
	}
	public static String sampleJson()
	{
		RequestData rd = new RequestData();
		rd.setClasses(Arrays.asList("neg", "pos"));
		rd.setDomain("movie_review");
		rd.setDataSet(Arrays.asList(new RequestData.Tuple("This is a good review", "pos"),
				new RequestData.Tuple("This is a bad review", "neg")));
		return rd.toString();
	}
	/**
	 * RequestData with a single Tuple. To be used for invoking {@linkplain ModelExecutionService#classifyInstance(RequestData)}.
	 * @param t
	 */
	public Instance toInstance()
	{
		Assert.notEmpty(getDataSet(), "'dataSet' is empty or null");
		Instances struct = getStructure();
		
		return buildInstance(struct, getDataSet().get(0));
		
	}
	public String toString()
	{
		try {
			return jsonWriter.writeValueAsString(this);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		return "ERR";
	}
	/**
	 * 
	 * @param json
	 * @return
	 */
	public static RequestData fromJson(String json)
	{
		try {
			return jsonReader.readValue(json);
		} catch (IOException e) {
			throw new IllegalArgumentException(e);
		}
	}
	/**
	 * RequestData with a single Tuple. To be used for invoking {@linkplain ModelExecutionService#classifyInstance(RequestData)}. This constructor
	 * is NOT supposed to be used with training data.
	 * @param text
	 * @param classes
	 */
	public RequestData(String text, List<String> classes)
	{
		setTuple(text, classes);
	}
	public void setTuple(String text, List<String> classes)
	{
		Assert.notEmpty(classes, "classes is empty or null");
		this.classes.addAll(classes);
		this.dataSet.addAll(Arrays.asList(new Tuple(text, null)));
	}
	public void setTuple(RequestData d)
	{
		setTuple(d.dataSet.isEmpty() ? "" : d.dataSet.get(0).text, d.classes);
		setDomain(d.domain);
	}
	@JsonIgnore
	protected Instances structure;
	/**
	 * 
	 */
	protected void buildStructure()
	{
		Assert.notEmpty(getClasses(), "'class' is empty or null");
		Attribute attr0 = new Attribute(IncrementalModelEngine.CLASSIFIER_ATTRIB_TEXT, (List<String>) null, IncrementalModelEngine.CLASSIFIER_ATTRIB_TEXT_IDX);
		Attribute attr1 = new Attribute(IncrementalModelEngine.CLASSIFIER_ATTRIB_CLASS, getClasses(), IncrementalModelEngine.CLASSIFIER_ATTRIB_CLASS_IDX);
		structure = new Instances(getDomain(), new ArrayList<>(Arrays.asList(attr0, attr1)), getDataSet().size());
		structure.setClass(attr1);
	}
	public Instances getStructure()
	{
		if (structure == null) {
			buildStructure();
		}
		return structure;
	}
	/**
	 * 
	 * @param struct
	 * @param t
	 * @return
	 */
	protected Instance buildInstance(Instances struct, Tuple t)
	{
		Instance i = new DenseInstance(2);
		i.setDataset(struct);
		i.setValue(struct.attribute(IncrementalModelEngine.CLASSIFIER_ATTRIB_TEXT_IDX), t.getText());
		i.setValue(struct.attribute(IncrementalModelEngine.CLASSIFIER_ATTRIB_CLASS_IDX), t.getTextClass());
		return i;
	}
	/**
	 * Construct a 2-attribute text instance, with the class attribute at last.
	 * @return
	 */
	public Instances toInstances() 
	{
		Assert.notEmpty(getDataSet(), "'dataSet' is empty or null");
		Instances data = getStructure();
		Instance i;
		for (Tuple t : getDataSet()) {
			if (StringUtils.isEmpty(t.textClass) || t.text == null)
				continue;

			i = buildInstance(data, t);
			
			data.add(i);
		}
		return data;
	}

	public static class Tuple implements DataSerializable {
		public Tuple() {
		}

		public Tuple(String text, String textClass) {
			super();
			this.text = text;
			this.textClass = textClass;
		}

		public String getText() {
			return text;
		}

		public void setText(String text) {
			this.text = text;
		}

		public String getTextClass() {
			return textClass;
		}

		public void setTextClass(String textClass) {
			this.textClass = textClass;
		}

		String text = "";
		String textClass = "";

		@Override
		public void writeData(ObjectDataOutput out) throws IOException {
			out.writeUTF(text);
			out.writeUTF(textClass);
		}

		@Override
		public void readData(ObjectDataInput in) throws IOException {
			setText(in.readUTF());
			setTextClass(in.readUTF());
		}
	}

	public String getDomain() {
		return domain;
	}

	public void setDomain(String domain) {
		this.domain = domain;
	}

	public List<String> getClasses() {
		return classes;
	}

	public void setClasses(List<String> classes) {
		this.classes.clear();
		this.classes.addAll(classes);
		buildStructure();
	}

	public List<Tuple> getDataSet() {
		return dataSet;
	}

	public void setDataSet(List<Tuple> dataSet) {
		this.dataSet.addAll(dataSet);
	}

	private boolean useSentimentVector = true;
	public boolean isUseSentimentVector() {
		return useSentimentVector;
	}
	public void setUseSentimentVector(boolean useSentimentVector) {
		this.useSentimentVector = useSentimentVector;
	}
	private String domain = IncrementalModelEngine.DEFAULT_CLASSIFICATION_QUEUE;
	private final List<String> classes = new ArrayList<>();
	private final List<Tuple> dataSet = new LinkedList<>();

	@Override
	public void writeData(ObjectDataOutput out) throws IOException {
		out.writeUTF(domain);
		out.writeBoolean(useSentimentVector);
		if (classes != null) {
			out.writeInt(classes.size());
			if (!classes.isEmpty()) {
				for (String s : classes)
					out.writeUTF(s);
			}
		} else
			out.writeInt(-1);

		if (dataSet != null) {
			out.writeInt(dataSet.size());
			if (!dataSet.isEmpty()) {
				for (Tuple r : dataSet)
					r.writeData(out);
			}
		} else
			out.writeInt(-1);
	}

	@Override
	public void readData(ObjectDataInput in) throws IOException {
		setDomain(in.readUTF());
		setUseSentimentVector(in.readBoolean());
		int len = in.readInt();
		if (len != -1) {
			for (int i = 0; i < len; i++) {
				classes.add(in.readUTF());
			}
		}

		len = in.readInt();
		if (len != -1) {
			Tuple r;
			for (int i = 0; i < len; i++) {
				r = new Tuple();
				r.readData(in);
				dataSet.add(r);
			}
		}

	}
}
