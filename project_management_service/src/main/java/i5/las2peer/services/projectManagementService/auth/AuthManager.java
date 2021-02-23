package i5.las2peer.services.projectManagementService.auth;

import java.sql.Connection;
import java.sql.SQLException;

import i5.las2peer.api.Context;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AnonymousAgent;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.services.projectManagementService.database.DatabaseManager;
import i5.las2peer.services.projectManagementService.exception.UserNotFoundException;
import i5.las2peer.services.projectManagementService.project.User;

/**
 * Helper class for getting the currently active user.
 * @author Philipp
 *
 */
public class AuthManager {
	
	private L2pLogger logger;
	private DatabaseManager dbm;

	public AuthManager(L2pLogger logger, DatabaseManager dbm) {
		this.logger = logger;
		this.dbm = dbm;
	}

	public boolean isAnonymous() {
        Agent agent = Context.getCurrent().getMainAgent();
		return agent instanceof AnonymousAgent;
	}
	
	/**
	 * Returns the User object to the current active user.
	 * When the user is already registered, i.e. can be found in the database,
	 * then the already registered user is returned.
	 * Otherwise, when the user is not registered yet, the user gets stored
	 * into the database.
	 * @return User object.
	 * @throws SQLException If something went wrong with the database.
	 */
	public User getUser() throws SQLException {
        Agent agent = Context.getCurrent().getMainAgent();
		
		UserAgent userAgent = (UserAgent) agent;
	    String email = userAgent.getEmail();
	    String loginName = userAgent.getLoginName();
		
		User user = null;
		Connection connection = null;
		try {
			connection = dbm.getConnection();
			// check if user is registered, i.e. exists in the database
			// when there does not exist a user with the given email, a 
			// UserNotFoundException gets thrown
			user = new User(email, connection);
			// no UserNotFoundException was thrown
			return user;
		} catch (UserNotFoundException e) {
			// user does not exist in the database
			// register user
			user = new User(email, loginName);
			
			// store user object to database
			user.persist(connection);
			return user;
		} finally {
			try {
			    connection.close();
			} catch (SQLException e) {
				logger.printStackTrace(e);
			}
		}
	}
	
}
