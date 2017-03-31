package org.reactivetechnologies.analytics.sentise.engine;

import java.util.List;

import org.reactivetechnologies.analytics.sentise.dto.RegressionModel;
import org.reactivetechnologies.analytics.sentise.dto.WekaData;
import org.reactivetechnologies.analytics.sentise.err.EngineException;

import weka.classifiers.AbstractClassifier;
import weka.core.Instances;
/**
 * Base class to derive Weka incremental classifiers.
 * @author esutdal
 *
 */
public abstract class AbstractIncrementalModelEngine extends AbstractClassifier implements IncrementalModelEngine {

	/**
	 * 
	 */
	private static final long serialVersionUID = 4302332827921394008L;

	/**
	 * Extract the structure from a given {@linkplain Instances}. Assuming the class 
	 * attribute is the last.
	 * @param i
	 * @return
	 */
	public static Instances getStructure(Instances i)
	{
		Instances s = new Instances(i, 0);
		s.setClassIndex(i.numAttributes()-1);
		return s;
	}
	/**
	 * Get the current incrementally updated classifier model.
	 * 
	 * @return
	 */
	public abstract RegressionModel generateModelSnapshot();

	/**
	 * Generate an ensemble output using a preset evaluation based on the
	 * {@linkplain CombinerType}, for the generated snapshot models. By default
	 * uses a {@linkplain CombinerType#VOTING} ensembling approach.
	 * 
	 * @param models
	 * @param combiner
	 * @param evaluationSet
	 * @return ensemble model
	 * @throws EngineException
	 */
	public abstract RegressionModel ensembleBuiltModels(List<RegressionModel> models, CombinerType combiner, WekaData evaluationSet)
			throws EngineException;
	/**
	 * Subclasses to override this method for initialization codes.
	 * @return
	 */
	protected abstract boolean onInitialization();
}
