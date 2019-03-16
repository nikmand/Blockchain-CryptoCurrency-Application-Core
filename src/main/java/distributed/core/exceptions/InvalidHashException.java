package distributed.core.exceptions;

public class InvalidHashException extends RuntimeException {

	private static final long serialVersionUID = 1L;
	private String message;

	public InvalidHashException(String message) {
		super(message);
		this.message = message;
	}

	@Override
	public String getMessage() {
		return message;
	}

}
