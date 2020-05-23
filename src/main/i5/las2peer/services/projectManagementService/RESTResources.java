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
import i5.las2peer.services.projectManagementService.exception.ProjectNotFoundException;
import i5.las2peer.services.projectManagementService.exception.UserNotFoundException;
import i5.las2peer.services.projectManagementService.project.Project;
import i5.las2peer.services.projectManagementService.project.User;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;

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
	
	/**
	 * Creates a new project in the database.
	 * Therefore, the user needs to be authorized.
	 * First, checks if a project with the given name already exists.
	 * If not, then the new project gets stored into the database.
	 * @param inputProject JSON representation of the project to store.
	 * @return Response containing the status code (and a message).
	 */
	@POST
	@Path("/projects")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Creates a new project in the database if no project with the same name is already existing.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_CREATED, message = "OK, project created."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_CONFLICT, message = "There already exists a project with the given name."),
			@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Input project is not well formatted."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response postProject(String inputProject) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "postProject: trying to store a new project");
		
		Agent agent = Context.getCurrent().getMainAgent();
		if(agent instanceof AnonymousAgent) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).entity("User not authorized.").build();
		} else if(agent instanceof UserAgent) {
			UserAgent userAgent = (UserAgent) agent;
            String email = userAgent.getEmail();
            String loginName = userAgent.getLoginName();
            
            Connection connection = null;
            try {
				User user = getUser(email, loginName);
				
				Project project = new Project(inputProject);
				
				// check if a project with the given name already exists
			    connection = dbm.getConnection();
			    try {
				    Project searchResult = new Project(project.getName(), connection);
				    // no ProjectNotFoundException thrown, so project already exists
				    return Response.status(HttpURLConnection.HTTP_CONFLICT).entity("A project with the given name already exists.").build();
			    } catch (ProjectNotFoundException e) {
			    	// project does not exist yet
			    	project.persist(connection);
			    	// add current user to the project
			    	project.addUser(user.getId(), connection);
					return Response.status(HttpURLConnection.HTTP_CREATED).entity(project.toJSONObject().toJSONString()).build();
			    }
			} catch (SQLException e) {
				logger.printStackTrace(e);
				return Response.serverError().entity("Internal server error.").build();
			} catch (ParseException e) {
				logger.printStackTrace(e);
				return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).build();
			} finally {
				try {
					connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
		return Response.serverError().entity("Internal server error.").build();
	}
	
	@GET
	@Path("/projects")
	public Response getProjectsByUser() {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "getProjectsByUser: searching for users projects");
		
		Agent agent = Context.getCurrent().getMainAgent();
		if(agent instanceof AnonymousAgent) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).entity("User not authorized.").build();
		} else if(agent instanceof UserAgent) {
			UserAgent userAgent = (UserAgent) agent;
            String email = userAgent.getEmail();
            String loginName = userAgent.getLoginName();
		}
		return Response.serverError().build();
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
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "getActiveUser: trying to retrieve currently active user");
		Agent agent = Context.getCurrent().getMainAgent();
		
		if (agent instanceof AnonymousAgent) {
            return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
        } else if (agent instanceof UserAgent) {
        	UserAgent userAgent = (UserAgent) agent;
            String email = userAgent.getEmail();
            String loginName = userAgent.getLoginName();
            
            User user;
			try {
				user = getUser(email, loginName);
				
				// returns user as json string
	            return Response.ok(user.toJSONObject().toJSONString()).build();
			} catch (SQLException e) {
				logger.printStackTrace(e);
				// return server error at the end
			}
        }
		return Response.serverError().entity("Internal server error.").build();
	}
	
	/**
	 * Returns the user with the given email and login name.
	 * When the user is already registered, i.e. can be found in the database,
	 * then the already registered user is returned.
	 * Otherwise, when the user is not registered yet, the user gets stored
	 * into the database.
	 * @param email Email of the user.
	 * @param loginName Login name of the user.
	 * @return User object.
	 * @throws SQLException If something went wrong with the database.
	 */
	private User getUser(String email, String loginName) throws SQLException {
		User user = null;
		Connection connection = null;
		try {
			connection = dbm.getConnection();
			// check if user is registered, i.e. exists in the database
			// when there does not exist a user with the given email, a 
			// UserNotFoundException gets thrown
			user = new User(email, connection);
			// no UserNotFoundException was thrown
			return user;
		} catch (UserNotFoundException e) {
			// user does not exist in the database
			// register user
			user = new User(email, loginName);
			
			// store user object to database
			user.persist(connection);
			return user;
		} finally {
			connection.close();
		}
	}
}
