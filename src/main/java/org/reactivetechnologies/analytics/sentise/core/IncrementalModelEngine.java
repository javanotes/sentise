package org.reactivetechnologies.analytics.sentise.core;

import org.reactivetechnologies.analytics.sentise.dto.WekaData;

import weka.classifiers.Classifier;

public interface IncrementalModelEngine {
	
	/**
	 * Name of the default classification queue on which regression listener will listen.
	 */
	String DEFAULT_CLASSIFICATION_QUEUE = "$DEF";
	/**
	 * Update and train model.
	 * 
	 * @param nextInstance
	 * @throws Exception
	 */
	void incrementModel(WekaData nextInstance) throws Exception;

	/**
	 * Name of the classifier algorithm used. 
	 * @return
	 */
	String classifierAlgorithm();
	
	/**
	 * Get the transient classifier instance. This object has a application scope lifetime and not persisted.
	 * @return
	 */
	Classifier classifierInstance();
	
}
