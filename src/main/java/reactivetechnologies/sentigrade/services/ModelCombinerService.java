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
package reactivetechnologies.sentigrade.services;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import reactivetechnologies.sentigrade.dto.RegressionModel;
import reactivetechnologies.sentigrade.dto.VectorRequestData;
import reactivetechnologies.sentigrade.engine.nlp.SentimentVector;
import reactivetechnologies.sentigrade.err.EngineException;
/**
 * Service interface for fetching an ensemble classifier model.
 * @author esutdal
 *
 */
public interface ModelCombinerService {
	/**
	 * Retrieve an ensemble classifier model, by combining all incremental classifier snapshots across the cluster. 
	 * @param buildNow if true, will signal a cluster wide build; else
	 * will check for any previously stored instance.
	 * @return
	 * @throws EngineException 
	 */
	RegressionModel retrieveModel(String domain, boolean buildNow) throws EngineException;
	/**
	 * Build an ensemble classifier with incremental snapshots across and return the latest result, asynchronously. The passed
	 * {@linkplain ExecutorService} maybe null, and in that case an internal thread pool will be used.
	 * @return
	 */
	Future<RegressionModel> retrieveModel(String domain, ExecutorService thread);
	
	/**
	 * Return the locally built model, without creating an ensemble.
	 * @param domain
	 * @return 
	 * @throws EngineException 
	 */
	RegressionModel getLocalModel(String domain) throws EngineException;
	
	/**
	 * Build a non-incremental classifier, based on a {@linkplain SentimentVector} based model. This model will be built locally
	 * and the instance will not be distributed.
	 * @param data
	 * @throws EngineException 
	 */
	void buildVectorModel(VectorRequestData data) throws EngineException;
	
}
