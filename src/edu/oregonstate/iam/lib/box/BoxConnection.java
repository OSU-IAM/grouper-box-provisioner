package edu.oregonstate.iam.lib.box;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.box.sdk.BoxAPIConnection;

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
 *			boxCon.saveRefreshToken();
 *		}
 * 
 * @author hagimotk
 *
 */
public class BoxConnection {
	
	static Logger logger = LoggerFactory.getLogger(BoxConnection.class);

    private BoxAPIConnection coAdminCon = null;
    
    private BoxConfig boxConfig = null;
    
    public BoxConnection(BoxConfig boxConfig) {
    	this.boxConfig = boxConfig;
    	initConnections();
    }
    
    
    public BoxAPIConnection getCoAdminConnection() {
    	return coAdminCon;
    }
    
    /**
     * Initialize coAdminConnection
     */
	private void initConnections() {

		// read refresh_token from file
		String refreshToken = ConfigReader.read(boxConfig.getRefreshTokenFile(),false);
    	coAdminCon = new BoxAPIConnection(boxConfig.getClientId(), boxConfig.getClientSecret(), boxConfig.getAccessToken(), refreshToken);
	}
	
    
    /**
     * For co-admin connection only.
     * Save the new refresh_token for next use...
     */
	public void saveRefreshToken() {
		
		// if it was never created, do nothing
		if(coAdminCon == null) return;
		
		// save the new refresh_token to the file for the next call...
		String newRefreshToken = coAdminCon.getRefreshToken();
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(boxConfig.getRefreshTokenFile())))) {
			bw.write(newRefreshToken);
			bw.flush();
		} catch (IOException e) {
			logger.info("IOException while writing the refresh_token to file");
			e.printStackTrace();
		}
	}
}
