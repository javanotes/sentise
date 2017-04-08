/* ============================================================================
*
* FILE: ClassifiedModel.java
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
package org.reactivetechnologies.sentigrade.dto;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;

import weka.core.xml.XStream;

/**
 * A wrapper for the ensemble {@linkplain Classifier} built by a
 * {@linkplain ModelExecutionService} service.
 * 
 * @author esutdal
 *
 */
public class ClassifiedModel<T> implements Serializable {

	public ClassifiedModel(CombinerResult status, T model) {
		super();
		this.status = status;
		this.model = model;
	}
	public ClassifiedModel(T model) {
		this(CombinerResult.IGNORED, model);
	}

	/**
	   * 
	   */
	private static final long serialVersionUID = 1830881064534581068L;
	public final CombinerResult status;
	public final T model;
	
	public void writeModel(OutputStream stream) throws Exception
	{
		XStream.write(stream, model);
	}
	
	@SuppressWarnings("unchecked")
	public T readModel(InputStream stream) throws Exception
	{
		return (T) XStream.read(stream);
	}

}
