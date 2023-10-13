package primemover.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.distribution.GumbelDistribution;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import primemover.model.Crawler;
import primemover.model.DecisionType;
import primemover.model.MediaOutlet;
import primemover.model.SelectionConfiguration;
import primemover.model.SelectorType;
import primemover.model.jobs.SelectorBaseJob;
import primemover.model.jobs.SingleSelectJob;
import primemover.service.IStorageService;
import primemover.service.datawriter.SelectionWriter;

public class SelectingHelper {

	private static Logger logger = LoggerFactory.getLogger(SelectingHelper.class);

	// @Autowired
	// OutletsProvider outletsProvider;

	@Autowired(required = false)
	List<IStorageService> storages = new ArrayList<IStorageService>();

	private Random rand = new Random();

	public static List<WebElement> getElementsByWebdriver(WebDriver driver, SelectorType type, String selector) {
		logger.debug("grabing elements by {} with selector {}", type, selector);
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

		List<WebElement> elements;
		switch (type) {
		case CLASS:
			// Wait for Element
			elements = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.className(selector)));
		case CSS:
			elements = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.cssSelector(selector)));
			break;
		case ID:
			elements = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.id(selector)));
			break;
		case XPATH:
			elements = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.xpath(selector)));
			break;
		default:
			throw new UnsupportedOperationException("Not implemented yet");
		}

		return elements;
	}

	/***
	 * Will return the first found element on the page
	 * 
	 * @param driver
	 * @param type
	 * @param selector
	 * @return
	 */
	public static WebElement getElementByWebdriver(WebDriver driver, SelectorType type, String selector) {
		logger.debug("grabing element by {} with selector {}", type, selector);
		WebElement element;
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

		switch (type) {
		case CLASS:
			element = wait.until(ExpectedConditions.presenceOfElementLocated(By.className(selector)));
			break;
		case CSS:
			element = wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector(selector)));
			break;
		case ID:
			element = wait.until(ExpectedConditions.presenceOfElementLocated(By.id(selector)));
			break;
		case XPATH:
			element = wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath(selector)));
			break;
		default:
			throw new UnsupportedOperationException("Not implemented yet");
		}

		return element;
	}

	public static WebElement getElementByWebdriver(WebDriver driver, SelectorBaseJob selectorJob) {
		SelectorType type = selectorJob.getType();
		String selector = selectorJob.getSelector();

		return getElementByWebdriver(driver, type, selector);
	}

	private String normalizeDomainFromHref(String urlString) {
		try {
			URI uri = new URI(urlString);
			String domain = uri.getHost();
			return domain.startsWith("www.") ? domain.substring(4) : domain;
		} catch (URISyntaxException e) {
			logger.warn("url is malformed {}", urlString, e);
			return "";
		}
	}

	/*
	 * """ :param known: indicates whether or not individual knows the outlet.
	 * :param beta_i: :param r_j: int > 0 rank of outlet in search engine result
	 * list :param d_tilde_i_j_t: float > 0, ideological distance |pi_i-pi_tilde_j|
	 * where pi_i represents the political orientation of the individual and
	 * pi_tilde_j that of outlet j :param rho_j: float >= 0, reach of outlet j
	 * :param alpha_tilde: float >= 0, shift parameter :param tau_tilde: float > 0,
	 * "transportation costs" i.e. costs of consuming ideologically distant news
	 * :return: float utility of selecting result """
	 */
	private double result_utility_w_i_j_t(double r_j, double beta_i, double known, double d_tilde_i_j_t,
			double alpha_tilde, double tau_tilde) {
		return (1 - beta_i * (r_j - 1)) + known * (alpha_tilde - tau_tilde * d_tilde_i_j_t * d_tilde_i_j_t);
	}

	@SuppressWarnings("unused")
	private double result_utility_w_i_j_t(double r_j, double beta_i) {
		return result_utility_w_i_j_t(r_j, beta_i, 0, 0, 1, 1);
	}

	private WebElement selectItem(Selection selection) {
		WebElement selectedItem = null;

		double choice = rand.nextDouble();
		selection.setChoice(choice);

		double lowerEnd;
		double upperEnd = 0.0d;
		for (SelectionItem item : selection.getItems()) {
			if (!item.isSkiped()) {
				lowerEnd = upperEnd;
				upperEnd = upperEnd + item.getProbability();

				item.setProb_LowerEnd(lowerEnd);
				item.setProb_UpperEnd(upperEnd);

				if (lowerEnd < choice && choice <= upperEnd) {
					item.setSelected(true);
					selectedItem = item.getElement();
				}
			}
		}

		return selectedItem;
	}

	private void calculateProbabilities(List<SelectionItem> calcItems) {

		double total_exp_u = 0.0d;
		for (SelectionItem item : calcItems) {
			if (!item.isSkiped()) {
				double exp_u = Math.exp(item.getU_final());
				total_exp_u += exp_u;
				item.setExp_u(exp_u);
			}
		}

		for (SelectionItem item : calcItems) {
			if (!item.isSkiped()) {
				item.setProbability(item.getExp_u() / total_exp_u);
			}
		}
	}

	private WebElement chooseCalculatedElement(Crawler crawler, SingleSelectJob job, List<WebElement> clickElements,
			List<WebElement> criteriaElements) {
		// Param verification
		Objects.requireNonNull(crawler);
		Objects.requireNonNull(job);
		Objects.requireNonNull(clickElements);
		Objects.requireNonNull(criteriaElements);

		if (clickElements.size() != criteriaElements.size()) {
			throw new IllegalArgumentException("size of click elements is not equal the size of the criteria elements");
		}

		SelectionConfiguration config = crawler.getConfig().getSelectionConfiguration();
		double tau = config.getTau();
		double beta = config.getBeta();
		double alpha = config.getAlpha();

		GumbelDistribution gumbel = new GumbelDistribution(-0.4557735, 0.793006);

		WebElement selectedElement = null;
		Selection selection = new Selection();
		List<SelectionItem> calcItems = new ArrayList<>(clickElements.size());

		for (int i = 0; i < clickElements.size(); i++) {
			WebElement webElement = clickElements.get(i);
			SelectionItem calcItem = new SelectionItem();
			String urlString = webElement.getAttribute("href");

			calcItem.setRank(i + 1);
			calcItem.setElement(webElement);
			calcItem.setUrl(urlString);

			// Defaults des Crawler
			calcItem.setAlpha(alpha);
			calcItem.setBeta(beta);
			calcItem.setTau(tau);

			if (!StringUtils.isEmpty(urlString)) {

				String normalizedUrl = normalizeDomainFromHref(urlString);
				Boolean known = config.getOutlets().containsKey(normalizedUrl);

				calcItem.setSkiped(false);
				calcItem.setNormalizedUrl(normalizedUrl);
				calcItem.setKnown(known);
				double u;
				if (known) {
					MediaOutlet outlet = config.getOutlets().get(normalizedUrl);

					double d_tilde_i_j_t = Math.abs(config.getPi() - outlet.getPi());

					calcItem.setOutlet(outlet);
					calcItem.setD_tilde_i_j_t(d_tilde_i_j_t);

					u = result_utility_w_i_j_t(calcItem.getRank(), beta, 1, d_tilde_i_j_t, alpha, tau);
				} else {
					calcItem.setD_tilde_i_j_t(0);
					u = result_utility_w_i_j_t(calcItem.getRank(), beta, 0, 0, alpha, tau);
				}

				double epsilon = gumbel.sample();

				calcItem.setU_raw(u);
				calcItem.setU_final(u + epsilon);
				calcItem.setEpsilon(epsilon);
			} else {
				calcItem.setSkiped(true);
			}

			calcItems.add(calcItem);
		}

		calculateProbabilities(calcItems);

		selection.setItems(calcItems);

		selectedElement = selectItem(selection);

		// Writing data
		boolean writeRes = true;
		for (IStorageService storage : storages) {
			writeRes = writeRes && storage.writeData(job, new SelectionWriter(job, selection));
		}

		return selectedElement;
	}

	List<String> matchCriteriaString(SingleSelectJob job, List<String> criteriaStrings) {
		// no need for calculation, pass through
		if (StringUtils.isBlank(job.getCriteriaExtractor())) {
			return new ArrayList<>(criteriaStrings);
		}

		// prepare regexp
		Pattern pattern = Pattern.compile(job.getCriteriaExtractor());
		List<String> result = new ArrayList<>(criteriaStrings.size());

		for (int i = 0; i < criteriaStrings.size(); i++) {
			String curCriteria = criteriaStrings.get(i);

			Matcher matcher = pattern.matcher(curCriteria);
			if (matcher.find()) {
				curCriteria = matcher.group();
			} else {
				curCriteria = "";
			}

			result.add(curCriteria);
		}

		return result;
	}

	List<String> extractCriteriaStrings(SingleSelectJob job, List<WebElement> criteriaElements) {
		List<String> result = new ArrayList<>(criteriaElements.size());

		// prepare regexp
		Pattern pattern = null;
		if (StringUtils.isNotBlank(job.getCriteriaExtractor())) {
			pattern = Pattern.compile(job.getCriteriaExtractor());
		}

		for (int i = 0; i < criteriaElements.size(); i++) {
			WebElement curElements = criteriaElements.get(i);

			String curCriteria = curElements.getText();

			if (pattern != null) {
				Matcher matcher = pattern.matcher(curCriteria);
				if (matcher.find()) {
					curCriteria = matcher.group();
				} else {
					curCriteria = "";
				}
			}
		}

		return result;
	}

	public WebElement chooseElement(Crawler crawler, SingleSelectJob job, List<WebElement> clickElements,
			List<WebElement> criteriaElements, DecisionType type) {

		// verification of job
		Objects.requireNonNull(crawler);
		Objects.requireNonNull(job);
		Objects.requireNonNull(clickElements);
		Objects.requireNonNull(criteriaElements);
		Objects.requireNonNull(type);

		WebElement selection = null;

		if (clickElements.size() > 0) {
			switch (type) {
			case CALCULATED:
				logger.debug("making randomized choice out of known outlets");

				selection = chooseCalculatedElement(crawler, job, clickElements, criteriaElements);
				break;
			case FIRST:
				logger.debug("select first");
				selection = clickElements.get(0);
				break;
			case LAST:
				logger.debug("select last");
				selection = clickElements.get(clickElements.size() - 1);
				break;
			case RANDOM:
				logger.debug("making random choice");
				Long id = Math.round(rand.nextDouble() * (clickElements.size() - 1));
				selection = clickElements.get(id.intValue());
				break;
			default:
				throw new UnsupportedOperationException("Not implemented yet");
			}
		}
		return selection;
	}
}
