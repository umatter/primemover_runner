package primemover.capture;

import com.browserup.harreader.model.Har;
import com.browserup.harreader.model.HarEntry;
import com.browserup.harreader.model.HarHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filter that reduces HAR (HTTP Archive) file size by removing unnecessary entries
 * and content based on configurable rules. This helps optimize storage costs and
 * data transfer while preserving the most important network traffic information.
 *
 * Filtering options:
 * - Remove static resources (images, CSS, fonts, JS)
 * - Exclude specific domains (tracking, analytics)
 * - Truncate large request/response bodies
 * - Remove specific HTTP headers
 *
 * All filtering is optional and configurable via application properties.
 *
 * @author PrimeMover Team
 */
@Component
public class HarContentFilter {
	private static final Logger LOGGER = LoggerFactory.getLogger(HarContentFilter.class);

	@Value("${har.filter.enabled:false}")
	private Boolean filterEnabled;

	@Value("${har.filter.removeStaticResources:false}")
	private Boolean removeStaticResources;

	@Value("${har.filter.staticResourceTypes:image/png,image/jpeg,image/gif,image/webp,image/svg+xml,text/css,font/woff,font/woff2,font/ttf,application/font-woff,application/font-woff2,application/x-font-woff,application/x-font-ttf}")
	private String staticResourceTypes;

	@Value("${har.filter.includeDomains:}")
	private String includeDomains;

	@Value("${har.filter.excludeDomains:}")
	private String excludeDomains;

	@Value("${har.filter.maxBodySize:}")
	private String maxBodySize;

	@Value("${har.filter.removeHeaders:}")
	private String removeHeaders;

	private Set<String> staticResourceTypesSet;
	private Set<String> includeDomainsSet;
	private Set<String> excludeDomainsSet;
	private Set<String> removeHeadersSet;
	private Long maxBodySizeBytes;

	@PostConstruct
	public void init() {
		staticResourceTypesSet = parseCommaSeparated(staticResourceTypes);
		includeDomainsSet = parseCommaSeparated(includeDomains);
		excludeDomainsSet = parseCommaSeparated(excludeDomains);
		removeHeadersSet = parseCommaSeparated(removeHeaders);

		if (maxBodySize != null && !maxBodySize.trim().isEmpty()) {
			try {
				maxBodySizeBytes = Long.parseLong(maxBodySize.trim());
			} catch (NumberFormatException e) {
				LOGGER.warn("Invalid har.filter.maxBodySize value: {}, body size filtering disabled", maxBodySize);
				maxBodySizeBytes = null;
			}
		}

		LOGGER.info("HAR content filter initialized. Enabled: {}, Remove static resources: {}, " +
						"Static types count: {}, Included domains count: {}, Excluded domains count: {}, Max body size: {}",
				filterEnabled, removeStaticResources, staticResourceTypesSet.size(),
				includeDomainsSet.size(), excludeDomainsSet.size(), maxBodySizeBytes);
	}

	/**
	 * Parses a comma-separated string into a Set, with trimming and empty filtering.
	 *
	 * @param input Comma-separated string
	 * @return Set of parsed values, or empty set if input is null/empty
	 */
	private Set<String> parseCommaSeparated(String input) {
		if (input == null || input.trim().isEmpty()) {
			return Collections.emptySet();
		}
		return Arrays.stream(input.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toSet());
	}

	/**
	 * Filters a HAR object by removing entries and content based on configured rules.
	 * This method modifies the HAR in place for performance.
	 *
	 * @param har The HAR object to filter
	 * @return The filtered HAR object (same instance, modified in place)
	 */
	public Har filterHar(Har har) {
		if (!filterEnabled || har == null || har.getLog() == null) {
			return har;
		}

		List<HarEntry> entries = har.getLog().getEntries();
		if (entries == null || entries.isEmpty()) {
			LOGGER.debug("HAR has no entries, skipping filtering");
			return har;
		}

		int originalEntryCount = entries.size();

		// Calculate original size
		long originalSize = 0;
		for (HarEntry entry : entries) {
			originalSize += estimateEntrySize(entry);
		}

		// Filter entries
		List<HarEntry> filteredEntries = entries.stream()
				.filter(this::shouldIncludeEntry)
				.peek(this::filterEntryContent)
				.collect(Collectors.toList());

		int filteredEntryCount = filteredEntries.size();

		// Calculate filtered size
		long filteredSize = 0;
		for (HarEntry entry : filteredEntries) {
			filteredSize += estimateEntrySize(entry);
		}

		// Replace entries list
		entries.clear();
		entries.addAll(filteredEntries);

		int removedEntries = originalEntryCount - filteredEntryCount;
		long savedBytes = originalSize - filteredSize;
		double reductionPercent = originalSize > 0 ? (savedBytes * 100.0 / originalSize) : 0;

		LOGGER.info("HAR filtered: removed {} of {} entries ({} remaining), " +
						"size reduced by {} bytes ({} -> {} bytes, {:.1f}% reduction)",
				removedEntries, originalEntryCount, filteredEntryCount,
				savedBytes, originalSize, filteredSize, reductionPercent);

		return har;
	}

	/**
	 * Determines whether a HAR entry should be included in the filtered result.
	 *
	 * @param entry The HAR entry to evaluate
	 * @return true if entry should be included, false if it should be filtered out
	 */
	private boolean shouldIncludeEntry(HarEntry entry) {
		if (entry == null || entry.getRequest() == null) {
			return false;
		}

		String url = entry.getRequest().getUrl();
		if (url == null) {
			return false;
		}

		// Filter by domain - whitelist approach (include domains)
		// If includeDomains is specified, ONLY include URLs containing those domains
		if (!includeDomainsSet.isEmpty()) {
			boolean matches = false;
			for (String domain : includeDomainsSet) {
				if (url.contains(domain)) {
					matches = true;
					break;
				}
			}
			if (!matches) {
				LOGGER.trace("Filtering out entry - not in included domains: {}", url);
				return false;
			}
		}

		// Filter by domain - blacklist approach (exclude domains)
		// Only applies if includeDomains is not specified or if the URL passed the include check
		if (!excludeDomainsSet.isEmpty()) {
			for (String domain : excludeDomainsSet) {
				if (url.contains(domain)) {
					LOGGER.trace("Filtering out entry for excluded domain: {}", domain);
					return false;
				}
			}
		}

		// Filter by resource type
		if (removeStaticResources && entry.getResponse() != null
				&& entry.getResponse().getContent() != null) {
			String mimeType = entry.getResponse().getContent().getMimeType();
			if (mimeType != null && shouldFilterContentType(mimeType)) {
				LOGGER.trace("Filtering out static resource: {} ({})", url, mimeType);
				return false;
			}
		}

		return true;
	}

	/**
	 * Checks if a content type should be filtered out as a static resource.
	 *
	 * @param mimeType The MIME type to check
	 * @return true if this content type should be filtered out
	 */
	private boolean shouldFilterContentType(String mimeType) {
		if (mimeType == null) {
			return false;
		}

		String normalizedMimeType = mimeType.toLowerCase().trim();

		// Check exact match
		if (staticResourceTypesSet.contains(normalizedMimeType)) {
			return true;
		}

		// Check prefix match (e.g., "image/png" matches "image/")
		for (String type : staticResourceTypesSet) {
			if (normalizedMimeType.startsWith(type)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Filters the content of an entry (headers, body) based on configured rules.
	 * This method modifies the entry in place.
	 *
	 * @param entry The entry to filter
	 */
	private void filterEntryContent(HarEntry entry) {
		if (entry == null) {
			return;
		}

		// Filter request headers
		if (!removeHeadersSet.isEmpty() && entry.getRequest() != null
				&& entry.getRequest().getHeaders() != null) {
			List<HarHeader> filteredHeaders = entry.getRequest().getHeaders().stream()
					.filter(h -> !removeHeadersSet.contains(h.getName()))
					.collect(Collectors.toList());
			entry.getRequest().getHeaders().clear();
			entry.getRequest().getHeaders().addAll(filteredHeaders);
		}

		// Filter response headers
		if (!removeHeadersSet.isEmpty() && entry.getResponse() != null
				&& entry.getResponse().getHeaders() != null) {
			List<HarHeader> filteredHeaders = entry.getResponse().getHeaders().stream()
					.filter(h -> !removeHeadersSet.contains(h.getName()))
					.collect(Collectors.toList());
			entry.getResponse().getHeaders().clear();
			entry.getResponse().getHeaders().addAll(filteredHeaders);
		}

		// Truncate large bodies
		if (maxBodySizeBytes != null) {
			truncateRequestBody(entry);
			truncateResponseBody(entry);
		}
	}

	/**
	 * Truncates request body if it exceeds the maximum configured size.
	 *
	 * @param entry The entry to potentially truncate
	 */
	private void truncateRequestBody(HarEntry entry) {
		if (entry.getRequest() != null && entry.getRequest().getPostData() != null
				&& entry.getRequest().getPostData().getText() != null) {
			String text = entry.getRequest().getPostData().getText();
			if (text.length() > maxBodySizeBytes) {
				String truncated = text.substring(0, maxBodySizeBytes.intValue())
						+ "... [TRUNCATED]";
				entry.getRequest().getPostData().setText(truncated);
			}
		}
	}

	/**
	 * Truncates response body if it exceeds the maximum configured size.
	 *
	 * @param entry The entry to potentially truncate
	 */
	private void truncateResponseBody(HarEntry entry) {
		if (entry.getResponse() != null && entry.getResponse().getContent() != null
				&& entry.getResponse().getContent().getText() != null) {
			String text = entry.getResponse().getContent().getText();
			if (text.length() > maxBodySizeBytes) {
				String truncated = text.substring(0, maxBodySizeBytes.intValue())
						+ "... [TRUNCATED]";
				entry.getResponse().getContent().setText(truncated);
			}
		}
	}

	/**
	 * Estimates the size of a HAR entry in bytes (rough approximation).
	 *
	 * @param entry The entry to estimate
	 * @return Approximate size in bytes
	 */
	private long estimateEntrySize(HarEntry entry) {
		long size = 0;

		if (entry.getRequest() != null) {
			if (entry.getRequest().getPostData() != null
					&& entry.getRequest().getPostData().getText() != null) {
				size += entry.getRequest().getPostData().getText().length();
			}
		}

		if (entry.getResponse() != null && entry.getResponse().getContent() != null
				&& entry.getResponse().getContent().getText() != null) {
			size += entry.getResponse().getContent().getText().length();
		}

		return size;
	}

	/**
	 * Checks if HAR content filtering is enabled.
	 *
	 * @return true if filtering is enabled
	 */
	public boolean isFilterEnabled() {
		return filterEnabled;
	}
}
