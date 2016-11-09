package edu.oregonstate.iam.lib.box;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxDeveloperEditionAPIConnection;
import com.box.sdk.EncryptionAlgorithm;
import com.box.sdk.IAccessTokenCache;
import com.box.sdk.InMemoryLRUAccessTokenCache;
import com.box.sdk.JWTEncryptionPreferences;

import edu.oregonstate.iam.lib.ConfigReader;

/**
 * Wrapper for various BoxAPIConnection objects.  
 * 
 * Usage with coAdminCon:
 * 		try {
 *			
 *			boxCon = new BoxConnection(boxConfig, false, true);
 *
 *			// do your stuff with coAdminCon
 *          someMethod(boxCon.getCoAdminConnection());
 *						
 *		} finally {
 *			if(boxCon != null) boxCon.saveRefreshToken();
 *		}
 * 
 * @author hagimotk
 *
 */
public class BoxConnection {
	
	static Logger logger = LoggerFactory.getLogger(BoxConnection.class);

    private BoxAPIConnection appEntCon = null;
    private BoxAPIConnection coAdminCon = null;
    
    private BoxConfig boxConfig = null;
    
    public BoxConnection(BoxConfig boxConfig) {
    	this(boxConfig, true, true);
    }
    
    public BoxConnection(BoxConfig boxConfig, boolean getAppEntCon, boolean getCoAdminCon) {
    	this.boxConfig = boxConfig;
    	initConnections(getAppEntCon, getCoAdminCon);
    }
    
    public BoxAPIConnection getAppEnterpriseConnection() {
    	return appEntCon;
    }
    
    public BoxAPIConnection getCoAdminConnection() {
    	
		String refreshToken = ConfigReader.read(boxConfig.getRefreshTokenFile(),false);
		// if refresh_token returned is not blank AND it is different from the one coAdminCon has:
		if(!("".equals(refreshToken)) && !coAdminCon.getRefreshToken().equals(refreshToken)) {
			coAdminCon.setRefreshToken(refreshToken); // set the refresh_token to the new one for this connection
			coAdminCon.refresh(); // Refresh this connection's access token using its refresh token... which will generate a new set of RT/AT
			saveRefreshToken(); // .. so save the refresh_token again...
		}

    	return coAdminCon;
    }
    
    /**
     * Initialize appEnterpriseConnection, if getAppEntCon is true.
     * Initialize coAdminConnection, if getCoAdminCon is true
     * 
     * Create AppEnterpriseConnection and/or a CoAdmin connection.
     * 
     * @param getAppEntCon
     * @param getCoAdminCon
     */
	private void initConnections(boolean getAppEntCon, boolean getCoAdminCon) {

		if(getAppEntCon) {
	    	//-------------------------------------------
	        // for AppEnterprise Connections (Service Accounts)
	        JWTEncryptionPreferences encryptionPref = getEncryptionPrefs();
	        //TODO It is a best practice to use an access token cache to prevent unneeded requests to Box for access tokens.
	        //For production applications it is recommended to use a distributed cache like Memcached or Redis, and to
	        //implement IAccessTokenCache to store and retrieve access tokens appropriately for your environment.
	        IAccessTokenCache accessTokenCache = new InMemoryLRUAccessTokenCache(boxConfig.getMaxCacheEntries());

			appEntCon = BoxDeveloperEditionAPIConnection.getAppEnterpriseConnection(boxConfig.getEnterpriseId(), boxConfig.getClientId(), boxConfig.getClientSecret(), encryptionPref, accessTokenCache);
	    	logger.debug("**Got AppEnterprise Connection");    
	    	
		}
    	
		if(getCoAdminCon) {
	    	//-------------------------------------------
	        // for Co-admin Connection
			// read refresh_token from file
			String refreshToken = ConfigReader.read(boxConfig.getRefreshTokenFile(),false);
	    	//-------------------------------------------
	    	coAdminCon = new BoxAPIConnection(boxConfig.getClientId(), boxConfig.getClientSecret(), boxConfig.getAccessToken(), refreshToken);
	    	logger.debug("**Got CoAdmin Connection");     
	    	
	    	// Gets an access token that can be used to authenticate an API request. 
	    	// This method will automatically refresh the access token if it has expired since the last call to <code>getAccessToken()</code>.
	    	coAdminCon.getAccessToken(); 
			saveRefreshToken();

	    	
		}
		
	}
	
    /**
     * For Service Account / AppEnterprise Connections
     * @param privateKey
     * @return
     */
	private JWTEncryptionPreferences getEncryptionPrefs() {
		String privateKey = ConfigReader.read(boxConfig.getPrivateKeyFile());
		
		JWTEncryptionPreferences encryptionPref = new JWTEncryptionPreferences();
        encryptionPref.setPublicKeyID(boxConfig.getPublicKeyId());
        encryptionPref.setPrivateKey(privateKey);
        encryptionPref.setPrivateKeyPassword(boxConfig.getPrivateKeyPassword());
        encryptionPref.setEncryptionAlgorithm(EncryptionAlgorithm.RSA_SHA_256);
		return encryptionPref;
	}
    
	public void saveRefreshToken() {
		
		// if it was never created, do nothing
		if(coAdminCon == null) return;
		
		// save the new refresh_token to the file for the next call...
		String newRefreshToken = coAdminCon.getRefreshToken();
		logger.debug("-- Saving RefreshToken ("+newRefreshToken+") to file...");
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(boxConfig.getRefreshTokenFile())))) {
			bw.write(newRefreshToken);
			bw.flush();
		} catch (IOException e) {
			logger.info("IOException while writing the refresh_token to file");
			e.printStackTrace();
		}
	}
}
