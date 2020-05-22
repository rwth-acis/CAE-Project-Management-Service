package i5.las2peer.services.projectManagementService;

import java.net.HttpURLConnection;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.logging.Level;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import i5.las2peer.api.Context;
import i5.las2peer.api.ServiceException;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.api.security.Agent;
import i5.las2peer.api.security.AnonymousAgent;
import i5.las2peer.api.security.UserAgent;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.services.projectManagementService.database.DatabaseManager;
import i5.las2peer.services.projectManagementService.exception.UserNotFoundException;
import i5.las2peer.services.projectManagementService.project.User;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import org.json.simple.JSONObject;

/**
 * REST resources of the Project Management Service.
 * @author Philipp
 *
 */
@Path("/")
public class RESTResources {
	
	private final ProjectManagementService service = (ProjectManagementService) Context.getCurrent().getService();
	private L2pLogger logger;
	private DatabaseManager dbm;

	public RESTResources() throws ServiceException {
		this.logger = (L2pLogger) service.getLogger();
		this.dbm = service.getDbm();
	}
	
	// TODO: Javadoc
	@POST
	@Path("/projects")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Creates a new project in the database if no project with the same name is already existing.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_CREATED, message="OK, project created."),
			@ApiResponse(code = HttpURLConnection.HTTP_CONFLICT, message="There already exists a project with the given name."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response postProject(String inputProject) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "postProject: trying to store a new project");
		return Response.status(HttpURLConnection.HTTP_CREATED).entity("Test!").build();
	}
	
	// TODO: Javadoc
	@GET
	@Path("/projects/{projectName}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Searches for a project in the database.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message="Found project with the given name."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message="Project with the given name could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response getProject(@PathParam("projectName") String projectName) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "getProject: searching project with name " + projectName);
		return Response.status(HttpURLConnection.HTTP_NOT_FOUND).entity("Project not found.").build();
	}
	
	
	
	/**
	 * Method for retrieving the currently active user.
	 * @return Response with the currently active user as JSON string, or error code.
	 */
	@GET
	@Path("/users/me")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Returns the currently active user.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "Returns the active user."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response getActiveUser() {
		Agent agent = Context.getCurrent().getMainAgent();
		
		if (agent instanceof AnonymousAgent) {
            return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
        } else if (agent instanceof UserAgent) {
        	UserAgent userAgent = (UserAgent) agent;
            String email = userAgent.getEmail();
            String loginName = userAgent.getLoginName();
            
            User user = null;
    		Connection connection = null;
    		try {
    			connection = dbm.getConnection();
    			// check if user is registered, i.e. exists in the database
    			// when there does not exist a user with the given email, a 
    			// UserNotFoundException gets thrown
    			user = new User(email, connection);
    		} catch (UserNotFoundException e) {
    			// user does not exist in the database
				// register user
    			user = new User(email, loginName);
    			
    			try {
					// store user object to database
					user.persist(connection);
				} catch (SQLException e1) {
					logger.printStackTrace(e1);
					return Response.serverError().entity("Database error!").build();
				}
			} catch (SQLException e) {
				logger.printStackTrace(e);
				return Response.serverError().entity("Database error!").build();
			} catch (Exception e) {
				logger.printStackTrace(e);
				return Response.serverError().entity("Server error!").build();
			} finally {
    			try {
    				connection.close();
    			} catch (SQLException e) {
    				logger.printStackTrace(e);
    			}
    		}
    		// returns user as json string
            return Response.ok(user.toJSONObject().toJSONString()).build();
        }
		return Response.serverError().build();
	}
}
