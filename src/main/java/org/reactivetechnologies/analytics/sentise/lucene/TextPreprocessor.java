/* ============================================================================
*
* FILE: TextInstanceFilter.java
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
package org.reactivetechnologies.analytics.sentise.lucene;

import org.reactivetechnologies.analytics.sentise.core.AbstractIncrementalModelEngine;
import org.springframework.util.StringUtils;

import weka.core.Instances;
import weka.core.Utils;
import weka.filters.Filter;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.attribute.Reorder;
import weka.filters.unsupervised.attribute.StringToWordVector;

public class TextPreprocessor {

	private TextPreprocessor() {
		super();
	}

	/**
	 * Converts String attributes into a set of attributes representing word
	 * occurrence information from the text contained in the strings. The set of
	 * words (attributes) is determined by the first batch filtered (typically
	 * training data). Uses a Lucene analyzer to tokenize the string.
	 * <p>
	 * NOTE: The text string should either be the first or last attribute.
	 * 
	 * @param dataRaw
	 * @param opts
	 * @param isTextLast
	 *            - whether last attribute is the text to be filtered, else
	 *            first
	 * @return
	 * @throws Exception
	 * @see {@linkplain StringToWordVector}
	 */
	public static Instances filter(Instances dataRaw, String opts, boolean isTextLast, boolean useLucene) throws Exception {

		//http://www.stefanoscerra.it/movie-reviews-classification-weka-data-mining/
		//https://weka.wikispaces.com/Text+categorization+with+WEKA
		
		//Most classifiers in Weka cannot handle String attributes. For these learning schemes one has to process the data with appropriate filters, 
		//e.g., the StringToWordVector filter which can perform TF/IDF transformation
		StringToWordVector swFilter = new StringToWordVector();
		if (StringUtils.hasText(opts)) {
			swFilter.setOptions(Utils.splitOptions(opts));
		}
		if (useLucene) {
			swFilter.setTokenizer(new LuceneWordTokenizer());
		}
		final Instances struct = AbstractIncrementalModelEngine.getStructure(dataRaw);
		
		//swFilter.setStemmer(new NullStemmer());// ignore any other stemmer
		swFilter.setInputFormat(struct);
		
		//swFilter.setPeriodicPruning(-1.0);
		//swFilter.setDoNotOperateOnPerClassBasis(true);
		//swFilter.setWordsToKeep(1000);
		//swFilter.setAttributeIndices("first");

		/*AttributeSelection asFilter = new AttributeSelection();
		asFilter.setInputFormat(dataRaw);
		asFilter.setEvaluator(new CfsSubsetEval());
		BestFirst bfSearch = new BestFirst();
		bfSearch.setOptions(Utils.splitOptions("-D 1 -N 5"));
		asFilter.setSearch(bfSearch);*/
		
		Instances wordVector = Filter.useFilter(dataRaw, swFilter);
		
		//string to nominal filter
		//StringToNominal strnom = new StringToNominal();
		//strnom.setInputFormat(struct);
		//wordVector = Filter.useFilter(wordVector, strnom);
		
		//The StringToWordVector filter places the class attribute of the generated output data at the beginning.
		Reorder reord = new Reorder();
		reord.setOptions(Utils.splitOptions("-R 2-last,first"));
		reord.setInputFormat(AbstractIncrementalModelEngine.getStructure(wordVector));
		wordVector = Filter.useFilter(wordVector, reord);
		
		//numeric to nominal filter
		NumericToNominal numnom = new NumericToNominal();
		numnom.setInputFormat(AbstractIncrementalModelEngine.getStructure(wordVector));
		numnom.setOptions(Utils.splitOptions("-R 2"));
		wordVector = Filter.useFilter(wordVector, numnom);
		
		//wordVector = Filter.useFilter(wordVector, asFilter);

		return wordVector;
	}

}
