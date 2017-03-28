/* ============================================================================
*
* FILE: CombinerType.java
*
The MIT License (MIT)

Copyright (c) 2016 Sutanu Dalui

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*
* ============================================================================
*/
package org.reactivetechnologies.analytics.sentise.core;

import java.io.IOException;

import org.reactivetechnologies.analytics.sentise.EngineException;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.meta.Stacking;
import weka.classifiers.meta.Vote;
import weka.core.Instances;
import weka.core.Utils;

/**
 * VOTING/STACKING ensembling of classifiers.
 * 
 * @author esutdal
 *
 */
public enum CombinerType {

	STACKING {
		@Override
		public Classifier getEnsembleClassifier(Classifier[] classifiers, Instances instances, String optionStr)
				throws EngineException {
			Stacking s = new Stacking();
			s.setClassifiers(classifiers);
			if (StringUtils.hasText(optionStr)) {
				try {
					s.setOptions(Utils.splitOptions(optionStr));
				} catch (Exception e) {
					throw new EngineException(e);
				}
			}

			return s;

		}
	},
	VOTING {
		@Override
		public Classifier getEnsembleClassifier(Classifier[] classifiers, Instances instances, String optionStr)
				throws EngineException {
			Vote v = new Vote();
			v.setClassifiers(classifiers);
			if (StringUtils.hasText(optionStr)) {
				try {
					v.setOptions(Utils.splitOptions(optionStr));
				} catch (Exception e) {
					throw new EngineException(e);
				}
			}

			return v;
		}
	},
	EVALUATING {

		private Evaluation evaluateClassifier(Classifier cl, Instances ins) throws IOException, Exception {
			Evaluation eval = new Evaluation(ins);
			eval.useNoPriors();
			eval.evaluateModel(cl, ins);
			return eval;
		}

		@Override
		public Classifier getEnsembleClassifier(Classifier[] classifiers, Instances instances, String optionStr)
				throws EngineException {

			Assert.notNull(instances, "Instances needed for EVALUATING combiner");
			Classifier bestFit = null;
			double rms = Double.MAX_VALUE;
			for (Classifier cl : classifiers) {
				try {
					Evaluation eval = evaluateClassifier(cl, instances);
					double d = eval.rootMeanSquaredError();
					if (d < rms) {
						rms = d;
						bestFit = cl;
					}
				} catch (Exception e) {
					throw new EngineException("Exception while evaluating model", e);
				}
			}
			return bestFit;
		}
	};
	/**
	 * Get an ensemble classifier from the supplied ones. For {@link #VOTING}
	 * and {@link #STACKING}, no instances need to be passed.
	 * 
	 * @param classifiers
	 * @param instances
	 * @param optionStr
	 * @return
	 * @throws EngineException
	 */
	public abstract Classifier getEnsembleClassifier(Classifier[] classifiers, Instances instances, String optionStr)
			throws EngineException;

}
