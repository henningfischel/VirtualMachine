public class UnexpectedTokenException extends Exception {
    public UnexpectedTokenException(String errorMessage) {
        super("Token " + errorMessage + " not valid");
    }

}
