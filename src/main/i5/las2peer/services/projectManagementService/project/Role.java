package i5.las2peer.services.projectManagementService.project;

/**
 * (Data-)Class for Roles. Provides means to convert JSON to Object and Object
 * to JSON. Also provides means to persist the object to a database.
 * TODO: check if this javadoc is still correct later
 */
public class Role {

	/**
	 * Id of the role.
	 * Initially set to -1 if role is not persisted yet.
	 */
	private int id = -1;
	
	/**
	 * Id of the project that the role belongs to.
	 */
	private int projectId;
	
	/**
	 * Name of the role.
	 */
	private String name;
	
	
	public int getId() {
		return this.id;
	}
	
	public int getProjectId() {
		return this.projectId;
	}
	
	public String getName() {
		return this.name;
	}
	
}
