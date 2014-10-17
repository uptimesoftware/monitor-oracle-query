package com.uptimesoftware.uptime.plugin.test;

import static org.junit.Assert.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.Test;

import com.uptimesoftware.uptime.plugin.MonitorOracleQuery.UptimeMonitorOracleQuery;

public class MonitorOracleQueryTest {

	// Constants
	private final static int TIMEOUT_SECONDS = 60;
	private final static String THIN_DRIVER = "thin";

	// Input params. Set them to null when not used.
	private String hostname;
	// default : 1521
	private int port = 1521;
	private String username;
	private String password;
	// default : ORCL
	private String sid = "ORCL";
	// Should output non-numeric result.
	// private String sqlQuery = "SELECT name, display_name FROM CSS17.ENTITY WHERE ENTITY_TYPE_ID=1";
	// Should output numeric (Double) result.
	// private String sqlQuery = "select READ_CACHE from PERFORMANCE_AGGREGATE where sample_id = 7094";
	// Should output numeric (Long) result.
	private String sqlQuery = "select sample_id from PERFORMANCE_AGGREGATE where sample_id = 7094";

	@Test
	public void connectDBTest() throws SQLException {
		Connection conn = invokeGetRemoteConnection(THIN_DRIVER, hostname, port, sid, username, password,
				TIMEOUT_SECONDS);
		assertNotNull(conn);
		PreparedStatement ps = invokePrepareStatement(conn, sqlQuery);
		assertNotNull(ps);
		ResultSet rs = invokeGetResultSet(ps);
		assertNotNull(rs);
		String result = invokeExtractFromResultSet(rs);
		assertNotNull(result);
		int numericOrText = invokeIsLongDoubleOrText(result);

		// 0 if Long, 1 if Double, 2 if non-numeric String
		System.out.println(result + " : " + numericOrText);

		if (conn != null || ps != null || rs != null) {
			invokeCloseAll(conn, ps, rs);
		}
		assertTrue(rs.isClosed());
		assertTrue(ps.isClosed());
		assertTrue(conn.isClosed());
	}

	/**
	 * Invoke private getRemoteConnection method by using Java Reflection.
	 * 
	 * @param connectionURL
	 *            Connection URL for DB connection.
	 * @param username
	 *            DB user name.
	 * @param password
	 *            DB password.
	 * @param ssl
	 *            True if using SSL, false otherwise.
	 * @return Connection to Oracle DB.
	 */
	private Connection invokeGetRemoteConnection(String driverType, String hostname, int port, String sid,
			String username, String password, int timeout) {
		Connection connection = null;
		try {
			Method method = UptimeMonitorOracleQuery.class.getDeclaredMethod("getRemoteConnection", new Class[] {
					String.class, String.class, int.class, String.class, String.class, String.class, int.class });
			method.setAccessible(true);
			connection = (Connection) method.invoke(UptimeMonitorOracleQuery.class.newInstance(), driverType, hostname,
					port, sid, username, password, timeout);
		} catch (NoSuchMethodException | SecurityException | IllegalArgumentException | IllegalAccessException
				| InvocationTargetException | InstantiationException e) {
			System.err.println(e);
		}
		return connection;
	}

	/**
	 * Invoke private prepareStatement method by using Java Reflection.
	 * 
	 * @param conn
	 *            SQL connection.
	 * @param sqlQuery
	 *            SQL query String to execute.
	 * @return PreparedStatement object.
	 */
	private PreparedStatement invokePrepareStatement(Connection conn, String sqlQuery) {
		PreparedStatement preparedStatement = null;
		try {
			Method method = UptimeMonitorOracleQuery.class.getDeclaredMethod("prepareStatement", new Class[] {
					Connection.class, String.class });
			method.setAccessible(true);
			preparedStatement = (PreparedStatement) method.invoke(UptimeMonitorOracleQuery.class.newInstance(),
					new Object[] { conn, sqlQuery });
		} catch (NoSuchMethodException | SecurityException | IllegalArgumentException | IllegalAccessException
				| InvocationTargetException | InstantiationException e) {
			e.printStackTrace();
		}
		return preparedStatement;
	}

	/**
	 * Invoke private getResultSet method by using Java Reflection.
	 * 
	 * @param ps
	 *            SQL PreparedStatement.
	 * @return SQL ResultSet object.
	 */
	private ResultSet invokeGetResultSet(PreparedStatement ps) {
		ResultSet resultSet = null;
		try {
			Method method = UptimeMonitorOracleQuery.class.getDeclaredMethod("getResultSet",
					new Class[] { PreparedStatement.class });
			method.setAccessible(true);
			resultSet = (ResultSet) method.invoke(UptimeMonitorOracleQuery.class.newInstance(), new Object[] { ps });
		} catch (NoSuchMethodException | SecurityException | IllegalArgumentException | IllegalAccessException
				| InvocationTargetException | InstantiationException e) {
			e.printStackTrace();
		}
		return resultSet;
	}

	/**
	 * Close all resources and connection.
	 * 
	 * @param connection
	 *            DB connection.
	 * @param preparedStatement
	 *            SQL PreparedStatement.
	 * @param resultSet
	 *            SQL ResultSet.
	 */
	private void invokeCloseAll(Connection connection, PreparedStatement preparedStatement, ResultSet resultSet) {
		try {
			Method method = UptimeMonitorOracleQuery.class.getDeclaredMethod("closeAll", new Class[] {
					Connection.class, PreparedStatement.class, ResultSet.class });
			method.setAccessible(true);
			resultSet = (ResultSet) method.invoke(UptimeMonitorOracleQuery.class.newInstance(), new Object[] {
					connection, preparedStatement, resultSet });
		} catch (NoSuchMethodException | SecurityException | IllegalArgumentException | IllegalAccessException
				| InvocationTargetException | InstantiationException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Invoke private extractFromResultSet method by using Java Reflection.
	 * 
	 * @param resultSet
	 *            SQL ResultSet.
	 * @return Result in string.
	 */
	private String invokeExtractFromResultSet(ResultSet resultSet) {
		String result = "";
		try {
			Method method = UptimeMonitorOracleQuery.class.getDeclaredMethod("extractFromResultSet",
					new Class[] { ResultSet.class });
			method.setAccessible(true);
			result = (String) method.invoke(UptimeMonitorOracleQuery.class.newInstance(), new Object[] { resultSet });
		} catch (NoSuchMethodException | SecurityException | IllegalArgumentException | IllegalAccessException
				| InvocationTargetException | InstantiationException e) {
			e.printStackTrace();
		}
		return result;
	}

	/**
	 * Invoke isLongDoubleOrText method by using Reflection.
	 * 
	 * @param stringResult
	 *            Query result in String.
	 * @return 0 if Long, 1 if Double, 2 if non-numeric String
	 */
	private int invokeIsLongDoubleOrText(String stringResult) {
		int which = 0;
		try {
			Method method = UptimeMonitorOracleQuery.class.getDeclaredMethod("isLongDoubleOrText",
					new Class[] { String.class });
			method.setAccessible(true);
			which = (int) method.invoke(UptimeMonitorOracleQuery.class.newInstance(), new Object[] { stringResult });
		} catch (NoSuchMethodException | SecurityException | IllegalArgumentException | IllegalAccessException
				| InvocationTargetException | InstantiationException e) {
			e.printStackTrace();
		}
		return which;
	}
}