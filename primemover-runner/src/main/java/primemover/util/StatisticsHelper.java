package primemover.util;

import java.util.Random;

public class StatisticsHelper {
	
	private static Random rand = new Random();

	public StatisticsHelper() {
	}
	
	
	/**
	 * Will run a MultinominalSelection
	 * 
	 * @param probs
	 * 
	 * @return index of the selected item within the given array
	 */
	public static Integer makeMultinominalSelection(double[] probs) {
		
		// calculate normalization factor
		double probSum = 0.0;
		double normalizationFactor;
		for (int n = 0; n < probs.length; n++) {
			probSum += probs[n];
		}
		normalizationFactor = 1.0d / probSum;
				
		// random choice (0..1)
		double choice = rand.nextDouble();
		
		// make choice
		double lowerRange = 0.0d;
		for (int n = 0; n < probs.length; n++) {
			double upperRange = lowerRange + probs[n] * normalizationFactor;
			
			if (lowerRange <= choice && choice < upperRange) {
				return n;
			}
			
			lowerRange = upperRange;
		}
		
		// should not happen, however there might be some rounding issues...
		return 0;
	}

}
