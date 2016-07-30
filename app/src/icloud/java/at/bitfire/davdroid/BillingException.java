package at.bitfire.davdroid;

public class BillingException extends Exception {

    public BillingException(String message) {
        super(message);
    }

    public BillingException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
