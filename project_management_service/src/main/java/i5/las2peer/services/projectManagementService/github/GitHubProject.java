package i5.las2peer.services.projectManagementService.github;

/**
 * Contains the information about a GitHub project which is connected 
 * to a CAE project.
 * @author Philipp
 *
 */
public class GitHubProject {

	/**
	 * The id of the GitHub project.
	 */
	private int id;
	
	/**
	 * The html of the GitHub project.
	 * This is the url where users can find the project.
	 */
	private String htmlUrl;
	
	public GitHubProject(int id, String htmlUrl) {
		this.id = id;
		this.htmlUrl = htmlUrl;
	}
	
	public int getId() {
		return this.id;
	}
	
	public String getHtmlUrl() {
		return this.htmlUrl;
	}
}
