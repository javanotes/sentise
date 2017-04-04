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
package org.reactivetechnologies.sentise.facade;

import org.reactivetechnologies.sentise.dto.ClassifiedModel;
import org.reactivetechnologies.sentise.dto.RequestData;
import org.reactivetechnologies.sentise.err.EngineException;

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
	 * @param <T>
	 * @return
	 * @throws EngineException 
	 * @see {@linkplain ClassifiedModel#writeModel(java.io.OutputStream)}
	 * @see {@linkplain ClassifiedModel#readModel(java.io.InputStream)}
	 */
	<T> ClassifiedModel<T> gatherClassifier(String domain) throws EngineException;
	/**
	 * Get the locally built (incrementally) classifier, without performing
	 * any ensemble.
	 * @param <T>
	 * @return
	 * @throws EngineException 
	 * @see {@linkplain ClassifiedModel#writeModel(java.io.OutputStream)}
	 * @see {@linkplain ClassifiedModel#readModel(java.io.InputStream)}
	 */
	<T> ClassifiedModel<T> getClassifier(String domain) throws EngineException;
	
	/**
	 * Integration API to {@link #buildClassifier(Instances, String)}.
	 * @param request
	 * @throws EngineException 
	 */
	void buildClassifier(RequestData request) throws EngineException;
	
	/**
	 * Integration API to {@link #classifyInstance(Instance, String)}.
	 * @param request
	 * @return
	 * @throws Exception
	 */
	String classifyInstance(RequestData request) throws Exception;
}
