package com.uptimesoftware.uptime.plugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

import oracle.jdbc.pool.OracleDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ro.fortsoft.pf4j.PluginWrapper;

import com.uptimesoftware.uptime.plugin.api.Extension;
import com.uptimesoftware.uptime.plugin.api.Plugin;
import com.uptimesoftware.uptime.plugin.api.PluginMonitor;
import com.uptimesoftware.uptime.plugin.monitor.MonitorState;
import com.uptimesoftware.uptime.plugin.monitor.Parameters;

/**
 * Oracle Basic (with retained data) Monitor.
 * 
 * The plugin provides same functionality as the built-in Oracle Basic monitor but stores the query results in a numeric
 * result for thresholding or comparison.
 * 
 * @author uptime software
 */
public class MonitorOracleQuery extends Plugin {

	/**
	 * Constructor - a plugin wrapper.
	 * 
	 * @param wrapper
	 */
	public MonitorOracleQuery(PluginWrapper wrapper) {
		super(wrapper);
	}

	/**
	 * A nested static class which has to extend PluginMonitor.
	 * 
	 * Functions that require implementation :
	 * 1) The monitor function will implement the main functionality and should set the monitor's state and result
	 * message prior to completion.
	 * 2) The setParameters function will accept a Parameters object containing the values filled into the monitor's
	 * configuration page in Up.time.
	 */
	@Extension
	public static class UptimeMonitorOracleQuery extends PluginMonitor {
		// Logger object.
		private static final Logger LOGGER = LoggerFactory.getLogger(UptimeMonitorOracleQuery.class);

		// Constants
		private final static int TIMEOUT_SECONDS = 60;
		private final static String THIN_DRIVER = "thin";
		private final static int OUTPUT_TYPE_LONG = 0;
		private final static int OUTPUT_TYPE_DOUBLE = 1;
		private final static int OUTPUT_TYPE_STRING = 2;

		// Store numeric value here if the output String contains numeric value.
		private long longValue = 0;
		private double doubleValue = 0;
		// Number of row in a result set.
		private int rowCounter = 0;

		// See definition in .xml file for plugin. Each plugin has different number of input/output parameters.
		// [Input]
		String hostname;
		int port;
		String username;
		String password;
		String sid;
		String sqlQuery;

		/**
		 * The setParameters function will accept a Parameters object containing the values filled into the monitor's
		 * configuration page in Up.time.
		 * 
		 * @param params
		 *            Parameters object which contains inputs.
		 */
		@Override
		public void setParameters(Parameters params) {
			LOGGER.debug("Step 1 : Setting parameters.");
			// [Input]
			hostname = params.getString("hostname");
			port = params.getInt("port");
			username = params.getString("username");
			password = params.getString("password");
			sid = params.getString("sid");
			sqlQuery = params.getString("sqlQuery");

			// Get rid of semicolons at the end of sqlQuery String if there is. JDBC won't recognize semicolon at the
			// end of query String.
			deleteSemicolonAtTheEnd(sqlQuery);
		}

		/**
		 * The monitor function will implement the main functionality and should set the monitor's state and result
		 * message prior to completion.
		 */
		@Override
		public void monitor() {
			LOGGER.debug("Step 2 : Connect to the database with the given parameters.");
			Connection connection = getRemoteConnection(THIN_DRIVER, hostname, port, sid, username, password,
					TIMEOUT_SECONDS);

			LOGGER.debug("Error handling 1 : If connecting fails, change monitor state to CRIT and set an error message.");
			if (connection == null) {
				setStateAndMessage(MonitorState.CRIT, "Could not connect to database, check monitor settings.");
				// connection is null. Plugin should stop here.
				return;
			}

			LOGGER.debug("Step 3 : Create a PreparedStatement for sending parameterized SQL statements to the database.");
			PreparedStatement preparedStatement = prepareStatement(connection, sqlQuery);

			LOGGER.debug("Error handling 2 : If creating statement fails, set monitor state CRIT and set an error message.");
			if (preparedStatement == null) {
				setStateAndMessage(MonitorState.CRIT, "Could not get prepared statement, check connection object.");
				// preparedStatement is null. Plugin should stop here.
				return;
			}

			LOGGER.debug("Step 4 : Preparing statement was successful. Execute the prepared statement and get result set.");
			ResultSet resultSet = getResultSet(preparedStatement);

			LOGGER.debug("Error handling 3 : If getting result set fails, set monitor state CRIT and set an error message.");
			// Although executeQuery() never returns null according to JDBC API, just making sure.
			if (resultSet == null) {
				setStateAndMessage(MonitorState.CRIT, "Could not get result set, check preparedStatement object.");
				// resultSet is null. Plugin should stop here.
				return;
			}

			LOGGER.debug("Step 5 : Getting a result set was successful. Extract result from the result set and set output.");
			String output = extractFromResultSet(resultSet).trim();
			switch (isLongDoubleOrText(output)) {
			case OUTPUT_TYPE_LONG:
				addVariable("numberoutput", longValue);
				break;
			case OUTPUT_TYPE_DOUBLE:
				addVariable("numberoutput", doubleValue);
				break;
			case OUTPUT_TYPE_STRING:
				addVariable("textoutput", output);
				break;
			}
			// set number of row in result set to output.
			addVariable("rowCounter", rowCounter);

			LOGGER.debug("Step 6 : close all (connection, preparedStatement, resultSet).");
			closeAll(connection, preparedStatement, resultSet);

			LOGGER.debug("Step 7 : Everything ran okay. Set monitor state to OK");
			setStateAndMessage(MonitorState.OK, "Monitor successfully ran.");
		}

		/**
		 * Private helper method to delete semicolon at the end of SQL query String.
		 * 
		 * @param sqlQuery
		 *            query to run.
		 */
		private void deleteSemicolonAtTheEnd(String sqlQuery) {
			int sqlQueryLength = sqlQuery.length();
			// If the char at the last position of sqlQuery String is semicolon and the length is bigger than 1, then
			// get rid of semicolon at the end of sqlQuery String.
			if ((sqlQuery.charAt(sqlQueryLength - 1) == ';') && (sqlQueryLength >= 2)) {
				LOGGER.debug("The sqlQuery String contains semicolon(s) at the end. Deleting selemicolon(s).");
				this.sqlQuery = sqlQuery.substring(0, sqlQuery.length() - 1).trim();
				// Recursion : run it until there is no semicolon(s) at the end of sqlQuery String.
				deleteSemicolonAtTheEnd(this.sqlQuery);
			}
		}

		/**
		 * Private helper function to get database connection.
		 * 
		 * @param driverType
		 *            DB Type. (thin, oci, kprb)
		 * @param hostname
		 *            Name of the host.
		 * @param port
		 *            Port number.
		 * @param sid
		 *            Service ID
		 * @param username
		 *            Name of user.
		 * @param password
		 *            Password
		 * @param timeout
		 *            Timeout in seconds
		 * @return Database connection object.
		 */
		private Connection getRemoteConnection(String driverType, String hostname, int port, String sid,
				String username, String password, int timeout) {
			Connection connection = null;
			try {
				OracleDataSource dataSource = new OracleDataSource();
				dataSource.setDriverType(driverType);
				dataSource.setServerName(hostname);
				dataSource.setPortNumber(port);
				dataSource.setServiceName(sid);
				dataSource.setUser(username);
				dataSource.setPassword(password);
				dataSource.setLoginTimeout(timeout);
				// Get connection with the database.
				connection = dataSource.getConnection();
				LOGGER.debug("Make sure connection is still open before moving on.");
				if (connection.isClosed()) {
					setStateAndMessage(MonitorState.CRIT, "Connection is closed.");
				}
			} catch (SQLException e) {
				LOGGER.error("Error getting remote connection.", e);
			}
			return connection;
		}

		/**
		 * Private helper function to get PreparedStatement with given Connection and SQL query.
		 * 
		 * @param connection
		 *            Connection object of a database
		 * @param script
		 *            SQL query to run
		 * @return PreparedStatement object containing the pre-compiled SQL statement.
		 */
		private PreparedStatement prepareStatement(Connection connection, String sqlScript) {
			PreparedStatement preparedStatement = null;
			try {
				preparedStatement = connection.prepareStatement(sqlScript);
			} catch (SQLException e) {
				LOGGER.error("Error while creating PreparedStatement failed : ", e);
			}
			return preparedStatement;
		}

		/**
		 * Private helper function to execute prepared statement and get result set.
		 * 
		 * @param preparedStatement
		 *            PreparedStatement object containing the pre-compiled SQL statement
		 * @return Result set after executing prepared statement. (executeQuery() never returns null according to JDBC
		 *         API)
		 */
		private ResultSet getResultSet(PreparedStatement preparedStatement) {
			ResultSet resultSet = null;
			try {
				resultSet = preparedStatement.executeQuery();
			} catch (SQLException e) {
				LOGGER.error("Error while executing prepared statement : ", e);
			}
			return resultSet;
		}

		/**
		 * Private helper function to extract String result from the given ResultSet.
		 * 
		 * @param rs
		 *            ResultSet object
		 * @return Extracted String result.
		 */
		private String extractFromResultSet(ResultSet rs) {
			StringBuilder extractedStringResult = new StringBuilder();
			try {
				// An object that can be used to get information about the types and properties of the columns in a
				// ResultSet object.
				ResultSetMetaData meta = rs.getMetaData();
				int columnCount = meta.getColumnCount();
				while (rs.next()) {
					rowCounter++;
					extractedStringResult.append(getRowAsString(rs, columnCount));
					extractedStringResult.append(System.lineSeparator());
				}
			} catch (SQLException e) {
				LOGGER.error("Error while extracting results from the given ResultSet : ", e);
			}

			return extractedStringResult.toString().trim();
		}

		/**
		 * Private helper function to build one String result instead of making a Map<Key, Value>.
		 * 
		 * @param rs
		 *            ResultSet object
		 * @param columnCount
		 *            Number of column count in the ResultSet object.
		 * @return One String object which contains all rows of the ResultSet. (each rows are divided by a space)
		 */
		private String getRowAsString(ResultSet rs, int columnCount) {
			StringBuilder rowString = new StringBuilder();
			// 1-based index
			for (int i = 1; i <= columnCount; i++) {
				try {
					rowString.append(rs.getString(i));
				} catch (SQLException e) {
					LOGGER.error(
							"Error while building one raw String result. Problem occurred when getting String from column number ["
									+ i + "]", e);
				}
				// Insert a space between each columns.
				rowString.append(" ");
			}
			// Return one raw String result.
			return rowString.toString();
		}

		/**
		 * Private helper function to check if the String result contains Long/Double numeric value or not.
		 * 
		 * @param stringResult
		 *            String result
		 * @return 0 if Long, 1 if Double, 2 if non-numeric String
		 */
		private int isLongDoubleOrText(String stringResult) {
			// 0 if Long, 1 if Double, 2 if non-numeric String
			try {
				if (stringResult.contains(".")) {
					doubleValue = Double.parseDouble(stringResult);
					return OUTPUT_TYPE_DOUBLE;
				} else {
					longValue = Long.parseLong(stringResult);
					return OUTPUT_TYPE_LONG;
				}
			} catch (NumberFormatException e) {
				// Do Nothing.
			}
			return OUTPUT_TYPE_STRING;
		}

		/**
		 * Private helper function to close all if they're open.
		 * 
		 * @param connection
		 *            Connection object
		 * @param preparedStatement
		 *            PreparedStatement object
		 * @param resultSet
		 *            ResultSet object
		 */
		private void closeAll(Connection connection, PreparedStatement preparedStatement, ResultSet resultSet) {
			// There are better ways to do it. Fix it later.
			try {
				if (!resultSet.isClosed()) {
					resultSet.close();
				}
				if (!preparedStatement.isClosed()) {
					preparedStatement.close();
				}
				if (!connection.isClosed()) {
					connection.close();
				}
			} catch (SQLException e) {
				LOGGER.error("Error while closing all.", e);
			}
		}
	}
}