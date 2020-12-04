package primemover.util;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import primemover.model.Outlet;
import primemover.service.OutletService;

public class OutletsProvider {
	
	private static Logger logger = LoggerFactory.getLogger(OutletsProvider.class);

	ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
	
	@Autowired
	OutletService outletService;
	
	@Value("${outlets.refreshIntervalMinutes:60}")
	private Integer refreshIntevalMinuts;
	
	private Map<String, Outlet> outletsMapByDomain;
	
	private ZonedDateTime lastRefresh;
	
	public OutletsProvider() {
		
	}
	
	
	public void refresh(boolean force) {
		try {
			logger.trace("acquiring lock");
			rwLock.writeLock().lock();
			
			ZonedDateTime now = ZonedDateTime.now();
			
			// is a refresh required?
			if (!force && lastRefresh != null) {
				if (ChronoUnit.MINUTES.between(lastRefresh, now) < refreshIntevalMinuts) {
					return;
				}
			}
			
			logger.debug("updating outlets");
			
			// refresh
			List<Outlet> outletsList = outletService.getOutlets();
			Map<String, Outlet> outletsMap = new HashMap<>(outletsList.size());
			
			for (Outlet outlet : outletsList) {
				outletsMap.put(outlet.getOutlet(), outlet);
			}		
			
			outletsMapByDomain = outletsMap;  // replace existing map
			lastRefresh = now;
		} finally {
			logger.trace("releasing lock");
			rwLock.writeLock().unlock();
		}
	}
	
	
	public void refresh() {
		this.refresh(false);
	}
	
	
	/**
	 * Gets an unmodifiable map
	 * 
	 * @return
	 */
	public Map<String, Outlet> getOutletsMapByDomain() {
		// check for updates
		refresh();
		
		// read list
		try {
			rwLock.readLock().lock();
			return Collections.unmodifiableMap(outletsMapByDomain);
		} finally {
			rwLock.readLock().unlock();
		}
	}
}
