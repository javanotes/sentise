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
package org.reactivetechnologies.analytics.sentise.files;

import java.io.File;
import java.util.Set;

public interface DirectoryEventHandler {

	/**
	 * Callback for file creation/modify event.
	 * @param f
	 */
	void onFileTouched(File f);
	/**
	 * Invoked on watcher startup, for passing initial set of files already present.
	 * @param set
	 */
	void handleInitialFiles(Set<File> set);
}
