package primemover.service.datawriter;

import java.text.SimpleDateFormat;
import java.util.Date;

import primemover.model.IJob;


public class StorageUtility {

	private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");

	public static String formatDateForFilePrefix(Date date) {
		return dateFormat.format(date);
	}
	
	public static String getIdentifier(String prefix, IJob job) {
		String task = job.getTask();
		if (task == null) {
			task = "NULL";
		}
		return prefix + "-" + job.getClass().getSimpleName().toLowerCase() + "-" + task + "-" + job.getId();
	}

}
