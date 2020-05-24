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
     * Users that are part of the project.
     */
    private ArrayList<User> users;
    
    /**
     * Roles that belong to the project.
     */
    private ArrayList<Role> roles;
    
    /**
     * Creates a project object from the given JSON string.
     * This constructor should be used before storing new projects.
     * Therefore, no project id need to be included in the JSON string yet.
     * @param creator User that creates the project.
     * @param jsonProject JSON representation of the project to store.
     * @throws ParseException If parsing went wrong.
     */
    public Project(User creator, String jsonProject) throws ParseException {
    	JSONObject project = (JSONObject) JSONValue.parseWithException(jsonProject);
    	this.name = (String) project.get("name");
    	
    	// only add the creator as user
    	// do not call addUser() method here, because the entry in ProjectToUser table should
    	// not be created now (will be done when calling persist() method)
    	this.users = new ArrayList<>();
    	this.users.add(creator);
    }
    
    /**
     * Creates a new project object by loading it from the database.
     * @param projectName the name of the project that resides in the database
     * @param connection a Connection Object
     * @throws SQLException if the project is not found (ProjectNotFoundException) or something else went wrong
     */
	public Project(String projectName, Connection connection) throws SQLException {
		// search for project with the given name
	    PreparedStatement statement = connection.prepareStatement("SELECT * FROM Project WHERE name=?;");
		statement.setString(1, projectName);
		// execute query
	    ResultSet queryResult = statement.executeQuery();
	    
	    // check for results
		if (queryResult.next()) {
			// call helper method for setting all the attributes
			setAttributesFromQueryResult(queryResult, connection);
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
		// search for project with the given id
	    PreparedStatement statement = connection.prepareStatement("SELECT * FROM Project WHERE id=?;");
		statement.setInt(1, projectId);
		// execute query
	    ResultSet queryResult = statement.executeQuery();
	    
	    // check for results
		if (queryResult.next()) {
			setAttributesFromQueryResult(queryResult, connection);
		} else {
			// there does not exist a project with the given id in the database
			throw new ProjectNotFoundException();
		}
		statement.close();
	}
	
	/**
	 * Gets used by the constructors that load a project from the database.
	 * @param queryResult Should contain all columns and next() should have been called already.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong.
	 */
	private void setAttributesFromQueryResult(ResultSet queryResult, Connection connection) throws SQLException {
		this.id = queryResult.getInt("id");
		this.name = queryResult.getString("name");
		
		// load users
	    loadUsers(connection);
	}
	
	private void loadUsers(Connection connection) throws SQLException {
		this.users = new ArrayList<>();
		
		PreparedStatement statement = connection.prepareStatement("SELECT User.email FROM ProjectToUser, User WHERE ProjectToUser.userId = User.id AND ProjectToUser.projectId = ?;");
		statement.setInt(1, this.id);
		// execute query
		ResultSet queryResult = statement.executeQuery();
		
		while(queryResult.next()) {
			String email = queryResult.getString("email");
			this.users.add(new User(email, connection));
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
			
			// store users
			persistUsers(connection);
			
			// no errors occurred, so commit
			connection.commit();
		} catch (SQLException e) {
			// roll back the whole stuff
			connection.rollback();
			throw e;
		}
	}
	
	private void persistUsers(Connection connection) throws SQLException {
		for(User user : this.users) {
			addUser(user.getId(), connection);
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
		
		// put users
		JSONArray jsonUsers = new JSONArray();
		for(User user : users) {
			jsonUsers.add(user.toJSONObject());
		}
		jsonProject.put("users", jsonUsers);

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
	
	/**
	 * Queries the database by using the given statement (which needs to fulfill some requirements, see below).
	 * @param statement IMPORTANT: this must already have all parameters set and the query needs to select the project id.
	 * @param connection Connection object
	 * @return ArrayList of projects resulted by the query.
	 * @throws SQLException If something with the database went wrong.
	 */
	private static ArrayList<Project> queryProjects(PreparedStatement statement, Connection connection) throws SQLException {
		ArrayList<Project> projects = new ArrayList<>();
		
		// execute query
		ResultSet queryResult = statement.executeQuery();
				
		// add every project of the results to the list
		while(queryResult.next()) {
			projects.add(new Project(queryResult.getInt("id"), connection));
		}
				
	    statement.close();
	    return projects;
	}
	
	/**
	 * Searches for projects where the user with the given id is part of.
	 * @param userId Id of the user to search the projects for.
	 * @param connection Connection object
	 * @return Empty ArrayList when no project was found. Otherwise it contains the projects that the user is part of.
	 * @throws SQLException If something with the database went wrong.
	 */
	public static ArrayList<Project> getProjectsByUser(int userId, Connection connection) throws SQLException {
		// search for projects where user is part of
		PreparedStatement statement = connection.prepareStatement("SELECT Project.id FROM Project, ProjectToUser WHERE Project.id = ProjectToUser.projectId AND ProjectToUser.userId = (?);");
		statement.setInt(1, userId);
		
		return queryProjects(statement, connection);
	}
	
	/**
	 * Searches for projects where the name is like the search input given.
	 * @param searchInput Search input / name of the project to search for.
	 * @param connection Connection object
	 * @return ArrayList of projects containing the search results.
	 * @throws SQLException If something with the database went wrong.
	 */
	public static ArrayList<Project> searchProjects(String searchInput, Connection connection) throws SQLException {
		// search for projects where the name is like the searchInput given
		PreparedStatement statement = connection.prepareStatement("SELECT Project.id FROM Project WHERE name LIKE ?;");
		statement.setString(1, "%" + searchInput + "%");
		
		return queryProjects(statement, connection);
	}
	
	/**
	 * Creates a JSONArray containing the projects from the given list as JSONObjects.
	 * @param projects ArrayList with Project objects
	 * @return JSONArray containing the projects given as JSONObjects.
	 */
	public static JSONArray projectListToJSONArray(ArrayList<Project> projects) {
		JSONArray jsonProjects = new JSONArray();
    	for(Project p : projects) {
    		jsonProjects.add(p.toJSONObject());
    	}
    	return jsonProjects;
	}
}
