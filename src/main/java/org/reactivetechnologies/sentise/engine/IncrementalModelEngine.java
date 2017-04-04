package org.reactivetechnologies.sentise.engine;

import org.reactivetechnologies.ticker.messaging.Data;

public interface IncrementalModelEngine<T> {
	
	/**
	 * Name of the default classification queue on which regression listener will listen.
	 */
	String DEFAULT_CLASSIFICATION_QUEUE = "$DEF";
	String CLASSIFIER_ATTRIB_TEXT = "text";
	int CLASSIFIER_ATTRIB_TEXT_IDX = 0;
	String CLASSIFIER_ATTRIB_CLASS = "class";
	int CLASSIFIER_ATTRIB_CLASS_IDX = 1;
	/**
	 * Update and train model.
	 * 
	 * @param nextInstance
	 * @throws Exception
	 */
	void incrementModel(Data nextInstance) throws Exception;

	/**
	 * Name of the classifier algorithm used. 
	 * @return
	 */
	String classifierAlgorithm();
	
	/**
	 * Get the transient classifier instance. This object has a application scope lifetime and not persisted.
	 * @return
	 */
	T classifierInstance();
	
}
