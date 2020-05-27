package i5.las2peer.services.projectManagementService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Properties;

import javax.ws.rs.core.MediaType;

import org.json.simple.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
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
import i5.las2peer.services.projectManagementService.project.Project;
import i5.las2peer.testing.MockAgentFactory;

/**
 * Test class for the CAE Project Management Service. Only tests on a REST level.
 */
public class ServiceTest {

	/**
	 * Database connection used to access database during the tests.
	 */
	private static Connection connection;
	
	private static final String HTTP_ADDRESS = "http://127.0.0.1";
	private static final int HTTP_PORT = WebConnector.DEFAULT_HTTP_PORT;

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
	
	
	
	@Test
	public void testProjectPosting() {
		System.out.println("------------- Starting testProjectPosting() -------------");
		try {
			MiniClient client = new MiniClient();
			client.setConnectorEndpoint(connector.getHttpEndpoint());
			
			// first test without auth
			System.out.println("1. Test without auth");
			ClientResponse result = client.sendRequest("POST", mainPath + "projects", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
			assertEquals(401, result.getHttpCode());
			System.out.println("Result of 'testProjectPosting' without auth: " + result.getResponse().trim());
			System.out.println();
			
			// test with auth and empty body
			client.setLogin(testAgent.getIdentifier(), testPass);
			System.out.println("2. Test with auth and empty body");
		    result = client.sendRequest("POST", mainPath + "projects", "", MediaType.APPLICATION_JSON, "", new HashMap<>());
		    assertEquals(400, result.getHttpCode());
		    System.out.println("Result of 'testProjectPosting' with auth and empty body: " + result.getResponse().trim());
		    System.out.println();
			
			// test with auth and body containing an empty JSON object
		    System.out.println("3. Test with auth and body containing an empty JSON object");
		    result = client.sendRequest("POST", mainPath + "projects", "{}", MediaType.APPLICATION_JSON, "", new HashMap<>());
		    assertEquals(400, result.getHttpCode());
		    System.out.println("Result of 'testProjectPosting' with auth and body containing an empty JSON object: " + result.getResponse().trim());
		    System.out.println();
		    
		    // test with auth and correct body
		    String projectName = "Project A";
		    System.out.println("4. Test with auth and correct body");
		    result = client.sendRequest("POST", mainPath + "projects", "{\"name\": \"" + projectName + "\"}", MediaType.APPLICATION_JSON, "", new HashMap<>());
		    assertEquals(201, result.getHttpCode());
		    System.out.println("Result of 'testProjectPosting' with auth and correct body: " + result.getResponse().trim());
		    System.out.println();
		    
		    // check if project got really created and can be found in the database now
		    // if the project cannot be found, then a ProjectNotFoundException will be thrown
		    Project project = new Project(projectName, connection);
		    
		    // test with auth and same project name as before
		    // this should not work, since two projects with the same name are not allowed
		    System.out.println("5. Test with auth and same project name as before");
		    result = client.sendRequest("POST", mainPath + "projects", "{\"name\": \"" + projectName + "\"}", MediaType.APPLICATION_JSON, "", new HashMap<>());
		    assertEquals(409, result.getHttpCode());
		    System.out.println("Result of 'testProjectPosting' with auth and same project name as before: " + result.getResponse().trim());
		    System.out.println();
		} catch(ProjectNotFoundException e) {
			e.printStackTrace();
			fail("Tried to create a new project, but project cannot be found in the database afterwards.");
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
}
