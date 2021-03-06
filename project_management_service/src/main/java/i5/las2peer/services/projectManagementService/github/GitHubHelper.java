package i5.las2peer.services.projectManagementService.github;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.net.http.*;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse.BodyHandlers;

import org.json.simple.JSONArray;
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
	private GitHubHelper() {}
	
	public static GitHubHelper getInstance() {
		if(GitHubHelper.instance == null) {
			GitHubHelper.instance = new GitHubHelper();
		}
		return GitHubHelper.instance;
	}
	
	public void setGitHubPersonalAccessToken(String gitHubPersonalAccessToken) {
		this.gitHubPersonalAccessToken = gitHubPersonalAccessToken;
	}
	
	public void setGitHubOrganization(String gitHubOrganization) {
		this.gitHubOrganization = gitHubOrganization;
	}
	
	public void setOAuthClientId(String oAuthClientId) {
		this.oAuthClientId = oAuthClientId;
	}
	
	public void setOAuthClientSecret(String oAuthClientSecret) {
		this.oAuthClientSecret = oAuthClientSecret;
	}

	/**
	 * GitHub configuration.
	 * This can be updated in the properties file of the service.
	 */
	private String gitHubPersonalAccessToken = null;
	private String gitHubOrganization = null;
	
	private String oAuthClientId = null;
	private String oAuthClientSecret = null;
	
	/**
	 * Creates a public GitHub project with the given name.
	 * @param projectName Name of the GitHub project which should be created.
	 * @return The newly created GitHubProject object.
	 * @throws GitHubException If something with the requests to the GitHub API went wrong.
	 */
	public GitHubProject createPublicGitHubProject(String projectName) throws GitHubException {
		if(gitHubPersonalAccessToken == null || gitHubOrganization == null) {
			throw new GitHubException("One of the variables personal access token or organization are not set.");
		}
		
		GitHubProject gitHubProject = createGitHubProject(projectName);
		makeGitHubProjectPublic(gitHubProject.getId());
		
		// create some predefined columns
		createProjectColumn(gitHubProject.getId(), "To do");
		createProjectColumn(gitHubProject.getId(), "In progress");
		createProjectColumn(gitHubProject.getId(), "Done");
		
		return gitHubProject;
	}
	
	/**
	 * Gives the GitHub user with the given username access to the given GitHub project.
	 * @param ghUsername Username of the GitHub user which should get access to the project.
	 * @param ghProject GitHubProject object
	 * @throws GitHubException If something with the API request went wrong
	 */
	public void grantUserAccessToProject(String ghUsername, GitHubProject ghProject) throws GitHubException {
		if(ghUsername == null) return;
		
		String authStringEnc = getAuthStringEnc();
		
		URL url;
		try {
			url = new URL(API_BASE_URL + "/projects/" + ghProject.getId() + "/collaborators/" + ghUsername);

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("PUT");
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setRequestProperty("Accept", "application/vnd.github.inertia-preview+json");
			connection.setRequestProperty("Authorization", "Basic " + authStringEnc);
			
			// forward (in case of) error
			if (connection.getResponseCode() != 204) {
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
	 * Deletes the given GitHub project.
	 * @param ghProject GitHub project which should be deleted.
	 * @throws GitHubException If something with the request to the GitHub API went wrong.
	 */
	public void deleteGitHubProject(GitHubProject ghProject) throws GitHubException {
        String authStringEnc = getAuthStringEnc();
		
		URL url;
		try {
			url = new URL(API_BASE_URL + "/projects/" + ghProject.getId());

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("DELETE");
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setRequestProperty("Accept", "application/vnd.github.inertia-preview+json");
			connection.setRequestProperty("Authorization", "Basic " + authStringEnc);
			
			// forward (in case of) error
			if (connection.getResponseCode() != 204) {
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
	 * Checks if the repository with the given URL exists.
	 * Note: To work correct, the GitHub URL which is given, needs to be correct.
	 * @param gitHubURL GitHub URL to a repository.
	 * @return
	 * @throws GitHubException
	 */
	public boolean repoExists(String gitHubURL) throws GitHubException {
		URL url;
		try {
			url = new URL(gitHubURL);
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.connect();
			
			return connection.getResponseCode() == HttpURLConnection.HTTP_OK;
		} catch (MalformedURLException e) {
			e.printStackTrace();
			throw new GitHubException(e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			throw new GitHubException(e.getMessage());
		}
	}
	
	/**
	 * Returns an ArrayList containing the version tags of the given repository as strings.
	 * @param repoOwner Owner/account on GitHub where the repository is hosted.
	 * @param repoName Name of the GitHub repository.
	 * @return ArrayList containing the version tags of the repository as strings.
	 * @throws GitHubException If something with the API request went wrong.
	 */
	public ArrayList<String> getRepoVersionTags(String repoOwner, String repoName) throws GitHubException {
		ArrayList<String> tags = new ArrayList<>();
		URL url;
		try {
			url = new URL(API_BASE_URL + "/repos/" + repoOwner + "/" + repoName + "/tags");
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setDoInput(true);
			connection.connect();
			
			// forward (in case of) error
			if (connection.getResponseCode() != 200) {
				String message = getErrorMessage(connection);
				throw new GitHubException(message);
			} else {
				// get response
				String response = getResponseBody(connection);
							
				// convert to JSONObject
				JSONArray json = (JSONArray) JSONValue.parseWithException(response);
				for(Object o : json) {
					JSONObject tag = (JSONObject) o;
					tags.add((String) tag.get("name"));
				}
				return tags;
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
	 * Requests the user's GitHub access token by using the given code.
	 * @param code
	 * @return GitHub access token.
	 * @throws GitHubException If something with the request to GitHub API went wrong.
	 */
	public String getUserAccessToken(String code) throws GitHubException {
		URL url;
		try {
			url = new URL("https://github.com/login/oauth/access_token");
			
			String body = this.getOAuthBody(code);
			
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setDoInput(true);
			connection.setDoOutput(true);
			connection.setUseCaches(false);
			connection.setRequestProperty("Accept", "application/json"); // otherwise we dont get json result
			connection.setRequestProperty("Content-Type", "application/json");
			connection.setRequestProperty("Content-Length", String.valueOf(body.length()));
			
			writeRequestBody(connection, body);
			
			// forward (in case of) error
			if (connection.getResponseCode() != 200) {
				String message = getErrorMessage(connection);
				throw new GitHubException(message);
			} else {
				// get response
				String response = getResponseBody(connection);
			    return response;
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
	 * Requests the username of the GitHub account by using the given access token.
	 * @param accessToken GitHub access token.
	 * @return Username of GitHub account.
	 * @throws GitHubException If something with the request to GitHub API went wrong.
	 */
	public String getGitHubUsername(String accessToken) throws GitHubException {
		URL url;
		try {
			url = new URL(API_BASE_URL + "/user");
			
			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("GET");
			connection.setDoInput(true);
			connection.setRequestProperty("Authorization", "token " + accessToken);
			
			// forward (in case of) error
			if (connection.getResponseCode() != 200) {
				String message = getErrorMessage(connection);
				throw new GitHubException(message);
			} else {
				// get response
				String response = getResponseBody(connection);
			    return response;
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
	 * Creates a GitHub project in the GitHub organization given by the properties file.
	 * @param projectName Name of the GitHub project.
	 * @return The newly created GitHubProject object.
	 * @throws GitHubException If something with creating the new project went wrong.
	 */
	private GitHubProject createGitHubProject(String projectName) throws GitHubException {
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
				String gitHubProjectHtmlUrl = (String) json.get("html_url");
				return new GitHubProject(gitHubProjectId, gitHubProjectHtmlUrl);
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
		
		String url = API_BASE_URL + "/projects/" + gitHubProjectId;
		
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .method("PATCH", BodyPublishers.ofString(body))
                .header("Content-Type", "application/json")
                .header("Accept", "application/vnd.github.inertia-preview+json")
                .header("Authorization", "Basic " + authStringEnc)
                .build();
		
		HttpResponse<String> response;
		try {
			response = client.send(request, BodyHandlers.ofString());
			if(response.statusCode() != 200) {
				String message = response.body();
				throw new GitHubException(message);
			}
		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
			throw new GitHubException(e.getMessage());
		}
	}
	
	/**
	 * Creates a new column with the given name in the GitHub project with the given id.
	 * @param gitHubProjectId Id of the GitHub project, where the column should be added to.
	 * @param columnName Name of the column, which should be created.
	 * @throws GitHubException If something with the request to the GitHub API went wrong.
	 */
	private void createProjectColumn(int gitHubProjectId, String columnName) throws GitHubException {
		String body = getCreateColumnBody(columnName);
		String authStringEnc = getAuthStringEnc();

		URL url;
		try {
			url = new URL(API_BASE_URL + "/projects/" + gitHubProjectId + "/columns");

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
			}
		} catch (MalformedURLException e) {
			e.printStackTrace();
			throw new GitHubException(e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			throw new GitHubException(e.getMessage());
		}
	}
	
	private String getOAuthBody(String code) {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("client_id", this.oAuthClientId);
		jsonObject.put("client_secret", this.oAuthClientSecret);
		jsonObject.put("code", code);
		return jsonObject.toJSONString();
	}
	
	/**
	 * Creates the body needed to create a new column in a GitHub project.
	 * @param columnName Name of the column that should be created.
	 * @return Body as string.
	 */
	private String getCreateColumnBody(String columnName) {
		JSONObject jsonObject = new JSONObject();
		jsonObject.put("name", columnName);
		String body = JSONObject.toJSONString(jsonObject);
		return body;
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
	 * @return Encoded auth string containing GitHub personal access token.
	 */
	private String getAuthStringEnc() {
		String authString = this.gitHubPersonalAccessToken;

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
	
}
