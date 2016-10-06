package edu.oregonstate.iam.lib.box;

import java.io.IOException;
import java.util.Properties;

import edu.oregonstate.iam.lib.ConfigReader;

/**
 * Object to hold Box configuration read as properties
 * 
 * @author hagimotk
 *
 */
public class BoxConfig {

	private static Properties properties;
	
	private static final String CLIENT_ID = "clientId";
    private static final String CLIENT_SECRET = "clientSecret";
    private static final String ENTERPRISE_ID = "enterpriseId";
    private static final String PUBLIC_KEY_ID = "publicKeyId";
    private static final String PRIVATE_KEY_FILE = "privateKeyFile";
    private static final String PRIVATE_KEY_PASSWORD = "privateKeyPassword";
    private static final String MAX_CACHE_ENTRIES = "maxCacheEntries";
    private static final String APP_USER_NAME = "appUserName";
    
	// this will be obsolete after first use but refresh_token will always get the correct acess_token that is current.
	private static final String ACCESS_TOKEN = "accessToken"; 
	private static final String REFRESH_TOKEN_FILE = "refreshTokenFile";

	// Used when process needs to send an email
	private static final String BOX_ADMIN_EMAIL = "boxAdminEmail";
	private static final String SMTP = "smtp";
	private static final String PORT = "port";
	private static final String FROM_ADDRESS = "fromAddress";

	/**
	 * Init BoxConfig - will read from file path specified
	 * 
	 * @param propertiesFile
	 * @throws IOException
	 */
	public BoxConfig(String propertiesFile) throws IOException{
		this(propertiesFile, false);
	}

	/**
	 * Init BoxConfig.
	 * 
	 * @param propertiesFile /path/to/properties
	 * @param isResource if true, it will try to read the properties from the resource path
	 * @throws IOException
	 */
	public BoxConfig(String propertiesFile, boolean isResource) throws IOException{
		if(properties == null){
			properties = ConfigReader.readAsProperties(propertiesFile, isResource);
		}
	}

	public String getClientId() {
		return properties.getProperty(CLIENT_ID);
	}
	
	public String getClientSecret() {
		return properties.getProperty(CLIENT_SECRET);
	}
	
	public String getEnterpriseId() {
		return properties.getProperty(ENTERPRISE_ID);
	}
	
	public String getPublicKeyId() {
		return properties.getProperty(PUBLIC_KEY_ID);
	}
	
	public String getPrivateKeyFile() {
		return properties.getProperty(PRIVATE_KEY_FILE);
	}
	
	public String getPrivateKeyPassword() {
		return properties.getProperty(PRIVATE_KEY_PASSWORD);
	}
	
	public int getMaxCacheEntries() {
		return Integer.parseInt(properties.getProperty(MAX_CACHE_ENTRIES));
	}
	
	public String getAppUserName() {
		return properties.getProperty(APP_USER_NAME);
	}
	
	public String getAccessToken() {
		return properties.getProperty(ACCESS_TOKEN);
	}
	
	public String getRefreshTokenFile() {
		return properties.getProperty(REFRESH_TOKEN_FILE);
	}
	
	public String getBoxAdminEmail() {
		return properties.getProperty(BOX_ADMIN_EMAIL);
	}

	public String getSMTP() {
		return properties.getProperty(SMTP);
	}

	public String getPort() {
		return properties.getProperty(PORT);
	}

	public String getFromAddress() {
		return properties.getProperty(FROM_ADDRESS);
	}

	/**
	 * For debugging
	 * @return
	 */
	public String getAllProperties() {
		return
				"clientId="+ getClientId() + "\n" +
				"clientSecret="+ getClientSecret() + "\n" + 
				"enterpriseId="+ getEnterpriseId() +  "\n" +
				"publicKeyId="+ getPublicKeyId() + "\n" +
				"privateKeyFile="+ getPrivateKeyFile() +  "\n" +
				"privateKeyPassword="+ getPrivateKeyPassword() +  "\n" +
				"maxCacheEntries="+ getMaxCacheEntries() + "\n" +
				"appUserName="+ getAppUserName() +  "\n" +
				"accessToken="+ getAccessToken() +  "\n" +
				"refreshTokenFile="+ getRefreshTokenFile()  +  "\n" +
				"boxAdminEmail="+ getBoxAdminEmail()  +  "\n" +
				"smtp="+ getSMTP() +  "\n" +
				"port="+ getPort() +  "\n" +
				"fromAddress="+ getFromAddress() ;

	}
	
}
