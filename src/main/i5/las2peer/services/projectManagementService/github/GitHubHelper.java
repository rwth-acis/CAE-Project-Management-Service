package i5.las2peer.services.projectManagementService.github;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Set;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import i5.las2peer.services.projectManagementService.exception.GitHubException;

/**
 * Helper class for working with GitHub API.
 * Currently supports creating new GitHub projects and update their
 * visibility to public.
 * @author Philipp
 *
 */
public class GitHubHelper {
	
	private static GitHubHelper instance;
	private static final String API_BASE_URL = "https://api.github.com";
	
	// make sure that constructor cannot be accessed from outside
	private GitHubHelper() {
		allowPatchMethod();
	}
	
	public static GitHubHelper getInstance() {
		if(GitHubHelper.instance == null) {
			GitHubHelper.instance = new GitHubHelper();
		}
		return GitHubHelper.instance;
	}
	
	public void setGitHubUser(String gitHubUser) {
		this.gitHubUser = gitHubUser;
	}
	
	public void setGitHubPassword(String gitHubPassword) {
		this.gitHubPassword = gitHubPassword;
	}
	
	public void setGitHubOrganization(String gitHubOrganization) {
		this.gitHubOrganization = gitHubOrganization;
	}

	/**
	 * GitHub configuration.
	 * This can be updated in the properties file of the service.
	 */
	private String gitHubUser = null;
	private String gitHubPassword = null;
	private String gitHubOrganization = null;
	
	/**
	 * Creates a public GitHub project with the given name.
	 * @param projectName Name of the GitHub project which should be created.
	 * @return Id of the newly created GitHub project.
	 * @throws GitHubException If something with the requests to the GitHub API went wrong.
	 */
	public int createPublicGitHubProject(String projectName) throws GitHubException {
		if(gitHubUser == null || gitHubPassword == null || gitHubOrganization == null) {
			throw new GitHubException("One of the variables user, password or organization are not set.");
		}
		
		int gitHubProjectId = createGitHubProject(projectName);
		makeGitHubProjectPublic(gitHubProjectId);
		return gitHubProjectId;
	}

	/**
	 * Creates a GitHub project in the GitHub organization given by the properties file.
	 * @param projectName Name of the GitHub project.
	 * @return Id of the newly created GitHub project.
	 * @throws GitHubException If something with creating the new project went wrong.
	 */
	private int createGitHubProject(String projectName) throws GitHubException {
		String body = getGitHubProjectBody(projectName);
		String authStringEnc = getAuthStringEnc();

		URL url;
		try {
			url = new URL(API_BASE_URL + "/orgs/" + this.gitHubOrganization + "/projects");

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setRequestProperty("Accept", "application/vnd.github.inertia-preview+json");
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("Content-Length", String.valueOf(body.length()));
			connection.setRequestProperty("Authorization", "Basic " + authStringEnc);

			writeRequestBody(connection, body);
			
			// forward (in case of) error
			if (connection.getResponseCode() != 201) {
				String message = getErrorMessage(connection);
				throw new GitHubException(message);
			} else {
				// get response
				String response = getResponseBody(connection);
				
				// convert to JSONObject
				JSONObject json = (JSONObject) JSONValue.parseWithException(response);
				int gitHubProjectId = ((Long) json.get("id")).intValue();
				return gitHubProjectId;
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
			throw new GitHubException(e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			throw new GitHubException(e.getMessage());
		} catch (ParseException e) {
			e.printStackTrace();
			throw new GitHubException(e.getMessage());
		}
	}
	
	/**
	 * Changes the visibility of the GitHub project with the given id to "private: false".
	 * After calling this method, the GitHub project should be public and can be accessed by 
	 * every GitHub user (accessed only means read-access).
	 * @param gitHubProjectId Id of the GitHub project id, whose visibility should be updated.
	 * @throws GitHubException If something with the request to the GitHub API went wrong.
	 */
	private void makeGitHubProjectPublic(int gitHubProjectId) throws GitHubException {
		String body = getVisibilityPublicBody();
		String authStringEnc = getAuthStringEnc();

		URL url;
		try {
			url = new URL(API_BASE_URL + "/projects/" + gitHubProjectId);

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("PATCH");
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setRequestProperty("Accept", "application/vnd.github.inertia-preview+json");
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("Content-Length", String.valueOf(body.length()));
			connection.setRequestProperty("Authorization", "Basic " + authStringEnc);

			writeRequestBody(connection, body);
			
			// forward (in case of) error
		    if (connection.getResponseCode() != 200) {
			    String message = getErrorMessage(connection);
				throw new GitHubException(message);
		    }
		} catch (MalformedURLException e) {
			e.printStackTrace();
			throw new GitHubException(e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			throw new GitHubException(e.getMessage());
		}
	}
	
	/**
	 * Creates the body needed to update the visibility of the GitHub project.
	 * @return Body as String.
	 */
	private String getVisibilityPublicBody() {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("private", false);
		String body = JSONObject.toJSONString(jsonObject);
		return body;
	}
	
	/**
	 * Creates the body needed for creating a new GitHub project.
	 * @param projectName Name of the project that should be created on GitHub.
	 * @return Body containing the information about the GitHub project which will be created.
	 */
	private String getGitHubProjectBody(String projectName) {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("name", projectName);
		jsonObject.put("body", "This GitHub project was auto-generated by the CAE.");
		String body = JSONObject.toJSONString(jsonObject);
		return body;
	}
	
	/**
	 * Getter for encoded auth string.
	 * @return Encoded auth string containing GitHub user and password.
	 */
	private String getAuthStringEnc() {
		String authString = this.gitHubUser + ":" + this.gitHubPassword;

		byte[] authEncBytes = Base64.getEncoder().encode(authString.getBytes());
		return new String(authEncBytes);
	}
	
	/**
	 * Extracts the error message from the response.
	 * @param connection HttpURLConnection object
	 * @return Error message as String.
	 * @throws IOException
	 */
	private String getErrorMessage(HttpURLConnection connection) throws IOException {
		String message = "Error creating GitHub project at: ";
		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
		for (String line; (line = reader.readLine()) != null;) {
			message += line;
		}
		reader.close();
		return message;
	}
	
	/**
	 * Getter for the body of the response.
	 * @param connection HttpURLConnection object
	 * @return Body of the response as string.
	 * @throws IOException
	 */
	private String getResponseBody(HttpURLConnection connection) throws IOException {
		String response = "";
		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		for (String line; (line = reader.readLine()) != null;) {
			response += line;
		}
		reader.close();
		return response;
	}
	
	/**
	 * Writes the request body.
	 * @param connection HttpURLConnection object
	 * @param body Body that should be written to the request.
	 * @throws IOException
	 */
	private void writeRequestBody(HttpURLConnection connection, String body) throws IOException {
		OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
		writer.write(body);
		writer.flush();
		writer.close();
	}
	
	/**
	 * This is a workaround for the following problem:
	 * In order to make the GitHub project public, it is necessary to 
	 * send a PATCH request. This is normally not working when using 
	 * HttpURLConnection. Since we dont want to use an external library for 
	 * it, we make use of this workaround found on Stackoverflow:
	 * https://stackoverflow.com/questions/25163131/httpurlconnection-invalid-http-method-patch/46323891#46323891
	 * Then also PATCH requests are working.
	 * @param methods
	 */
	private static void allowPatchMethod() {
        try {
            Field methodsField = HttpURLConnection.class.getDeclaredField("methods");

            Field modifiersField = Field.class.getDeclaredField("modifiers");
            modifiersField.setAccessible(true);
            modifiersField.setInt(methodsField, methodsField.getModifiers() & ~Modifier.FINAL);

            methodsField.setAccessible(true);

            String[] oldMethods = (String[]) methodsField.get(null);
            Set<String> methodsSet = new LinkedHashSet<>(Arrays.asList(oldMethods));
            methodsSet.add("PATCH");
            String[] newMethods = methodsSet.toArray(new String[0]);

            methodsField.set(null, newMethods);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
	
}
