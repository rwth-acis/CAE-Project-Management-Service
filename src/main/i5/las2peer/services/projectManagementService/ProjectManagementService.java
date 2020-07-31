package i5.las2peer.services.projectManagementService;

import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import i5.las2peer.services.projectManagementService.auth.AuthManager;
import i5.las2peer.services.projectManagementService.database.DatabaseManager;
import i5.las2peer.services.projectManagementService.github.GitHubHelper;
import i5.las2peer.services.projectManagementService.project.Project;
import i5.las2peer.services.projectManagementService.project.User;
import i5.las2peer.services.projectManagementService.reqbaz.ReqBazHelper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import i5.las2peer.api.ManualDeployment;
import i5.las2peer.logging.L2pLogger;

/**
 * Project Management Service
 * 
 * A las2peer service used for project management in the CAE, i.e. creating/editing projects,
 * managing project components and users.
 */
@ServicePath("/project-management")
@ManualDeployment
public class ProjectManagementService extends RESTService {
	
	/*
	 * Database configuration
	 */
	private String jdbcDriverClassName;
	private String jdbcLogin;
	private String jdbcPass;
	private String jdbcUrl;
	private String jdbcSchema;
	private DatabaseManager dbm;
	
	/*
	 * GitHub user login data.
	 */
	private String gitHubUser;
	private String gitHubPassword;
	private String gitHubOrganization;
	
	/*
	 * GitHub OAuth data
	 */
    private String gitHubOAuthClientId;
    private String gitHubOAuthClientSecret;
	
	/*
	 * Requirements Bazaar configuration.
	 */
	private String reqBazBackendUrl;
	private int reqBazProjectId;
	// debug variable to turn on/off the creation of requirements bazaar categories
	private boolean debugDisableCategoryCreation;
	
	public ProjectManagementService() {
		// read and set properties values
		setFieldValues();
		// instantiate a database manager to handle database connection pooling
		// and credentials
		dbm = new DatabaseManager(jdbcDriverClassName, jdbcLogin, jdbcPass, jdbcUrl, jdbcSchema);
		
		// setup GitHubHelper
		GitHubHelper gitHubHelper = GitHubHelper.getInstance();
		gitHubHelper.setGitHubUser(this.gitHubUser);
		gitHubHelper.setGitHubPassword(this.gitHubPassword);
		gitHubHelper.setGitHubOrganization(this.gitHubOrganization);
		gitHubHelper.setOAuthClientId(this.gitHubOAuthClientId);
		gitHubHelper.setOAuthClientSecret(this.gitHubOAuthClientSecret);
		
		// setup ReqBazHelper
		ReqBazHelper reqBazHelper = ReqBazHelper.getInstance();
		reqBazHelper.setReqBazBackendUrl(this.reqBazBackendUrl);
		reqBazHelper.setReqBazProjectId(this.reqBazProjectId);
	}
	
	@Override
	protected void initResources() {
		getResourceConfig().register(RESTResources.class);
	}
	
	public DatabaseManager getDbm(){
		return dbm;
	}
	
	public boolean isCategoryCreationDisabled() {
		return this.debugDisableCategoryCreation;
	}
	
	/**
	 * Method used by CAE Model Persistence Service.
	 * When calling this method, the agent needs to be a real user.
	 * @param versionedModelId Id of the versioned model, where the permissions should be checked for.
	 * @return Whether the user calling the method has the permission to commit to the versioned model.
	 */
	public boolean hasCommitPermission(int versionedModelId) {
		Connection connection = null;
        try {
        	AuthManager authManager = new AuthManager((L2pLogger) this.getLogger(), this.getDbm());
			User user = authManager.getUser();
			
			connection = dbm.getConnection();
			
			// get project where the versioned model belongs to
			// the versioned model should at least be connected with a component
			// but maybe if the project got deleted and the component still exists then no
			// project can be found, thus we need to check if we get a query result
			PreparedStatement statement = connection
					.prepareStatement("SELECT Project.* FROM Component, ProjectToComponent, Project " +
			                          "WHERE Component.versionedModelId = ? AND " +
							          "ProjectToComponent.componentId = Component.id AND ProjectToComponent.projectId = Project.id;");
			statement.setInt(1, versionedModelId);
			
			ResultSet queryResult = statement.executeQuery();
			if(!queryResult.next()) {
				// could not find a project which is connected to the versioned model
				return false;
			}
			
			int projectId = queryResult.getInt("id");
			statement.close();
			
			// get Project object
			Project project = new Project(projectId, connection);
			
			// check if user is a member of the project
			if(project.hasUser(user.getId(), connection)) {
				// user is a member of the project where the versioned model belongs to
				return true;
			} else {
				// user is no member of the project where the versioned model belongs to
				return false;
			}
        } catch (Exception e) {
        	return false;
        } finally {
        	try {
				if(connection != null) connection.close();
			} catch (SQLException e) {
				return false;
			}
        }
	}
	
	/**
	 * Method used by CAE Model Persistence Service
	 * @return Whether the user calling the method is anonymous or not.
	 */
	public boolean isAnonymous() {
		try {
			AuthManager authManager = new AuthManager((L2pLogger) this.getLogger(), this.getDbm());
			return authManager.isAnonymous();
		} catch (Exception e) {
			return false;
		}
	}
}
