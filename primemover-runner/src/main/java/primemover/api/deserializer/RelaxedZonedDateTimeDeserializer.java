package primemover.api.deserializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

public class RelaxedZonedDateTimeDeserializer extends JsonDeserializer<ZonedDateTime> {

	private ZoneId defaulTimeZone;

	public RelaxedZonedDateTimeDeserializer(ZoneId defaulTimeZone) {
		super();
		this.defaulTimeZone = defaulTimeZone;
	}

	@Override
	public ZonedDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
			throws IOException {
		Exception rethrow = null;

		String inputStr = jsonParser.getText();

		// allow missing Zone-Information to be interpreted as local datetime
		try {
			DateTimeFormatter parser = new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd'T'HH:mm:ss")
					.appendFraction(ChronoField.MICRO_OF_SECOND, 0, 6, true).optionalStart().appendZoneOrOffsetId()
					.optionalEnd().toFormatter().withZone(defaulTimeZone);

			return ZonedDateTime.parse(inputStr, parser);
		} catch (Exception ex) {
			// relax
			rethrow = ex;
		}
		// fallback to "yyyy-mm-dd" at start of day
		try {
			LocalDate localDate = LocalDate.parse(inputStr, DateTimeFormatter.ISO_LOCAL_DATE);

			return localDate.atStartOfDay(this.defaulTimeZone);
		} catch (Exception ex) {
			// relax
		}

		throw new IOException("Could not parse datetime " + inputStr, rethrow);
	}
}
