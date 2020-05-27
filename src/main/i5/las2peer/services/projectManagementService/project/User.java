package i5.las2peer.services.projectManagementService.project;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.json.simple.JSONObject;

import i5.las2peer.services.projectManagementService.exception.UserNotFoundException;

/**
 * (Data-)Class for User. Provides means to convert Object
 * to JSON. Also provides means to persist the object to a database.
 */
public class User {
	
	/**
	 * Id of the user.
	 * Might be -1 if the user is not stored to the database yet.
	 */
	private int id = -1;
	
	/**
	 * Email of the user.
	 */
	private String email;
	
	/**
	 * Login name of the user.
	 */
	private String loginName;
	
	/**
	 * Sets parameters except for the id.
	 * Can be used before persisting the user.
	 * @param email Email of the user that should be created.
	 * @param loginName Login name of the user that should be created.
	 */
	public User(String email, String loginName) {
		this.email = email;
		this.loginName = loginName;
	}
	
	/**
	 * Method for storing the user object to the database.
	 * @param connection a Connection object
	 * @throws SQLException If something with database went wrong.
	 */
	public void persist(Connection connection) throws SQLException {
		PreparedStatement statement;
		// formulate empty statement for storing the user
		statement = connection.prepareStatement("INSERT INTO User (email, loginName) VALUES (?,?);", Statement.RETURN_GENERATED_KEYS);
		// set email and loginName of user
		statement.setString(1, this.email);
		statement.setString(2, this.loginName);
		
		// execute query
		statement.executeUpdate();
		
		// get the generated project id and close statement
		ResultSet genKeys = statement.getGeneratedKeys();
		genKeys.next();
		this.id = genKeys.getInt(1);
		statement.close();
	}
	
	/**
	 * Method for loading user by given email from database.
	 * @param email Email of user to search for.
	 * @param connection a Connection object
	 * @throws SQLException If something with the database went wrong (or UserNotFoundException if user not found).
	 */
	public User(String email, Connection connection) throws SQLException {
		this.email = email;
		
		// search for user with the given name
	    PreparedStatement statement = connection.prepareStatement("SELECT * FROM User WHERE email=?;");
		statement.setString(1, email);
		// execute query
	    ResultSet queryResult = statement.executeQuery();
	    
	    // check for results
		if (queryResult.next()) {
			this.id = queryResult.getInt(1);
			this.loginName = queryResult.getString("loginName");
		} else {
			// there does not exist a user with the given email in the database
			throw new UserNotFoundException();
		}
		statement.close();
	}
	
	/**
	 * Searches for a user with the given loginName.
	 * @param loginName Login name of the user to search for.
	 * @param connection Connection object
	 * @return User object
	 * @throws SQLException If something with the database went wrong (or UserNotFoundException).
	 */
	public static User loadUserByLoginName(String loginName, Connection connection) throws SQLException {
		PreparedStatement statement = connection.prepareStatement("SELECT * FROM User WHERE loginName=?;");
		statement.setString(1, loginName);
		// execute query
		ResultSet queryResult = statement.executeQuery();
		
		// check for results
		if(queryResult.next()) {
		    String email = queryResult.getString("email");
			statement.close();
			return new User(email, connection);
		} else {
			statement.close();
			throw new UserNotFoundException();
		}
	}
	
	/**
	 * Creates a JSON object from the user object.
	 * @return JSONObject containing the user information.
	 */
	@SuppressWarnings("unchecked")
	public JSONObject toJSONObject() {
		JSONObject jsonUser = new JSONObject();
		
		// put attributes
		jsonUser.put("id", this.id);
		jsonUser.put("loginName", this.loginName);
		jsonUser.put("email", this.email);

		return jsonUser;
	}
	
	public int getId() {
		return this.id;
	}
	
}
