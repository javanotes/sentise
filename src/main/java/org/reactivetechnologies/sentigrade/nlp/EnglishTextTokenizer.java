/* ============================================================================
*
* FILE: EnglishTextTokenizer.java
*
* MODULE DESCRIPTION:
* See class description
*
* Copyright (C) 2015 by
* 
*
* The program may be used and/or copied only with the written
* permission from  or in accordance with
* the terms and conditions stipulated in the agreement/contract
* under which the program has been supplied.
*
* All rights reserved
*
* ============================================================================
*/
package org.reactivetechnologies.sentigrade.nlp;

import java.io.IOException;
import java.io.StringReader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.core.StopFilter;
import org.apache.lucene.analysis.en.EnglishPossessiveFilter;
import org.apache.lucene.analysis.ngram.NGramTokenFilter;
import org.apache.lucene.analysis.shingle.ShingleFilter;
import org.apache.lucene.analysis.snowball.SnowballFilter;
import org.apache.lucene.analysis.standard.ClassicFilter;
import org.apache.lucene.analysis.standard.ClassicTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.util.CharArraySet;
import org.reactivetechnologies.sentigrade.err.OperationFailedUnexpectedly;
import org.springframework.util.Assert;
import org.tartarus.snowball.ext.PorterStemmer;
/**
 * A Lucene based analyzer for gram tokenizing plain english texts.
 * @author esutdal
 *
 */
public class EnglishTextTokenizer extends Analyzer implements Iterator<String>{

	private final static Set<String> characters = new HashSet<>();
	static {
		for (char i = 32; i <= 126; i++) {
			characters.add(Character.toString(i));
		}
	}

	private EnglishTextTokenizer() {
		super();
	}
	private String text;
	private TokenStream stream;
	public EnglishTextTokenizer(String text) {
		this();
		this.text = text;
	}
	private boolean opened;
	public void open() throws IOException
	{
		stream = tokenStream(null, new StringReader(text));
		stream.reset();
		opened = true;
	}

	/**
	 * 
	 * @param analyzer
	 * @param text
	 * @return
	 * @throws IOException
	 */
	public static Set<String> getTokens(String text) throws IOException {
		Set<String> result = new HashSet<String>();
		TokenStream stream = null;
		Analyzer analyzer = new EnglishTextTokenizer();
		try {
			stream = analyzer.tokenStream(null, new StringReader(text));
			stream.reset();
			while (stream.incrementToken()) {
				result.add(stream.getAttribute(CharTermAttribute.class).toString());
			}

		} catch (IOException e) {
			throw e;
		} finally {
			if (stream != null) {
				stream.end();
				stream.close();
			}
			analyzer.close();
		}
		return result;
	}

	@Override
	public void close()
	{
		if (stream != null) {
			try {
				stream.end();
				stream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		super.close();
	}
	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		Tokenizer source = new ClassicTokenizer();
		CharArraySet stopWords = CharArraySet.copy(characters);
		stopWords.addAll(StopAnalyzer.ENGLISH_STOP_WORDS_SET);

		TokenFilter filter = new ClassicFilter(source);
		filter = new EnglishPossessiveFilter(filter);
		filter = new LowerCaseFilter(filter);
		filter = new ShingleFilter(filter, 2, 2);
		filter = new StopFilter(filter, stopWords);
		if (enableStemming) {
			filter = new SnowballFilter(filter, new PorterStemmer());
		}
		filter = new NGramTokenFilter(filter, 1, 3);
		
		return new TokenStreamComponents(source, filter);

	}

	private boolean enableStemming;
	@Override
	public boolean hasNext() {
		Assert.isTrue(opened, "TokenStream not opened!");
		try {
			return stream.incrementToken();
		} catch (IOException e) {
			throw new OperationFailedUnexpectedly(e);
		}
	}

	@Override
	public String next() {
		try {
			return stream.getAttribute(CharTermAttribute.class).toString();
		} catch (NullPointerException e) {
			throw new NoSuchElementException();
		}
	}

	public boolean isEnableStemming() {
		return enableStemming;
	}

	public void setEnableStemming(boolean enableStemming) {
		this.enableStemming = enableStemming;
	}

}
