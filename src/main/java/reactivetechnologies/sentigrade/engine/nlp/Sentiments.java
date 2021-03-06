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
package reactivetechnologies.sentigrade.engine.nlp;

class Sentiments extends SentimentVector
{
	private int n=0;
	public synchronized void add(SentimentVector s)
	{
		this.overallScore += s.overallScore;
		this.adjScore += s.adjScore;
		this.advScore += s.advScore;
		this.nounScore += s.nounScore;
		this.verbScore += s.verbScore;
		n++;
	}
	
	public synchronized void normalize()
	{
		if(n > 1)
		{
			overallScore /= n;
			adjScore /= n;
			advScore /= n;
			nounScore /= n;
			verbScore /= n;
		}
	}
	
}