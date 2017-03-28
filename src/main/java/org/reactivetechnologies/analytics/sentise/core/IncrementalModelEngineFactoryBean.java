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
package org.reactivetechnologies.analytics.sentise.core;

import java.lang.reflect.Method;

import org.reactivetechnologies.ticker.utils.ApplicationContextWrapper;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.core.Utils;

public class IncrementalModelEngineFactoryBean implements FactoryBean<AbstractIncrementalModelEngine> {

	@Value("${weka.classifier:weka.classifiers.bayes.NaiveBayesUpdateable}")
	private String wekaClassifier;

	@Value("${weka.classifier.options: }")
	private String options;
	@Override
	public AbstractIncrementalModelEngine getObject() throws Exception {
		Classifier c = null;
		try {
			c = AbstractClassifier.forName(wekaClassifier,
					StringUtils.hasText(options) ? Utils.splitOptions(options) : null);
		} catch (Exception e) {
			c = (Classifier) ApplicationContextWrapper.newInstance(wekaClassifier);
			if(StringUtils.hasText(options))
			{
				Method m = ReflectionUtils.findMethod(c.getClass(), "setOptions");
				if(m != null)
					ReflectionUtils.invokeMethod(m, c, (Object[])Utils.splitOptions(options));
			}
			
		}

		return new CachedIncrementalClassifierBean(c);
	}

	@Override
	public Class<?> getObjectType() {
		return AbstractIncrementalModelEngine.class;
	}

	@Override
	public boolean isSingleton() {
		return false;
	}

}
