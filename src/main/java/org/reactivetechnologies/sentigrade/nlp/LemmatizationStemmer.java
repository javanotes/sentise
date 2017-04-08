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
package org.reactivetechnologies.sentigrade.nlp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.util.CoreMap;
import weka.core.stemmers.Stemmer;

public class LemmatizationStemmer implements Stemmer {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static StanfordCoreNLP pipeline;
	static {
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize, ssplit, pos, lemma, ner");// ,
																				// parse,
																				// sentiment
		pipeline = new StanfordCoreNLP(props);
	}

	public LemmatizationStemmer() {
	}

	@Override
	public String getRevision() {
		return "";
	}
	
	private static void put(Map<String, AtomicInteger> map, String token)
	{
		if(!map.containsKey(token))
			map.put(token, new AtomicInteger());
		
		map.get(token).incrementAndGet();
	}
	/**
	 * The frequency distribution of each lemma.
	 * @param word
	 * @return
	 */
	public Map<String, AtomicInteger> frequencyDistribution(String word) 
	{
		Map<String, AtomicInteger> freqDist = new LinkedHashMap<>();
		// create an empty Annotation just with the given text
		Annotation document = new Annotation(word);
		// run all Annotators on this text
		pipeline.annotate(document);

		// these are all the sentences in this document
		// a CoreMap is essentially a Map that uses class objects as keys and
		// has values with custom types
		List<CoreMap> sentences = document.get(SentencesAnnotation.class);

		String lemma;
		String ne;
		for (CoreMap sentence : sentences) {
			// traversing the words in the current sentence
			// a CoreLabel is a CoreMap with additional token-specific methods

			for (CoreLabel token : sentence.get(TokensAnnotation.class)) {

				lemma = token.get(LemmaAnnotation.class);
				ne = token.get(NamedEntityTagAnnotation.class);

				if (filterNamedEntityTags && "O".equals(ne))
					put(freqDist, lemma);
				else
					put(freqDist, lemma);
			}

		}
		return freqDist;

	}

	private boolean filterNamedEntityTags;

	@Override
	public String stem(String word) {
		Map<String, AtomicInteger> fd = frequencyDistribution(word);
		StringBuilder lemmas = new StringBuilder();
		for(String lemma: fd.keySet())
			lemmas.append(lemma).append(" ");
		
		return lemmas.toString();
	}

	public boolean isFilterNamedEntityTags() {
		return filterNamedEntityTags;
	}

	public void setFilterNamedEntityTags(boolean filterNamedEntityTags) {
		this.filterNamedEntityTags = filterNamedEntityTags;
	}

}
