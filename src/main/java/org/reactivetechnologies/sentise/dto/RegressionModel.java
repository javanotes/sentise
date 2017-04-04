package org.reactivetechnologies.sentise.dto;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;
import com.hazelcast.util.HashUtil;
import com.hazelcast.util.MD5Util;


public abstract class RegressionModel implements DataSerializable, Serializable {
	/**
	* 
	*/
	private static final long serialVersionUID = -7364621200107984642L;

	private transient CombinerResult combineStatus;
	private long generatedOn;

	public RegressionModel() {
	}


	protected String classifierImpl;

	public String getClassifierImpl() {
		return classifierImpl;
	}

	public void setClassifierImpl(String classifierImpl) {
		this.classifierImpl = classifierImpl;
	}

	private long murmurHash;
	private String md5Hex;

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
	private double meanAbsErr;
	private double rootMeanSqErr;
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
			out.writeLong(murmurHash);
			out.writeUTF(md5Hex);
			out.writeLong(getGeneratedOn());
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
			setLongId(in.readLong());
			setStringId(in.readUTF());
			setGeneratedOn(in.readLong());
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
			bytes = getModelBytes(StandardCharsets.UTF_8);
			murmurHash = HashUtil.MurmurHash3_x64_64(bytes, 0, bytes.length);
			md5Hex = MD5Util.toMD5String(getClassifierImpl().toString() + murmurHash);
		} catch (Exception e) {
			e.printStackTrace();
			murmurHash = -1;
		}
	}

	/**
	 * return model content as byte[].
	 * @param utf8
	 * @return
	 */
	protected abstract byte[] getModelBytes(Charset utf8);

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

}
