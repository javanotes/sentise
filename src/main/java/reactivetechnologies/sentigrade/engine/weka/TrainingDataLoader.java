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
package reactivetechnologies.sentigrade.engine.weka;

import java.io.IOException;

import weka.core.Instances;

public interface TrainingDataLoader {
	/**
	 * Load data set from a root directory, containing child directories with the the name of class attributes. This basically
	 * mimics the {@linkplain TextDirectoryLoader} of Weka. Each class directory can have multiple text files, where each file
	 * content will represent a single data instance.
	 * @param domain
	 * @param dir
	 * @return
	 * @throws IOException
	 */
	Instances loadFromRootDirectory(String domain, String dir) throws IOException;
	/**
	 * Load data set from a root directory, containing plain text data files. Each line of a file will represent a single data
	 * instance and a tab separated code to denote its class. Like '[sentence] \t [class]'.
	 * @param domain
	 * @param dir
	 * @param tabFormat
	 * @return
	 * @throws IOException
	 */
	Instances loadFromFormattedText(String domain, String dir, String tabFormat) throws IOException;

}