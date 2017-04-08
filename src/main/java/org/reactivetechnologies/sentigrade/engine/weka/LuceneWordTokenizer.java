/* ============================================================================
*
* FILE: LuceneTokenizerFilter.java
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

import java.io.Closeable;
import java.util.Iterator;

import org.reactivetechnologies.sentigrade.nlp.EnglishTextTokenizer;
import org.springframework.util.Assert;

import weka.core.tokenizers.WordTokenizer;

/**
 * @author esutdal
 *
 */
class LuceneWordTokenizer extends WordTokenizer implements Closeable{
	public LuceneWordTokenizer() {
		this(false);
	}

	public LuceneWordTokenizer(boolean fallback) {
		super();
		this.fallback = fallback;
	}

	private final boolean fallback;
	/**
	 * 
	 */
	private static final long serialVersionUID = 7965082235263621687L;

	/**
	 * Returns a string describing the stemmer
	 * 
	 * @return a description suitable for displaying in the
	 *         explorer/experimenter gui
	 */
	public String globalInfo() {
		return "Using Lucene analyzer to tokenize the strings.";
	}

	/**
	 * Tests if this enumeration contains more elements.
	 * 
	 * @return true if and only if this enumeration object contains at least one
	 *         more element to provide; false otherwise.
	 */
	public boolean hasMoreElements() {
		if (fallback) {
			return super.hasMoreElements();
		}
		else
		{
			Assert.notNull(tokens);
			return tokens.hasNext();
		}
	}

	/**
	 * Returns the next element of this enumeration if this enumeration object
	 * has at least one more element to provide.
	 * 
	 * @return the next element of this enumeration.
	 */
	public String nextElement() {
		if (fallback) {
			return super.nextElement();
		}
		else
		{
			Assert.notNull(tokens);
			return tokens.next();
		}
	}

	private Iterator<String> tokens;

	private boolean enableStemming;
	public boolean isEnableStemming() {
		return enableStemming;
	}

	public void enableStemming(boolean enableStemming) {
		this.enableStemming = enableStemming;
	}

	/**
	 * Sets the string to tokenize. Tokenization happens immediately.
	 * 
	 * @param s
	 *            the string to tokenize
	 */
	@Override
	public void tokenize(String s) {
		if (fallback) {
			 super.tokenize(s);
		}
		else
		{
			try 
			{
				analyzer = new EnglishTextTokenizer(s);
				analyzer.setEnableStemming(enableStemming);
				analyzer.open();
				tokens = analyzer;
			} 
			catch (Exception e) {
				throw new UnsupportedOperationException("Failed to tokenize stream using Lucene", e);
			}
		}

	}

	private EnglishTextTokenizer analyzer;
	@Override
	public String getRevision() {
		return "n/a";
	}

	@Override
	public void close()  {
		if(analyzer != null)
			analyzer.close();
	}

}
