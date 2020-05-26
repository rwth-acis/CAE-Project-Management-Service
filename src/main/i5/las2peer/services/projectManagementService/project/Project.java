package i5.las2peer.services.projectManagementService.project;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import i5.las2peer.services.projectManagementService.exception.NoDefaultRoleFoundException;
import i5.las2peer.services.projectManagementService.exception.ProjectNotFoundException;
import i5.las2peer.services.projectManagementService.exception.RoleNotFoundException;

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
     * Users that are part of the project.
     */
    private ArrayList<User> users;
    
    /**
     * Assigns a role to every user.
     */
    private HashMap<User, Role> roleAssignment;
    
    /**
     * Project id of the project in the Requirements Bazaar which is linked
     * to this CAE project.
     */
    private int reqBazProjectId;
    
    /**
     * Category id of the category in the Requirements Bazaar which is linked
     * to this CAE project.
     */
    private int reqBazCategoryId;
    
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
    	
    	this.roleAssignment = new HashMap<>();
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
		this.reqBazProjectId = queryResult.getInt("reqBazProjectId");
		this.reqBazCategoryId = queryResult.getInt("reqBazCategoryId");
		
		// load roles
		loadRoles(connection);
		
		// load users
	    loadUsers(connection);
	}
	
	/**
	 * Loads the roles of the project from the database.
	 * Therefore, the id of the project already needs to be set.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong.
	 */
	private void loadRoles(Connection connection) throws SQLException {
		this.roles = new ArrayList<>();
		
		PreparedStatement statement = connection.prepareStatement("SELECT * FROM Role WHERE projectId = ?;");
		statement.setInt(1, this.id);
		// execute query
		ResultSet queryResult = statement.executeQuery();
		
		while(queryResult.next()) {
			int roleId = queryResult.getInt("id");
			String name = queryResult.getString("name");
			boolean isDefault = queryResult.getBoolean("is_default");
			this.roles.add(new Role(roleId, this.id, name, isDefault));
		}
		
		statement.close();
	}
	
	/**
	 * Loads the users of the project from the database.
	 * Therefore, the id of the project already needs to be set.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong.
	 */
	private void loadUsers(Connection connection) throws SQLException {
		this.users = new ArrayList<>();
		
		// also prepare map for role assignment
		this.roleAssignment = new HashMap<>();
		
		PreparedStatement statement = connection.prepareStatement("SELECT User.email FROM ProjectToUser, User WHERE ProjectToUser.userId = User.id AND ProjectToUser.projectId = ?;");
		statement.setInt(1, this.id);
		// execute query
		ResultSet queryResult = statement.executeQuery();
		
		while(queryResult.next()) {
			String email = queryResult.getString("email");
			User user = new User(email, connection);
			
			// assign users role
			this.loadUsersRole(user, connection);
			
			// add user to users list
			this.users.add(user);
		}
		
		statement.close();
	}
	
	/**
	 * Finds out the role of the given user in the current project.
	 * Therefore, the id of the current project object needs to be set.
	 * When the role could be found, then it gets assigned to the user by
	 * adding it to the roleAssignment map.
	 * @param user User to load the role for.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong (RoleNotFoundException when role does not exist).
	 */
	private void loadUsersRole(User user, Connection connection) throws SQLException {
		PreparedStatement statement = connection
				.prepareStatement("SELECT UserToRole.roleId FROM UserToRole, ProjectToUser " +
		                          "WHERE UserToRole.projectToUserId = ProjectToUser.id AND " +
						          "ProjectToUser.projectId = ? AND ProjectToUser.userId = ?;");
		
		statement.setInt(1, this.id);
		statement.setInt(2, user.getId());
		// execute query
		ResultSet queryResult = statement.executeQuery();
		
		if(queryResult.next()) {
			int roleId = queryResult.getInt("roleId");	
			// find role with the given id in roles list
			for(Role role : this.roles) {
				if(role.getId() == roleId) {
					this.roleAssignment.put(user, role);
					return;
				}
			}
		}
		throw new RoleNotFoundException();
	}
	
	/**
	 * Searches the roleAssignment map for the given user.
	 * Note: Check if the roleAssignment map is loaded before calling this
	 * method.
	 * @param user User object to search the role for.
	 * @return Role object of the user.
	 */
	public Role getRoleByUser(User user) {
		return this.roleAssignment.get(user);
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
			
			// store default roles
			persistPredefinedRoles(connection);
			
			// store users (must be done after storing roles, because default role needs to be persisted)
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
			addUser(user, connection, false); // false, because user should not be added to this.users again
		}
	}
	
	/**
	 * Stores the predefined roles to the project.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong.
	 */
	private void persistPredefinedRoles(Connection connection) throws SQLException {
		this.roles = PredefinedRoles.get(this.id);
		
		// persist roles
		for(Role role : this.roles) {
			role.persist(connection);
		}
		
		// there is no need to connect the project with the roles, since
		// the roles already contain the projectId as a foreign key
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
		jsonProject.put("reqBazProjectId", this.reqBazProjectId);
		jsonProject.put("reqBazCategoryId", this.reqBazCategoryId);
		
		// put roles
		JSONArray jsonRoles = new JSONArray();
		for(Role role : roles) {
			jsonRoles.add(role.toJSONObject());
		}
		jsonProject.put("roles", jsonRoles);
		
		// put users
		// this should also include the role of each user; since the role is not stored in
		// the User object itself (because it does not only depend on the user, but on
		// the project too) the role needs to be added manually
		JSONArray jsonUsers = new JSONArray();
		for(User user : users) {
			JSONObject jsonUser = user.toJSONObject();
			
			// find out id of the role which is assigned to the user
			int roleId = roleAssignment.get(user).getId();
			jsonUser.put("roleId", roleId);
			
			jsonUsers.add(jsonUser);
		}
		jsonProject.put("users", jsonUsers);

		return jsonProject;
	}
	
	/**
	 * Adds the given role to the current project.
	 * @param role Role to add.
	 * @param connection Connection object
	 * @return False, if a role with the same name already exists in the project. True, if role got added.
	 * @throws SQLException If something with the database went wrong.
	 */
	public boolean addRole(Role role, Connection connection) throws SQLException {
		// check if role with the name already exists
		if(hasRole(role.getName())) return false;
		
		// role with the same name does not exist for the project
		// add role to project now
		role.persist(connection);
		
		return true;
	}
	
	public boolean hasRole(String name) {
		for(Role r : this.roles) {
			if(r.getName().equals(name)) return true;
		}
		return false;
	}
	
	public boolean hasRole(int roleId) {
		for(Role r : this.roles) {
			if(r.getId() == roleId) return true;
		}
		return false;
	}
	
	/**
	 * Removes the role with the given id from the project.
	 * @param roleId Id of the role to remove.
	 * @param connection Connection object
	 * @return False, if role cannot be removed because it is assigned to at least one user. True, if removed successfully.
	 * @throws SQLException If something with the database went wrong (RoleNotFoundException when role does not exist in project).
	 */
	public boolean removeRole(int roleId, Connection connection) throws SQLException {
		// first check if role is part of the project
		if(!hasRole(roleId)) throw new RoleNotFoundException();
		
		// check if role is assigned to at least one user, because then it should not be removed
		PreparedStatement statement = connection.prepareStatement("SELECT * FROM UserToRole WHERE roleId = ?;");
		statement.setInt(1, roleId);
		// execute query
		statement.executeQuery();
		
		ResultSet queryResult = statement.executeQuery();
		if(queryResult.next()) return false; // at least one user has assigned this role, dont remove it
		statement.close();
		
		// no user has assigned the role, delete it now
		// remove role
		statement = connection.prepareStatement("DELETE FROM Role WHERE id = ?;");
		statement.setInt(1, roleId);
		// execute update
		statement.executeUpdate();
		statement.close();
		
		return true;
	}
	
	/**
	 * Adds the user with the given id to the project.
	 * @param user User object of the user to add to the project.
	 * @param connection Connection object
	 * @param addToUsersList Whether the user also should be added to the users list of the Project object.
	 * @return False if user is already part of the project. True if user was added successfully.
	 * @throws SQLException If something with the database went wrong.
	 */
	public boolean addUser(User user, Connection connection, boolean addToUsersList) throws SQLException {
		int userId = user.getId();
		
		// first check if user is already part of the project
		if(hasUser(userId, connection)) return false;
		
		// do not auto commit after inserting user to ProjectToUser table, because
		// after that when the role gets set an error might occur
		connection.setAutoCommit(false);
		
		// user is not part of the project yet, so add the user
		PreparedStatement statement = connection.prepareStatement("INSERT INTO ProjectToUser (projectId, userId) VALUES (?,?);", Statement.RETURN_GENERATED_KEYS);
		statement.setInt(1, this.id);
		statement.setInt(2, userId);
		// execute update
		statement.executeUpdate();
		
		// get the generated ProjectToUser id and close statement
		ResultSet genKeys = statement.getGeneratedKeys();
		genKeys.next();
		int projectToUserId = genKeys.getInt(1);
		
		statement.close();
		
		// set role of user to the default one
		try {
		    Role defaultRole = this.getDefaultRole();
		    
		    statement = connection.prepareStatement("INSERT INTO UserToRole (userId, roleId, projectToUserId) VALUES (?,?,?);");
		    statement.setInt(1, userId);
		    statement.setInt(2, defaultRole.getId());
		    statement.setInt(3, projectToUserId);
		    
		    // execute update
		    statement.executeUpdate();
		    statement.close();
		    
		    // no errors occurred, so commit
		 	connection.commit();
		 	
		 	// also add user to users list of project
		 	if(addToUsersList) {
		 	    this.users.add(user);
		 	}
		 	// also put role into roleAssignment map
		 	this.roleAssignment.put(user, defaultRole);
		} catch (NoDefaultRoleFoundException e) {
			// roll back the whole stuff
			connection.rollback();
		    throw e;
		}
		return true;
	}
	
	/**
	 * Removes the user with the given id from the project.
	 * @param userId Id of the user to remove.
	 * @param connection Connection object
	 * @return False, if user cannot be removed because he is no member of the project. True, if removed successfully.
	 * @throws SQLException If something with the database went wrong.
	 */
	public boolean removeUser(int userId, Connection connection) throws SQLException {
		// first check if user is part of the project
		if(!hasUser(userId, connection)) return false;
		
		// user is member of the project, so remove user
		PreparedStatement statement = connection.prepareStatement("DELETE FROM ProjectToUser WHERE projectId = ? and userId = ?;");
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
	
	/**
	 * Updates the Requirements Bazaar config in the database.
	 * @param reqBazProjectId ProjectId of the project in the Requirements Bazaar.
	 * @param reqBazCategoryId CategoryId of the category in the Requirements Bazaar.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong.
	 */
	public void updateRequirementsBazaarConfig(int reqBazProjectId, int reqBazCategoryId, Connection connection) throws SQLException {
		PreparedStatement statement = connection
				.prepareStatement("UPDATE Project SET reqBazProjectId = ?, reqBazCategoryId = ? WHERE id = ?;");
		statement.setInt(1, reqBazProjectId);
		statement.setInt(2, reqBazCategoryId);
		statement.setInt(3, this.id);
		
		// execute update
		statement.executeUpdate();
		statement.close();
	}
	
	/**
	 * Disconnects the CAE project from the Requirements Bazaar project in the database.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong.
	 */
	public void disconnectRequirementsBazaar(Connection connection) throws SQLException {
		PreparedStatement statement = connection
				.prepareStatement("UPDATE Project SET reqBazProjectId = NULL, reqBazCategoryId = NULL WHERE id = ?;");
		statement.setInt(1, this.id);
		
		// execute update
		statement.executeUpdate();
		statement.close();
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
	 * Iterates over the list of roles of the project and returns the
	 * one role which is marked as the default role.
	 * @return Role object where isDefault is set to true.
	 * @throws NoDefaultRoleFoundException If the list of roles does not contain a default role.
	 */
	private Role getDefaultRole() throws NoDefaultRoleFoundException {
		for(Role role : this.roles) {
			if(role.isDefault()) return role;
		}
		throw new NoDefaultRoleFoundException();
	}
}
