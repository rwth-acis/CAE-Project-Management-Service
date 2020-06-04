package i5.las2peer.services.projectManagementService;

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
import i5.las2peer.services.projectManagementService.database.DatabaseManager;
import i5.las2peer.services.projectManagementService.exception.GitHubException;
import i5.las2peer.services.projectManagementService.exception.ProjectNotFoundException;
import i5.las2peer.services.projectManagementService.exception.RoleNotFoundException;
import i5.las2peer.services.projectManagementService.exception.UserNotFoundException;
import i5.las2peer.services.projectManagementService.project.Project;
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
	 * @param inputProject JSON representation of the project to store.
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
			@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Input project is not well formatted."),
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
			    	// persist method also stores users of the project (only the creator for now) etc.
			    	project.persist(connection);
			    	
					return Response.status(HttpURLConnection.HTTP_CREATED).entity(project.toJSONObject().toJSONString()).build();
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
	 * Searches for the project that the user is part of.
	 * Therefore, the user needs to be authorized.
	 * @return Response containing the status code (and a message or project list).
	 */
	@GET
	@Path("/projects")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Searches for the project that the user is part of.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, list of users projects is returned."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response getProjectsByUser() {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "getProjectsByUser: searching for users projects");
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).entity("User not authorized.").build();
		} else {
			Connection connection = null;
            try {
            	// first get current user from database
            	// the id of the user will be needed later
            	User user = authManager.getUser();
            	
            	connection = dbm.getConnection();
            	// get all projects where the user is part of
            	ArrayList<Project> projects = Project.getProjectsByUser(user.getId(), connection);
            	
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
	}
	
	/**
	 * Searches for projects in the database.
	 * Therefore, no authorization is needed.
	 * @param projectName Project name to search for.
	 * @return Response containing the status code (and a message or project list).
	 */
	@GET
	@Path("/projects/{projectName}")
	@Produces(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Searches for projects in the database.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message="Found project(s) with the given name."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response searchProjects(@PathParam("projectName") String projectName) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "getProject: searching project(s) with name " + projectName);
		
		Connection connection = null;
		try {
			connection = dbm.getConnection();
			
			// search for projects
			ArrayList<Project> projects = Project.searchProjects(projectName, connection);
			
			// return JSONArray as string
        	return Response.ok(Project.projectListToJSONArray(projects).toJSONString()).build();
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
	 * Adds a user to a project.
	 * Therefore, the user sending the request needs to be authorized in order
	 * to check if the user is a member of the project, because only project members 
	 * should be allowed to add users to it.
	 * @param projectId Id of the project where the user should be added to.
	 * @param inputUser JSON object containing a "loginName" attribute with the name of the user to add.
	 * @return Response with status code (and possibly an error description).
	 */
	@POST
	@Path("/projects/{id}/users")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Adds a user to the project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, added user to project. Also returns JSON of user which got added."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User is not member of the project and thus not allowed to add users to it."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id or user to add to project could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Input user is not well formatted."),
			@ApiResponse(code = HttpURLConnection.HTTP_CONFLICT, message = "The user is already member of the project."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response addUserToProject(@PathParam("id") int projectId, String inputUser) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "addUserToProject: adding user to project with id " + projectId);
		
		if (authManager.isAnonymous()) {
            return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
        } else {
            // check if user is allowed to add a user to the project
			Connection connection = null;
			try {
			    connection = dbm.getConnection();
			    
			    User user = authManager.getUser();
			    
			    // get project by id (load it from database)
			    Project project = new Project(projectId, connection);
			    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is part of the project and thus is allowed to add new users
			    	// extract name of the user given in the request body (as json)
			    	JSONObject jsonUserToAdd = (JSONObject) JSONValue.parseWithException(inputUser);
			    	    
			    	if(jsonUserToAdd.containsKey("loginName")) {
			    	    String userToAddLoginName = (String) jsonUserToAdd.get("loginName");
			    	    	
			    	    User userToAdd = User.loadUserByLoginName(userToAddLoginName, connection);
			    	    boolean added = project.addUser(userToAdd, connection, true); // true, because user also should be added to users list of Project object
			    	    if(added) {
			    	        // return result: ok
			    	    	// user object should be returned
			    	    	// also the assigned role should be included
			    	    	JSONObject o = userToAdd.toJSONObject();
			    	    	o.put("roleId", project.getRoleByUser(userToAdd).getId());
			    	    	
			    	        return Response.ok(o.toJSONString()).build();
			    	    } else {
			    	    	// user is already a member of the project
			    	    	return Response.status(HttpURLConnection.HTTP_CONFLICT)
			    	    			.entity("User is already member of the project.").build();
			    	    }
			    	} else {
			    	    return Response.status(HttpURLConnection.HTTP_BAD_REQUEST)
			    	            .entity("Input user does not contain key 'loginName' which is needed.").build();
			    	}
			    } else {
			    	// user does not have the permission to add users to the project
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to add a user to it.").build();
			    }
			    
			} catch (UserNotFoundException e) {
				return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
						.entity("User with the given login name could not be found.").build();
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
	 * Updates the Requirements Bazaar config of the project.
	 * @param projectId Project id of the CAE project where the Requirements Bazaar config should be updated.
	 * @param inputReqBazConfig JSON object (as string) containing the project and category id of the Requirements Bazaar.
	 * @return Response with status code (and possibly an error description).
	 */
	@PUT
	@Path("/projects/{id}/reqbaz")
	@Consumes(MediaType.APPLICATION_JSON)
	@ApiOperation(value = "Updates the Requirements Bazaar config of the project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, updated Requirements Bazaar config."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized to update Requirements Bazaar config."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User needs to be member of the project to edit its config."),
			@ApiResponse(code = HttpURLConnection.HTTP_BAD_REQUEST, message = "Input Requirements Bazaar config is not well formatted or information is missing."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response putProjectReqBaz(@PathParam("id") int projectId, String inputReqBazConfig) {
		Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "putProjectReqBaz: called with project id " + projectId);
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			// check if user is allowed to update the requirements bazaar config of the project
			Connection connection = null;
			try {
		        connection = dbm.getConnection();
						    
			    User user = authManager.getUser();
						    
				// get project by id (load it from database)
				Project project = new Project(projectId, connection);
						    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is member of the project and thus allowed to edit its config
			    	// extract reqbaz projectId and categoryId given in the request body (as json)
			    	JSONObject jsonReqBazConfig = (JSONObject) JSONValue.parseWithException(inputReqBazConfig);
			    	
			    	if(jsonReqBazConfig.containsKey("projectId") && jsonReqBazConfig.containsKey("categoryId")) {
			    		int reqBazProjectId = ((Long) jsonReqBazConfig.get("projectId")).intValue();
			    		int reqBazCategoryId = ((Long) jsonReqBazConfig.get("categoryId")).intValue();
			    		
			    		project.updateRequirementsBazaarConfig(reqBazProjectId, reqBazCategoryId, connection);
			            return Response.ok().build();
			    	} else {
			    		return Response.status(HttpURLConnection.HTTP_BAD_REQUEST)
			    	            .entity("Input Requirements Bazaar config does not contain key 'projectId' or 'categoryId' which is needed.").build();
			    	}
			    } else {
			    	// user is no member of the project and thus not allowed to edit its config
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to edit its config.").build();
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
	 * Disconnects the Requirements Bazaar from the CAE project.
	 * @param projectId Id of the project where the Requirements Bazaar category should be disconnected from.
	 * @return Response with status code (and possibly an error description).
	 */
	@DELETE
	@Path("/projects/{id}/reqbaz")
	@ApiOperation(value = "Disconnects the Requirements Bazaar from the CAE project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, disconnected Requirements Bazaar from CAE project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized to disconnect Requirements Bazaar."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User needs to be member of the project to disconnect it from the Requirements Baqzaar."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response disconnectProjectRequirementsBazaar(@PathParam("id") int projectId) {
        Context.get().monitorEvent(MonitoringEvent.SERVICE_MESSAGE, "disconnectProjectRequirementsBazaar: called with project id " + projectId);
		
		if(authManager.isAnonymous()) {
			return Response.status(HttpURLConnection.HTTP_UNAUTHORIZED).build();
		} else {
			// check if user is allowed to disconnect the the requirements bazaar from the project
			Connection connection = null;
			try {
		        connection = dbm.getConnection();
						    
			    User user = authManager.getUser();
						    
				// get project by id (load it from database)
				Project project = new Project(projectId, connection);
						    
			    if(project.hasUser(user.getId(), connection)) {
			    	// user is member of the project and thus allowed to disconnect the requirements bazaar
			    	project.disconnectRequirementsBazaar(connection);
			    	return Response.ok().build();
			    } else {
			    	// user is no member of the project and thus not allowed to disconnect the Requirements Bazaar
			    	return Response.status(HttpURLConnection.HTTP_FORBIDDEN)
			    			.entity("User needs to be member of the project to disconnect the Requirements Bazaar.").build();
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
	 * Adds a component to a project.
	 * @param projectId Id of the project where a component should be added to.
	 * @param inputComponent JSON representation of the component to add to the project.
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
			    	// persist component
			    	component.persist(project, connection);
			    	
			    	// send component as result
			    	return Response.ok(component.toJSONObject().toJSONString()).build();
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
	 * Lists the components of the project.
	 * @param projectId Id of the project where the components should be listed.
	 * @return Response with status (and possibly error message).
	 */
	@GET
	@Path("/projects/{projectId}/components")
	@ApiOperation(value = "Lists the components of the project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, returns list of components of the project."),
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
			
			// get components of the project
			ArrayList<Component> components = project.getComponents();
			
			// create JSONArray of the ArrayList
			JSONArray jsonComponents = new JSONArray();
			for(Component component : components) {
				jsonComponents.add(component.toJSONObject());
			}
			
			// return JSONArray as string
        	return Response.ok(jsonComponents.toJSONString()).build();
		} catch (ProjectNotFoundException e) {
			logger.printStackTrace(e);
			return Response.status(HttpURLConnection.HTTP_NOT_FOUND)
					.entity("Project with the given id could not be found.").build();
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
	 * Removes the component with the given id from the project with the given id.
	 * Therefore, the user sending the request needs to be a member of the project.
	 * Note: Currently, the component will not be deleted, since it might be used as a dependency
	 * somewhere else. Right now only the entry in the ProjectToComponent table gets removed.
	 * TODO: When dependencies are included, then this method needs to be updated: Check if component
	 * that should be deleted is used as a dependency somewhere. If not, then remove the component too 
	 * (and not only the ProjectToComponent entry).
	 * @param projectId Id of the project where the component should be removed from.
	 * @param componentId Id of the component which should be removed from the project.
	 * @return Response with status code (and possibly error message).
	 */
	@DELETE
	@Path("/projects/{projectId}/components/{componentId}")
	@ApiOperation(value = "Removes a component from a project.")
	@ApiResponses(value = {
			@ApiResponse(code = HttpURLConnection.HTTP_OK, message = "OK, removed component from project."),
			@ApiResponse(code = HttpURLConnection.HTTP_UNAUTHORIZED, message = "User not authorized."),
			@ApiResponse(code = HttpURLConnection.HTTP_FORBIDDEN, message = "User is not member of the project and thus not allowed to remove components from it."),
			@ApiResponse(code = HttpURLConnection.HTTP_NOT_FOUND, message = "Project with the given id or component to remove from project could not be found."),
			@ApiResponse(code = HttpURLConnection.HTTP_INTERNAL_ERROR, message = "Internal server error.")
	})
	public Response removeProjectComponent(@PathParam("projectId") int projectId, @PathParam("componentId") int componentId) {
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
			    	boolean removed = project.removeComponent(componentId, connection);
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
	            return Response.ok(user.toJSONObject().toJSONString()).build();
			} catch (SQLException e) {
				logger.printStackTrace(e);
				// return server error at the end
			}
		}
		
		return Response.serverError().entity("Internal server error.").build();
	}
}
