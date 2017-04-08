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
package org.reactivetechnologies.sentigrade.nlp;
/**
 * 
 * @author esutdal
 *
 */
public class SentimentVector {

	public double getOverallScore() {
		return overallScore;
	}
	public void setOverallScore(double overallScore) {
		this.overallScore = overallScore;
	}
	public double getAdjScore() {
		return adjScore;
	}
	public void setAdjScore(double adjScore) {
		this.adjScore = adjScore;
	}
	public double getAdvScore() {
		return advScore;
	}
	public void setAdvScore(double advScore) {
		this.advScore = advScore;
	}
	public double getConjScore() {
		return conjScore;
	}
	public void setConjScore(double conjScore) {
		this.conjScore = conjScore;
	}
	public double getModvScore() {
		return modvScore;
	}
	public void setModvScore(double modvScore) {
		this.modvScore = modvScore;
	}
	public double getNounScore() {
		return nounScore;
	}
	public void setNounScore(double nounScore) {
		this.nounScore = nounScore;
	}
	double overallScore = 0.0;
	double adjScore = 0.0;//JJ,JJR, JJS
	double advScore = 0.0;//RB,RBR,RBS
	double conjScore = 0.0;//CC
	double modvScore = 0.0;//MD (maybe, might, should..)
	double nounScore = 0.0;
	double verbScore = 0.0;
	public double getVerbScore() {
		return verbScore;
	}
	public void setVerbScore(double verbScore) {
		this.verbScore = verbScore;
	}
	@Override
	public String toString() {
		return "SentimentVector [overallScore=" + overallScore + ", adjScore=" + adjScore + ", advScore=" + advScore
				+ ", nounScore=" + nounScore + ", verbScore=" + verbScore + "]";
	}
}
