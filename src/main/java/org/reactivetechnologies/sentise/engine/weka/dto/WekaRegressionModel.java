package org.reactivetechnologies.sentise.engine.weka.dto;

import java.io.IOException;
import java.nio.charset.Charset;

import org.reactivetechnologies.sentise.dto.RegressionModel;
import org.reactivetechnologies.sentise.err.OperationFailedUnexpectedly;
import org.reactivetechnologies.sentise.utils.ConfigUtil;
import org.springframework.util.Assert;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;

import weka.classifiers.Classifier;
import weka.core.Instances;

public class WekaRegressionModel extends RegressionModel {
	/**
	* 
	*/
	private static final long serialVersionUID = -7364621200107984642L;

	private boolean attribsInitialized;

	private Instances structure;
	public WekaRegressionModel() {
	}

	public WekaRegressionModel(Classifier trainedClassifier) {
		super();
		setTrainedClassifier(trainedClassifier);
	}

	/**
	 * 
	 * @param trainedClassifier
	 * @throws IOException
	 */
	public WekaRegressionModel(String trainedClassifier) throws IOException {
		super();
		Assert.notNull(trainedClassifier, "Serialized classifier is null");
		fromXmlString(trainedClassifier);
	}

	public Classifier getTrainedClassifier() {
		return trainedClassifier;
	}

	public void setTrainedClassifier(Classifier trainedClassifier) {
		this.trainedClassifier = trainedClassifier;
		this.classifierImpl = trainedClassifier.getClass().getName();
	}

	public Instances getTrainingSet() {
		return trainingSet;
	}

	public void setTrainingSet(Instances trainingSet) {
		this.trainingSet = trainingSet;
	}


	/**
	 * Serialize classifier model to a XML format.
	 * 
	 * @return
	 * @throws IOException
	 */
	public String toXmlString() throws IOException {
		try {
			return ConfigUtil.toXml(trainedClassifier);
		} catch (Exception e) {
			if (e instanceof IOException)
				throw (IOException) e;
			else
				throw new OperationFailedUnexpectedly(e);
		}
	}

	/**
	 * Prepare classifier from a XML format.
	 * 
	 * @param xmlString
	 * @return
	 * @throws IOException
	 */
	public Classifier fromXmlString(String xmlString) throws IOException {
		try {

			setTrainedClassifier((Classifier) ConfigUtil.fromXml(xmlString));

		} catch (Exception e) {
			throw new IOException(e);
		}
		return getTrainedClassifier();
	}

	private Classifier trainedClassifier;
	private transient Instances trainingSet;

	@Override
	public void writeData(ObjectDataOutput out) throws IOException {
		try {
			super.writeData(out);
			out.writeUTF(toXmlString());
			out.writeBoolean(isAttribsInitialized());
			out.writeUTF(ConfigUtil.toXml(structure));
		} catch (Exception e) {
			throw new IOException(e);
		}

	}

	@Override
	public void readData(ObjectDataInput in) throws IOException {

		try 
		{
			super.readData(in);
			fromXmlString(in.readUTF());
			setAttribsInitialized(in.readBoolean());
			setStructure((Instances) ConfigUtil.fromXml(in.readUTF()));
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	

	public boolean isAttribsInitialized() {
		return attribsInitialized;
	}

	public void setAttribsInitialized(boolean attribsInitialized) {
		this.attribsInitialized = attribsInitialized;
	}

	public Instances getStructure() {
		return structure;
	}

	public void setStructure(Instances structure) {
		this.structure = structure;
	}

	@Override
	protected byte[] getModelBytes(Charset utf8) {
		try {
			return trainedClassifier != null ? toXmlString().getBytes(utf8) : new byte[0];
		} catch (IOException e) {
			throw new OperationFailedUnexpectedly("Unable to write classifer as xml", e);
		}
	}
}
