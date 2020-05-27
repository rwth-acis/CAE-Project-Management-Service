package i5.las2peer.services.projectManagementService;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class DatabaseHelper {
	
	private static String[] TABLE_NAMES = {"Project", "ProjectToUser", "Role", "User", "UserToRole"};

	/**
	 * Tries to clear every table of the database.
	 * @param connection Connection object
	 * @return True, if database got cleared. False, if something went wrong.
	 */
	public static boolean clearDatabase(Connection connection) {
		PreparedStatement statement;
		try {
			for(String tableName : TABLE_NAMES) {
				statement = connection.prepareStatement("DELETE FROM " + tableName + ";");
				statement.executeUpdate();
			}
			return true;
		} catch (SQLException e) {
			e.printStackTrace();
		}
		return false;
	}
	
}
