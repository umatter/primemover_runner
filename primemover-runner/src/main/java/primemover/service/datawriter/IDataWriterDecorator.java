package primemover.service.datawriter;

public interface IDataWriterDecorator extends IDataWriter {
	IDataWriter getInnerWriter();
}
