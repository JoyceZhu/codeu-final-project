package com.flatironschool.javacs;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import redis.clients.jedis.Jedis;


/**
 * Represents the results of a search query.
 *
 */
public class WikiSearch {
	
	// map from URLs that contain the term(s) to relevance score
	private Map<String, Integer> map;

	/**
	 * Constructor.
	 * 
	 * @param map
	 */
	public WikiSearch(Map<String, Integer> map) {
		this.map = map;
	}
	
	/**
	 * Looks up the relevance of a given URL.
	 * 
	 * @param url
	 * @return
	 */
	public Integer getRelevance(String url) {
		Integer relevance = map.get(url);
		return relevance==null ? 0: relevance;
	}
	
	/**
	 * Prints the contents in order of term frequency.
	 * 
	 * @param map
	 */
	private  void print() {
		List<Entry<String, Integer>> entries = sort();
		for (Entry<String, Integer> entry: entries) {
			System.out.println(entry);
		}
	}
	
	/**
	 * Computes the union of two search results.
	 * 
	 * @param that
	 * @return New WikiSearch object.
	 */
	public WikiSearch or(WikiSearch that) {
        Map <String, Integer> temp = new HashMap <String, Integer>();
        // Or: sum up relevances for terms in either set of keys
        for (String term: map.keySet())
        {
            temp.put(term, this.getRelevance(term));           
        }
        for (String term: that.map.keySet())
        {
        	if (temp.containsKey(term))
        		temp.put(term, this.getRelevance(term) + that.getRelevance(term));
    		else // New term not in first Search object
    			temp.put(term, that.getRelevance(term)); 
        }
		return new WikiSearch(temp);
	}
	
	/**
	 * Computes the intersection of two search results.
	 * 
	 * @param that
	 * @return New WikiSearch object.
	 */
	public WikiSearch and(WikiSearch that) {
        Map <String, Integer> temp = new HashMap <String, Integer>();
        // And: sum up relevances for terms which exist in both sets of keys
        for (String term: map.keySet())
        {
            if (that.map.containsKey(term))
                temp.put(term, this.getRelevance(term) + that.getRelevance(term));
        }
		return new WikiSearch(temp);
	}
	
	/**
	 * Computes the intersection of two search results.
	 * 
	 * @param that
	 * @return New WikiSearch object.
	 */
	public WikiSearch minus(WikiSearch that) {
        Map <String, Integer> temp = new HashMap <String, Integer>();
        // Minus: include results in first map, but NOT second
        for (String term: map.keySet())
        	if (!that.map.containsKey(term))
        		temp.put(term, this.getRelevance(term));
		return new WikiSearch(temp);
	}
	
	/**
	 * Computes the relevance of a search with multiple terms.
	 * 
	 * @param rel1: relevance score for the first search
	 * @param rel2: relevance score for the second search
	 * @return
	 */
	protected int totalRelevance(Integer rel1, Integer rel2) {
		// simple starting place: relevance is the sum of the term frequencies.
		return rel1 + rel2;
	}

	/**
	 * Sort the results by relevance.
	 * 
	 * @return List of entries with URL and relevance.
	 */
	public List<Entry<String, Integer>> sort() {
        List<Entry<String, Integer>> temp = new LinkedList<Entry<String, Integer>>();
        for (Map.Entry<String, Integer> entry : map.entrySet())
			temp.add(entry);
		Comparator<Entry<String, Integer>> compareFunc = new Comparator<Entry<String, Integer>>(){		
			public int compare(Entry<String, Integer> w1, Entry<String, Integer> w2) {
				return (w1.getValue().compareTo(w2.getValue()));
			}
		};
		Collections.sort(temp, compareFunc);
		return temp;
	}

	/**
	 * Performs a search and makes a WikiSearch object.
	 * 
	 * @param term
	 * @param index
	 * @return
	 */
	public static WikiSearch search(String term, JedisIndex index) {
		Map<String, Integer> map = index.getCounts(term);
		return new WikiSearch(map);
	}

	public static void main(String[] args) throws IOException {
		
		// make a JedisIndex
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis); 
		
		// search for the first term
		String term1 = "java";
		System.out.println("Query: " + term1);
		WikiSearch search1 = search(term1, index);
		search1.print();
		
		// search for the second term
		String term2 = "programming";
		System.out.println("Query: " + term2);
		WikiSearch search2 = search(term2, index);
		search2.print();
		
		// compute the intersection of the searches
		System.out.println("Query: " + term1 + " AND " + term2);
		WikiSearch intersection = search1.and(search2);
		intersection.print();

		// test out JOpt simple

		OptionParser parser = new OptionParser( "a::" );
		parser.accepts( "term" ).withOptionalArg();
		parser.accepts("help");
		OptionSet options = parser.parse(args);
		//System.out.println(options.has("a"));
		if (options.has("a"))
			System.out.println("a: " + options.valueOf( "a" ));
		if (options.has("term"))
		{
			System.out.println("Query: " + options.valueOf("term"));
			search(options.valueOf("term").toString(), index).print();
		}
	}
}
