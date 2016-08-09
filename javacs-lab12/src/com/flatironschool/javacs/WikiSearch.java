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
	private void print() {
		List<Entry<String, Integer>> entries = sort();
		for (Entry<String, Integer> entry: entries) {
			System.out.println(entry);
		}
	}

	/**
	 * Simply prints how many documents had the term indexed.
	 *
	 * @param term
	 */
	private void printNumDocs(String term) {
		List<Entry<String, Integer>> entries = sort();
		System.out.println(term + ": " + entries.size() + " documents");
	}

	/**
	 * Only prints the top N results for the given term.
	 * If there are fewer than N results, prints them all.
	 *
	 * @param limit
	 * @param term
	 */
	private void printTopN(int limit, String term) {
		List<Entry<String, Integer>> entries = sort();
		System.out.println("Top " + limit + " results for term " + term + ":");
		if (limit >= entries.size())
			for (Entry<String, Integer> entry: entries)
				System.out.println(entry);
		else {
			for (int i = 0; i < limit; i++)
				System.out.println(entries.get(i));
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
	 * Given a list of comma-delimited terms, compute their union.
	 *
	 * @param terms
	 * @param index
	 */
	public static void handleChainedOr(String terms, JedisIndex index)
	{
		WikiSearch currentResult;
		// The -1 limit parameter instructs split to match as many times as possible.
		String[] tokens = terms.split(",", -1);
		if (tokens.length < 2)
		{
			System.out.println("Must provide at least 2 comma-delimited search terms.");
			return;
		}
		System.out.print("Query: ");
		currentResult = search(tokens[0], index);
		System.out.print(tokens[0] + " OR ");
		for (int i = 1; i < tokens.length; i++)
		{
			currentResult = currentResult.or(search(tokens[i], index));
			System.out.print(tokens[i]);
			// Maybe there's a magic Java join method like that of Python that would save me
			// all this clumsy coding. Will look for it...sometime
			if (i != tokens.length - 1)
				System.out.print(" OR ");
		}
		System.out.println();

		currentResult.print();
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
	 * Given a list of comma-delimited terms, compute their intersection.
	 *
	 * @param terms
	 * @param index
	 */
	public static void handleChainedAnd(String terms, JedisIndex index)
	{
		WikiSearch currentResult;
		// The -1 limit parameter instructs split to match as many times as possible.
		String[] tokens = terms.split(",", -1);
		if (tokens.length < 2)
		{
			System.out.println("Must provide at least 2 comma-delimited search terms.");
			return;
		}
		System.out.print("Query: ");
		currentResult = search(tokens[0], index);
		System.out.print(tokens[0] + " AND ");
		for (int i = 1; i < tokens.length; i++)
		{
			currentResult = currentResult.and(search(tokens[i], index));
			System.out.print(tokens[i]);
			// Maybe there's a magic Java join method like that of Python that would save me
			// all this clumsy coding. Will look for it...sometime
			if (i != tokens.length - 1)
				System.out.print(" AND ");
		}
		System.out.println();

		currentResult.print();
	}

	/**
	 * Computes the difference of two search results.
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
	 * Given a list of comma-delimited terms, compute their difference.
	 *
	 * @param terms
	 * @param index
	 */
	public static void handleChainedWithout(String terms, JedisIndex index)
	{
		WikiSearch currentResult;
		// The -1 limit parameter instructs split to match as many times as possible.
		String[] tokens = terms.split(",", -1);
		if (tokens.length < 2)
		{
			System.out.println("Must provide at least 2 comma-delimited search terms.");
			return;
		}
		System.out.print("Query: ");
		currentResult = search(tokens[0], index);
		System.out.print(tokens[0] + " MINUS ");
		for (int i = 1; i < tokens.length; i++)
		{
			currentResult = currentResult.minus(search(tokens[i], index));
			System.out.print(tokens[i]);
			// Maybe there's a magic Java join method like that of Python that would save me
			// all this clumsy coding. Will look for it...sometime
			if (i != tokens.length - 1)
				System.out.print(" MINUS ");
		}
		System.out.println();

		currentResult.print();
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

	/**
	 * Prints help on what command line options are available.
	 *
	 */
	public static void printHelpMessage()
	{
		System.out.println("WikiSearch: a Wikipedia web crawler and indexer");
		System.out.println();
		System.out.println("OPTIONS AVAILABLE:");
		System.out.println();

		printOption("--help", "Display this help message.");
		printOption("--term", "Search indexed pages for the indicated term.");
		printOption("-n", "Display only the top N results.");
		printOption("-c", "Display only the number of documents which contain the specified term.");
		printOption("--and", "Compute the intersection of a list of comma-delimited terms. Eg, to search for A && B && C, pass the argument \"A,B,C\".");
		printOption("--or", "Compute the union of a list of comma-delimited terms. Eg, to search for A || B || C, pass the argument \"A,B,C\".");
		printOption("--without", "Compute the difference of a list of comma-delimited terms. Eg, to search for A - B - C, which is grouped as (A - B) - C, pass the argument \"A,B,C\".");
		printOption("--list", "Lists the URLs available for search in the index.");
	}

	/**,
	 * Helper which prints formatted information on which options are available.
	 *
	 */
	public static void printOption(String option, String description)
	{
		System.out.println(option + ":");
		System.out.println();
		System.out.println(description);
		System.out.println();
	}

	public static void main(String[] args) throws IOException {

		// make a JedisIndex
		Jedis jedis = JedisMaker.make();
		JedisIndex index = new JedisIndex(jedis);

		// search for the first term
		// String term1 = "java";
		// System.out.println("Query: " + term1);
		// WikiSearch search1 = search(term1, index);
		// search1.print();

		// // search for the second term
		// String term2 = "programming";
		// System.out.println("Query: " + term2);
		// WikiSearch search2 = search(term2, index);
		// search2.print();

		// // compute the intersection of the searches
		// System.out.println("Query: " + term1 + " AND " + term2);
		// WikiSearch intersection = search1.and(search2);
		// intersection.print();


		OptionParser parser = new OptionParser( "a::n::c::" );
		parser.accepts("term").withOptionalArg();
		parser.accepts("and").withOptionalArg();
		parser.accepts("or").withOptionalArg();
		parser.accepts("without").withOptionalArg();
		parser.accepts("list");
		parser.accepts("help");
		OptionSet options = parser.parse(args);
		if (options.has("help"))
		{
			printHelpMessage();
			System.exit(0);
		}
		if (options.has("list"))
		{
			index.printURLs();
			System.exit(0);
		}
		if (options.has("c"))
		{
			// We can't search for how many documents indexed a term unless a term is provided!
			if (!options.has("term"))
			{
				System.out.println("Need to provide term to search for");
				System.exit(1);
			}
			else
			{
				// We still need to compute the results of searching for the term, but instead of calling print(), we'll just report how many pages had the term indexed.
				WikiSearch result = search(options.valueOf("term").toString(), index);
				result.printNumDocs(options.valueOf("term").toString());
			}
		}
		if (options.has("n"))
		{
			// We can't search for the top N results for a term unless a term is provided!
			if (!options.has("term"))
			{
				System.out.println("Need to provide term to search for");
				System.exit(1);
			}
			else
			{
				// We still need to compute the results of searching for the term, but have the limitation of reporting up to N maximum results.
				try {
					int limit = Integer.parseInt(options.valueOf("n").toString());
					if (limit < 1)
					{
						System.out.println("Please provide a positive ( > 0) integer limit for how many results you want to see.");
						System.exit(1);
					}
					WikiSearch result = search(options.valueOf("term").toString(), index);
					result.printTopN(limit, options.valueOf("term").toString());
				}
				catch (NumberFormatException e) {
					System.out.println("Please provide an integer limit for how many results you want to see.");
					System.exit(1);
				}
			}
		}

		if (options.has("and") && options.valueOf("and") != null)
			handleChainedAnd(options.valueOf("and").toString(), index);
		if (options.has("or") && options.valueOf("or") != null)
			handleChainedOr(options.valueOf("or").toString(), index);
		if (options.has("without") && options.valueOf("without") != null)
			handleChainedAnd(options.valueOf("without").toString(), index);
		if (options.has("term") && options.valueOf("term") != null)
		{
			System.out.println("Query: " + options.valueOf("term"));
			search(options.valueOf("term").toString(), index).print();
		}
	}
}
