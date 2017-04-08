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
package org.reactivetechnologies.sentigrade.engine.weka;

import org.reactivetechnologies.sentigrade.nlp.LemmatizationStemmer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import weka.attributeSelection.InfoGainAttributeEval;
import weka.attributeSelection.Ranker;
import weka.core.Instances;
import weka.core.Utils;
import weka.core.tokenizers.NGramTokenizer;
import weka.filters.Filter;
import weka.filters.supervised.attribute.AttributeSelection;
import weka.filters.unsupervised.attribute.NumericToNominal;
import weka.filters.unsupervised.attribute.Reorder;
import weka.filters.unsupervised.attribute.StringToWordVector;
/**
 * 	A  document  is  converted  in  Vector  space  representation  which  is  a  standard  procedure  in  the  representation  of 
	documents. Features in documents are in the form of N-gram, POS tags, named entities, topics. The value of feature is binary  (presence/absence),  
	Binary  or  TF-IDF  (many  variants).  <p>To  select  the  most  informative  features  for  model 
	training  various  filters  are  used  that  also  reduce  noise  in  feature  representation  and  thus  improve  final  classification performance. <p>
	The major activity in text  mining  is to classify document into the  accurate  category. Feature  selection  methods are  widely 
	used for gathering most valuable  words for each category in text mining processes. They help to find most distinctive 
	words  for  each  category  by  calculating  some  variables  on  data.
 * 
 * @author esutdal
 *@see https://www.ijirset.com/upload/2015/november/65_Text.pdf
 */
public class Preprocessor {

	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(Preprocessor.class);
	private Preprocessor() {
		super();
	}
	
	public static class ArgSwitch
	{
		boolean useLucene = false;
		boolean useFeatureSelect = true;
		boolean useNominalAttrib = true;
		boolean useLemmatizer = false;
		
		public boolean isUseLemmatizer() {
			return useLemmatizer;
		}
		public void setUseLemmatizer(boolean useLemmatizer) {
			this.useLemmatizer = useLemmatizer;
		}
		public boolean isUseNominalAttrib() {
			return useNominalAttrib;
		}
		public void setUseNominalAttrib(boolean useNominalAttrib) {
			this.useNominalAttrib = useNominalAttrib;
		}
		public boolean isUseLucene() {
			return useLucene;
		}
		public void setUseLucene(boolean useLucene) {
			this.useLucene = useLucene;
		}
		public boolean isUseFeatureSelect() {
			return useFeatureSelect;
		}
		public void setUseFeatureSelect(boolean useFeatureSelect) {
			this.useFeatureSelect = useFeatureSelect;
		}
	}

	/**
	 * Converts String attributes into a set of attributes representing word
	 * occurrence information from the text contained in the strings. The set of
	 * words (attributes) is determined by the first batch filtered (typically
	 * training data). Uses a Lucene analyzer to tokenize the string.
	 * @param dataRaw
	 * @param args
	 * @return
	 * @throws Exception
	 */
	public static Instances process(Instances dataRaw, ArgSwitch args) throws Exception {

		//http://www.stefanoscerra.it/movie-reviews-classification-weka-data-mining/
		//https://weka.wikispaces.com/Text+categorization+with+WEKA
		//http://jmgomezhidalgo.blogspot.in/2013/06/baseline-sentiment-analysis-with-weka.html
		//http://jmgomezhidalgo.blogspot.in/2013/02/text-mining-in-weka-revisited-selecting.html
		
		//Most classifiers in Weka cannot handle String attributes. For these learning schemes one has to process the data with appropriate filters, 
		//e.g., the StringToWordVector filter which can perform TF/IDF transformation
		
		LuceneWordTokenizer lucenTokens = null;
		
		
		//swFilter.setPeriodicPruning(-1.0);
		//swFilter.setDoNotOperateOnPerClassBasis(true);
		//swFilter.setWordsToKeep(1000);
		//swFilter.setAttributeIndices("first");

		try 
		{
			StringToWordVector strToWord = new StringToWordVector();
			
			if (args.useLucene) {
				lucenTokens = new LuceneWordTokenizer();
				strToWord.setTokenizer(lucenTokens);
			}
			else
				strToWord.setTokenizer(new NGramTokenizer());
			
			final Instances struct = AbstractIncrementalModelEngine.getStructure(dataRaw);
			
			if (args.useLemmatizer) {
				strToWord.setStemmer(new LemmatizationStemmer());
			}
			else
			{
				if(lucenTokens != null)
					lucenTokens.enableStemming(true);
					
			}
			strToWord.setInputFormat(struct);
			
			Instances wordVector = Filter.useFilter(dataRaw, strToWord);
			
			
			//The StringToWordVector filter places the class attribute of the generated output data at the beginning.
			Reorder reord = new Reorder();
			reord.setOptions(Utils.splitOptions("-R 2-last,first"));
			reord.setInputFormat(AbstractIncrementalModelEngine.getStructure(wordVector));
			wordVector = Filter.useFilter(wordVector, reord);
			
			if (args.useNominalAttrib) {
				//numeric to nominal filter
				NumericToNominal numnom = new NumericToNominal();
				numnom.setInputFormat(AbstractIncrementalModelEngine.getStructure(wordVector));
				numnom.setOptions(Utils.splitOptions("-R 2"));
				wordVector = Filter.useFilter(wordVector, numnom);
			}
			
			if (args.useFeatureSelect) {
				//feature selection
				AttributeSelection asFilter = new AttributeSelection();
				asFilter.setInputFormat(AbstractIncrementalModelEngine.getStructure(wordVector));
				asFilter.setEvaluator(new InfoGainAttributeEval());//chi-square, gain ratio
				Ranker bfSearch = new Ranker();
				bfSearch.setThreshold(0.0);
				asFilter.setSearch(bfSearch);
				//asFilter.setOptions(Utils.splitOptions("-c 1"));
				wordVector = Filter.useFilter(wordVector, asFilter);
			}
			return wordVector;
		} 
		finally {
			if(lucenTokens != null)
				lucenTokens.close();
		}
	}

}
