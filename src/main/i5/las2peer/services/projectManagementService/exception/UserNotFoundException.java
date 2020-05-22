package i5.las2peer.services.projectManagementService.exception;

import java.sql.SQLException;

/**
 * Exception class to differentiate "correct" not found cases from real database
 * errors.
 */
public class UserNotFoundException extends SQLException {
	private static final long serialVersionUID = 2446028906720712848L;
}
