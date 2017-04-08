package org.reactivetechnologies.sentise.engine.moa;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.PostConstruct;

import org.reactivetechnologies.sentise.dto.RegressionModel;
import org.reactivetechnologies.sentise.engine.IncrementalModelEngine;
import org.reactivetechnologies.sentise.engine.moa.dto.MoaData;
import org.reactivetechnologies.sentise.engine.moa.dto.MoaRegressionModel;
import org.reactivetechnologies.sentise.err.EngineException;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

import com.yahoo.labs.samoa.instances.Attribute;
import com.yahoo.labs.samoa.instances.Instance;
import com.yahoo.labs.samoa.instances.Instances;
import com.yahoo.labs.samoa.instances.InstancesHeader;

import moa.classifiers.Classifier;
/**
 * Base class to derive Weka incremental classifiers.
 * @author esutdal
 *
 */
public abstract class AbstractMOAModelEngine implements IncrementalModelEngine<Classifier> {

	
	public AbstractMOAModelEngine(String learnerClass) throws ReflectiveOperationException {
		super();
		this.learner = (Classifier) ClassUtils.forName(learnerClass, null).newInstance();
	}

	protected Classifier learner;
	protected boolean attribsInitialized;
	
	@PostConstruct
	private void initialize()
	{
		onInitialization();
		Assert.notNull(learner, "Classifier is null!");
		
	}
	
	private void setModelContext(Instances i)
	{
		InstancesHeader hdr = new InstancesHeader(i);
		learner.setModelContext(hdr);
	}
	
	private void initializeAttribs(Instance i)
	{
		List<Attribute> attrs = new ArrayList<>(i.numAttributes());
		for(int j=0 ;j<i.numAttributes(); j++)
		{
			attrs.add(j, i.attribute(j));
		}
		Instances ii = new Instances("", attrs, 0);
		setModelContext(ii);
		learner.prepareForUse();
		attribsInitialized = true;
	}
	
	protected void trainOnInstance(Instance i)
	{
		if(!attribsInitialized)
		{
			initializeAttribs(i);
		}
		
		learner.trainOnInstance(i);
	}
	
	/**
	 * Subclasses to override this method for initialization codes.
	 * @return
	 */
	protected abstract boolean onInitialization();

	public abstract MoaRegressionModel generateModelSnapshot();

	public abstract MoaRegressionModel ensembleBuiltModels(List<RegressionModel> models, MoaData evaluationSet) throws EngineException ;
	
}
