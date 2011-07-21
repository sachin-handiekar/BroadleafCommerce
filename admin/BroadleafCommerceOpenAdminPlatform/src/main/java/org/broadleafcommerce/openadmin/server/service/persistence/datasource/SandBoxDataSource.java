package org.broadleafcommerce.openadmin.server.service.persistence.datasource;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.apache.commons.pool.impl.GenericKeyedObjectPool;
import org.broadleafcommerce.openadmin.server.service.SandBoxContext;
import org.hsqldb.Server;
import org.hsqldb.persist.HsqlProperties;
import org.hsqldb.server.ServerAcl.AclFormatException;

public class SandBoxDataSource implements DataSource {
	
	private static final Log LOG = LogFactory.getLog(SandBoxDataSource.class);
	
	public static final String DRIVERNAME = "org.hsqldb.jdbcDriver";
	public static final int DEFAULTPORT = 40025;
	public static final String DEFAULTADDRESS = "localhost";
	
	protected PrintWriter logWriter;
	protected int loginTimeout = 5;
	protected GenericKeyedObjectPool sandboxDataBasePool;
	protected Server server;
	protected int port = DEFAULTPORT;
	protected String address = DEFAULTADDRESS;
	
	public SandBoxDataSource() {
		try {
			Class.forName(DRIVERNAME);
		} catch (ClassNotFoundException e) {
			throw new RuntimeException(e);
		}
		try {
			HsqlProperties p = new HsqlProperties();
			p.setProperty("server.remote_open",true);
			server = new Server();
			server.setAddress(address);
			server.setPort(port);
			server.setProperties(p);
			server.setLogWriter(logWriter==null?new PrintWriter(System.out):logWriter);
			server.setErrWriter(logWriter==null?new PrintWriter(System.out):logWriter);
			server.start();
		} catch (IOException e) {
			throw new RuntimeException(e);
		} catch (AclFormatException e) {
			throw new RuntimeException(e);
		}
		sandboxDataBasePool = new GenericKeyedObjectPool(new PoolableSandBoxDataBaseFactory());
	}
	
	public void close() {
		try {
			sandboxDataBasePool.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getAddress() {
		return address;
	}

	public void setAddress(String address) {
		this.address = address;
	}
	
	//GenericKeyedObjectPool methods

	public int getMaxActive() {
		return sandboxDataBasePool.getMaxActive();
	}

	public void setMaxActive(int maxActive) {
		sandboxDataBasePool.setMaxActive(maxActive);
	}

	public int getMaxTotal() {
		return sandboxDataBasePool.getMaxTotal();
	}

	public void setMaxTotal(int maxTotal) {
		sandboxDataBasePool.setMaxTotal(maxTotal);
	}

	public byte getWhenExhaustedAction() {
		return sandboxDataBasePool.getWhenExhaustedAction();
	}

	public void setWhenExhaustedAction(byte whenExhaustedAction) {
		sandboxDataBasePool.setWhenExhaustedAction(whenExhaustedAction);
	}

	public long getMaxWait() {
		return sandboxDataBasePool.getMaxWait();
	}

	public void setMaxWait(long maxWait) {
		sandboxDataBasePool.setMaxWait(maxWait);
	}

	public int getMaxIdle() {
		return sandboxDataBasePool.getMaxIdle();
	}

	public void setMaxIdle(int maxIdle) {
		sandboxDataBasePool.setMaxIdle(maxIdle);
	}

	public void setMinIdle(int poolSize) {
		sandboxDataBasePool.setMinIdle(poolSize);
	}

	public int getMinIdle() {
		return sandboxDataBasePool.getMinIdle();
	}

	public long getTimeBetweenEvictionRunsMillis() {
		return sandboxDataBasePool.getTimeBetweenEvictionRunsMillis();
	}

	public void setTimeBetweenEvictionRunsMillis(
			long timeBetweenEvictionRunsMillis) {
		sandboxDataBasePool
				.setTimeBetweenEvictionRunsMillis(timeBetweenEvictionRunsMillis);
	}

	public long getMinEvictableIdleTimeMillis() {
		return sandboxDataBasePool.getMinEvictableIdleTimeMillis();
	}

	public void setMinEvictableIdleTimeMillis(long minEvictableIdleTimeMillis) {
		sandboxDataBasePool
				.setMinEvictableIdleTimeMillis(minEvictableIdleTimeMillis);
	}

	public boolean getLifo() {
		return sandboxDataBasePool.getLifo();
	}

	public void setLifo(boolean lifo) {
		sandboxDataBasePool.setLifo(lifo);
	}

	public int getNumActive() {
		return sandboxDataBasePool.getNumActive();
	}

	public int getNumIdle() {
		return sandboxDataBasePool.getNumIdle();
	}

	public int getNumActive(Object key) {
		return sandboxDataBasePool.getNumActive(key);
	}

	public int getNumIdle(Object key) {
		return sandboxDataBasePool.getNumIdle(key);
	}
	
	//DataSource methods

	@Override
	public PrintWriter getLogWriter() throws SQLException {
		return logWriter;
	}

	@Override
	public void setLogWriter(PrintWriter out) throws SQLException {
		this.logWriter = out;
	}

	@Override
	public void setLoginTimeout(int seconds) throws SQLException {
		this.loginTimeout = seconds;
	}

	@Override
	public int getLoginTimeout() throws SQLException {
		return loginTimeout;
	}

	@Override
	public <T> T unwrap(Class<T> iface) throws SQLException {
		return null;
	}

	@Override
	public boolean isWrapperFor(Class<?> iface) throws SQLException {
		return false;
	}

	@Override
	public Connection getConnection() throws SQLException {
		try {
			return (Connection) sandboxDataBasePool.borrowObject(SandBoxContext.getSandBoxContext().getSandBoxName());
		} catch (Exception e) {
			throw new SQLException(e);
		}
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		throw new SQLException("Not Supported");
	}

	private class PoolableSandBoxDataBaseFactory implements KeyedPoolableObjectFactory {

		@Override
		public Object makeObject(Object key) throws Exception {
			String jdbcUrl = "jdbc:hsqldb:hsql://localhost:40025/broadleaf_"+key+";mem:broadleaf_"+key;
			Connection connection = DriverManager.getConnection(jdbcUrl, "SA", "");
			SandBoxConnection blcConnection = new SandBoxConnection(connection, sandboxDataBasePool, (String) key);
			
			LOG.info("Opening sandbox database at: " + jdbcUrl);
			
			return blcConnection;
		}

		@Override
		public void destroyObject(Object key, Object obj) throws Exception {
			Connection c = (Connection) obj;
			try {
				c.prepareStatement("DROP SCHEMA broadleaf_" + key + " CASCADE").execute();
			} finally {
				c.prepareStatement("SHUTDOWN").execute();
				
				LOG.info("Closing sandbox database at: jdbc:hsqldb:hsql://localhost:40025/broadleaf_"+key+";mem:broadleaf_"+key);
			}
		}

		@Override
		public boolean validateObject(Object key, Object obj) {
			//TODO add a generic connection validation
			return true;
		}

		@Override
		public void activateObject(Object key, Object obj) throws Exception {
			//do nothing
		}

		@Override
		public void passivateObject(Object key, Object obj) throws Exception {
			//do nothing
		}
		
	}

}
