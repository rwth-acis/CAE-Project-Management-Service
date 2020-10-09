package i5.las2peer.services.projectManagementService.exception;

import java.sql.SQLException;

/**
 * Gets thrown when the role that was searched for does not exist in the project.
 * @author Philipp
 *
 */
public class RoleNotFoundException extends SQLException {
	private static final long serialVersionUID = -6358411425859277993L;
}
