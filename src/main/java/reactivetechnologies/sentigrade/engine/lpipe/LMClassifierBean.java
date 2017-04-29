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
package reactivetechnologies.sentigrade.engine.lpipe;

import org.reactivetechnologies.ticker.messaging.Data;

import com.aliasi.classify.DynamicLMClassifier;
import com.aliasi.classify.LMClassifier;
import com.aliasi.stats.MultivariateEstimator;

import reactivetechnologies.sentigrade.engine.ClassificationModelEngine;
import weka.core.Instance;

public class LMClassifierBean implements ClassificationModelEngine<LMClassifier<?,MultivariateEstimator>> {

	private LMClassifier<?,MultivariateEstimator> classifier;
	public LMClassifierBean() {
		classifier = DynamicLMClassifier.createNGramProcess(new String[]{}, 8);
	}

	@Override
	public void incrementModel(Data nextInstance) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void incrementModel(Instance nextInstance) throws Exception {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String classifierAlgorithm() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LMClassifier<?, MultivariateEstimator> classifierInstance() {
		return classifier;
	}

}
