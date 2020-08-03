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
	 * Widget config which allows to view every widget.
	 */
	public static final String VIEW_ALL = "{\"Frontend Modeling\":{\"widgets\":{\"Wireframe\":{\"enabled\":true},\"Modeling\":{\"enabled\":true},\"Code Editor\":{\"enabled\":true},\"Versioning\":{\"enabled\":true},\"Live Preview\":{\"enabled\":true}}},\"Microservice Modeling\":{\"widgets\":{\"Modeling\":{\"enabled\":true},\"Swagger Editor\":{\"enabled\":true},\"Code Editor\":{\"enabled\":true},\"Versioning\":{\"enabled\":true}}},\"Application Mashup\":{\"widgets\":{\"Modeling incl. Select\":{\"enabled\":true},\"Deployment\":{\"enabled\":true},\"Versioning\":{\"enabled\":true},\"Matching\":{\"enabled\":true}}}}";

	/**
	 * Gets the list of predefined roles every project gets when creating it.
	 * @param projectId Id of the project where the roles should be added to (later).
	 * @return ArrayList containing Role objects for every predefined role.
	 */
	public static ArrayList<Role> get(int projectId) {
		ArrayList<Role> predefinedRoles = new ArrayList<>();
		
		Role frontendModeler = new Role(projectId, "Frontend Modeler", VIEW_ALL, true); // default role
		Role applicationModeler = new Role(projectId, "Application Modeler", VIEW_ALL, false);
		Role backendModeler = new Role(projectId, "Backend Modeler", VIEW_ALL, false);
		Role softwareEngineer = new Role(projectId, "Software Engineer", VIEW_ALL, false);
		
		predefinedRoles.add(frontendModeler);
		predefinedRoles.add(applicationModeler);
		predefinedRoles.add(backendModeler);
		predefinedRoles.add(softwareEngineer);
		
		return predefinedRoles;
	}
	
}
