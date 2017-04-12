package reactivetechnologies.sentigrade.engine;

import org.reactivetechnologies.ticker.messaging.Data;
import org.springframework.util.StringUtils;

import weka.core.Instance;

public interface IncrementalModelEngine<T> {
	
	/**
	 * Name of the default classification queue on which regression listener will listen.
	 */
	String DEFAULT_CLASSIFIER_DOMAIN = "$DEF";
	String CLASSIFIER_ATTRIB_TEXT = "text";
	int CLASSIFIER_ATTRIB_TEXT_IDX = 0;
	String CLASSIFIER_ATTRIB_CLASS = "class";
	int CLASSIFIER_ATTRIB_CLASS_IDX = 1;
	
	String CLASSIFIER_ATTRIB_ST_ALL = "score_all";
	String CLASSIFIER_ATTRIB_ST_ADJ = "score_adj";
	String CLASSIFIER_ATTRIB_ST_ADV = "score_adv";
	String CLASSIFIER_ATTRIB_ST_NOUN = "score_noun";
	String CLASSIFIER_ATTRIB_ST_VERB = "score_verb";
	int CLASSIFIER_ATTRIB_ST_CLASS_IDX = 5;
	int CLASSIFIER_ATTRIB_ST_ALL_IDX = 4;
	int CLASSIFIER_ATTRIB_ST_ADJ_IDX = 3;
	int CLASSIFIER_ATTRIB_ST_ADV_IDX = 2;
	int CLASSIFIER_ATTRIB_ST_NOUN_IDX = 1;
	int CLASSIFIER_ATTRIB_ST_VERB_IDX = 0;
	/**
	 * Update and train model.
	 * 
	 * @param nextInstance
	 * @throws Exception
	 */
	void incrementModel(Data nextInstance) throws Exception;
	/**
	 * Update an incremental classifier with a single instance.
	 * @param nextInstance
	 * @throws Exception
	 */
	void incrementModel(Instance nextInstance) throws Exception;

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
	
	public static String getDomain(String domain)
	{
		return StringUtils.hasText(domain) ? domain : DEFAULT_CLASSIFIER_DOMAIN;
	}
	
}
