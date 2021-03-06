package zephyropen.api;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.Vector;

import zephyropen.command.Command;
import zephyropen.socket.InputChannel;
import zephyropen.socket.InputChannelFactory;
import zephyropen.socket.OutputChannel;
import zephyropen.socket.OutputChannelFactory;
import zephyropen.util.DataLogger;
import zephyropen.util.ExternalNetwork;
import zephyropen.util.LogManager;
import zephyropen.util.Utils;

/**
 * <p>
 * Holds the framework configuration parameters. Used as a shared resource for
 * all other classes to initialize from. The defaults are enough to send
 * commands and register API's for incoming XML events.
 * 
 * @author <a href="mailto:brad.zdanivsky@gmail.com">Brad Zdanivsky</a>
 */
public class ZephyrOpen {

	/** track all major changes here */
	public static final String VERSION = "2.5";
	public static final String DEFAULT_PORT = "4444";
	public static final String DEFAULT_ADDRESS = "230.0.0.1";
	public static final String zephyropen = "zephyropen";
	public final static String fs = System.getProperty("file.separator");
	public static final String TIME_MS = "timestamp";
	public static final String timestamp = "timestamp";
	public static final String frameworkVersion = "frameworkVersion";
	public static final String networkService = "networkService";
	public static final String externalLookup = "externalLookup";
	public static final String externalAddress = "wan";
	public static final String port = "port";
	public static final String sender = "sender";
	public static final String localAddress = "lan";
	public static final String close = "close";
	public static final String frameworkDebug = "frameworkDebug";
	public static final String loopback = "loopback";
	public static final String multicast = "multicast";
	public static final String displayRecords = "displayRecords";
	public static final String discovery = "discovery";
	public static final String services = "services";
	public static final String address = "address";
	public static final String deviceName = "deviceName";
	public static final String shutdown = "shutdown";
	public static final String command = "command";
	public static final String action = "action";
	public static final String launch = "launch";
	public static final String kill = "kill";
	public static final String status = "status";
	public static final String kind = "kind";
	public static final String server = "server";
	public static final String xSize = "xSize";
	public static final String ySize = "ySize";
	public static final String code = "code";
	public static final String os = "os";
	public static final String log = "log";
	public static final String folder = "folder";
	public static final String home = "home";
	public static final String password = "password";
	public static final String serialPort = "serialPort";
	public static final String com = "com";
	public static final String showLAN = "showLAN";
	public static final String user = "user";
	public static final String loggingEnabled = "loggingEnabled";
	public static final String recording = "recording";
	public static final String filelock = "filelock";
	public static final String propFile = "propFile";
	public static final String userHome = "userHome";
	public static final String userLog = "userLog";
	public static final String root = "root";

	/** decimal points truncated for display, not rounded */
	public static final int PRECISION = 2;
	public static final int ERROR = -1;

	/** max time in milliseconds before closing the process */
	public static final long ONE_MINUTE = 60000;
	public static final long TWO_MINUTES = 120000;
	public static final long FIVE_MINUTES = 300000;
	public static final long TEN_MINUTES = 600000;
	public static final long TIME_OUT = TWO_MINUTES;

	/** communication channels to the framework */
	private InputChannel inputchannel = null;
	private OutputChannel outputChannel = null;

	/** reference to this singleton class */
	private static ZephyrOpen singleton = null;

	/** properties object to hold configuration */
	private Properties props = new Properties();

	/** log errors in common file */
	public static LogManager logger = null;

	/** don't allow framework calls if not up and running */
	private boolean configured = false;

	/** collection of data loggers to close on shutdown */
	private Vector<DataLogger> dataLoggers = null;

	/** prevent changes */
	private boolean locked = false;

	public static ZephyrOpen getReference() {
		if (singleton == null) singleton = new ZephyrOpen();
		return singleton;
	}

	/**
	 *  private constructor for this singleton class
	 * 
	 * load minimal defaults, ensure version is avail to all
	 *  
	 */
	private ZephyrOpen() {
		props.put(frameworkVersion, VERSION);
		props.put(os, System.getProperty("os.name"));
		props.put(root, System.getProperty("user.home") + fs + zephyropen);
		props.put(networkService, multicast);
		props.put(address, DEFAULT_ADDRESS);
		props.put(port, DEFAULT_PORT);
		props.put(loopback, "true");

		props.put(frameworkDebug, "true");		

	}

	/** Configure the Framework with given properties file */
	public synchronized void init(final String usr, final String dev) {

		if (configured) return;

		props.put(user, usr);
		props.put(deviceName, dev);
		props.put(userHome, props.getProperty(root) + fs + usr);
		props.put(userLog, props.getProperty(userHome) + fs + log);
		props.put(propFile, props.getProperty(userHome) + fs + dev + ".properties" );

		if (!parseConfigFile()){ 
			System.out.println("error paring file: " + props.getProperty(propFile));
		}
		
		createHome(); // will bring down system on fail
		startFramework(); // boot me
	}

	/** Configure the Framework with Defaults */
	public synchronized void init() {

		if (configured) return;
		
		// create no name directories for unnamed user/device
		if(!props.containsKey(user)) props.put(user, zephyropen);
		if(!props.containsKey(deviceName)) props.put(deviceName, zephyropen);
		
		if(!props.containsKey(userHome))
			put(userHome, props.getProperty(root) + fs + props.getProperty(user));

		if(!props.containsKey(userLog))
			put(userLog, props.getProperty(userHome) + fs + log);
		
		createHome(); // will bring down system on fail
		startFramework(); // boot me
	}

	/**
	 * Open framework network connections
	 * 
	 * <p>
	 * note: calling this function more than once will have no effect
	 */
	private void startFramework() {

		if (configured) {
			System.err.println("can't re-initalize the framework");
			return;
		}
		
		/** configure the network parameters */
		try {

			inputchannel = (InputChannel) InputChannelFactory.create();
			outputChannel = OutputChannelFactory.create();

			if (inputchannel == null || outputChannel == null)
				throw new Exception("no network connection");

			props.put(localAddress,
					(InetAddress.getLocalHost()).getHostAddress());

		} catch (Exception e) {
			shutdown(e);
		}

		/** optionally look up our external address */
		if (getBoolean(externalLookup))
			props.put(externalAddress, ExternalNetwork.getExternalIPAddress());

		/** register shutdown hook */
		Runtime.getRuntime().addShutdownHook(new CleanUpThread());

		/** open log file and register the frame work for debugging */
		if (getBoolean(frameworkDebug)) FrameworkAPI.getReference();
		if (getBoolean(loggingEnabled)) enableDebug();
		
		/** all set */
		configured = true;
	}

	/** setup framework to debug */
	private void enableDebug() {
		logger = new LogManager();
		if(props.containsKey(deviceName))
			logger.open(props.getProperty(userLog) + fs + props.getProperty(deviceName)+ ".log");
		else
			logger.open(props.getProperty(root) + fs + "debug.log");
		logger.append("started version " + VERSION);
	}

	/** ready the folders needed for this user */
	private void createHome() {
		
		if (new File(props.getProperty(root)).mkdirs())
			System.err.println("created directory: " + get(root));
		
		if (new File(props.getProperty(userHome)).mkdirs())
			System.err.println("created directory: " + get(userHome));

		if (new File(props.getProperty(userLog)).mkdirs())
			System.err.println("created directory: " + get(userLog));

		// be sure are there
		testFolders();
	}

	/** ensure are valid */
	private void testFolders() {

		File file = new File(props.getProperty(root));
		if (!file.exists() || !file.isDirectory()) {
			System.err.println("can't create root folder: " + file.getAbsolutePath());
			shutdown();
		}

		file = new File(props.getProperty(userHome));
		if (!file.exists() || !file.isDirectory()) {
			System.err.println("testFolders home: " + get(userHome));
			System.err.println("can't create home folder: " + file.getAbsolutePath());
			shutdown();
		}

		file = new File(props.getProperty(userLog));
		if (!file.exists() || !file.isDirectory()) {
			System.err.println("can't create home folder: " + file.getAbsolutePath());
			shutdown();
		}
	}

	/**
	 * @param file
	 *            is the properties file to configure the framework
	 */
	private boolean parseConfigFile() {

		final String filepath = props.getProperty(propFile);

		if (filepath == null) {
			System.err.println("called parseConfigFile() with null in propFile!");
			return false;
		}

		try {

			// set up new properties object from file
			FileInputStream propFile = new FileInputStream(filepath);
			info("framework parsed config file [" + filepath + "]");
			props.load(propFile);
			propFile.close();

			// now be sure no white space is in any properties!
			Enumeration<Object> keys = props.keys();
			while (keys.hasMoreElements()) {
				String key = (String) keys.nextElement();
				String value = (String) props.get(key);
				props.put(key, value.trim());
			}

		} catch (Exception e) {
			System.err.println("can't parse config file [" + filepath + "], terminate.");
		}

		return true;
	}

	/** @return true if written to config file */
	public boolean updateConfigFile() {

		final String filepath = props.getProperty(propFile);
		File file = new File(filepath);	
		if(file.exists()) file.delete();
		
		Properties hold = (Properties) props.clone();
			
		// System.out.println("constants, write to file: " + props.getProperty(propFile));
	
		OutputStream out = null;
		try {
			out = new FileOutputStream(filepath);
		} catch (FileNotFoundException e) {
			System.err.println(e.getMessage());
			return false;
		}

		try {
			hold.store(out, null);
		} catch (IOException e) {
			System.err.println(e.getMessage());
			return false;
		}			
	
		return true;
	}

	/** @return a copy of the properties file */
	public Properties getProperties() {
		return (Properties) props.clone();
	}

	@Override
	public String toString() {
		String str = "";
		Enumeration<Object> keys = props.keys();
		while (keys.hasMoreElements()) {
			String key = (String) keys.nextElement();
			String value = ((String) (props.get(key))).trim();
			str += (key + " = " + value + ", ");
		}
		return str;
	}

	public void delete(String key) {
		if(props.contains(key))
			props.remove(key);
	}
	
	/**
	 * Put a name/value pair into the configuration
	 * 
	 * @param key
	 * @param value
	 */
	public synchronized void put(String key, String value) {

		if (locked) {
			System.out.println("framework constants locked, can't put(): " + key);
			return;
		}

		// if (getBoolean(frameworkDebug))
		//	if (props.containsKey(key))
		//		System.out.println(".... refreshing property for: " + key + " = " + value);

		props.put(key.trim(), value.trim());
	}

	public  void put(String tag, boolean b) {
		if (b)put(tag, "true");
		else put(tag, "false");
	}

	public void put(String key, int value) {
		put(key, String.valueOf(value));
	}

	public void put(String key, long value) {
		put(key, String.valueOf(value));
	}

	/**
	 * lookup values from props file
	 * 
	 * @param key
	 *            is the lookup value
	 * @return the matching value from properties file (or null if not found)
	 */
	public synchronized String get(String key) {

		String ans = null;

		try {

			ans = props.getProperty(key.trim());

		} catch (Exception e) {
			System.err.println(e.getMessage());
			return null;
		}

		return ans;
	}

	/**
	 * lookup values from props file
	 * 
	 * @param key
	 *            is the lookup value
	 * @return the matching value from properties file (or false if not found)
	 */
	public boolean getBoolean(String key) {

		boolean value = false;

		try {

			value = Boolean.parseBoolean(get(key));

		} catch (Exception e) {
			return false;
		}

		return value;
	}

	/**
	 * lookup values from props file
	 * 
	 * @param key
	 *            is the lookup value
	 * @return the matching value from properties file (or zero if not found)
	 */
	public int getInteger(String key) {

		String ans = null;
		int value = ERROR;

		try {

			ans = get(key);
			value = Integer.parseInt(ans);

		} catch (Exception e) {
			return ERROR;
		}

		return value;
	}

	/** @return the framework's output channel */
	public OutputChannel getOutputChannel() {

		if (!configured)
			return null;

		return outputChannel;
	}

	/**
	 * <p>
	 * Write a line to common log file in format :
	 * <p>
	 * timestamp (ms), class that sent message, the error message
	 * 
	 * @param line
	 *            of text to write
	 * @param clazz
	 *            that encountered the error
	 */
	public void error(String line, Object clazz) {
		if (getBoolean(frameworkDebug))
			if (logger != null)
				logger.append("ERROR, " + new Date().toString() + ", " + clazz.getClass().getName() + ", " + line);

		System.err.println(Utils.getTime() + " " + clazz.getClass().getName() + ", " + line);
	}

	/** */
	public void error(String line) {
		if (logger != null)
			logger.append("ERROR, " + new Date().toString() + ", " + zephyropen + ", " + line);

		System.err.println(Utils.getTime() + " " + line);
	}

	/** */
	public void info(String line, Object clazz) {
		
		if(!getBoolean(frameworkDebug)) return;
		
		if (logger != null)
			logger.append("INFO, " + new Date().toString() + ", " + clazz.getClass().getName() + ", " + line);

		System.out.println(Utils.getTime() + " " + clazz.getClass().getName() + " " + line);
	}

	/** */
	public void info(String line) {
		
		if(!getBoolean(frameworkDebug)) return;
		
		if (logger != null)
			logger.append("INFO, " + new Date().toString() + ", " + get(deviceName) + ", " + zephyropen + ", " + line);

		System.out.println(Utils.getTime() + " " + zephyropen + " " + line);
	}

	public synchronized void lock() {
		locked = true;
	}

	public synchronized void unlock() {
		locked = false;
	}

	public synchronized boolean isLocked() {
		return locked;
	}

	/** send a "close" message */
	public void killDevice(String dev, String usr) {

		Command kill = new Command();
		kill.add(action, ZephyrOpen.kill);
		kill.add(user, usr);
		kill.add(deviceName, dev);
		kill.send();
		kill.send();

		info("sent: " + kill.toString(), this);
	}

	/** send a "close" message */
	public void closeServers() {

		Command kill = new Command();
		kill.add(action, ZephyrOpen.close);
		kill.send();
		kill.send();

		info("sent: " + kill.toString(), this);
	}

	/** send shutdown messages to group */
	public void shutdownFramework() {

		if (!configured) {
			System.out.println("not configured, terminate.");
			System.exit(0);
		}

		Command command = new Command();
		command.add(action, shutdown);
		command.send();
		command.send();
		command.add(action, kill);
		command.send();
		command.send();
	}

	/** terminate the application, but clean up first */
	public void shutdown(String line) {
		info(line, this);
		shutdown();
	}

	/** terminate the application, but clean up first */
	public void shutdown() {
		// info("shutdown(): closing files");
		closeLoggers();
		System.exit(0);
	}

	/** terminate the application, but clean up first */
	public void shutdown(Exception e) {
		if (logger != null)
			logger.append(e.getMessage());

		e.printStackTrace(System.err);
		shutdown();
	}

	/** shutdown hook, close any left over files and loggers */
	public class CleanUpThread extends Thread {
		public void run() {
			closeLoggers();
		}
	}

	/** terminate the application, but clean up first */
	public void closeLoggers() {	
		
		if (logger != null)
			logger.close();
		
		if (dataLoggers != null) {
			@SuppressWarnings("rawtypes")
			Enumeration e = dataLoggers.elements();
			while (e.hasMoreElements())
				((DataLogger) e.nextElement()).close();
		}
	}

	/** track open loggers */
	public void addLogger(DataLogger dataLogger) {

		// optional creation
		if (dataLoggers == null)
			dataLoggers = new Vector<DataLogger>();

		dataLoggers.add(dataLogger);
	}
}