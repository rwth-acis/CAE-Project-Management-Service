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
	 * View 1 allows to view the following widgets:
	 * - Wireframing
	 * - Frontend Modeling
	 * - Live Preview
	 * - Versioning of frontend
	 * Besides that, also the full menu is available:
	 * - Requirements Bazaar widget
	 * - GitHub projects widget
	 * - Versioning of mashup
	 * - Deployment
	 * - Matching
	 */
    public static final String VIEW_1 = "{\"Frontend Modeling\":{\"widgets\":{\"Wireframe\":{\"enabled\":true},\"Modeling\":{\"enabled\":true},\"Code Editor\":{\"enabled\":false},\"Versioning\":{\"enabled\":true},\"Live Preview\":{\"enabled\":true}}},\"Microservice Modeling\":{\"widgets\":{\"Modeling\":{\"enabled\":false},\"Swagger Editor\":{\"enabled\":false},\"Code Editor\":{\"enabled\":false},\"Versioning\":{\"enabled\":false}}},\"Application Mashup\":{\"widgets\":{\"Modeling incl. Select\":{\"enabled\":false},\"Deployment\":{\"enabled\":false},\"Versioning\":{\"enabled\":false},\"Matching\":{\"enabled\":false}}}}";
    
    /**
     * View 2 contains View 1.
     * Besides that it allows to view the following widgets:
     * - Application Modeling incl. Select
     * - 
     */
    public static final String VIEW_2 = "{\"Frontend Modeling\":{\"widgets\":{\"Wireframe\":{\"enabled\":true},\"Modeling\":{\"enabled\":true},\"Code Editor\":{\"enabled\":false},\"Versioning\":{\"enabled\":true},\"Live Preview\":{\"enabled\":true}}},\"Microservice Modeling\":{\"widgets\":{\"Modeling\":{\"enabled\":false},\"Swagger Editor\":{\"enabled\":false},\"Code Editor\":{\"enabled\":false},\"Versioning\":{\"enabled\":false}}},\"Application Mashup\":{\"widgets\":{\"Modeling incl. Select\":{\"enabled\":true},\"Deployment\":{\"enabled\":true},\"Versioning\":{\"enabled\":true},\"Matching\":{\"enabled\":true}}}}";
    
    /**
     * View 3 contains View 2.
     * Besides that it allows to view the following widgets:
     * - Backend Modeling
     * - Swagger Editor
     * - Versioning of backend
     */
    public static final String VIEW_3 = "{\"Frontend Modeling\":{\"widgets\":{\"Wireframe\":{\"enabled\":true},\"Modeling\":{\"enabled\":true},\"Code Editor\":{\"enabled\":false},\"Versioning\":{\"enabled\":true},\"Live Preview\":{\"enabled\":true}}},\"Microservice Modeling\":{\"widgets\":{\"Modeling\":{\"enabled\":true},\"Swagger Editor\":{\"enabled\":true},\"Code Editor\":{\"enabled\":false},\"Versioning\":{\"enabled\":true}}},\"Application Mashup\":{\"widgets\":{\"Modeling incl. Select\":{\"enabled\":true},\"Deployment\":{\"enabled\":true},\"Versioning\":{\"enabled\":true},\"Matching\":{\"enabled\":true}}}}";
    
    /**
     * View 4 includes every widget.
     * Beside the ones from View 3, it contains:
     * - Live Code Editor of frontend and backend
     */
    public static final String VIEW_4 = VIEW_ALL;
	
	/**
	 * Gets the list of predefined roles every project gets when creating it.
	 * @param projectId Id of the project where the roles should be added to (later).
	 * @return ArrayList containing Role objects for every predefined role.
	 */
	public static ArrayList<Role> get(int projectId) {
		ArrayList<Role> predefinedRoles = new ArrayList<>();
		
		Role frontendModeler = new Role(projectId, "Frontend Modeler", VIEW_1, true); // default role
		Role applicationModeler = new Role(projectId, "Application Modeler", VIEW_2, false);
		Role backendModeler = new Role(projectId, "Backend Modeler", VIEW_3, false);
		Role softwareEngineer = new Role(projectId, "Software Engineer", VIEW_4, false);
		
		predefinedRoles.add(frontendModeler);
		predefinedRoles.add(applicationModeler);
		predefinedRoles.add(backendModeler);
		predefinedRoles.add(softwareEngineer);
		
		return predefinedRoles;
	}
	
}
