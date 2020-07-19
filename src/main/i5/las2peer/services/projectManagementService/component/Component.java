package i5.las2peer.services.projectManagementService.component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import i5.las2peer.api.Context;
import i5.las2peer.services.projectManagementService.ProjectManagementService;
import i5.las2peer.services.projectManagementService.exception.GitHubException;
import i5.las2peer.services.projectManagementService.exception.ReqBazException;
import i5.las2peer.services.projectManagementService.project.Project;
import i5.las2peer.services.projectManagementService.reqbaz.ReqBazCategory;
import i5.las2peer.services.projectManagementService.reqbaz.ReqBazHelper;

public class Component {
	
	/**
	 * Id of the component, which is set to -1
	 * before component gets persisted for the first time.
	 */
	private int id = -1;
	
	/**
	 * Name of the component.
	 */
	private String name;
	
	/**
	 * Type of the component.
	 */
	private ComponentType type;
	
	/**
	 * The connected category in the Requirements Bazaar.
	 */
	private ReqBazCategory reqBazCategory;
	
	/**
	 * Id of the versioned model which is connected to the component.
	 */
	private int versionedModelId;
	
	/**
	 * Constructor used when creating a totally new component, which is 
	 * not yet stored in the database.
	 * @param jsonComponent JSON representation of the component.
	 * @throws ParseException If the given JSON string could not be parsed or attributes are missing.
	 */
	public Component(String jsonComponent) throws ParseException {
		JSONObject component = (JSONObject) JSONValue.parseWithException(jsonComponent);
    	if(!component.containsKey("name")) throw new ParseException(0, "Attribute 'name' of component is missing.");
    	this.name = (String) component.get("name");
    	
    	if(!component.containsKey("type")) throw new ParseException(0, "Attribute 'type' of component is missing.");
    	String typeStr = (String) component.get("type");
        setType(typeStr);
	}
	
	/**
	 * Constructor used when creating a totally new component, which is 
	 * not yet stored in the database. This constructor gets used internally
	 * when creating the empty application component for a new project.
	 * @param name Name of the component that should get created.
	 * @param type Type of the component that should get created.
	 */
	public Component(String name, ComponentType type) {
		this.name = name;
		this.type = type;
	}
	
	/**
	 * Sets the ComponentType to the type given as a string.
	 * @param typeStr Type of the component as string.
	 * @throws ParseException If type does not match the format.
	 */
	private void setType(String typeStr) throws ParseException {
    	switch(typeStr) {
	        case "frontend":
		        this.type = ComponentType.FRONTEND;
		        break;
	        case "microservice":
	    	    this.type = ComponentType.MICROSERVICE;
	    	    break;
	        case "application":
	    	    this.type = ComponentType.APPLICATION;
	    	    break;
	        default:
	    	    throw new ParseException(0, "Attribute 'type' is not 'frontend', 'microservice' or 'application'.");
	    }
	}
	
	private String typeToString() {
		switch(this.type) {
		    case FRONTEND:
			    return "frontend";
		    case MICROSERVICE:
		    	return "microservice";
		    case APPLICATION:
		    	return "application";
		}
		// this cannot be the case
		return "error";
	}
	
	/**
	 * Creates a component by loading it from the database.
	 * @param componentId Id of the component to search for.
	 * @param connection Connection object
	 * @throws SQLException If something with the database went wrong.
	 * @throws ParseException If the stored component type does not match the format.
	 */
	public Component(int componentId, Connection connection) throws SQLException, ParseException {
		this.id = componentId;
		
		PreparedStatement statement = connection.prepareStatement("SELECT * FROM Component WHERE id = ?;");
		statement.setInt(1, componentId);
		
		// execute query
		ResultSet queryResult = statement.executeQuery();
		if(queryResult.next()) {
			this.name = queryResult.getString("name");
			setType(queryResult.getString("type"));
			this.versionedModelId = queryResult.getInt("versionedModelId");
			
			int reqBazProjectId = queryResult.getInt("reqBazProjectId");
			int reqBazCategoryId = queryResult.getInt("reqBazCategoryId");
			this.reqBazCategory = new ReqBazCategory(reqBazCategoryId, reqBazProjectId);
		}
		statement.close();
	}
	
	/**
	 * Persists a component. Also creates an empty versioned model.
	 * @param project Project which is the owner of the component.
	 * @param connection Connection object
	 * @param accessToken OIDC access token which should be used to create the Requirements Bazaar category.
	 * @throws SQLException If something with the database went wrong.
	 * @throws ReqBazException If something with creating the Requirements Bazaar category went wrong.
	 */
	public void persist(Project project, Connection connection, String accessToken) throws SQLException, ReqBazException {
		boolean autoCommitBefore = connection.getAutoCommit();
		try {
			connection.setAutoCommit(false);
			
			// create empty versioned model
			this.versionedModelId = ComponentInitHelper.createEmptyVersionedModel(connection);
			
			// create category in requirements bazaar
			ProjectManagementService service = (ProjectManagementService) Context.getCurrent().getService();
			if(!service.isCategoryCreationDisabled()) {
			    String categoryName = project.getId() + "-" + this.name;
			    this.reqBazCategory = ReqBazHelper.getInstance().createCategory(categoryName, accessToken);
			}
			
			// create component
		    PreparedStatement statement = connection
			    	.prepareStatement("INSERT INTO Component (name, type, versionedModelId, reqBazProjectId, reqBazCategoryId) VALUES (?,?,?,?,?);", Statement.RETURN_GENERATED_KEYS);
		    statement.setString(1, this.name);
		    statement.setString(2, typeToString());
		    statement.setInt(3, versionedModelId);
		    
		    if(!service.isCategoryCreationDisabled()) {
		    	statement.setInt(4, this.reqBazCategory.getProjectId());
			    statement.setInt(5, this.reqBazCategory.getId());	
		    } else {
		    	// -1 stands for no Requirements Bazaar category connected
		        statement.setInt(4, -1);
		        statement.setInt(5, -1);
		    }
		    
		    // execute update
		    statement.executeUpdate();
		    // get the generated component id and close statement
		    ResultSet genKeys = statement.getGeneratedKeys();
		    genKeys.next();
		    this.id = genKeys.getInt(1);
		    statement.close();
		
		    // also store in ProjectToComponent table
		    statement = connection.prepareStatement("INSERT INTO ProjectToComponent (projectId, componentId) VALUES (?,?);");
		    statement.setInt(1, project.getId());
		    statement.setInt(2, this.id);
		    statement.executeUpdate();
		    statement.close();
		    
		    connection.commit();
		} catch (SQLException e) {
			// roll back the whole stuff
			connection.rollback();
			throw e;
		} finally {
			connection.setAutoCommit(autoCommitBefore);
		}
	}
	
	/**
	 * Deletes the component from the database.
	 * @param connection Connection object
	 * @param accessToken Access token of the user, required to access the Requirements Bazaar API.
	 * @throws SQLException If something with the database went wrong.
	 * @throws ReqBazException If something with the Requirements Bazaar API went wrong.
	 */
	public void delete(Connection connection, String accessToken) throws SQLException, ReqBazException {
		PreparedStatement statement;
		// store current value of auto commit
		boolean autoCommitBefore = connection.getAutoCommit();
		try {
			connection.setAutoCommit(false);
			
			// delete component from database
			statement = connection.prepareStatement("DELETE FROM Component WHERE id = ?;");
			statement.setInt(1, this.id);
			statement.executeUpdate();
			statement.close();
			
			// TODO: delete corresponding category in the Requirements Bazaar
			if(this.isConnectedToReqBaz()) {
				ReqBazHelper.getInstance().deleteCategory(this.reqBazCategory, accessToken);
			}
		} catch (SQLException e) {
			// roll back the whole stuff
			connection.rollback();
			throw e;
		} catch (ReqBazException e) {
			// roll back the whole stuff
			connection.rollback();
			throw e;
		} finally {
			// reset auto commit to previous value
			connection.setAutoCommit(autoCommitBefore);
		}
	}
	
	/**
	 * Checks whether the component is connected to a Requirements Bazaar category.
	 * @return Whether the component is connected to a Requirements Bazaar category.
	 */
	private boolean isConnectedToReqBaz() {
		if(this.reqBazCategory == null) return false;
		return this.reqBazCategory.getId() != -1;
	}
	
	@SuppressWarnings("unchecked")
	public JSONObject toJSONObject() {
		JSONObject jsonComponent = new JSONObject();
		
		jsonComponent.put("id", this.id);
		jsonComponent.put("name", this.name);
		jsonComponent.put("type", typeToString());
		jsonComponent.put("versionedModelId", this.versionedModelId);
		if(this.reqBazCategory != null) {
			jsonComponent.put("reqBazProjectId", this.reqBazCategory.getProjectId());
			jsonComponent.put("reqBazCategoryId", this.reqBazCategory.getId());
		}
		
		return jsonComponent;
	}
	
	/**
	 * Returns a list of all the components that are stored in the database.
	 * @param connection Connection object
	 * @return List of Component objects.
	 * @throws SQLException If something with the database went wrong.
	 * @throws ParseException If something (with parsing) while loading a component from the database went wrong.
	 */
	public static ArrayList<Component> getAllComponents(Connection connection) throws SQLException, ParseException {
		ArrayList<Component> components = new ArrayList<>();
		
		PreparedStatement statement = connection.prepareStatement("SELECT id FROM Component");
		ResultSet results = statement.executeQuery();
		while(results.next()) {
			components.add(new Component(results.getInt("id"), connection));
		}
		statement.close();
		return components;
	}
	
	
	public int getId() {
		return this.id;
	}
	
	public String getName() {
		return this.name;
	}
	
	public ComponentType getType() {
		return this.type;
	}
	
	public int getVersionedModelId() {
		return this.versionedModelId;
	}

}
