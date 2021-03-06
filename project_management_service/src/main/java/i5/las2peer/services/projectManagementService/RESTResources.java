package i5.las2peer.services.projectManagementService;

import java.io.Serializable;
import java.net.HttpURLConnection;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import i5.las2peer.api.Context;
import i5.las2peer.api.ServiceException;
import i5.las2peer.api.logging.MonitoringEvent;
import i5.las2peer.logging.L2pLogger;
import i5.las2peer.services.projectManagementService.auth.AuthManager;
import i5.las2peer.services.projectManagementService.component.Component;
import i5.las2peer.services.projectManagementService.component.Dependency;
import i5.las2peer.services.projectManagementService.component.ExternalDependency;
import i5.las2peer.services.projectManagementService.database.DatabaseManager;
import i5.las2peer.services.projectManagementService.exception.GitHubException;
import i5.las2peer.services.projectManagementService.exception.InvitationNotFoundException;
import i5.las2peer.services.projectManagementService.exception.ProjectNotFoundException;
import i5.las2peer.services.projectManagementService.exception.ReqBazException;
import i5.las2peer.services.projectManagementService.exception.RoleNotFoundException;
import i5.las2peer.services.projectManagementService.exception.UserNotFoundException;
import i5.las2peer.services.projectManagementService.github.GitHubHelper;
import i5.las2peer.services.projectManagementService.project.Project;
import i5.las2peer.services.projectManagementService.project.ProjectInvitation;
import i5.las2peer.services.projectManagementService.project.Role;
import i5.las2peer.services.projectManagementService.project.User;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.Contact;
import io.swagger.annotations.Info;
import io.swagger.annotations.License;
import io.swagger.annotations.SwaggerDefinition;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

/**
 * REST resources of the Project Management Service.
 * @author Philipp
 *
 */
// TODO: adjust license
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
@Path("/")
public class RESTResources {
	
	private static final String MODEL_PERSISTENCE_SERVICE = "i5.las2peer.services.modelPersistenceService.ModelPersistenceService@0.1";
	private final ProjectManagementService service = (ProjectManagementService) Context.getCurrent().getService();
	private L2pLogger logger;
	private DatabaseManager dbm;
	private AuthManager authManager;

	public RESTResources() throws ServiceException {
		this.logger = (L2pLogger) service.getLogger();
		this.dbm = service.getDbm();
		this.authManager = new AuthManager(this.logger, this.dbm);
	}
	
	/**
	 * Creates a new project in the database.
	 * Therefore, the user needs to be authorized.
	 * First, checks if a project with the given name already exists.
	 * If not, then the new project gets stored into the database.
	 * @param inputProject JSON representation of the project to store (containing name and access token of user needed to create Requirements Bazaar category).
	 * @return Response containing the status code (and a message or the created project).
	 */
	@POST
	@Path("/projects")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Creates a new project in the database if no project with the same name is already existing.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_CREATED, message = "OK, project created."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_CONFLICT, message = "There already exists a project with the given name."),
			@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Input project is not well formatted or some attribute is missing."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response postProject(String inputProject) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "postProject: trying to store a new project");
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).entity("User not authorized.").build();
		} else {
			Connection connection = null;
            try {
				User user = authManager.getUser();
				
				Project project = new Project(user, inputProject);
				
				// check if a project with the given name already exists
			    connection = dbm.getConnection();
			    try {
				    Project searchResult = new Project(project.getName(), connection);
				    // no ProjectNotFoundException thrown, so project already exists
				    return Response.status(HttpURLConnection.HTTP_CONFLICT).entity("A project with the given name already exists.").build();
			    } catch (ProjectNotFoundException e) {
			    	// project does not exist yet
			    	// extract access token from inputProject
			    	JSONObject json = (JSONObject) JSONValue.parse(inputProject);
			    	if(json.containsKey("access_token")) {
			    		String accessToken = (String) json.get("access_token");
			    		
			    		// persist method also stores users of the project (only the creator for now) etc.
				    	project.persist(connection, accessToken);
				    	
				    	// if user has stored a GitHub username, then grant access to the GitHub project
				    	if(user.getGitHubUsername() != null) {
				    	    GitHubHelper.getInstance().grantUserAccessToProject(user.getGitHubUsername(), project.getGitHubProject());
				    	}
				    	
						return Response.status(HttpURLConnection.HTTP_CREATED).entity(project.toJSONObject().toJSONString()).build();
			    	} else {
			    		logger.printStackTrace(e);
						return Response.status(HttpURLConnection.HTTP_BAD_REQUEST)
								.entity("Input project has no attribute 'access_token' which is needed.").build();
			    	}
			    }
			} catch (SQLException e) {
				logger.printStackTrace(e);
				return Response.serverError().entity("Internal server error.").build();
			} catch (ParseException e) {
				logger.printStackTrace(e);
				return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).build();
			} catch (GitHubException e) {
				logger.printStackTrace(e);
				return Response.serverError()
						.entity("Internal server error: An error occurred while creating the connected GitHub project.").build();
			} catch (ReqBazException e) {
				logger.printStackTrace(e);
				return Response.serverError()
						.entity("Internal server error: An error occurred while creating the Requirements Bazaar category for the application component.").build();
			} finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * The result of requests to this endpoint depend on whether the user sending the 
	 * request is anonymous or not.
	 * 1. If the user sending the request is anonymous, then all projects are returned.
	 * 2. If the user sending the request is not anonymous, then the projects by the
	 * user / the projects where the user is a member of are returned.
	 * @return Response containing the status code (and a message or project list).
	 */
	@GET
	@Path("/projects")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "When sending anonymous: Returns all projects. Otherwise searches for projects that the user is part of.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, list of users projects is returned."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response getProjectsByUser() {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "getProjectsByUser: searching for projects");
		
		Connection connection = null;
		try {
			connection = dbm.getConnection();
			
			ArrayList<Project> projects;
			
			if(authManager.isAnonymous()) {
				// load all projects from database
				// when searching for projects with empty name, then every project should be found
				// because it gets searched for name LIKE "%%"
				projects = Project.searchProjects("", connection);
			} else {
				// first get current user from database
            	// the id of the user will be needed later
            	User user = authManager.getUser();
            	
            	// get all projects where the user is part of
            	projects = Project.getProjectsByUser(user.getId(), connection);
			}
			// return JSONArray as string
        	return Response.ok(Project.projectListToJSONArray(projects).toJSONString()).build();
		} catch (SQLException e) {
        	logger.printStackTrace(e);
        	return Response.serverError().entity("Internal server error.").build();
        } finally {
			try {
				if(connection != null) connection.close();
			} catch (SQLException e) {
				logger.printStackTrace(e);
				return Response.serverError().entity("Internal server error.").build();
			}
		}
	}
	
	/**
	 * Deletes the project with the given id.
	 * Therefore, the user sending the request needs to be a member 
	 * of the project and an access token is needed for accessing the 
	 * Requirements Bazaar API.
	 * @param projectId Id of the project which should be removed.
	 * @return Response with status code (and possibly error message).
	 */
	@DELETE
	@Path("/projects/{projectId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Deletes the project from the database.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_NO_CONTENT, message = "Successfully deleted project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User is not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User needs to be member of the project to delete it."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Could not find project with the given id."),
			@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Access token is missing."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response deleteProject(@PathParam("projectId") int projectId, String body) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "deleteProject: deleting project with id " + projectId);
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).entity("User not authorized.").build();
		} else {
			// check if user is allowed to remove a user from the project
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
						    
				User user = authManager.getUser();
						    
			    // get project by id (load it from database)
				Project project = new Project(projectId, connection);
						    
				if(project.hasUser(user.getId(), connection)) {
				    // user is part of the project and thus is allowed to delete it
					JSONObject json = (JSONObject) JSONValue.parse(body);
			    	if(!json.containsKey("access_token")) {
			    		return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).entity("Access token missing.").build();
			    	}
			    	String accessToken = (String) json.get("access_token");
					// delete the project
					project.delete(connection, accessToken);
					return Response.status(HttpURLConnection.HTTP_NO_CONTENT).build();
				} else {
					// user is no member of the project and thus cannot delete it
					return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
							.entity("User needs to be member of the project to delete it.").build();
				}
			} catch (ProjectNotFoundException e) {
				logger.printStackTrace(e);
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND).build();
			} catch (SQLException e) {
	        	logger.printStackTrace(e);
	        	return Response.serverError().entity("Internal server error.").build();
	        } catch (GitHubException e) {
	        	logger.printStackTrace(e);
	        	return Response.serverError().entity("Internal server error: Problem with GitHub.").build();
			} catch (ReqBazException e) {
				logger.printStackTrace(e);
	        	return Response.serverError().entity("Internal server error: Problem with Requirements Bazaar.").build();
			} finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
		
	}
	
	/**
	 * Searches for a project with the given id in the database.
	 * Therefore, no authorization is needed.
	 * @param projectId Project id to search for.
	 * @return Response containing the status code (and a message or project).
	 */
	@GET
	@Path("/projects/{projectId}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Searches for a project with the given id in the database.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message="Found project with the given id."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message="Could not find project with given id."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response getProjectById(@PathParam("projectId") int projectId) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "getProject: searching project with id " + projectId);
		
		Connection connection = null;
		try {
			connection = dbm.getConnection();
			
			// search for project
			Project project = new Project(projectId, connection);
			
			// return JSONArray as string
        	return Response.ok(project.toJSONObject().toJSONString()).build();
		} catch (ProjectNotFoundException e) {
			return Response.status(HttpURLConnection.HTTP_NOT_FOUND).build();
		} catch (SQLException e) {
			logger.printStackTrace(e);
			return Response.serverError().entity("Internal server error.").build();
		} finally {
			try {
			    connection.close();
			} catch (SQLException e) {
				logger.printStackTrace(e);
			}
		}
	}
	
	/**
	 * Adds a user to a project / accepts an invitation to a project.
	 * Therefore, the user sending the request needs to be authorized in order
	 * to check if an invitation for the user exists.
	 * @param projectId Id of the project where the user should be added to.s
	 * @return Response with status code (and possibly an error description).
	 */
	@POST
	@Path("/projects/{id}/users")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Adds the user to the project / accepts an invitation.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, added user to project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_CONFLICT, message = "User is already member of the project."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User is not invited to the project."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with given id not found."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response addUserToProject(@PathParam("id") int projectId, String inputUser) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "addUserToProject: adding user to project with id " + projectId);
		
		if (authManager.isAnonymous()) {
            return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
        } else {
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
			    
			    // get user sending the request
			    User user = authManager.getUser();
			    
			    // get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    // check if an invitation exists
			    if(ProjectInvitation.exists(projectId, user.getId(), connection)) {
			    	// invitation exists, thus add user to project
			    	boolean added = project.addUser(user, connection, true); // true, because user also should be added to users list of Project object
		    	    if(added) {
		    	        // return result: ok
		    	    	
		    	    	// remove invitation
		    	    	ProjectInvitation.delete(projectId, user.getId(), connection);
		    	    	
		    	    	// grant user access to GitHub project if user has stored a GitHub username
		    	    	if(user.getGitHubUsername() != null) {
		    	    		GitHubHelper.getInstance().grantUserAccessToProject(user.getGitHubUsername(), project.getGitHubProject());
		    	    	}
		    	    	
		    	        return Response.ok().build();
		    	    } else {
		    	    	// user is already a member of the project
		    	    	// this should never be the case, because then there could not be an 
		    	    	// invitation, but just in case it may happen at some point
		    	    	return Response.status(HttpURLConnection.HTTP_CONFLICT)
		    	    			.entity("User is already member of the project.").build();
		    	    }
			    } else {
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User is not invited to the project.").build();
			    }
			} catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } catch (GitHubException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
			} finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
        }
	}
	
	/**
	 * Removes a user from a project.
	 * Therefore, the user sending the request needs to be authorized in order
	 * to check if the user is a member of the project, because only project members 
	 * should be allowed to remove users from it.
	 * @param projectId Id of the project where the user should be removed from.
	 * @param userId Id of the user to remove from the project.
	 * @return Response with status code (and possibly an error description).
	 */
	@DELETE
	@Path("/projects/{projectId}/users/{userId}")
	@ApiOperation(value = "Removes a user from the project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, removed user from project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User is not member of the project and thus not allowed to remove users from it."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id or user to remove from project could not be found or user to remove is no member of the project."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response removeUserFromProject(@PathParam("projectId") int projectId, @PathParam("userId") int userId) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "removeUserFromProject: removing user with id " + userId + " from project with id " + projectId);
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			// check if user is allowed to remove a user from the project
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
			    
			    User user = authManager.getUser();
			    
			    // get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is part of the project and thus is allowed to remove users
	    	    	
		    	    boolean removed = project.removeUser(userId, connection);
		    	    if(removed) {
		    	        // return result: ok
		    	        return Response.ok().build();
		    	    } else {
		    	    	// user is no member of the project
		    	    	return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
		    	    			.entity("User is no member of the project.").build();
		    	    }
			    } else {
			    	// user does not have the permission to remove users from the project
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to remove a user from it.").build();
			    }
			    
			} catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * Edits the role of a user in a project.
	 * @param projectId Id of the project where the role of the user should be edited.
	 * @param userId Id of the user whose role should be edited.
	 * @param roleId Id of the new role.
	 * @return Response with status and probably error message.
	 */
	@PUT
	@Path("/projects/{projectId}/users/{userId}/role/{roleId}")
	@ApiOperation(value = "Edits the role of a user in a project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, edited user's role in project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User is not member of the project and thus not allowed to edit user roles."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id or user whose role should be edited could not be found or user to edit is no member of the project."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
    public Response putUserRole(@PathParam("projectId") int projectId, @PathParam("userId") int userId, @PathParam("roleId") int roleId) {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "putUserRole called");
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			// check if user is allowed to edit role of user in this project
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
			    
			    User user = authManager.getUser();
			    
			    // get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is part of the project and thus is allowed to edit users role
	    	    	
		    	    boolean edited = project.editUserRole(userId, roleId, connection);
		    	    if(edited) {
		    	    	return Response.ok().build();
		    	    } else {
		    	    	return Response.status(HttpURLConnection.HTTP_BAD_REQUEST)
		    	    			.entity("User is no project member or role does not exist.").build();
		    	    }
			    } else {
			    	// user does not have the permission to edit users role in the project
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to edit role of a user.").build();
			    }
			    
			} catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * Adds a role to a project.
	 * Therefore, the user sending the request needs to be authorized in order
	 * to check if the user is a member of the project, because only project members 
	 * should be allowed to add roles to it.
	 * @param projectId Id of the project where the role should be added to.
	 * @param inputRole JSON object containing a "name" attribute with the name of the role to add.
	 * @return Response with status code (and possibly an error description).
	 */
	@POST
	@Path("/projects/{id}/roles")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Adds a role to the project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, added role to project. Also returns JSON of role which got added."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User is not member of the project and thus not allowed to add roles to it."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Input role is not well formatted."),
			@ApiResponse(code = HttpURLConnection.HTTP_CONFLICT, message = "The project already contains a role with the same name."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response postProjectRole(@PathParam("id") int projectId, String inputRole) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "postProjectRole: trying to store a new role to a project");
		
		if (authManager.isAnonymous()) {
            return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
        } else {
            // check if user is allowed to add a role to the project
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
			    
			    User user = authManager.getUser();
			    
			    // get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is part of the project and thus is allowed to add new roles
			    	// extract name of the role given in the request body (as json)
			    	JSONObject jsonRoleToAdd = (JSONObject) JSONValue.parseWithException(inputRole);
			    	    
			    	if(jsonRoleToAdd.containsKey("name")) {
			    	    String roleToAddName = (String) jsonRoleToAdd.get("name");
			    	    
			    	    Role role = new Role(project.getId(), roleToAddName, false); // role should not be the default role
			    	    boolean added = project.addRole(role, connection);
			    	    if(added) {
			    	        // return result: ok
			    	        return Response.ok(role.toJSONObject().toJSONString()).build();
			    	    } else {
			    	    	// role with the same name already exists in the project
			    	    	return Response.status(HttpURLConnection.HTTP_CONFLICT)
			    	    			.entity("Role with the same name already exists in the project.").build();
			    	    }
			    	} else {
			    	    return Response.status(HttpURLConnection.HTTP_BAD_REQUEST)
			    	            .entity("Input user does not contain key 'name' which is needed.").build();
			    	}
			    } else {
			    	// user does not have the permission to add roles to the project
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to add a role to it.").build();
			    }
			} catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } catch (ParseException p) {
	    		logger.printStackTrace(p);
	    		return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).entity("Parse error.").build();
	    	} finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
        }
	}
	
	/**
	 * Removes a role from a project.
	 * Therefore, the user sending the request needs to be authorized in order
	 * to check if the user is a member of the project, because only project members 
	 * should be allowed to remove roles from it.
	 * @param projectId Id of the project where the role should be removed from.
	 * @param roleId Id of the role to remove.
	 * @return Response with status code (and possibly an error description).
	 */
	@DELETE
	@Path("/projects/{projectId}/roles/{roleId}")
	@ApiOperation(value = "Removes a role from the project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, removed role from project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User is not member of the project and thus not allowed to remove roles from it."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id or role to remove from project could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_CONFLICT, message = "The role is assigned to at least one user and thus cannot be removed."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response removeRoleFromProject(@PathParam("projectId") int projectId, @PathParam("roleId") int roleId) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "removeRoleFromProject: removing role with roleId " + roleId + " from project with id " + projectId);

		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			// check if user is allowed to remove a role from the project
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
			    
			    User user = authManager.getUser();
			    
			    // get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is part of the project and thus is allowed to remove roles
			    	boolean removed = project.removeRole(roleId, connection);
		    	    if(removed) {
		    	        // return result: ok
		    	        return Response.ok().build();
		    	    } else {
		    	    	// role could not be removed because it is still assigned to at least one user
		    	    	return Response.status(HttpURLConnection.HTTP_CONFLICT)
		    	    			.entity("The role is assigned to at least one user and thus cannot be removed.").build();
		    	    }
			    } else {
			    	// user does not have the permission to remove roles from the project
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to remove a role from it.").build();
			    }
			    
			} catch (RoleNotFoundException e) {
				// role was not included in the project
    	    	return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
    	    			.entity("Project contains no role with the given id.").build();
			} catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * Updates the widget config of the given role.
	 * @param projectId Id of the project which the role belongs to.
	 * @param roleId Id of the role which should be updated.
	 * @param widgetConfig New widget config that should be stored.
	 * @return Response with status (and possibly an error message).
	 */
	@PUT
	@Path("/projects/{projectId}/roles/{roleId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Updates the widget config of the given role.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, update role."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User is not member of the project and thus not allowed to update roles."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id or role to update could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response updateRole(@PathParam("projectId") int projectId, @PathParam("roleId") int roleId, String widgetConfig) {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "updateRole: called with projectId " + projectId + " and roleId " + roleId);
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			Connection connection = null;
		    try {
			    connection = dbm.getConnection();
			    
			    // get user
			    User user = authManager.getUser();
			    
			    // get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is part of the project and thus is allowed to update roles
			    	
			    	project.updateRoleWidgetConfig(roleId, widgetConfig, connection);
			    	return Response.ok().build();
			    } else {
			    	// user does not have the permission to update roles from the project
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to update a role from it.").build();
			    }
		    } catch (RoleNotFoundException e) {
				// role was not included in the project
    	    	return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
    	    			.entity("Project contains no role with the given id.").build();
			}catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	
	/**
	 * Adds a component to a project.
	 * @param projectId Id of the project where a component should be added to.
	 * @param inputComponent JSON representation of the component to add to the project (must contain access token of user).
	 * @return Response with status code (and possibly an error description).
	 */
	@POST
	@Path("/projects/{projectId}/components")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Adds component to project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, added component to the project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized to add component to project."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User needs to be member of the project to add components to it."),
			@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Given component is not well formatted or attributes are missing."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response postProjectComponent(@PathParam("projectId") int projectId, String inputComponent) {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "postProjectComponent: trying to add component to project");
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			// check if user is allowed to add a component to the project
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
									    
				User user = authManager.getUser();
									    
				// get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is member of the project and thus allowed to add components to it
			    	Component component = new Component(inputComponent);
			    	
			    	// get access token from 
			    	JSONObject json = (JSONObject) JSONValue.parse(inputComponent);
			    	if(json.containsKey("access_token")) {
			    		String accessToken = (String) json.get("access_token");
			    		
			    		// persist component
				    	component.persist(project, connection, accessToken);
				    	
				    	// send component as result
				    	return Response.ok(component.toJSONObject().toJSONString()).build();
			    	} else {
						return Response.status(HttpURLConnection.HTTP_BAD_REQUEST)
								.entity("Attribute 'access_token' is missing.").build();
			    	}
			    } else {
			    	// user is no member of the project and thus not allowed to add components to it
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to add components to it.").build();
			    }
			} catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } catch (ParseException e) {
				logger.printStackTrace(e);
				return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).entity("Parse error.").build();
			} catch (ReqBazException e) {
				logger.printStackTrace(e);
				return Response.serverError()
						.entity("Internal server error: Could not create Requirements Bazaar category for component.").build();
			} finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * Lists the components, dependencies and external dependencies of the project.
	 * @param projectId Id of the project where the components should be listed.
	 * @return Response with status (and possibly error message).
	 */
	@GET
	@Path("/projects/{projectId}/components")
	@ApiOperation(value = "Lists the components of the project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, returns list of components, dependencies and external dependencies of the project."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response getProjectComponents(@PathParam("projectId") int projectId) {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "getProjectComponents: trying to get components of project with id " + projectId);
		
        Connection connection = null;
		try {
			connection = dbm.getConnection();
			
			// load project by id
			Project project = new Project(projectId, connection);
			
			JSONObject result = new JSONObject();
			
			// get components of the project
			ArrayList<Component> components = project.getComponents();
			JSONArray jsonComponents = new JSONArray();
			for(Component component : components) {
				JSONObject jsonComponent = component.toJSONObject();
				
				// also get version tags that are available for this component
				Serializable[] params = {component.getVersionedModelId()};
				ArrayList<String> versions = (ArrayList<String>) Context.getCurrent().invoke(MODEL_PERSISTENCE_SERVICE, "getVersionsOfVersionedModel", params);
			    jsonComponent.put("versions", versions);
				
				jsonComponents.add(jsonComponent);
			}
			result.put("components", jsonComponents);
			
			// get dependencies of the project
			ArrayList<Dependency> dependencies = project.getDependencies();
			JSONArray jsonDependencies = new JSONArray();
			for(Dependency dependency : dependencies) {
				JSONObject jsonDependency = dependency.toJSONObject();
				JSONObject jsonComponent = (JSONObject) jsonDependency.get("component");
				
				// also get version tags that are available for the component of this dependency
				Serializable[] params = {dependency.getComponent().getVersionedModelId()};
				ArrayList<String> versions = (ArrayList<String>) Context.getCurrent().invoke(MODEL_PERSISTENCE_SERVICE, "getVersionsOfVersionedModel", params);
				jsonComponent.put("versions", versions);
				jsonDependency.put("component", jsonComponent);
				
				jsonDependencies.add(jsonDependency);
			}
			result.put("dependencies", jsonDependencies);
			
			// get external dependencies of the project
			ArrayList<ExternalDependency> externalDependencies = project.getExternalDependencies();
			JSONArray jsonExternalDependencies = new JSONArray();
			for(ExternalDependency externalDependency : externalDependencies) {
				JSONObject jsonExtDependency = externalDependency.toJSONObject();
				
				// get version tags from GitHub API
				ArrayList<String> versions = GitHubHelper.getInstance()
						.getRepoVersionTags(externalDependency.getGitHubRepoOwner(), externalDependency.getGitHubRepoName());
				
				jsonExtDependency.put("versions", versions);
				
				jsonExternalDependencies.add(jsonExtDependency);
			}
			result.put("externalDependencies", jsonExternalDependencies);
			
			// return result as string
        	return Response.ok(result.toJSONString()).build();
		} catch (ProjectNotFoundException e) {
			logger.printStackTrace(e);
			return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
					.entity("Project with the given id could not be found.").build();
		} catch (SQLException e) {
			logger.printStackTrace(e);
			return Response.serverError().entity("Internal server error.").build();
		} catch (Exception e) {
			logger.printStackTrace(e);
			return Response.serverError().entity("Internal server error.").build();
		}  finally {
			try {
			    connection.close();
			} catch (SQLException e) {
				logger.printStackTrace(e);
			}
		}
	}
	
	/**
	 * Removes the component with the given id from the project with the given id.
	 * Therefore, the user sending the request needs to be a member of the project.
	 * Access token needs to be sent in the method body.
	 * 
	 * If the component is not used somewhere else anymore (e.g. as a dependency), then it gets removed 
	 * from the CAE.
	 * @param projectId Id of the project where the component should be removed from.
	 * @param componentId Id of the component which should be removed from the project.
	 * @return Response with status code (and possibly error message).
	 */
	@DELETE
	@Path("/projects/{projectId}/components/{componentId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Removes a component from a project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, removed component from project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User is not member of the project and thus not allowed to remove components from it."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id or component to remove from project could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Access token missing."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response removeProjectComponent(@PathParam("projectId") int projectId, @PathParam("componentId") int componentId, String body) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE,
				"removeProjectComponent: trying to remove component with id " + componentId +
				" from project with id " + projectId);
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			// check if user is allowed to remove a component from the project
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
			    
			    User user = authManager.getUser();
			    
			    // get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is allowed to remove components from the project
			    	JSONObject json = (JSONObject) JSONValue.parse(body);
			    	if(!json.containsKey("access_token")) {
			    		return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).entity("Access token missing.").build();
			    	}
			    	String accessToken = (String) json.get("access_token");
			    	
			    	boolean removed = project.removeComponent(componentId, connection, accessToken);
		    		if(removed) {
		    			// removed component successfully
		    			return Response.ok().build();
		    		} else {
		    			// component is not included in the project
		    	    	return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
		    	    			.entity("Component with the given id could not be found in the project.").build();
		    		}
			    } else {
			    	// user does not have the permission to remove components from the project
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to remove a component from it.").build();
			    }
			} catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } catch (ParseException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
			} catch (ReqBazException e) {
				logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
			} finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * Adds a dependency to a project.
	 * @param projectId Id of the project where a dependency should be added to.
	 * @param componentId Id of the component which should be added as a dependency to the project.
	 * @return Response with status code (and possibly an error description).
	 */
	@POST
	@Path("/projects/{projectId}/dependencies/{componentId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Adds dependency to project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, added dependency to the project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized to add dependency to project."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User needs to be member of the project to add dependencies to it."),
			@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Component is already part of the project."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response postProjectDependency(@PathParam("projectId") int projectId, @PathParam("componentId") int componentId) {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "postProjectDependency: trying to add dependency to project");
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			// check if user is allowed to add a dependency to the project
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
									    
				User user = authManager.getUser();
									    
				// get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is member of the project and thus allowed to add dependencies to it
			    	
			    	// check if component exists (if not, then a SQLException gets thrown)
			        new Component(componentId, connection);
			        
			        // check if the component is already included in the project
			        if(project.hasComponent(componentId) || project.hasDependency(componentId)) {
			        	return Response.status(HttpURLConnection.HTTP_BAD_REQUEST)
			        			.entity("Component is already part of the project.").build();
			        }
			    	
			    	Dependency dependency = new Dependency(projectId, componentId);
			    	dependency.persist(connection);
			    	
			    	return Response.ok().build();
			    } else {
			    	// user is no member of the project and thus not allowed to add dependencies to it
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to add dependencies to it.").build();
			    }
			} catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } catch (ParseException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
			} finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * Removes the dependency with the given id from the project with the given id.
	 * Therefore, the user sending the request needs to be a member of the project.
	 * @param projectId Id of the project where the dependency should be removed from.
	 * @param componentId Id of the component-dependency which should be removed from the project.
	 * @return Response with status code (and possibly error message).
	 */
	@DELETE
	@Path("/projects/{projectId}/dependencies/{componentId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Removes a dependency from a project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, removed dependency from project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User is not member of the project and thus not allowed to remove dependencies from it."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id or dependency to remove from project could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response removeProjectDependency(@PathParam("projectId") int projectId, @PathParam("componentId") int componentId) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE,
				"removeProjectDependency: trying to remove component-dependency with id " + componentId +
				" from project with id " + projectId);
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			// check if user is allowed to remove a dependency from the project
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
			    
			    User user = authManager.getUser();
			    
			    // get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is allowed to remove dependencies from the project
			    	
			    	boolean removed = project.removeDependency(componentId, connection);
		    		if(removed) {
		    			// removed dependency successfully
		    			return Response.ok().build();
		    		} else {
		    			// dependency is not included in the project
		    	    	return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
		    	    			.entity("Dependency with the given id could not be found in the project.").build();
		    		}
			    } else {
			    	// user does not have the permission to remove dependencies from the project
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to remove a dependency from it.").build();
			    }
			} catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * Adds an external dependency to a project.
	 * @param projectId Id of the project where an external dependency should be added to.
	 * @param inputExternalDependency JSON object containing the GitHub URL where the external dependency is hosted.
	 * @return Response with status code (and possibly an error description).
	 */
	@POST
	@Path("/projects/{projectId}/extdependencies")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Adds external dependency to project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, added external dependency to the project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized to add external dependency to project."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id could not be found or GitHub repo does not exist."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User needs to be member of the project to add external dependencies to it."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response postProjectExternalDependency(@PathParam("projectId") int projectId, String inputExternalDependency) {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "postProjectExternalDependency: trying to add external dependency to project");
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			// check if user is allowed to add an external dependency to the project
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
									    
				User user = authManager.getUser();
									    
				// get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is member of the project and thus allowed to add external dependencies to it
			        
			    	JSONObject json = (JSONObject) JSONValue.parse(inputExternalDependency);
			    	String gitHubURL = (String) json.get("gitHubURL");
			    	String type = (String) json.get("type");
			    	
			    	// check if repo exists
			    	if(!GitHubHelper.getInstance().repoExists(gitHubURL)) {
			    		return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
			    				.entity("GitHub repository does not exist.").build();
			    	}
			    	
			    	ExternalDependency externalDependency = new ExternalDependency(projectId, gitHubURL, type);
			    	externalDependency.persist(connection);
			    	
			    	return Response.ok().build();
			    } else {
			    	// user is no member of the project and thus not allowed to add external dependencies to it
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to add external dependencies to it.").build();
			    }
			} catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } catch (GitHubException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
			} finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * Removes the external dependency with the given id from the project with the given id.
	 * Therefore, the user sending the request needs to be a member of the project.
	 * @param projectId Id of the project where the external dependency should be removed from.
	 * @param externalDependencyId Id of the external dependency which should be removed from the project.
	 * @return Response with status code (and possibly error message).
	 */
	@DELETE
	@Path("/projects/{projectId}/extdependencies/{externalDependencyId}")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Removes an external dependency from a project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, removed external dependency from project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User is not member of the project and thus not allowed to remove external dependencies from it."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id or external dependency to remove from project could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response removeProjectExternalDependency(@PathParam("projectId") int projectId, @PathParam("externalDependencyId") int externalDependencyId) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE,
				"removeProjectExternalDependency: trying to remove external dependency with id " + externalDependencyId +
				" from project with id " + projectId);
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			// check if user is allowed to remove an external dependency from the project
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
			    
			    User user = authManager.getUser();
			    
			    // get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is allowed to remove external dependencies from the project
			    	
			    	boolean removed = project.removeExternalDependency(externalDependencyId, connection);
		    		if(removed) {
		    			// removed external dependency successfully
		    			return Response.ok().build();
		    		} else {
		    			// external dependency is not included in the project
		    	    	return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
		    	    			.entity("External dependency with the given id could not be found in the project.").build();
		    		}
			    } else {
			    	// user does not have the permission to remove external dependencies from the project
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to remove an external dependency from it.").build();
			    }
			} catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * Invites a user to a project.
	 * @param projectId Id of the project where a user should be invited to.
	 * @param inputUser JSON containing the loginName of the user that should be invited.
	 * @return Reponse with status code (and possibly error message).
	 */
	@POST
	@Path("/projects/{projectId}/invitations")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Creates a new invitation to a project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, invited user to project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User is not member of the project and thus not allowed to invite others to it."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id or user to invite to project could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_CONFLICT, message = "User is already member of the project or is already invited to it."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response postInvitation(@PathParam("projectId") int projectId, String inputUser) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "postInvitation: posting an invitation to project with id " + projectId);
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			// check if user is allowed to add a user to the project
			Connection connection = null;
		    try {
			    connection = dbm.getConnection();
			
			    // get user sending the request
				User user = authManager.getUser();
				
				// get project, where a user should be invited to
				Project project = new Project(projectId, connection);
				
				if(project.hasUser(user.getId(), connection)) {
				    // user is part of the project and thus is allowed to invite others to it
					// get user that should be invited
					// extract name of the user given in the request body (as json)
			    	JSONObject jsonUserToInvite = (JSONObject) JSONValue.parseWithException(inputUser);
			    	    
			    	if(jsonUserToInvite.containsKey("loginName")) {
			    	    String userToInviteLoginName = (String) jsonUserToInvite.get("loginName");
			    	    	
			    	    User userToInvite = User.loadUserByLoginName(userToInviteLoginName, connection);
			    	    
			    	    // check if the user is already member of the project
			    	    // then an invitation does not make any sense
			    	    if(project.hasUser(userToInvite.getId(), connection)) {
			    	    	return Response.status(HttpURLConnection.HTTP_CONFLICT)
			    	    			.entity("User cannot be invited, because user is already member of the project.").build();
			    	    }
			    	    
			    	    // check if an invitation already exists
			    	    // then another invitation would not make any sense
			    	    if(ProjectInvitation.exists(projectId, userToInvite.getId(), connection)) {
			    	    	return Response.status(HttpURLConnection.HTTP_CONFLICT)
			    	    			.entity("User is already invited to the project.").build();
			    	    }
			    	    
			    	    // create invitation
			    	    ProjectInvitation inv = new ProjectInvitation(projectId, userToInvite.getId());
			    	    inv.persist(connection);
			    	    
			    	    return Response.ok().build();
			    	} else {
			    		return Response.status(HttpURLConnection.HTTP_BAD_REQUEST)
			    				.entity("Attribute 'loginName' is missing.").build();
			    	}
				} else {
					// user does not have the permission to invite others to the project
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to invite others to it.").build();
				}
				
		    } catch (UserNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("User with the given loginName could not be found.").build();
			} catch (ProjectNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("Project with the given id could not be found.").build();
			} catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } catch (ParseException e) {
            	logger.printStackTrace(e);
				return Response.status(HttpURLConnection.HTTP_BAD_REQUEST).build();
			} finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * Returns the invitations that the requesting user received.
	 * Therefore, the user sending the request needs to be authorized.
	 * @return Response with status code (and probably error message).
	 */
	@GET
	@Path("/invitations")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Returns the invitations of the user.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, returns the invitations of the user."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response getInvitationsByUser() {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "getInvitationsByUser: searching invitations for user");
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			Connection connection = null;
		    try {
			    connection = dbm.getConnection();
			    
			    // get user
			    User user = authManager.getUser();
			    
			    // load invitations by userId
			    JSONArray invitations = ProjectInvitation.loadInvitationsByUser(user.getId(), connection);
			    
			    // return them
			    return Response.ok(invitations.toJSONString()).build();
		    } catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * Deletes the invitation with the given id.
	 * This method may be used to decline an invitation to a project.
	 * @param invitationId Id of the invitation that should be deleted/declined.
	 * @return Response with status code (and possibly error message).
	 */
	@DELETE
	@Path("/invitations/{invitationId}")
	@ApiOperation(value = "Deletes the invitation with the given id.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, deleted invitation."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "Unauthorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "Invitation does not belong to the user. Thus the user is not allowed to delete it."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Invitation with the given id could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response deleteInvitation(@PathParam("invitationId") int invitationId) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "deleteInvitation: trying to delete invitation with id " + invitationId);
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			Connection connection = null;
		    try {
			    connection = dbm.getConnection();
			    
			    // get user
			    User user = authManager.getUser();
			    
			    // check if the invitation belong to the user
			    // try to load the invitation
			    ProjectInvitation inv = new ProjectInvitation(invitationId, connection);
			    
			    // if no InvitationNotFoundException got thrown, then the invitation exists
			    // check if the invitation belongs to the user now
			    if(inv.getUserId() == user.getId()) {
			    	// invitation belongs to user
			    	// delete it from database
			    	inv.delete(connection);
			    	
			    	return Response.ok().build();
			    } else {
			    	// invitation does not belong to the user
			    	// thus, user is not allowed to delete it
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("Your do not have the permission to delete this invitation.").build();
			    }
		    } catch (InvitationNotFoundException e) {
		    	logger.printStackTrace(e);
            	return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
            			.entity("Invitation with given id could not be found.").build();
		    } catch (SQLException e) {
            	logger.printStackTrace(e);
            	return Response.serverError().entity("Internal server error.").build();
            } finally {
				try {
					if(connection != null) connection.close();
				} catch (SQLException e) {
					logger.printStackTrace(e);
					return Response.serverError().entity("Internal server error.").build();
				}
			}
		}
	}
	
	/**
	 * Searches for all components that exist in the database.
	 * @return Response with status code and possibly error message. If no error occurs, then a list of components is returned.
	 */
	@GET
	@Path("/components")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Returns a list of all components that exist in the database.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, returning list of all components."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response getAllComponents() {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "getAllComponents called");
     
        Connection connection = null;
	    try {
		    connection = dbm.getConnection();
		    
		    // load list of all components from the database
		    ArrayList<Component> allComponents = Component.getAllComponents(connection);
		    
		    // convert to JSONArray
		    JSONArray a = new JSONArray();
		    for(Component c : allComponents) {
		    	a.add(c.toJSONObject());
		    }
		    // return as JSON string
		    return Response.ok(a.toJSONString()).build();
	    } catch (SQLException e) {
        	logger.printStackTrace(e);
        	return Response.serverError().entity("Internal server error.").build();
        } catch (ParseException e) {
        	logger.printStackTrace(e);
        	return Response.serverError().entity("Internal server error.").build();
		} finally {
			try {
				if(connection != null) connection.close();
			} catch (SQLException e) {
				logger.printStackTrace(e);
				return Response.serverError().entity("Internal server error.").build();
			}
		}
        
	}
	
	/**
	 * Requests GitHub access token for the currently active user. Therefore it uses the given
	 * gitHubCode. Both the access token and the GitHub username of the user are stored into the 
	 * database and returned in the requests result.
	 * @param gitHubCode Code given from GitHub API to request access token.
	 * @return Response containing user's access token and GitHub username (or error code and error message).
	 */
	@POST
	@Path("/users/githubcode/{gitHubCode}")
	@ApiOperation(value = "Requests GitHub access token and username for the currently active user by using the given code.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, returning GitHub access token and username."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User is not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
    public Response postGitHubCode(@PathParam("gitHubCode") String gitHubCode) {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "postGitHubCode called");
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			try {
				String response = GitHubHelper.getInstance().getUserAccessToken(gitHubCode);
				
				JSONObject json = (JSONObject) JSONValue.parse(response);
				if(json.containsKey("access_token")) {
					String accessToken = (String) json.get("access_token");
					
				    Connection connection = null;
				    try {
				    	connection = dbm.getConnection();
				    	
						// get user
					    User user = authManager.getUser();
				        user.putGitHubAccessToken(accessToken, connection);
				        
				        String jsonUserStr = GitHubHelper.getInstance().getGitHubUsername(accessToken);
				        
				        JSONObject jsonUser = (JSONObject) JSONValue.parse(jsonUserStr);
				        if(!jsonUser.containsKey("login")) {
				        	return Response.serverError().build();
				        }
				        
				        String username = (String) jsonUser.get("login");
				        user.putUsername(username, connection);
				        
				        JSONObject jsonResponse = (JSONObject) JSONValue.parse(response);
				        jsonResponse.put("gitHubUsername", username);
				        
				        return Response.ok(jsonResponse.toJSONString()).build();
				    } catch (SQLException e) {
				    	logger.printStackTrace(e);
			        	return Response.serverError().entity("Internal server error.").build();
				    } finally {
						try {
							if(connection != null) connection.close();
						} catch (SQLException e) {
							logger.printStackTrace(e);
							return Response.serverError().entity("Internal server error.").build();
						}
					}
				} else {
					return Response.serverError().build();
				}
			} catch (GitHubException e) {
				logger.printStackTrace(e);
				return Response.serverError().entity(e.getMessage()).build();
			}
		}
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
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			User user;
			try {
				user = authManager.getUser();
				
				// returns user as json string
	            return Response.ok(user.toJSONObject(true).toJSONString()).build();
			} catch (SQLException e) {
				logger.printStackTrace(e);
				// return server error at the end
			}
		}
		
		return Response.serverError().entity("Internal server error.").build();
	}
	
	/**
	 * Searches for users where the username is like the given username.
	 * @param username Username to search for.
	 * @return Response with status code (and possibly error message).
	 */
	@GET
	@Path("/users/{username}")
	@ApiOperation(value = "Searches for users where the username is like the given username.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, returning list of users that were found."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response searchUsers(@PathParam("username") String username) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "searchUsers: searching for users with a username like " + username);
		
		Connection connection = null;
	    try {
		    connection = dbm.getConnection();
		    
		    ArrayList<User> users = User.searchUsers(username, connection);
		    
		    JSONArray jsonUsers = new JSONArray();
		    // only add the usernames
		    for(User user : users) {
		    	jsonUsers.add(user.getLoginName());
		    }
		    return Response.ok(jsonUsers.toJSONString()).build();
	    } catch (SQLException e) {
        	logger.printStackTrace(e);
        	return Response.serverError().entity("Internal server error.").build();
        } finally {
			try {
				if(connection != null) connection.close();
			} catch (SQLException e) {
				logger.printStackTrace(e);
				return Response.serverError().entity("Internal server error.").build();
			}
		}
		
	}
}
