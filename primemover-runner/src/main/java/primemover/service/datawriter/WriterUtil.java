package primemover.service.datawriter;

public class WriterUtil {

	/**
	 * Checks for a IDataWriterDecorater and return the innerWriter. Also detects
	 * chained IDataWriterDecorators.
	 * 
	 * @param writer to check
	 * @return non decorated writer-object, or the writer-param when not decorated
	 */
	public static IDataWriter getNonDecoratedWriter(final IDataWriter writer) {
		IDataWriter nonDecorated = writer;
		while (nonDecorated instanceof IDataWriterDecorator) {
			nonDecorated = ((IDataWriterDecorator) writer).getInnerWriter();
		}
		return nonDecorated;
	}

	/**
	 * Will test for a writer of type PageSourceWriter
	 * 
	 * @param writer
	 * @return true: Param is a PageSourceWriter, false: Param is another type of
	 *         writer
	 */
	public static boolean isPageSourceWriter(final IDataWriter writer) {
		IDataWriter toCompare = getNonDecoratedWriter(writer);
		return toCompare instanceof PageSourceWriter;
	}

}
