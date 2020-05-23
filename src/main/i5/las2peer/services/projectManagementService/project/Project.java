package i5.las2peer.services.projectManagementService.project;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import i5.las2peer.services.projectManagementService.exception.ProjectNotFoundException;

/**
 * (Data-)Class for Projects. Provides means to convert JSON to Object and Object
 * to JSON. Also provides means to persist the object to a database.
 * TODO: check if this javadoc is still correct later
 */
public class Project {
	
	/**
	 * Id of the project.
	 * Initially set to -1 if project is not persisted yet.
	 */
    private int id = -1;
    
    /**
     * Name of the project.
     */
    private String name;
    
    /**
     * Roles that belong to the project.
     */
    private ArrayList<Role> roles;
    
    /**
     * Creates a project object from the given JSON string.
     * This constructor should be used before storing new projects.
     * Therefore, no project id need to be included in the JSON string yet.
     * @param jsonProject JSON representation of the project to store.
     * @throws ParseException If parsing went wrong.
     */
    public Project(String jsonProject) throws ParseException {
    	JSONObject project = (JSONObject) JSONValue.parseWithException(jsonProject);
    	this.name = (String) project.get("name");
    }
    
    /**
     * Creates a new project by loading it from the database.
     * @param projectName the name of the project that resides in the database
     * @param connection a Connection Object
     * @throws SQLException if the project is not found (ProjectNotFoundException) or something else went wrong
     */
	public Project(String projectName, Connection connection) throws SQLException {
		this.name = projectName;
		
		// search for project with the given name
	    PreparedStatement statement = connection.prepareStatement("SELECT * FROM Project WHERE name=?;");
		statement.setString(1, projectName);
		// execute query
	    ResultSet queryResult = statement.executeQuery();
	    
	    // check for results
		if (queryResult.next()) {
			this.id = queryResult.getInt(1);
		} else {
			// there does not exist a project with the given name in the database
			throw new ProjectNotFoundException();
		}
		statement.close();
	}
	
    /**
     * Creates a new project by loading it from the database.
     * @param projectId the id of the project that resides in the database
     * @param connection a Connection Object
     * @throws SQLException if the project is not found (ProjectNotFoundException) or something else went wrong
     */
	public Project(int projectId, Connection connection) throws SQLException {
		this.id = projectId;
		
		// search for project with the given id
	    PreparedStatement statement = connection.prepareStatement("SELECT * FROM Project WHERE id=?;");
		statement.setInt(1, projectId);
		// execute query
	    ResultSet queryResult = statement.executeQuery();
	    
	    // check for results
		if (queryResult.next()) {
			this.name = queryResult.getString("name");
		} else {
			// there does not exist a project with the given id in the database
			throw new ProjectNotFoundException();
		}
		statement.close();
	}
	
	/**
	 * Persists a project.
	 * @param connection a Connection Object
	 * @throws SQLException if something with the database has gone wrong
	 */
	public void persist(Connection connection) throws SQLException {
		PreparedStatement statement;
		try {
			connection.setAutoCommit(false);
			
			// formulate empty statement for storing the project
			statement = connection.prepareStatement("INSERT INTO Project (name) VALUES (?);", Statement.RETURN_GENERATED_KEYS);
			// set name of project
			statement.setString(1, this.name);
			// execute update
			statement.executeUpdate();
		    // get the generated project id and close statement
			ResultSet genKeys = statement.getGeneratedKeys();
			genKeys.next();
			this.id = genKeys.getInt(1);
			statement.close();
			
			// no errors occurred, so commit
			connection.commit();
		} catch (SQLException e) {
			// roll back the whole stuff
			connection.rollback();
			throw e;
		}
	}
	
	/**
	 * Getter for the id of the project.
	 * Note: This is -1 if the project got created from JSON and 
	 * has not been stored to the database yet.
	 * @return Id of the project.
	 */
	public int getId() {
	    return id;
	}
	
	/**
	 * Getter for the name of the project.
	 * @return Name of the project.
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Returns the JSON representation of this project.
	 * @return a JSON object representing a project
	 */
	@SuppressWarnings("unchecked")
	public JSONObject toJSONObject() {
		JSONObject jsonProject = new JSONObject();
		
		// put attributes
		jsonProject.put("id", this.id);
		jsonProject.put("name", this.name);

		return jsonProject;
	}
	
	/**
	 * Adds the user with the given id to the project.
	 * @param userId Id of the user to add to the project.
	 * @param connection Connection object
	 * @return False if user is already part of the project. True if user was added successfully.
	 * @throws SQLException If something with the database went wrong.
	 */
	public boolean addUser(int userId, Connection connection) throws SQLException {
		// first check if user is already part of the project
		if(hasUser(userId, connection)) return false;
		
		// user is not part of the project yet, so add the user
		PreparedStatement statement = connection.prepareStatement("INSERT INTO ProjectToUser (projectId, userId) VALUES (?,?);");
		statement.setInt(1, this.id);
		statement.setInt(2, userId);
		// execute update
		statement.executeUpdate();
		statement.close();
		
		// commit changes
		connection.commit();
		
		return true;
	}
	
	/**
	 * Checks if the current project has a user with the given id.
	 * @param userId Id of the user to search for.
	 * @param connection Connection object
	 * @return Whether the user is part of the project or not.
	 * @throws SQLException If something with the database went wrong.
	 */
	public boolean hasUser(int userId, Connection connection) throws SQLException {
		// search for entry in ProjectToUser table
	    PreparedStatement statement = connection.prepareStatement("SELECT * FROM ProjectToUser WHERE projectId = ? AND userId = ?;");
	    statement.setInt(1, this.id);
	    statement.setInt(2, userId);
	    // execute query
	    ResultSet queryResult = statement.executeQuery();
	    boolean exists = queryResult.next();
	    statement.close();
	    return exists;
	}
	
	public static ArrayList<Project> getProjectsByUser(int userId, Connection connection) throws SQLException {
		ArrayList<Project> projects = new ArrayList<>();
		
		// search for projects where user is part of
		PreparedStatement statement = connection.prepareStatement("SELECT Project.id FROM Project, ProjectToUser WHERE Project.id = ProjectToUser.projectId AND ProjectToUser.userId = (?);");
		statement.setInt(1, userId);
		// execute query
		ResultSet queryResult = statement.executeQuery();
		
		// add every project of the results to the list
		while(queryResult.next()) {
			projects.add(new Project(queryResult.getInt(1), connection));
		}
		
	    statement.close();
		return projects;
	}
}
