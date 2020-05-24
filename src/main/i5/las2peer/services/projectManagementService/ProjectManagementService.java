package i5.las2peer.services.projectManagementService;

import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import i5.las2peer.services.projectManagementService.database.DatabaseManager;
import i5.las2peer.api.ManualDeployment;

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
	
	public ProjectManagementService() {
		// read and set properties values
		setFieldValues();
		// instantiate a database manager to handle database connection pooling
		// and credentials
		dbm = new DatabaseManager(jdbcDriverClassName, jdbcLogin, jdbcPass, jdbcUrl, jdbcSchema);
	}
	
	@Override
	protected void initResources() {
		getResourceConfig().register(RESTResources.class);
	}
	
	public DatabaseManager getDbm(){
		return dbm;
	}
	
}
