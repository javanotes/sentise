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
package org.reactivetechnologies.sentise.nlp;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.ResourceUtils;

/**
 * http://sentiwordnet.isti.cnr.it/
 * 
 * @author esutdal
 *
 */
class SWN3 {

	private final Map<String, Double> dictionary = new LinkedHashMap<>();
	private final Map<String, Double> dictionary2 = new LinkedHashMap<>();

	void approach2() throws Exception 
	{
		HashMap<String, List<Double>> _temp = new HashMap<>();

		BufferedReader csv = null;
		try 
		{
			csv = new BufferedReader(new FileReader(
					ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + "SentiWordNet_3.0.0.txt")));
			String line = "";
			while ((line = csv.readLine()) != null) {
				if (line.isEmpty())
					continue;
				if(line.trim().startsWith("#"))
					continue;
				
				String[] data = line.split("\t");

				if (data[2].isEmpty() || data[3].isEmpty())
					continue;
				Double score = Double.parseDouble(data[2]) - Double.parseDouble(data[3]);
				String[] words = data[4].split(" ");
				for (String w : words) {
					if (w.isEmpty())
						continue;

					String[] w_n = w.split("#");
					w_n[0] += "#" + data[0];
					int index = Integer.parseInt(w_n[1]) - 1;
					if (_temp.containsKey(w_n[0])) {
						List<Double> l = _temp.get(w_n[0]);
						if (index > l.size())
							for (int i = l.size(); i < index; i++)
								l.add(0.0);
						l.add(index, score);
						_temp.put(w_n[0], l);
					} else {
						List<Double> l = new ArrayList<>();
						for (int i = 0; i < index; i++)
							l.add(0.0);
						l.add(index, score);
						_temp.put(w_n[0], l);
					}
				}
			}

			Set<String> temp = _temp.keySet();
			for (Iterator<String> iterator = temp.iterator(); iterator.hasNext();) {
				String word = iterator.next();
				List<Double> l = _temp.get(word);
				double score = 0.0;
				double sum = 0.0;
				for (int i = 0; i < l.size(); i++)
					score += ((double) 1 / (double) (i + 1)) * l.get(i);
				for (int i = 1; i <= l.size(); i++)
					sum += (double) 1 / (double) i;
				score /= sum;
				dictionary2.put(word, score);
			}
		} 
		catch (Exception e) {
			throw e;
		} finally {
			if (csv != null) {
				try {
					csv.close();
				} catch (IOException e) {
				}
			}
		}

	}

	private Map<String, Map<Integer, Double>> readSynSet(BufferedReader csv) throws NumberFormatException, IOException, ParseException {
		int lineNumber = 0;
		// From String to list of doubles.
		Map<String, Map<Integer, Double>> tempDictionary = new HashMap<String, Map<Integer, Double>>();
		String line;
		while ((line = csv.readLine()) != null) {
			lineNumber++;

			// If it's a comment, skip this line.
			if (!line.trim().startsWith("#")) {
				// We use tab separation
				String[] data = line.split("\t");
				String wordTypeMarker = data[0];

				// Example line:
				// POS ID PosS NegS SynsetTerm#sensenumber Desc
				// a 00009618 0.5 0.25 spartan#4 austere#3 ascetical#2
				// ascetic#2 practicing great self-denial;...etc

				// Is it a valid line? Otherwise, through exception.
				if (data.length != 6) {
					throw new ParseException("Incorrect tabulation format in file. Tokens Expected 6, Found "+data.length , lineNumber);
				}

				// Calculate synset score as score = PosS - NegS
				Double synsetScore = Double.parseDouble(data[2]) - Double.parseDouble(data[3]);

				// Get all Synset terms
				String[] synTermsSplit = data[4].split(" ");

				// Go through all terms of current synset.
				for (String synTermSplit : synTermsSplit) {
					// Get synterm and synterm rank
					String[] synTermAndRank = synTermSplit.split("#");
					String synTerm = synTermAndRank[0] + "#" + wordTypeMarker;

					int synTermRank = Integer.parseInt(synTermAndRank[1]);
					// What we get here is a map of the type:
					// term -> {score of synset#1, score of synset#2...}

					// Add map to term if it doesn't have one
					if (!tempDictionary.containsKey(synTerm)) {
						tempDictionary.put(synTerm, new TreeMap<Integer, Double>());
					}

					// Add synset link to synterm
					tempDictionary.get(synTerm).put(synTermRank, synsetScore);
				}
			}
		}
		return tempDictionary;
	}

	private void prepareTable(Map<String, Map<Integer, Double>> synonymSet) {
		// Go through all the terms.
		for (Map.Entry<String, Map<Integer, Double>> entry : synonymSet.entrySet()) {
			String word = entry.getKey();
			Map<Integer, Double> synSetScoreMap = entry.getValue();

			// Calculate weighted average. Weigh the synsets according to
			// their rank.
			// Score= 1/2*first + 1/3*second + 1/4*third ..... etc.
			// Sum = 1/1 + 1/2 + 1/3 ...
			double score = 0.0;
			double sum = 0.0;
			for (Map.Entry<Integer, Double> setScore : synSetScoreMap.entrySet()) {
				score += setScore.getValue() / (double) setScore.getKey();
				sum += 1.0 / (double) setScore.getKey();
			}
			score /= sum;

			dictionary.put(word, score);
		}
	}

	private static final Logger log = LoggerFactory.getLogger(SWN3.class);

	private void load0() throws IOException {
		// This is our main dictionary representation
		dictionary.clear();
		loaded = false;
		BufferedReader csv = null;
		try {
			csv = new BufferedReader(new FileReader(
					ResourceUtils.getFile(ResourceUtils.CLASSPATH_URL_PREFIX + "SentiWordNet_3.0.0.txt")));

			Map<String, Map<Integer, Double>> synonymSet = readSynSet(csv);
			prepareTable(synonymSet);
			loaded = true;
			log.info("SentiWordNet loaded..");
			
			//approach2();
			
		} catch (Exception e) {
			throw new IllegalStateException(e);
		} finally {
			if (csv != null) {
				csv.close();
			}
		}
	}

	private volatile boolean loaded;

	public boolean isLoaded() {
		return loaded;
	}

	public synchronized void load(boolean force) throws IOException {
		if (force) {
			load0();
		} else if (!loaded)
			load0();
	}

	public SWN3() {

	}

	public static final String NEUTRAL_SENTI = "neutral";
	public static final String STRONG_POS_SENTI = "strong_positive";
	public static final String POS_SENTI = "positive";
	public static final String WEAK_POS_SENTI = "weak_positive";
	public static final String STRONG_NEG_SENTI = "strong_negative";
	public static final String NEG_SENTI = "negative";
	public static final String WEAK_NEG_SENTI = "weak_negative";
	
	/**
	 * Get the class for a given score.
	 * @param score
	 * @return
	 */
	public static String classForScore(double score) {
		String sent = NEUTRAL_SENTI;
		if (score >= 0.75)
			sent = STRONG_POS_SENTI;
		else if (score > 0.25 && score < 0.75)
			sent = POS_SENTI;
		else if (score > 0 && score <= 0.25)
			sent = WEAK_POS_SENTI;
		else if (score < 0 && score >= -0.25)
			sent = WEAK_NEG_SENTI;
		else if (score < -0.25 && score > -0.75)
			sent = NEG_SENTI;
		else if (score <= -0.75)
			sent = STRONG_NEG_SENTI;
		return sent;
	}
	
	/**
	 * Score on all basis
	 * @param word
	 * @return
	 */
	public double extract(String word) {
		double total = 0.0;
		total += extract(word, "#n");
		total += extract(word, "#a");
		total += extract(word, "#r");
		total += extract(word, "#v");

		return total;
	}

	public double extract(String word, String pos) {
		Assert.isTrue(loaded, "Data not loaded!");
		String key = word.replaceAll("([^a-zA-Z\\s])", "") + "#" + pos;
		//System.out.println("Contains "+key+"? "+dictionary.containsKey(key));
		return dictionary.containsKey(key) ? dictionary.get(key) : 0.0;
	}
	private double extract2(String word, String pos) {
		Assert.isTrue(loaded, "Data not loaded!");
		String key = word.replaceAll("([^a-zA-Z\\s])", "") + "#" + pos;
		return dictionary2.containsKey(key) ? dictionary.get(key) : 0.0;
	}

	public String extractClass(String word, String pos) {
		return classForScore(extract(word, pos));
	}

	/**
	 * @deprecated
	 * @param word
	 * @param pos
	 * @return
	 */
	@SuppressWarnings("unused")
	private String extractClass2(String word, String pos) {
		return classForScore(extract2(word, pos));
	}
	/**
	 * Sentiment class for adjective
	 * @param word
	 * @return
	 */
	public double extractAdj(String word) {
		return extract(word, "a");
	}
	/**
	 * Sentiment class for verb
	 * @param word
	 * @return
	 */
	public double extractVerb(String word) {
		return extract(word, "v");
	}
	/**
	 * Sentiment class for adverb
	 * @param word
	 * @return
	 */
	public double extractAdv(String word) {
		return extract(word, "r");
	}
	/**
	 * 
	 * @param word
	 * @return
	 */
	public double extractNoun(String word) {
		return extract(word, "n");
	}
	public static void main(String[] args) throws IOException {

		/*SWN3 sentiwordnet = new SWN3();
		sentiwordnet.load();
		System.out.println("good#a " + sentiwordnet.extractClass("good", "a"));
		System.out.println("bad#a " + sentiwordnet.extractClass("bad", "a"));
		System.out.println("blue#a " + sentiwordnet.extractClass("blue", "a"));
		System.out.println("corrupt#n " + sentiwordnet.extractClass("corrupt", "a"));
		System.out.println("this_good#r " + sentiwordnet.extractClass("this_good", "v"));*/
		
		/*System.out.println("good#a " + sentiwordnet.extractClass2("good", "a"));
		System.out.println("bad#a " + sentiwordnet.extractClass2("bad", "a"));
		System.out.println("blue#a " + sentiwordnet.extractClass2("blue", "a"));
		System.out.println("blue#n " + sentiwordnet.extractClass2("blue", "n"));*/
	}

	public void load() throws IOException {
		load(false);
	}

}
