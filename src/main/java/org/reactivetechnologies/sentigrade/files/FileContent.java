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
package org.reactivetechnologies.sentigrade.files;

public class FileContent {
	public FileContent() {

	}
	/**
	 * 
	 * @param content
	 * @param fileName
	 * @param subdirPath
	 */
	public FileContent(String content, String fileName, String subdirPath) {
		super();
		this.setContent(content);
		this.setFileName(fileName);
		this.setSubdirPath(subdirPath);
	}

	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}

	public boolean isStop() {
		return stop;
	}
	public void setStop(boolean stop) {
		this.stop = stop;
	}

	public String getFileName() {
		return fileName;
	}
	public void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public String getSubdirPath() {
		return subdirPath;
	}
	public void setSubdirPath(String subdirPath) {
		this.subdirPath = subdirPath;
	}

	public int getkIndex() {
		return kIndex;
	}
	public void setkIndex(int kIndex) {
		this.kIndex = kIndex;
	}

	private String fileName;
	private String content;
	private boolean stop;
	private String subdirPath;
	private int kIndex;
}