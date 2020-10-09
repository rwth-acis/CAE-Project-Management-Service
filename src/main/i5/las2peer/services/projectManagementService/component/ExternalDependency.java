package i5.las2peer.services.projectManagementService.component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.json.simple.JSONObject;

public class ExternalDependency {
	
	/**
	 * Id of the external dependency.
	 */
	private int id;
	
	/**
	 * Id of the project, where the external dependency belongs to.
	 */
	private int projectId;
	
	/**
	 * URL to the GitHub repository where the external dependency is hosted on.
	 */
	private String gitHubURL;
	
	/**
	 * Type, either "frontend" or "microservice".
	 */
	private String type;
	
	/**
	 * Constructor that creates a new ExternalDependency object from the given project id and GitHub URL.
	 * @param projectId Id of the project, where the external dependency should be added to.
	 * @param gitHubURL URL to the corresponding GitHub repository.
	 * @param type Type of the external dependencies.
	 */
	public ExternalDependency(int projectId, String gitHubURL, String type) {
		this.projectId = projectId;
		this.gitHubURL = gitHubURL;
		this.type = type;
	}
	
	/**
	 * Loads an ExternalDependency object from the database.
	 * @param id Id of the ExternalDependency object to load.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong.
	 */
	public ExternalDependency(int id, Connection connection) throws SQLException {
		this.id = id;
		PreparedStatement statement = connection.prepareStatement("SELECT * FROM ExternalDependency WHERE id = ?;");
		statement.setInt(1, id);
		ResultSet result = statement.executeQuery();
		if(result.next()) {
			this.projectId = result.getInt("projectId");
			this.gitHubURL = result.getString("gitHubURL");
			this.type = result.getString("type");
		}
		
		statement.close();
	}
	
	/**
	 * Persists the ExternalDependency object to the database.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong.
	 */
	public void persist(Connection connection) throws SQLException {
	    PreparedStatement statement = connection
	    		.prepareStatement("INSERT INTO ExternalDependency (projectId, gitHubURL, type) VALUES (?,?,?);");
	    statement.setInt(1, this.projectId);
	    statement.setString(2, this.gitHubURL);
	    statement.setString(3, this.type);
	    statement.executeUpdate();
	    statement.close();
	}
	
	/**
	 * Returns the JSON representation of this dependency.
	 * @return a JSON object representing a dependency.
	 */
	@SuppressWarnings("unchecked")
	public JSONObject toJSONObject() {
		JSONObject jsonExternalDependency = new JSONObject();
		
		jsonExternalDependency.put("externalDependencyId", this.id);
		jsonExternalDependency.put("gitHubURL", this.gitHubURL);
		jsonExternalDependency.put("type", this.type);
		
		return jsonExternalDependency;
	}
	
	public int getId() {
		return this.id;
	}
	
	public String getGitHubRepoOwner() {
		return this.gitHubURL.split(".com/")[1].split("/")[0];
	}
	
	public String getGitHubRepoName() {
		String repoName = this.gitHubURL.split(".com/")[1].split("/")[1];
		if(repoName.endsWith(".git")) repoName = repoName.replace(".git", "");
		return repoName;
	}

}
