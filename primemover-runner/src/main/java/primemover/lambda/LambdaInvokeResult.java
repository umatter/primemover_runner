package primemover.lambda;

/**
 * Common result interface for Lambda invocations, whether from AWS or local.
 */
public class LambdaInvokeResult {
    private final int statusCode;
    private final String payload;
    private final String functionError;

    public LambdaInvokeResult(int statusCode, String payload, String functionError) {
        this.statusCode = statusCode;
        this.payload = payload;
        this.functionError = functionError;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getPayload() {
        return payload;
    }

    public String getFunctionError() {
        return functionError;
    }

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300 && (functionError == null || functionError.isEmpty());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int statusCode;
        private String payload;
        private String functionError;

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder payload(String payload) {
            this.payload = payload;
            return this;
        }

        public Builder functionError(String functionError) {
            this.functionError = functionError;
            return this;
        }

        public LambdaInvokeResult build() {
            return new LambdaInvokeResult(statusCode, payload, functionError);
        }
    }
}
