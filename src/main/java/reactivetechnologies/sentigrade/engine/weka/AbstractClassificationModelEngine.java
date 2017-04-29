package reactivetechnologies.sentigrade.engine.weka;

import java.util.List;

import reactivetechnologies.sentigrade.dto.RegressionModel;
import reactivetechnologies.sentigrade.engine.ClassificationModelEngine;
import reactivetechnologies.sentigrade.engine.weka.dto.WekaData;
import reactivetechnologies.sentigrade.engine.weka.dto.WekaRegressionModel;
import reactivetechnologies.sentigrade.err.EngineException;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.core.Instances;
/**
 * Base class to derive Weka incremental classifiers.
 * @author esutdal
 *
 */
public abstract class AbstractClassificationModelEngine extends AbstractClassifier implements ClassificationModelEngine<Classifier> {

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
	public abstract WekaRegressionModel generateModelSnapshot();

	/**
	 * Generate an ensemble output using a preset evaluation based on the
	 * {@linkplain EnsembleCombiner}, for the generated snapshot models. By default
	 * uses a {@linkplain EnsembleCombiner#VOTING} ensembling approach.
	 * 
	 * @param models
	 * @param combiner
	 * @param evaluationSet
	 * @return ensemble model
	 * @throws EngineException
	 */
	public abstract WekaRegressionModel ensembleBuiltModels(List<RegressionModel> models, EnsembleCombiner combiner, WekaData evaluationSet)
			throws EngineException;
	/**
	 * Subclasses to override this method for initialization codes.
	 * @return
	 */
	protected abstract boolean onInitialization();
	/**
	 * If it is an updatable classifier.
	 * @return
	 */
	public abstract boolean isClassifierUpdatable();
}
