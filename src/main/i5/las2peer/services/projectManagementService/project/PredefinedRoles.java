package i5.las2peer.services.projectManagementService.project;

import java.util.ArrayList;

/**
 * Helper class for creating Role objects for the predefined roles
 * that every project gets initially when creating it.
 * @author Philipp
 *
 */
public class PredefinedRoles {

	/**
	 * Gets the list of predefined roles every project gets when creating it.
	 * @param projectId Id of the project where the roles should be added to (later).
	 * @return ArrayList containing Role objects for every predefined role.
	 */
	public static ArrayList<Role> get(int projectId) {
		ArrayList<Role> predefinedRoles = new ArrayList<>();
		
		Role frontendModeler = new Role(projectId, "Frontend Modeler", true); // default role
		Role applicationModeler = new Role(projectId, "Application Modeler", false);
		Role backendModeler = new Role(projectId, "Backend Modeler", false);
		Role softwareEngineer = new Role(projectId, "Software Engineer", false);
		
		predefinedRoles.add(frontendModeler);
		predefinedRoles.add(applicationModeler);
		predefinedRoles.add(backendModeler);
		predefinedRoles.add(softwareEngineer);
		
		return predefinedRoles;
	}
	
}
