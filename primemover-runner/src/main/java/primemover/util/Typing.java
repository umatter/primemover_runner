package primemover.util;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;


public class Typing {
	private static Logger logger = LoggerFactory.getLogger(Typing.class);
	
	
	private static Random rand = new Random();
	
	static {
		// init neighbouring keys
		loadNeighbouringKeys();
	}

	public Typing() {
	}
	
	/** 
	 * Write to element directly
	 * 
	 * @param element
	 * @param value
	 * @param addReturn
	 * @return
	 */
	public static Boolean typeTextDirect(WebElement element, String value, Boolean addReturn) {
		element.sendKeys(value);
		if (addReturn) {
			element.sendKeys(Keys.RETURN);
		}
		
		return true;
	}
	
	private static double getTypeDelay() {
		double min_delay = 0.18;
		double max_delay = 0.22;
		
		return min_delay + (max_delay - min_delay) * rand.nextDouble();
	}
	
	private static void delayTyping() {
		double curDelay = getTypeDelay();
		try {
			Thread.sleep((long) (curDelay * 1000));
		} catch (InterruptedException e) {
			// just ignore
		}
	}
	
	private static HashMap<Character, List<Character>> neighbouringKeysUS = null;
	
	private static void loadNeighbouringKeys() {
		HashMap<Character, List<Character>> neighbouringKeys = new HashMap<Character, List<Character>>();
		
		try {
			// read in json -> fields may vary therefore inconvenient way without auto-mapper
			File file = new ClassPathResource("data/adjacent_letters_US-en.json").getFile();
			ObjectMapper mapper = new ObjectMapper();
			JsonNode root = mapper.readTree(file);
			
			for (Iterator<String> fields = root.fieldNames(); fields.hasNext();) {
				String field = fields.next();
				Character key = field.charAt(0);
				
				String valueString = root.get(field).asText();
				char[] chars = valueString.toCharArray();
				
				List<Character> valueList = new ArrayList<Character>(chars.length);
				for(char c: chars) {
					valueList.add(c);
				}
				
				neighbouringKeys.put(key, valueList);
			}
			
		} catch (IOException e) {
			logger.error("required adjacent letters not found");
		}
		
		neighbouringKeysUS = neighbouringKeys;
	}
	
	
	private static HashMap<Character, List<Character>> getNeighbouringKeys() {
		if (neighbouringKeysUS == null) {
			synchronized (Typing.class) {
				if (neighbouringKeysUS == null) {
					loadNeighbouringKeys();
				}
			}
		}
		return neighbouringKeysUS;	
	}
	
	/**
	 * Try to simulate human behaviour
	 * 
	 * @param element
	 * @param text
	 * @param addReturn
	 * @return
	 */
	public static Boolean typeTextSimulated(WebElement element, String text, Boolean addReturn, Boolean typosAllowed, Boolean keepTypos) {
		

		double keepErrorsProb = 0.22;
		double mistakeProb = 0.03;
		double maxCharsTypedBeforeErasure = 3;
		
		HashMap<Character, List<Character>> neighbouringKeys = getNeighbouringKeys();
				
		for (int n = 0; n < text.length(); n++){
			// Current character
			Character c = text.charAt(n);
			
			// delay typing, initial delay is not relevant
			delayTyping();
			
			if (typosAllowed) {
				// Will we make a mistake while typing?
				boolean makeMistake = mistakeProb > Math.random();
				
				if (makeMistake && neighbouringKeys.containsKey(c)) {
					// mistake to happen
					List<Character> keys = neighbouringKeys.get(c);
					
					// select the key to use
					int idx = (int) Math.ceil(rand.nextDouble() * keys.size());
					c = keys.get(idx - 1);
					
					// more wrong letters to type and reset later. Watch out for max length of the string
					boolean keepMistake = (keepErrorsProb <= rand.nextDouble()) && keepTypos;
					if (!keepMistake) {
						// type ahead and erase again
						
						// decide how many chars to type before erasure, also adjust for remaining chars
						int numLettersToType = (int) (Math.ceil(rand.nextDouble() * (maxCharsTypedBeforeErasure + 1)) - 1);
						int remainingChars = text.length() - n - 1;
						numLettersToType = Math.min(numLettersToType, remainingChars);
						
						// type ahead
						for (int m = n; m < (n + numLettersToType); m++) {
							delayTyping();
							c = text.charAt(m);
							element.sendKeys(String.valueOf(c));
						}
						
						// erase again
						for (int m = 0; m <= numLettersToType; m++) { // also delete first wrong letter!
							delayTyping();
							element.sendKeys(Keys.BACK_SPACE);
						}
						
						// Type correctly
						delayTyping();
						c = text.charAt(n);
						element.sendKeys(String.valueOf(c));
					}				
				} else {
					// no typo
					element.sendKeys(String.valueOf(c));
				}
			} else {
				// no typos allowed
				element.sendKeys(String.valueOf(c));
			}
		}
		
		if (addReturn) {
			element.sendKeys(Keys.RETURN);
		}
		
		return true;
	}

}
