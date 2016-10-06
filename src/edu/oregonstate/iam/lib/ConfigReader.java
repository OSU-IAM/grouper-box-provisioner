package edu.oregonstate.iam.lib;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

public class ConfigReader {

	static Logger logger = LoggerFactory.getLogger(ConfigReader.class);
	
	/**
	 * Read a given file and return the content as a String 
	 * @param filename
	 * @return
	 */
	public static String read(String filename){
		return read(filename, false);
	}
	
	/**
	 * Read a given file and return the content as a String 
	 * @param filename
	 * @param clean - true: strip space, tab, newline chars, false: leave as is
	 * @return
	 */
	public static String read(String filename, boolean clean){
		
		File file = new File(filename);
		String file_content = "";
		 
		try (FileInputStream fis = new FileInputStream(file)) {
 
			int content;
			while ((content = fis.read()) != -1) {
				file_content += (char) content;
			}
			
		} catch (IOException e) {
			logger.error("read() threw an IOException.\n {}", e.getMessage()); 
			e.printStackTrace();
		} 
		
		if (clean) file_content = file_content.replaceAll("\\s", "").replaceAll("\r\n", "");
		return file_content;
	}

	/**
	 * Read a given file from resource location and return the content as a String
	 * @param filename
	 * @param clean - true: strip space, tab, newline chars, false: leave as is
	 * @return
	 */
	public static String readFromResource(String filename, boolean clean){
		
		String file_content = "";
		 
		try (InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(filename)) {
 
			int content;
			while ((content = is.read()) != -1) {
				file_content += (char) content;
			}
			
		} catch (IOException e) {
			logger.error("read() threw an IOException.\n {}", e.getMessage()); 
			e.printStackTrace();
		} 
		
		if (clean) file_content = file_content.replaceAll("\\s", "").replaceAll("\r\n", "");
		return file_content;
	}

	
	/**
	 * 
	 * Take a string and convert it into an APIConfig GSON Java object
	 * 
	 * @param config_string
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Object readConfigFromString(String config_string, Class klass){
		Gson gson = new Gson();
		return gson.fromJson(config_string, klass); 
	}
	
	/**
	 * Reads a given file with "name=value" pairs on each line and returns a Properties object.
	 * 
	 * @param configFilePath properties file path
	 * @param isResource if true, it will try to read it from the resource path 
	 * @return Properties object with the name/value parsed.
	 */
	public static Properties readAsProperties(String configFilePath, boolean isResource) {
		
		Properties props = new Properties();
		InputStream inputStream = null;
		try{
    		if(isResource) inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(configFilePath);
	    	else inputStream = new FileInputStream(configFilePath);
  
			props.load(inputStream);
 
		} catch (IOException ioe) {
			logger.info("Exception while reading properties file ("+configFilePath+"): "+ioe.getMessage());
		} finally {
			try {
				if(inputStream != null) inputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		return props;
	}


}
