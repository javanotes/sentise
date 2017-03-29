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
package org.reactivetechnologies.analytics.sentise.facade;

import org.reactivetechnologies.analytics.sentise.EngineException;
import org.reactivetechnologies.analytics.sentise.dto.ClassifiedModel;
import org.reactivetechnologies.analytics.sentise.dto.RequestData;

import weka.classifiers.Classifier;
import weka.core.Instance;
import weka.core.Instances;
/**
 * The main service class for facading with classifier implementations.
 * @author esutdal
 *
 */
public interface ModelExecutionService {
	
	/**
	 * Gather and ensemble a classifier from across the cluster. This will
	 * trigger a new build, only if no other gathering is already running in the cluster.
	 * <p>This method is useful, if we want a generated ensemble to be saved for reuse.
	 * @return
	 * @throws EngineException 
	 * @see {@linkplain ClassifiedModel#writeModel(java.io.OutputStream)}
	 * @see {@linkplain ClassifiedModel#readModel(java.io.InputStream)}
	 */
	ClassifiedModel gatherClassifier(String domain) throws EngineException;
	/**
	 * Extension to {@link Classifier#buildClassifier(weka.core.Instances)}, to build (scatter really).
	 * individual Instance.
	 * @param data
	 * @param domain
	 */
	void buildClassifier(Instances data, String domain);
	/**
	 * Integration API to {@link #buildClassifier(Instances, String)}.
	 * @param request
	 * @throws EngineException 
	 */
	void buildClassifier(RequestData request) throws EngineException;
	/**
	 * Extension to {@link Classifier#classifyInstance(weka.core.Instances)}, to classify.
	 * individual Instance.
	 * @param instance
	 * @param domain
	 * @return
	 * @throws Exception
	 */
	double classifyInstance(Instance instance, String domain) throws Exception;
	/**
	 * Integration API to {@link #classifyInstance(Instance, String)}.
	 * @param request
	 * @return
	 * @throws Exception
	 */
	String classifyInstance(RequestData request) throws Exception;
}
