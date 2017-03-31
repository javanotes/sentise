package org.reactivetechnologies.analytics.sentise.dto;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;

import org.reactivetechnologies.analytics.sentise.err.OperationFailedUnexpectedly;
import org.reactivetechnologies.analytics.sentise.utils.ConfigUtil;
import org.springframework.util.Assert;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import com.hazelcast.util.HashUtil;
import com.hazelcast.util.MD5Util;

import weka.classifiers.Classifier;
import weka.core.Instances;

public class RegressionModel implements DataSerializable, Serializable {
	/**
	* 
	*/
	private static final long serialVersionUID = -7364621200107984642L;

	private boolean attribsInitialized;
	private transient CombinerResult combineStatus;
	private long generatedOn;

	private Instances structure;
	public RegressionModel() {
	}

	public RegressionModel(Classifier trainedClassifier) {
		super();
		setTrainedClassifier(trainedClassifier);
	}

	/**
	 * 
	 * @param trainedClassifier
	 * @throws IOException
	 */
	public RegressionModel(String trainedClassifier) throws IOException {
		super();
		Assert.notNull(trainedClassifier, "Serialized classifier is null");
		fromXmlString(trainedClassifier);
	}

	public Classifier getTrainedClassifier() {
		return trainedClassifier;
	}

	private String classifierImpl;

	public void setTrainedClassifier(Classifier trainedClassifier) {
		this.trainedClassifier = trainedClassifier;
		this.classifierImpl = trainedClassifier.getClass().getName();
	}

	public String getClassifierImpl() {
		return classifierImpl;
	}

	public void setClassifierImpl(String classifierImpl) {
		this.classifierImpl = classifierImpl;
	}

	private long murmurHash;
	private String md5Hex;

	public Instances getTrainingSet() {
		return trainingSet;
	}

	public void setTrainingSet(Instances trainingSet) {
		this.trainingSet = trainingSet;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.ericsson.fmt.forecasting.engine.impl.RegressionModel#getName()
	 */
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double[] getResults() {
		return results;
	}

	public void setResults(double[] results) {
		this.results = results;
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

	public double getIterationError() {
		return iterationError;
	}

	public void setIterationError(double iterationError) {
		this.iterationError = iterationError;
	}

	public double getMeanAbsErr() {
		return meanAbsErr;
	}

	public void setMeanAbsErr(double meanAbsErr) {
		this.meanAbsErr = meanAbsErr;
	}

	public double getRootMeanSqErr() {
		return rootMeanSqErr;
	}

	public void setRootMeanSqErr(double rootMeanSqErr) {
		this.rootMeanSqErr = rootMeanSqErr;
	}

	public double getPctIncorrect() {
		return pctIncorrect;
	}

	public void setPctIncorrect(double pctIncorrect) {
		this.pctIncorrect = pctIncorrect;
	}

	public int getFolds() {
		return folds;
	}

	public void setFolds(int folds) {
		this.folds = folds;
	}

	private double iterationError;
	private Classifier trainedClassifier;
	private double meanAbsErr;
	private double rootMeanSqErr;
	private transient Instances trainingSet;
	private String name;
	private transient double[] results;
	private double pctIncorrect;
	private int folds = 0;

	@Override
	public void writeData(ObjectDataOutput out) throws IOException {
		try {
			out.writeDouble(getIterationError());
			out.writeDouble(getMeanAbsErr());
			out.writeDouble(getRootMeanSqErr());
			out.writeDouble(getPctIncorrect());
			out.writeInt(getFolds());
			out.writeUTF(getName());
			out.writeUTF(classifierImpl);
			out.writeUTF(toXmlString());
			out.writeLong(murmurHash);
			out.writeUTF(md5Hex);
			out.writeLong(getGeneratedOn());
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
			setIterationError(in.readDouble());
			setMeanAbsErr(in.readDouble());
			setRootMeanSqErr(in.readDouble());
			setPctIncorrect(in.readDouble());
			setFolds(in.readInt());
			setName(in.readUTF());
			classifierImpl = in.readUTF();
			fromXmlString(in.readUTF());
			setLongId(in.readLong());
			setStringId(in.readUTF());
			setGeneratedOn(in.readLong());
			setAttribsInitialized(in.readBoolean());
			setStructure((Instances) ConfigUtil.fromXml(in.readUTF()));
		} catch (Exception e) {
			throw new IOException(e);
		}
	}

	/**
	 * Generates different ids, by creating a murmur hash on the serialized
	 * string form of the classifier.
	 */
	public void generateId() {
		byte[] bytes = null;
		try {
			bytes = toXmlString().getBytes(StandardCharsets.UTF_8);
			murmurHash = HashUtil.MurmurHash3_x64_64(bytes, 0, bytes.length);
			md5Hex = MD5Util.toMD5String(getTrainedClassifier().toString() + murmurHash);
		} catch (Exception e) {
			e.printStackTrace();
			murmurHash = -1;
		}
	}

	public void setLongId(Long id) {
		murmurHash = id;
	}

	public Long getLongId() {
		return murmurHash;
	}

	public String getStringId() {
		return md5Hex;
	}

	public void setStringId(String id) {
		md5Hex = id;
	}

	public long getGeneratedOn() {
		return generatedOn;
	}

	public void setGeneratedOn(long generatedOn) {
		this.generatedOn = generatedOn;
	}

	public CombinerResult getCombineStatus() {
		return combineStatus;
	}

	public void setCombineStatus(CombinerResult combineStatus) {
		this.combineStatus = combineStatus;
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
}
