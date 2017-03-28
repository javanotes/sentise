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
package org.reactivetechnologies.analytics.sentise.dto;

import java.io.IOException;

import com.hazelcast.nio.ObjectDataInput;
import com.hazelcast.nio.ObjectDataOutput;
import com.hazelcast.nio.serialization.DataSerializable;

public class Signal implements DataSerializable {
	public Signal() {
	}

	public Signal(Byte command, String domain) {
		super();
		this.command = command;
		this.domain = domain;
	}

	public String getDomain() {
		return domain;
	}

	public Byte getCommand() {
		return command;
	}

	private Byte command;
	private String domain;

	@Override
	public void writeData(ObjectDataOutput out) throws IOException {
		out.writeByte(command);
		out.writeUTF(domain);
	}

	@Override
	public void readData(ObjectDataInput in) throws IOException {
		command = in.readByte();
		domain = in.readUTF();
	}
}