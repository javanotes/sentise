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
package org.reactivetechnologies.sentise.dto;

public class ResponseData {

	public static enum Mode {LOCAL,CLUSTER}
	public static final int CODE_OK = 0;
	public static final int CODE_NOK = 1;
	int code = CODE_OK;
	String message = "";
	String classification = "";
	Mode model = Mode.CLUSTER;
	public Mode getModel() {
		return model;
	}
	public void setModel(Mode model) {
		this.model = model;
	}
	public int getCode() {
		return code;
	}
	public void setCode(int code) {
		this.code = code;
	}
	public String getMessage() {
		return message;
	}
	public void setMessage(String message) {
		this.message = message;
	}
	public String getClassification() {
		return classification;
	}
	public void setClassification(String classification) {
		this.classification = classification;
	}
	public ResponseData() {
	}

}
