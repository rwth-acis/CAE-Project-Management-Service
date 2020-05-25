package i5.las2peer.services.projectManagementService.project;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.json.simple.JSONObject;

/**
 * (Data-)Class for Roles. Provides means to convert JSON to Object and Object
 * to JSON. Also provides means to persist the object to a database.
 * TODO: check if this javadoc is still correct later
 */
public class Role {

	/**
	 * Id of the role.
	 * Initially set to -1 if role is not persisted yet.
	 */
	private int id = -1;
	
	/**
	 * Id of the project that the role belongs to.
	 */
	private int projectId;
	
	/**
	 * Name of the role.
	 */
	private String name;
	
	/**
	 * Whether the role is the default one of the project.
	 */
	private boolean isDefault;
	
	public Role(int id, int projectId, String name, boolean isDefault) {
		this.id = id;
		this.projectId = projectId;
	    this.name = name;	
	    this.isDefault = isDefault;
	}
	
	public Role(int projectId, String name, boolean isDefault) {
	    this.projectId = projectId;
	    this.name = name;	
	    this.isDefault = isDefault;
	}
	
	/**
	 * Method for storing the role object to the database.
	 * Project id, name and isDefault need to be set before calling this method.
	 * @param connection a Connection object
	 * @throws SQLException If something with database went wrong.
	 */
	public void persist(Connection connection) throws SQLException {
		PreparedStatement statement = connection.prepareStatement("INSERT INTO Role (projectId, name, is_default) VALUES (?,?,?);", Statement.RETURN_GENERATED_KEYS);
		// set projectId and name
		statement.setInt(1, this.projectId);
		statement.setString(2, this.name);
		statement.setBoolean(3, this.isDefault);
		
		// execute query
		statement.executeUpdate();
				
		// get the generated role id and close statement
		ResultSet genKeys = statement.getGeneratedKeys();
		genKeys.next();
		this.id = genKeys.getInt(1);
		statement.close();
	}
	
	/**
	 * Returns the JSON representation of this role.
	 * This currently does not contain the attributes projectId and isDefault.
	 * @return a JSON object representing a role
	 */
	@SuppressWarnings("unchecked")
	public JSONObject toJSONObject() {
		JSONObject jsonRole = new JSONObject();
		
		jsonRole.put("id", this.id);
		jsonRole.put("name", this.name);
		
		return jsonRole;
	}
	
	public int getId() {
		return this.id;
	}
	
	public int getProjectId() {
		return this.projectId;
	}
	
	public String getName() {
		return this.name;
	}
	
	public boolean isDefault() {
		return this.isDefault;
	}
}
