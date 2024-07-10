package primemover.lambda;

public class LambdaException extends RuntimeException {

	public LambdaException() {
	}

	public LambdaException(String message) {
		super(message);
	}

	public LambdaException(Throwable cause) {
		super(cause);
	}

	public LambdaException(String message, Throwable cause) {
		super(message, cause);
	}

	public LambdaException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
