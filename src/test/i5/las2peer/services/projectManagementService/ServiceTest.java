package i5.las2peer.services.projectManagementService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Properties;

import javax.ws.rs.core.MediaType;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import i5.las2peer.api.p2p.ServiceNameVersion;
import i5.las2peer.connectors.webConnector.WebConnector;
import i5.las2peer.connectors.webConnector.client.ClientResponse;
import i5.las2peer.connectors.webConnector.client.MiniClient;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.p2p.LocalNodeManager;
import i5.las2peer.security.UserAgentImpl;
import i5.las2peer.services.projectManagementService.ProjectManagementService;
import i5.las2peer.services.projectManagementService.database.DatabaseManager;
import i5.las2peer.services.projectManagementService.exception.ProjectNotFoundException;
import i5.las2peer.services.projectManagementService.project.PredefinedRoles;
import i5.las2peer.services.projectManagementService.project.Project;
import i5.las2peer.services.projectManagementService.project.Role;
import i5.las2peer.services.projectManagementService.project.User;
import i5.las2peer.testing.MockAgentFactory;

/**
 * Test class for the CAE Project Management Service. Only tests on a REST level.
 * Note, that a database needs to be available and accessable by using the config 
 * of the properties file.
 */
public class ServiceTest {

	/**
	 * Database connection used to access database during the tests.
	 */
	private static Connection connection;

	private static LocalNode node;
	private static WebConnector connector;
	private static ByteArrayOutputStream logStream;

	private static UserAgentImpl testAgent;
	private static final String testPass = "adamspass";

	private static final String mainPath = "project-management/";

	/**
	 * Called before a test starts.
	 * 
	 * First, initializes a database connection with the given properties from the properties file.
	 * Then sets up the node, initializes connector and adds user agent that can be used throughout the test.
	 * 
	 * @throws Exception
	 */
	@Before
	public void startServer() throws Exception {
		// paths to properties
		Properties properties = new Properties();
		String propertiesFile = "./etc/i5.las2peer.services.projectManagementService.ProjectManagementService.properties";
		
		String jdbcDriverClassName = null;
		String jdbcUrl = null;
		String jdbcSchema = null;
		String jdbcLogin = null;
		String jdbcPass = null;
			
		// load properties
		try {
			FileReader reader = new FileReader(propertiesFile);
			properties.load(reader);

			jdbcDriverClassName = properties.getProperty("jdbcDriverClassName");
			jdbcUrl = properties.getProperty("jdbcUrl");
			jdbcSchema = properties.getProperty("jdbcSchema");
			jdbcLogin = properties.getProperty("jdbcLogin");
			jdbcPass = properties.getProperty("jdbcPass");
		} catch (Exception e) {
			e.printStackTrace();
			fail("File loading problems: " + e);
		}
			
		DatabaseManager databaseManager = new DatabaseManager(jdbcDriverClassName, jdbcLogin, jdbcPass, jdbcUrl,jdbcSchema);
		connection = databaseManager.getConnection();
		
		// clear (test-)database
		DatabaseHelper.clearDatabase(connection);
			
			
		// start node
		node = new LocalNodeManager().newNode();
		node.launch();

		// add agent to node
		testAgent = MockAgentFactory.getAdam();
		testAgent.unlock(testPass); // agents must be unlocked in order to be stored
		testAgent.setEmail("email@test.de");
		node.storeAgent(testAgent);

		// start service
		// during testing, the specified service version does not matter
		node.startService(new ServiceNameVersion(ProjectManagementService.class.getName(), "0.1.0"), "a pass");

		// start connector
		connector = new WebConnector(true, 0, false, 0); // port 0 means use system defined port
		logStream = new ByteArrayOutputStream();
		connector.setLogStream(new PrintStream(logStream));
		connector.start(node);
	}

	/**
	 * Called after the test has finished. Shuts down the server and prints out the connector log file for reference.
	 * 
	 * @throws Exception
	 */
	@After
	public void shutDownServer() throws Exception {
		// clear (test-)database
		// TODO: clear database in the end
		//DatabaseHelper.clearDatabase(connection);
		
		// close database connection
		connection.close();
		
		if (connector != null) {
			connector.stop();
			connector = null;
		}
		if (node != null) {
			node.shutDown();
			node = null;
		}
		if (logStream != null) {
			System.out.println("Connector-Log:");
			System.out.println("--------------");
			System.out.println(logStream.toString());
			logStream = null;
		}
	}
	
	
	/**
	 * Tests the POST method of /projects.
	 */
	@Test
	public void testPostProjects() {
		System.out.println("------------- Starting testPostProjects() -------------");
		// NOTE: This test is not working anymore, since we have no OIDC access token, which would be needed
		// to create a project, since it is needed to create a Requirements Bazaar category.
		/*try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			
			// first test without auth
			System.out.println("1. Test without auth");
			ClientResponse result = client.sendRequest("POST", mainPath + "projects", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
			assertEquals(401, result.getHttpCode());
			System.out.println("Result of 'testPostProjects' without auth: " + result.getResponse().trim());
			System.out.println();
			
			// test with auth and empty body
			// therefore, first set testAgent as logged in
			client.setLogin(testAgent.getIdentifier(), testPass);
			System.out.println("2. Test with auth and empty body");
		    result = client.sendRequest("POST", mainPath + "projects", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
		    assertEquals(400, result.getHttpCode());
		    System.out.println("Result of 'testPostProjects' with auth and empty body: " + result.getResponse().trim());
		    System.out.println();
			
			// test with auth and body containing an empty JSON object
		    System.out.println("3. Test with auth and body containing an empty JSON object");
		    result = client.sendRequest("POST", mainPath + "projects", "{}", MediaType.APPLICATION_JSON, "", new HashMap<>());
		    assertEquals(400, result.getHttpCode());
		    System.out.println("Result of 'testPostProjects' with auth and body containing an empty JSON object: " + result.getResponse().trim());
		    System.out.println();
		    
		    // test with auth and correct body
		    String projectName = "Project A";
		    System.out.println("4. Test with auth and correct body");
		    result = client.sendRequest("POST", mainPath + "projects", "{\"name\": \"" + projectName + "\"}", MediaType.APPLICATION_JSON, "", new HashMap<>());
		    assertEquals(201, result.getHttpCode());
		    System.out.println("Result of 'testPostProjects' with auth and correct body: " + result.getResponse().trim());
		    System.out.println();
		    
		    // check if project got really created and can be found in the database now
		    // if the project cannot be found, then a ProjectNotFoundException will be thrown
		    Project project = new Project(projectName, connection);
		    
		    // test with auth and same project name as before
		    // this should not work, since two projects with the same name are not allowed
		    System.out.println("5. Test with auth and same project name as before");
		    result = client.sendRequest("POST", mainPath + "projects", "{\"name\": \"" + projectName + "\"}", MediaType.APPLICATION_JSON, "", new HashMap<>());
		    assertEquals(409, result.getHttpCode());
		    System.out.println("Result of 'testPostProjects' with auth and same project name as before: " + result.getResponse().trim());
		    System.out.println();
		} catch(ProjectNotFoundException e) {
			e.printStackTrace();
			fail("Tried to create a new project, but project cannot be found in the database afterwards.");
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}*/
	}
	
	/**
	 * Tests the GET method of /projects.
	 */
	@Test
	public void testGetProjects() {
		System.out.println("------------- Starting testGetProjects() -------------");
		
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			
			// first test without auth
			System.out.println("1. Test without auth");
			ClientResponse result = client.sendRequest("GET", mainPath + "projects", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
			// without auth, all projects should be returned
			assertEquals(200, result.getHttpCode());
			System.out.println("Result of 'testGetProjects' without auth: " + result.getResponse().trim());
			System.out.println();
			
			// test with auth (now the result should be an empty list, since the database gets cleared when test starts and thus
			// no project exists)
			// therefore, first set testAgent as logged in
		    client.setLogin(testAgent.getIdentifier(), testPass);
			System.out.println("2. Test with auth");
			result = client.sendRequest("GET", mainPath + "projects", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
			assertEquals(200, result.getHttpCode());
			assertEquals("[]", result.getResponse().trim());
			System.out.println("Result of 'testGetProjects' with auth: " + result.getResponse().trim());
			System.out.println();
			
			// now we manually create a project
			// therefore, we need a User object (just get the one from testAgent)
			// The rest of the test is not working anymore, since we do not have an OIDC access token
			/*
			User user = new User(testAgent.getEmail(), connection);
			// now create the project
			String projectName = "Project A";
			Project project = new Project(user, "{\"name\": \"" + projectName + "\"}");
			project.persist(connection);
			
			// now we can send a request to check if the resulting list contains the newly created project
			System.out.println("3. Test with auth and one project");
			result = client.sendRequest("GET", mainPath + "projects", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
			assertEquals(200, result.getHttpCode());
			
			// now do a very basic check and see if a project with the name of the new one exists in the result list
			JSONArray resultingList = (JSONArray) JSONValue.parse(result.getResponse().trim());
			JSONObject resultingProject = null;
			for(Object entry : resultingList) {
				JSONObject jsonEntry = (JSONObject) entry;
				if(jsonEntry.get("name").equals(projectName)) {
					resultingProject = jsonEntry;
					break;
				}
			}
			assertTrue("Request result does not contain created project.", resultingProject != null);
			
			// check for some attributes and if at least one user exists
			assertTrue("Request result does not contain attribute 'roles'.", resultingProject.containsKey("roles"));
			assertTrue("Request result does not contain attribute 'users'.", resultingProject.containsKey("users"));
			JSONArray users = (JSONArray) resultingProject.get("users");
			assertTrue("Request result does not contain at least one user.", !users.isEmpty());
			
			System.out.println("Result of 'testGetProjects' with auth and one project: " + result.getResponse().trim());
			System.out.println();*/
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
	
	/**
	 * Tests the GET method of /projects/{projectName}.
	 */
	@Test
	public void testGetProjectsByName() {
        System.out.println("------------- Starting testGetProjectsByName() -------------");
		
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			
			String projectName = "ProjectA";
			
			// first test without auth (this should work, because no auth is needed)
			// since the project that gets searched does not exist (since database got cleared before test)
			// an empty array should be returned
			System.out.println("1. Test without auth");
			ClientResponse result = client.sendRequest("GET", mainPath + "projects/" + projectName, "", MediaType.APPLICATION_JSON, "", new HashMap<>());
			assertEquals(200, result.getHttpCode());
			assertEquals("[]", result.getResponse().trim());
			System.out.println("Result of 'testGetProjectsByName' without auth: " + result.getResponse().trim());
			System.out.println();
			
			// now we manually create a project
			// therefore, we need a User object
			// since we have not sent a request with the testAgent logged in yet, we first
			// need to store a new User object
			// The rest of the test is not working anymore, since we do not have an OIDC access token
			/*
			User user = new User("test@test.de", "TestUser");
			user.persist(connection);
			// now create the project
		    Project project = new Project(user, "{\"name\": \"" + projectName + "\"}");
			project.persist(connection);
			
			// now send a request and check if the result contains the project
			System.out.println("2. Test without auth and one project");
			result = client.sendRequest("GET", mainPath + "projects/" + projectName, "", MediaType.APPLICATION_JSON, "", new HashMap<>());
			assertEquals(200, result.getHttpCode());
			
			// now do a very basic check and see if a project with the name of the new one exists in the result list
			JSONArray resultingList = (JSONArray) JSONValue.parse(result.getResponse().trim());
			JSONObject resultingProject = null;
			for(Object entry : resultingList) {
				JSONObject jsonEntry = (JSONObject) entry;
				if(jsonEntry.get("name").equals(projectName)) {
					resultingProject = jsonEntry;
					break;
				}
			}
			assertTrue("Request result does not contain created project.", resultingProject != null);
			
			System.out.println("Result of 'testGetProjectsByName' without auth: " + result.getResponse().trim());
			System.out.println();*/
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
	
	/**
	 * Tests the POST method of /projects/{id}/users.
	 */
	@Test
	public void testPostProjectsUsers() {
		System.out.println("------------- Starting testPostProjectsUsers() -------------");
		
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			
			// first test without auth
			System.out.println("1. Test without auth");
			ClientResponse result = client.sendRequest("POST", mainPath + "projects/1/users", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
			// without auth this should not be possible
			assertEquals(401, result.getHttpCode());
			System.out.println("Result of 'testPostProjectsUsers' without auth: " + result.getResponse().trim());
			System.out.println();
			
			// try with auth now (project with id 1 should not exist)
			client.setLogin(testAgent.getIdentifier(), testPass);
			System.out.println("2. Test with auth but non-existing project");
			result = client.sendRequest("POST", mainPath + "projects/1/users", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
		    // since the project should not exist, we expect to get 404
			assertEquals(404, result.getHttpCode());
			System.out.println("Result of 'testPostProjectsUsers' with auth but non-existing project: " + result.getResponse().trim());
			System.out.println();
			
			// create and store a project with testAgent as creator
			// The rest of the test is not working anymore, since we do not have an OIDC access token
			/*
			User user = new User(testAgent.getEmail(), connection);
			String projectName = "ProjectA";
			Project project = new Project(user, "{\"name\": \"" + projectName + "\"}");
			project.persist(connection);
			int projectId = project.getId();
			
			// send request without body
			System.out.println("3. Test with auth, existing project but empty body");
			result = client.sendRequest("POST", mainPath + "projects/" + projectId + "/users", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
		    // since the input user is not well formatted (or body empty), we expect to get 400
			assertEquals(400, result.getHttpCode());
			System.out.println("Result of 'testPostProjectsUsers' with auth, existing project but empty body: " + result.getResponse().trim());
			System.out.println();
			
			// now we test adding a user to the project which does not exist in the database
			System.out.println("4. Test with auth, existing project but non-existing user to add");
			result = client.sendRequest("POST", mainPath + "projects/" + projectId + "/users", "{\"loginName\": \"TestUserLoginName\"}", MediaType.APPLICATION_JSON, "", new HashMap<>());
			// since there should not exist a user with the loginName "TestUserLoginName" in the database
			// we expect to get 404 status code
			assertEquals(404, result.getHttpCode());
			System.out.println("Result of 'testPostProjectsUsers' with auth, existing project but non-existing user to add: " + result.getResponse().trim());
			System.out.println();
			
			// now we need a second user in the database who can be added as a project member
			User user2 = new User("email2@test.de", "TestUser");
			user2.persist(connection);
			
			// try to add user2 to the project
			System.out.println("5. Test with auth, existing project and existing user to add");
			result = client.sendRequest("POST", mainPath + "projects/" + projectId + "/users", "{\"loginName\": \"TestUser\"}", MediaType.APPLICATION_JSON, "", new HashMap<>());
			assertEquals(200, result.getHttpCode());
			// check if project in database now contains user2
			Project updatedProject = new Project(projectName, connection);
			assertTrue("Tried to add user to project, but after that user is not included in users list of project.", 
					updatedProject.hasUser(user2.getId(), connection));
			System.out.println("Result of 'testPostProjectsUsers' with auth, existing project and existing user to add: " + result.getResponse().trim());
			System.out.println();*/
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
	
	/**
	 * Tests the DELETE method of /projects/{projectId}/users/{userId}.
	 */
	@Test
	public void testDeleteProjectsUsers() {
		System.out.println("------------- Starting testDeleteProjectsUsers() -------------");
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			
			// first test without auth
			System.out.println("1. Test without auth");
			ClientResponse result = client.sendRequest("DELETE", mainPath + "projects/1/users/1", "");
			// without auth this should not be possible
			assertEquals(401, result.getHttpCode());
			System.out.println("Result of 'testDeleteProjectsUsers' without auth: " + result.getResponse().trim());
			System.out.println();
			
			// try with auth now (project with id 1 should not exist)
			client.setLogin(testAgent.getIdentifier(), testPass);
			System.out.println("2. Test with auth but non-existing project");
			result = client.sendRequest("DELETE", mainPath + "projects/1/users/1", "");
			// since the project should not exist, we expect to get 404
			assertEquals(404, result.getHttpCode());
			System.out.println("Result of 'testDeleteProjectsUsers' with auth but non-existing project: " + result.getResponse().trim());
			System.out.println();
			
			// create and store a project with testAgent as creator
			// The rest of the test is not working anymore, since we do not have an OIDC access token
			/*
			User user = new User(testAgent.getEmail(), connection);
			String projectName = "ProjectA";
			Project project = new Project(user, "{\"name\": \"" + projectName + "\"}");
			project.persist(connection);
			int projectId = project.getId();
			
			// now we test removing a user from the project which does not exist in the database
			System.out.println("3. Test with auth, existing project but non-existing user to remove");
		    result = client.sendRequest("DELETE", mainPath + "projects/" + projectId + "/users/10", "");
			// since there should not exist a user with the id "10" in the database
			// we expect to get 404 status code
		    assertEquals(404, result.getHttpCode());
			System.out.println("Result of 'testDeleteProjectsUsers' with auth, existing project but non-existing user to remove: " + result.getResponse().trim());
			System.out.println();
			
			// add a second user to the project manually
			User user2 = new User("test@test.de", "TestUser");
			user2.persist(connection);
			project.addUser(user2, connection, true);
			
			// try to remove user2 from the project
			System.out.println("4. Test with auth, existing project and existing user to remove");
		    result = client.sendRequest("DELETE", mainPath + "projects/" + projectId + "/users/" + user2.getId(), "");
		    assertEquals(200, result.getHttpCode());
		    // reload project to check if user got really removed
		    project = new Project(projectName, connection);
		    assertTrue("User is still member of the project, but should not be.", !project.hasUser(user2.getId(), connection));
		    System.out.println("Result of 'testDeleteProjectsUsers' with auth, existing project and existing user to remove: " + result.getResponse().trim());
			System.out.println();
			
			// now user2 creates a project, where the testAgent/user is no member of
			String projectNameNoMember = "ProjectB";
			Project projectNoMember = new Project(user2, "{\"name\": \"" + projectNameNoMember + "\"}");
			projectNoMember.persist(connection);
			
			// if the testAgent/user tries to edit the users of the project, then it should not work
			// because only members of a project can edit the users list
			System.out.println("5. Test with auth, testAgent is no project member");
			// userId of user to remove does not matter
		    result = client.sendRequest("DELETE", mainPath + "projects/" + projectNoMember.getId() + "/users/1", "");
		    assertEquals(403, result.getHttpCode());
		    System.out.println("Result of 'testDeleteProjectsUsers' with auth, testAgent is no project member: " + result.getResponse().trim());
			System.out.println();*/
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
	
	/**
	 * Tests the POST method of /projects/{id}/roles.
	 */
	@Test
	public void testPostProjectsRoles() {
		System.out.println("------------- Starting testPostProjectsRoles() -------------");
		
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			
			// first test without auth
			System.out.println("1. Test without auth");
			ClientResponse result = client.sendRequest("POST", mainPath + "projects/1/roles", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
			// without auth this should not be possible
			assertEquals(401, result.getHttpCode());
			System.out.println("Result of 'testPostProjectsRoles' without auth: " + result.getResponse().trim());
			System.out.println();
						
			// try with auth now (project with id 1 should not exist)
			client.setLogin(testAgent.getIdentifier(), testPass);
			System.out.println("2. Test with auth but non-existing project");
		    result = client.sendRequest("POST", mainPath + "projects/1/roles", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
		    // since the project should not exist, we expect to get 404
		    assertEquals(404, result.getHttpCode());
			System.out.println("Result of 'testPostProjectsRoles' with auth but non-existing project: " + result.getResponse().trim());
		    System.out.println();
		    
		    // create and store a project with testAgent as creator
		    // The rest of the test is not working anymore, since we do not have an OIDC access token
		    /*
		 	User user = new User(testAgent.getEmail(), connection);
		 	String projectName = "ProjectA";
		 	Project project = new Project(user, "{\"name\": \"" + projectName + "\"}");
		 	project.persist(connection);
		 	int projectId = project.getId();
		 			
		 	// send request without body
		 	System.out.println("3. Test with auth, existing project but empty body");
		 	result = client.sendRequest("POST", mainPath + "projects/" + projectId + "/roles", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
		    // since the input user is not well formatted (or body empty), we expect to get 400
		 	assertEquals(400, result.getHttpCode());
		 	System.out.println("Result of 'testPostProjectsRoles' with auth, existing project but empty body: " + result.getResponse().trim());
		 	System.out.println();
		 	
		 	// test what happens when adding a role with a name that is already used by a role
		 	// therefore, we make use of the predefined rules, because they should already exist
		 	// in the created project
		 	System.out.println("4. Test with auth, but role name that is already used");
		 	// get one of the role names from the predefined roles
		 	Role role = PredefinedRoles.get(projectId).get(0);
		 	result = client.sendRequest("POST", mainPath + "projects/" + projectId + "/roles", "{\"name\": \"" + role.getName() + "\"}", MediaType.APPLICATION_JSON, "", new HashMap<>());
		 	// since we try to add a role with a name that is already used by another role, we except
		 	// 409 as the status code of the response
		 	assertEquals(409, result.getHttpCode());
		 	System.out.println("Result of 'testPostProjectsRoles' with auth, but role name that is already used: " + result.getResponse().trim());
		 	System.out.println();
		 	
		 	// create a second project where the testAgent/user is no member of
		 	User user2 = new User("user2@test.de", "TestUser");
		 	user2.persist(connection);
		 	String projectNameNoMember = "ProjectB";
		 	Project projectNoMember = new Project(user2, "{\"name\": \"" + projectNameNoMember + "\"}");
		 	projectNoMember.persist(connection);
		 	
		 	// test to add a role to the project with testAgent logged in
		 	// testAgent should not be permitted to do this action
		 	System.out.println("5. Test with auth, but user is no member of the project");
		 	// body should not matter
		 	result = client.sendRequest("POST", mainPath + "projects/" + projectNoMember.getId() + "/roles", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
		 	assertEquals(403, result.getHttpCode());
		 	System.out.println("Result of 'testPostProjectsRoles' with auth, but user is no member of the project: " + result.getResponse().trim());
		 	System.out.println();
		 	
		 	// test to add role to project where user is member of and where role name is not used yet
		 	System.out.println("6. Test with auth, user is project member and role name is not used yet");
		 	String roleName = "TestRole";
		 	result = client.sendRequest("POST", mainPath + "projects/" + projectId + "/roles", "{\"name\": \"" + roleName + "\"}", MediaType.APPLICATION_JSON, "", new HashMap<>());
		 	assertEquals(200, result.getHttpCode());
		 	// test if role got really created in the database
		 	// reload project
		 	Project reloaded = new Project(projectName, connection);
		 	assertTrue("Tried to add role to project, but role does not exist now.", reloaded.hasRole(roleName));
		 	System.out.println("Result of 'testPostProjectsRoles' with auth, user is project member and role name is not used yet: " + result.getResponse().trim());
		 	System.out.println();*/
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
	
	/**
	 * Tests the DELETE method of /projects/{projectId}/roles/{roleId}.
	 */
	@Test
	public void testDeleteProjectsRoles() {
		System.out.println("------------- Starting testDeleteProjectsRoles() -------------");
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			
			// first test without auth
			System.out.println("1. Test without auth");
			ClientResponse result = client.sendRequest("DELETE", mainPath + "projects/1/roles/1", "");
			// without auth this should not be possible
			assertEquals(401, result.getHttpCode());
			System.out.println("Result of 'testDeleteProjectsRoles' without auth: " + result.getResponse().trim());
			System.out.println();
						
			// try with auth now (project with id 1 should not exist)
			client.setLogin(testAgent.getIdentifier(), testPass);
			System.out.println("2. Test with auth but non-existing project");
			result = client.sendRequest("DELETE", mainPath + "projects/1/roles/1", "");
			// since the project should not exist, we expect to get 404
			assertEquals(404, result.getHttpCode());
			System.out.println("Result of 'testDeleteProjectsRoles' with auth but non-existing project: " + result.getResponse().trim());
			System.out.println();
			
			// create a test project with testAgent as creator
			// The rest of the test is not working anymore, since we do not have an OIDC access token
			/*
			User user = new User(testAgent.getEmail(), connection);
		 	String projectName = "ProjectA";
		 	Project project = new Project(user, "{\"name\": \"" + projectName + "\"}");
		 	project.persist(connection);
		 	int projectId = project.getId();
		 	
		 	// try with auth and existing project but non-existing role id
		 	System.out.println("3. Test with auth, existing project but non-existing role id");
			result = client.sendRequest("DELETE", mainPath + "projects/" + projectId + "/roles/100", "");
			// since a role with the id should not exist, we expect to get 404
			assertEquals(404, result.getHttpCode());
			System.out.println("Result of 'testDeleteProjectsRoles' with auth, existing project but non-existing role id: " + result.getResponse().trim());
			System.out.println();
			
			// try to remove the role that is assigned to the testAgent/user
			// this should not work, because then the testAgent would not have a role assigned anymore
			// first: get id of the role thats assigned to the testAgent/user
			int testAgentRoleId = project.getRoleByUser(user).getId();
			// try to remove the role with this id from the project
			System.out.println("4. Test with auth, existing project but role id of a role that is assigned to at least one user");
			result = client.sendRequest("DELETE", mainPath + "projects/" + projectId + "/roles/" + testAgentRoleId, "");
			assertEquals(409, result.getHttpCode());
			System.out.println("Result of 'testDeleteProjectsRoles' with auth, existing project but role id of a role that is assigned to at least one user: " + result.getResponse().trim());
			System.out.println();
			
			// create a second project where the testAgent/user is no member of
		 	User user2 = new User("user2@test.de", "TestUser");
		 	user2.persist(connection);
		 	String projectNameNoMember = "ProjectB";
		 	Project projectNoMember = new Project(user2, "{\"name\": \"" + projectNameNoMember + "\"}");
		 	projectNoMember.persist(connection);
		 	
		 	// try removing a role from a project where testAgent is no member of
		 	System.out.println("5. Test with auth, but user is no member of the project");
		 	// role id should not matter
			result = client.sendRequest("DELETE", mainPath + "projects/" + projectNoMember.getId() + "/roles/1", "");
			assertEquals(403, result.getHttpCode());
			System.out.println("Result of 'testDeleteProjectsRoles' with auth, but user is no member of the project: " + result.getResponse().trim());
			System.out.println();
			
			// remove existing role from existing project that is not assigned to any user
			System.out.println("6. Test with auth, existing project and role that is not assigned to any user");
			// get role that is not assigned
			// therefore, the predefined roles need to have at least 2 roles
			Role roleToRemove = project.getRoles().get(project.getRoles().size()-1);
			result = client.sendRequest("DELETE", mainPath + "projects/" + projectId + "/roles/" + roleToRemove.getId(), "");
			assertEquals(200, result.getHttpCode());
			// check if role got removed 
			// reload project
			Project reloaded = new Project(projectName, connection);
			assertTrue("Tried to remove role, but role still exists in database.", !reloaded.hasRole(roleToRemove.getName()));
			System.out.println("Result of 'testDeleteProjectsRoles' with auth, existing project and role that is not assigned to any user: " + result.getResponse().trim());
			System.out.println();*/
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
	
	/**
	 * Tests the GET method of /users/me.
	 */
	@Test
	public void testGetUsersMe() {
		System.out.println("------------- Starting testGetUsersMe() -------------");
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			
			// first test without auth
			System.out.println("1. Test without auth");
			ClientResponse result = client.sendRequest("GET", mainPath + "users/me", "");
			// without auth this should not be possible
			assertEquals(401, result.getHttpCode());
			System.out.println("Result of 'testGetUsersMe' without auth: " + result.getResponse().trim());
			System.out.println();
			
			// try with auth now
		    client.setLogin(testAgent.getIdentifier(), testPass);
		    System.out.println("2. Test with auth");
			result = client.sendRequest("GET", mainPath + "users/me", "");
			assertEquals(200, result.getHttpCode());
			System.out.println("Result of 'testGetUsersMe' with auth: " + result.getResponse().trim());
			System.out.println();
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
}
