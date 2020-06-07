package webdata;

import com.sun.source.tree.Tree;
import webdata.utils.ReviewWithScore;
import webdata.utils.Utils;

import java.util.*;

public class ReviewSearch {

    private IndexReader ir;

    /**
     * Constructor
     */
    public ReviewSearch(IndexReader iReader) {
        ir = iReader;
    }

    private double[] calcLtf(double[] termFrequencies) {
        Arrays.parallelSetAll(termFrequencies,
                i -> ((termFrequencies[i] == 0) ? 0 : (1 + Math.log10(termFrequencies[i]))));
        return termFrequencies;
    }

    private double[] calcTidf(double[] termFrequencies) {
        int numOfReviews = ir.getNumberOfReviews();
        Arrays.parallelSetAll(termFrequencies,
                i -> ((termFrequencies[i] == 0) ? 0 : (Math.log10(numOfReviews / termFrequencies[i]))));
        return termFrequencies;
    }

    private TreeMap<String, Integer> histogramQuery(Enumeration<String> query) {
        TreeMap<String, Integer> hist = new TreeMap<>();
        while (query.hasMoreElements()) {
            String term = query.nextElement();
            Integer freq = (hist.keySet().contains(term)) ? hist.get(term) + 1 : 1;
            hist.put(term, freq);
        }
        return hist;
    }

    private TreeMap<Integer, TreeMap<String, Integer>> getReviews(Set<String> queryTerms) {
        TreeMap<Integer, TreeMap<String, Integer>> reviews = new TreeMap<>();
        for (String term: queryTerms) {
            Enumeration<Integer> termReviewAndFrequencies = ir.getReviewsWithToken(term);
            while (termReviewAndFrequencies.hasMoreElements()) {
                int reviewNumber = termReviewAndFrequencies.nextElement();
                int frequencyInReview = termReviewAndFrequencies.nextElement();
                TreeMap<String, Integer> curMap;
                if (reviews.keySet().contains(reviewNumber)) { // If the current review number already exists in the reviews map
                    curMap = reviews.get(reviewNumber); // Get the currently existing "vector" of this review
                } else {
                    curMap = new TreeMap<>(); // Create a "vector" of all available unique query terms
                    for (String t: queryTerms) {
                        curMap.put(t, 0);
                    }
                }
                curMap.replace(term, frequencyInReview);
                reviews.put(reviewNumber, curMap);
            }
        }
        return reviews;
    }

    private double[] computeLTCOfQuery(TreeMap<String, Integer> queryHist) {
        double[] ltf = calcLtf(webdata.utils.Utils.integerCollectionToDoubleArray(queryHist.values()));
        double[] termFrequencies = new double[ltf.length];
        int i = 0;
        for (String token: queryHist.keySet()) {
            termFrequencies[i] = ir.getTokenFrequency(token);
            ++i;
        }
        termFrequencies = calcTidf(termFrequencies);

        double[] ltc = new double[ltf.length];
        double cosNormalization = 0;
        for (i = 0; i < ltf.length; ++i) {
            ltc[i] = ltf[i] * termFrequencies[i];
            cosNormalization += Math.pow(ltc[i], 2);
        }
        cosNormalization = Math.sqrt(cosNormalization);
        final double cosNormalizationConst = cosNormalization;
        if (cosNormalizationConst != 0) {
            Arrays.parallelSetAll(ltc,
                    j -> (ltc[j] / cosNormalizationConst));
        }
        return ltc;
    }

    private double calcReviewScore(double[] freqOfTerms, double[] qqq) {
        freqOfTerms = calcLtf(freqOfTerms);
        double score = 0;
        for (int i = 0; i < qqq.length; ++i) {
            score += freqOfTerms[i] * qqq[i];
        }
        return score;
    }

    /**
     * Returns a list of the id-s of the k most highly ranked reviews for the
     * given query, using the vector space ranking function lnn.ltc (using the
     * SMART notation)
     * The list should be sorted by the ranking
     */
    public Enumeration<Integer> vectorSpaceSearch(Enumeration<String> query, int k) {
        // Compute qqq:
        TreeMap<String, Integer> queryHist = histogramQuery(query);
        double[] ltc = computeLTCOfQuery(queryHist);
        TreeMap<String, Double> queryVec = new TreeMap<>();
        int i = 0;
        for (String term: queryHist.keySet()) {
            if (ltc[i] != 0) {
                queryVec.put(term, ltc[i]);
            }
            ++i;
        }

        // Compute ddd:
        TreeMap<Integer, TreeMap<String, Integer>> reviews = getReviews(queryVec.keySet());
        ReviewWithScore[] reviewWithScores = new ReviewWithScore[reviews.size()];
        i = 0;
        for (Integer review: reviews.keySet()) {
            double score = calcReviewScore(Utils.integerCollectionToDoubleArray(reviews.get(review).values()),
                                           Utils.doubleCollectionToDoubleArray(queryVec.values()));
            ReviewWithScore rws = new ReviewWithScore(review, score);
            reviewWithScores[i] = rws;
            ++i;
        }
        Arrays.sort(reviewWithScores, (o1, o2) -> -o1.compareTo(o2));
        int numOfBestResults = Math.min(k, reviewWithScores.length);
        Integer[] bestResults = new Integer[numOfBestResults];
        for (i = 0; i < bestResults.length; ++i) {
            bestResults[i] = reviewWithScores[i].getReviewNumber();
        }

        Vector<Integer> bestReviews = new Vector<>(Arrays.asList(bestResults));
        return bestReviews.elements();
    }

    /**
     * Returns a list of the id-s of the k most highly ranked reviews for the
     * given query, using the language model ranking function, smoothed using a
     * mixture model with the given value of lambda
     * The list should be sorted by the ranking
     */
    public Enumeration<Integer> languageModelSearch(Enumeration<String> query, double lambda, int k) {
        return null;
    }

    /**
     * Returns a list of the id-s of the k most highly ranked productIds for the
     * given query using a function of your choice
     * The list should be sorted by the ranking
     */
    public Collection<String> productSearch(Enumeration<String> query, int k) {
        return null;
    }
}
