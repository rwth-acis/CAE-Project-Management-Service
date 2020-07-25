package i5.las2peer.services.projectManagementService.component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

public class Dependency {

	/**
	 * Id of the dependency item.
	 */
	private int id;
	
	/**
	 * Id of the project, where this dependency is added to.
	 */
	private int projectId;
	
	/**
	 * Id of the component, which is added as a dependency to the project.
	 */
	private int componentId;
	
	/**
	 * Component object which is included in the project.
	 * This might be null, if dependency not loaded from database.
	 */
	private Component component;
	
	/**
	 * Constructor that creates a Dependency object from the given project and component ids.
	 * @param projectId Id of the project, where the component is a dependency of.
	 * @param componentId Id of the component, that is a dependency of the project.
	 */
	public Dependency(int projectId, int componentId) {
		this.projectId = projectId;
		this.componentId = componentId;
	}
	
	/**
	 * Loads a Dependency object from the database.
	 * @param id Id of the Dependency object to load.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong.
	 */
	public Dependency(int id, Connection connection) throws SQLException {
		this.id = id;
		PreparedStatement statement = connection.prepareStatement("SELECT * FROM Dependency WHERE id = ?;");
		statement.setInt(1, id);
		ResultSet result = statement.executeQuery();
		if(result.next()) {
			this.projectId = result.getInt("projectId");
			this.componentId = result.getInt("componentId");
			try {
				this.component = new Component(this.componentId, connection);
			} catch (ParseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		statement.close();
	}
	
	/**
	 * Persists the Dependency object to the database.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong.
	 */
	public void persist(Connection connection) throws SQLException {
	    PreparedStatement statement = connection.prepareStatement("INSERT INTO Dependency (projectId, componentId) VALUES (?,?);");
	    statement.setInt(1, this.projectId);
	    statement.setInt(2, this.componentId);
	    statement.executeUpdate();
	    statement.close();
	}
	
	/**
	 * Returns the JSON representation of this dependency.
	 * @return a JSON object representing a dependency.
	 */
	@SuppressWarnings("unchecked")
	public JSONObject toJSONObject() {
		JSONObject jsonDependency = new JSONObject();
		
		jsonDependency.put("dependencyId", this.id);
		jsonDependency.put("component", this.component.toJSONObject());
		
		return jsonDependency;
	}
	
	public int getId() {
		return this.id;
	}
	
	public int getProjectId() {
		return this.projectId;
	}
	
	public int getComponentId() {
		return this.componentId;
	}
	
	public Component getComponent() {
		return this.component;
	}
}
