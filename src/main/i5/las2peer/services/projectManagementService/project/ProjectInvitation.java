package i5.las2peer.services.projectManagementService.project;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

import net.minidev.json.JSONValue;

/**
 * Represents an invitation where a user gets invited to a project.
 * @author Philipp
 *
 */
public class ProjectInvitation {
	
	/**
	 * Id of the ProjectInvitation entry in the database.
	 */
	private int id;
	
	/**
	 * Id of the project where the user gets invited to.
	 */
	private int projectId;
	
	/**
	 * Id of the user that gets invited to the project.
	 */
	private int userId;
	
	public ProjectInvitation(int id, int projectId, int userId) {
		this.id = id;
		this.projectId = projectId;
		this.userId = userId;
	}
	
	public ProjectInvitation(int projectId, int userId) {
		this.projectId = projectId;
		this.userId = userId;
	}
	
	/**
	 * Persists the current ProjectInvitation object to the database.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong.
	 */
	public void persist(Connection connection) throws SQLException {
		PreparedStatement statement = connection.prepareStatement("INSERT INTO ProjectInvitation (projectId, userId) VALUES (?,?);", Statement.RETURN_GENERATED_KEYS);
	    statement.setInt(1, this.projectId);
	    statement.setInt(2, this.userId);
	    
	    // execute update
	    statement.executeUpdate();
	    
	    // get id of the new entry
	    ResultSet genKeys = statement.getGeneratedKeys();
		genKeys.next();
		this.id = genKeys.getInt(1);
	    statement.close();
	}
	
	/**
	 * Checks if an invitation for the given project and user already exists.
	 * @param projectId Id of the project
	 * @param userId Id of the user
	 * @param connection Connection object
	 * @return Whether an invitation for the given project and user already exists.
	 * @throws SQLException If something with the database went wrong.
	 */
	public static boolean exists(int projectId, int userId, Connection connection) throws SQLException {
		PreparedStatement statement = connection.prepareStatement("SELECT * FROM ProjectInvitation WHERE projectId = ? AND userId = ?;");
		statement.setInt(1, projectId);
		statement.setInt(2, userId);
		
		// execute query
		ResultSet queryResult = statement.executeQuery();
		boolean exists = queryResult.next();
		statement.close();
		return exists;
	}
	
	/**
	 * Loads all invitations by the given user from the database.
	 * @param userId Id of the user where the invitations should be loaded for.
	 * @param connection Connection object
	 * @return JSONArray containing the invitations that the user received.
	 * @throws SQLException If something with the database went wrong.
	 */
	public static JSONArray loadInvitationsByUser(int userId, Connection connection) throws SQLException {
		PreparedStatement statement = connection.prepareStatement("SELECT * FROM ProjectInvitation WHERE userId = ?;");
		statement.setInt(1, userId);
		
		JSONArray invitations = new JSONArray();
		
		ResultSet queryResult = statement.executeQuery();
		while(queryResult.next()) {
			ProjectInvitation inv = new ProjectInvitation(queryResult.getInt("id"),
					queryResult.getInt("projectId"), queryResult.getInt("userId"));
			
			JSONObject json = inv.toJSONObject();
			// add name of project
			Project project = new Project(inv.getProjectId(), connection);
			json.put("projectName", project.getName());
			
			invitations.add(json);
		}
		
		statement.close();
		
		return invitations;
	}
	
	/**
	 * Returns the JSON representation of the ProjectInvitation object.
	 * @return JSON representation of the ProjectInvitation object
	 */
	@SuppressWarnings("unchecked")
	public JSONObject toJSONObject() {
		JSONObject jsonInvitation = new JSONObject();
		
		jsonInvitation.put("id", this.id);
		jsonInvitation.put("projectId", this.projectId);
		jsonInvitation.put("userId", this.userId);
		
		return jsonInvitation;
	}
	
	public int getProjectId() {
		return this.projectId;
	}

}
