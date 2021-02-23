package i5.las2peer.services.projectManagementService.exception;

import java.sql.SQLException;

/**
 * Used by getDefaultRole() in Project class.
 * Gets thrown when the project has no default role.
 * @author Philipp
 *
 */
public class NoDefaultRoleFoundException extends SQLException {
	private static final long serialVersionUID = 3428582733194462997L;
}
