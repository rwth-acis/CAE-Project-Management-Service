package i5.las2peer.services.projectManagementService;

import i5.las2peer.restMapper.RESTService;
import i5.las2peer.restMapper.annotations.ServicePath;
import i5.las2peer.services.projectManagementService.database.DatabaseManager;
import io.swagger.annotations.Api;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;
import i5.las2peer.api.ManualDeployment;

/**
 * Project Management Service
 * 
 * A las2peer service used for project management in the CAE, i.e. creating/editing projects,
 * managing project components and users.
 */
// TODO Adjust license in API description
@Api
@SwaggerDefinition(
		info = @Info(
				title = "CAE Project Management Service",
				version = "0.1.0",
				description = "A las2peer service used for project management in the CAE, i.e. creating/editing projects, managing project components and users.",
				termsOfService = "none",
				contact = @Contact(
						name = "Philipp D",
						url = "https://github.com/phil-cd",
						email = "-"),
				license = @License(
						name = "your software license name",
						url = "http://your-software-license-url.com")))
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
