package edu.oregonstate.iam.lib.box;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Iterator;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.box.sdk.BoxAPIConnection;
import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxAPIRequest;
import com.box.sdk.BoxAPIResponse;
import com.box.sdk.BoxFolder;
import com.box.sdk.BoxGroup;
import com.box.sdk.BoxUser;
import com.box.sdk.CreateUserParams;
import com.box.sdk.EmailAlias;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Common Box API methods - wrapped.
 * 
 * @author hagimotk
 *
 */
public class BoxUtil {

	static Logger logger = LoggerFactory.getLogger(BoxUtil.class);
	
	/**
	 * Adds an email as alias for the given BoxUser without generating a notification
	 * 
	 * @param api
	 * @param boxUserId
	 * @param emailAlias
	 * @return
	 */
	public static boolean addEmailAliasNoNotification(BoxAPIConnection api, String boxUserId, String emailAlias) {

		boolean res = true;
		
		logger.debug("BoxUserId: {}, Alias: {}", boxUserId, emailAlias);
		
		URL url = null;
		String urlString = "https://api.box.com/2.0/users/"+boxUserId+"/email_aliases";
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			logger.info("Bad URL: {}",urlString);
			return false;
		}
		
		String method = "POST";
		BoxAPIRequest apiRequest = new BoxAPIRequest(api, url, method);
		apiRequest.addHeader("Box-Notifications", "off");
		apiRequest.setBody("{\"email\": \""+emailAlias+"\"}");
		BoxAPIResponse apiResponse = null;
		try{
			apiResponse = apiRequest.send();
			logger.debug("Added email Alias for {}: {}.",boxUserId, emailAlias);
		} catch (BoxAPIException bae) {

			res = false;
			logger.debug("Response: {}",bae.getResponse());
			if(bae.getResponseCode() == 403 && bae.getResponse().contains("already has a Box account")) {
				logger.debug("{} already has a Box account!",emailAlias);
			} else {
				logger.debug("Got {} while trying to add email alias: {}",bae.getResponseCode(),emailAlias);
//				throw bae;	
			}

		} finally {
			try {
				if(apiResponse != null) apiResponse.disconnect();							
			} catch(BoxAPIException bae) {
				// TODO just consume this.. submitted a case.  Whenever disconnect is called for this method, it seems to throw an exception. 
				// case: https://community.box.com/t5/custom/page/page-id/BoxViewTicketDetail?ticket_id=1183248
				
			}
		}
		
		return res;
	}
	
	/**
	 * This method will delete an email alias without generating a notification
	 * 
	 * @param api
	 * @param boxUserId
	 * @param emailAliasId
	 * @throws MalformedURLException
	 */
	public static boolean deleteEmailAliasNoNotification(BoxAPIConnection api, String boxUserId, String emailAliasId) {
		
		boolean res = true;
		
		URL url = null;
		String urlString = "https://api.box.com/2.0/users/"+boxUserId+"/email_aliases/"+emailAliasId;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			logger.info("Bad URL: {}",urlString);
			return false;
		}
		String method = "DELETE";
		BoxAPIRequest apiRequest = new BoxAPIRequest(api, url, method);
		apiRequest.addHeader("Box-Notifications", "off");
		BoxAPIResponse apiResponse = null;
		try {
			apiResponse = apiRequest.send();
			logger.debug("Deleted email Alias ("+emailAliasId+") from "+boxUserId+".  ResponseCode="+apiResponse.getResponseCode()+": "+apiResponse.toString());
		} catch (BoxAPIException bae) {
			
			res = false;
			logger.debug("Response: {}",bae.getResponse());
			if(bae.getResponseCode() == 404 && bae.getResponse().contains("not_found")) {
				logger.debug("Email alias {} for {} not found",emailAliasId,boxUserId);
			} else {
				throw bae;	
			}

		} finally {
			if(apiResponse != null) apiResponse.disconnect();			
		}
		
		return res;
	}
	
    /**
     * Need to be coAdmin of the Enterprise - Neither AppUserCon or AppEntCon will work...
     * @param api
     * @param login
     * @return
     */
	public static BoxUser getManagedUserByLogin(BoxAPIConnection api, String login) throws BoxAPIException {
		
		BoxUser user = null;
		
		Iterable<BoxUser.Info> allUserInfo = BoxUser.getAllEnterpriseUsers(api, login, "");
		for(BoxUser.Info userInfo : allUserInfo) {
			logger.debug("id={}, login={}, name={}, status={}",userInfo.getID(), userInfo.getLogin(), userInfo.getName(), userInfo.getStatus().name());
			if(userInfo.getLogin().equalsIgnoreCase(login)) {
				user = userInfo.getResource();
				break;
			}
		}
		if(user == null) logger.debug("Couldn't get user object for {}.",login);
		
		return user;
	}

	/**
	 * Gets an id for the emailAlias for the given user/email
	 * 
	 * @param user
	 * @param emailAlias
	 * @return
	 */
	public static String getEmailAliasId(BoxUser user, String emailAlias) {
		String emailAliasId = "";
		
		Collection<EmailAlias> aliases = user.getEmailAliases();
		for(EmailAlias alias : aliases) {
			if(emailAlias.equalsIgnoreCase(alias.getEmail())) {
				emailAliasId = alias.getID();
				break;
			}
		}		
		
		return emailAliasId;
	}
	

	/**
	 * Rename a given folder to a new name calling the API as the given box user.
	 * If the name is already taken, it will append timeInMillis to the filename.
	 * 
	 * @param api
	 * @param boxUser user to call this api as (As-User)
	 * @param boxFolder folder to rename
	 * @param newName new name for the folder
	 * @return
	 */
	public static BoxFolder renameFolder(BoxAPIConnection api, BoxUser asUserBoxUser, BoxFolder boxFolder, String newName) {
		
		return moveAndRenameFolder(api, asUserBoxUser, boxFolder, "-1", newName);

	}
	
	/**
	 * Moves the folder to the given destination folder and renames.  Can be used to just rename the folder in place.
	 * If the name is already taken, it will append timeInMillis to the filename.
	 * 
	 * @param api
	 * @param asUserBoxUser if not null, will add "As-User" header to make the call as this user.
	 * @param boxFolder folder to rename (and move, if parentId is not "-1")
	 * @param parentId destination folder ID, if "-1", will consider this call just a "rename" and no move.
	 * @param newName new name for the folder to be moved
	 * @return
	 */
	public static BoxFolder moveAndRenameFolder(BoxAPIConnection api, BoxUser asUserBoxUser, BoxFolder boxFolder, String parentId, String newName) {
		
		BoxFolder folder = null;
		
		URL url = null;
		String urlString = "https://api.box.com/2.0/folders/"+boxFolder.getID();
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			logger.info("Bad URL: {}",urlString);
			return folder;
		}
		
		String method = "PUT";
		BoxAPIRequest apiRequest = new BoxAPIRequest(api, url, method);
		if(asUserBoxUser != null) apiRequest.addHeader("As-User", asUserBoxUser.getID());
		String bodyString = "{";
		if(!parentId.equals("-1")) bodyString += "\"parent\":{\"id\":\""+parentId+"\"},";
		bodyString += "\"name\":\""+newName+"\"}"; 
		apiRequest.setBody(bodyString);
		BoxAPIResponse apiResponse = null;
		try{
			apiResponse = apiRequest.send();
			Scanner scanner = null;
			try {
				scanner = new Scanner(apiResponse.getBody()).useDelimiter("\\A");
			    if(scanner.hasNext()) {
			    	String jsonRes = scanner.next();
					logger.debug(jsonRes);
			    	//parse JSON to get folder ID.  Make sure there was only one result...
			    	JsonParser jsonParser = new JsonParser();
					JsonObject jsonObj = (JsonObject) jsonParser.parse(jsonRes);
					//get id of the folder and return BoxFolder
					String folderId = jsonObj.get("id").toString().replace("\"", "");
					logger.debug("folderId={}",folderId);
					folder = new BoxFolder(api, folderId);
			    	
			    }
				
			} finally {
				if(scanner != null) scanner.close();
			}
			
		} catch (BoxAPIException bae) {

			// if the name was already taken, try calling the method again with "_timeInMillis" appended..
			if(bae.getResponseCode()==409 && bae.getResponse().contains("item_name_in_use")) {
				
				String newRandomName = newName+"_"+Calendar.getInstance().getTimeInMillis();
				return moveAndRenameFolder(api, asUserBoxUser, boxFolder, parentId, newRandomName);
				
			} else {
				logger.debug("Response: {}",bae.getResponse());
				bae.printStackTrace();				
			}
			
		} finally {
			try {
				if(apiResponse != null) apiResponse.disconnect();							
			} catch(BoxAPIException bae) {
				// TODO just consume this.. submitted a case.  Whenever disconnect is called for this method, it seems to throw an exception. 
				// case: https://community.box.com/t5/custom/page/page-id/BoxViewTicketDetail?ticket_id=1183248
				
			}
		}
		return folder;
	}

	/**
	 * Move the sourceOwnerBoxUserId root folder to destinationOwnerBoxUserId's root
	 * 
	 * @param api
	 * @param sourceOwnerBoxUserId
	 * @param destinationOwnerBoxUserId
	 * @return
	 */
	public static BoxFolder moveSourceRootFolderToDestRoot(BoxAPIConnection api, String sourceOwnerBoxUserId, String destinationOwnerBoxUserId) {
		
		BoxFolder folder = null;
		
		URL url = null;
		String urlString = "https://api.box.com/2.0/users/"+sourceOwnerBoxUserId+"/folders/0";
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			logger.info("Bad URL: {}",urlString);
			return folder;
		}
		
		String method = "PUT";
		BoxAPIRequest apiRequest = new BoxAPIRequest(api, url, method);
//		apiRequest.addHeader("As-User", boxUserId);
		apiRequest.setBody("{\"owned_by\":{\"id\":\""+destinationOwnerBoxUserId+"\"},\"notify\":\"false\"}");
		BoxAPIResponse apiResponse = null;
		try{
			apiResponse = apiRequest.send();
			Scanner scanner = null;
			try {
				scanner = new Scanner(apiResponse.getBody()).useDelimiter("\\A");
			    if(scanner.hasNext()) {
			    	String jsonRes = scanner.next();
					logger.debug(jsonRes);
			    	//parse JSON to get folder ID.  Make sure there was only one result...
			    	JsonParser jsonParser = new JsonParser();
					JsonObject jsonObj = (JsonObject) jsonParser.parse(jsonRes);
					//get id of the folder and return BoxFolder
					String folderId = jsonObj.get("id").toString().replace("\"", "");
					logger.debug("folderId={}",folderId);
					folder = new BoxFolder(api, folderId);
			    	
			    }
				
			} finally {
				if(scanner != null) scanner.close();
			}
			
		} catch (BoxAPIException bae) {

			logger.debug("Response: {}",bae.getResponse());
		} finally {
			try {
				if(apiResponse != null) apiResponse.disconnect();							
			} catch(BoxAPIException bae) {
				// TODO just consume this.. submitted a case.  Whenever disconnect is called for this method, it seems to throw an exception. 
				// case: https://community.box.com/t5/custom/page/page-id/BoxViewTicketDetail?ticket_id=1183248
				
			}
		}
		return folder;
	}

	/**
	 * Gets a BoxFolder object with the given folderName under parentFolder for the user
	 * 
	 * @param api  BoxApiConnection
	 * @param boxUserId internal Box User ID of the folder owner
	 * @param parentFolderId
	 * @param folderName  Name of the folder to search
	 * @return
	 */
	public static BoxFolder getBoxFolderByName(BoxAPIConnection api, String boxUserId, String parentFolderId, String folderName) {
		
		BoxFolder tempFolder = null;
		
		// replace spaces with %20 and wrap the entire name in double quotes (%22) to get exact name match
		String searchFolderName = "%22"+folderName.replace(" ", "%20")+"%22";
		
		URL url = null;
		String urlString = "https://api.box.com/2.0/search"
				+ "?query="+searchFolderName
				+ "&scope=user_content"
				+ "&content_types=name"
				+ "&type=folder"
				+ "&ancestor_folder_ids="+parentFolderId
				+ "&owner_user_ids="+boxUserId;
		logger.debug(urlString);
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			logger.info("Bad URL: {}",urlString);
			return tempFolder;
		}
		
		String method = "GET";
		BoxAPIRequest apiRequest = new BoxAPIRequest(api, url, method);
		BoxAPIResponse apiResponse = null;
		try{
			apiResponse = apiRequest.send();
			Scanner scanner = null;
			try {
			    scanner = new Scanner(apiResponse.getBody()).useDelimiter("\\A");
			    if(scanner.hasNext()) {
			    	String jsonRes = scanner.next();
			    	//parse JSON to get folder ID.  Make sure there was only one result...
			    	JsonParser jsonParser = new JsonParser();
					JsonObject jsonObj = (JsonObject) jsonParser.parse(jsonRes);
					if(!jsonObj.get("total_count").toString().equals("1")) {
						logger.debug("Got none or more than 1 folder.");
						logger.debug(jsonRes);					
						return tempFolder;
					}
					// There should be one element under entries at this point
					JsonArray jsonArr = jsonObj.getAsJsonArray("entries");
					Iterator<JsonElement> iter = jsonArr.iterator();
					String folderId = "";
					if(iter.hasNext()) {
						JsonObject obj = (JsonObject) iter.next();
						folderId = obj.get("id").toString().replace("\"", "");	
						logger.debug("Got folderId: {}",folderId);
					}

					if(!folderId.equals("")) tempFolder = new BoxFolder(api, folderId);
			    }
			} finally {
				if(scanner != null) scanner.close();
			}
			
		} catch (BoxAPIException bae) {

			logger.debug("Response: {}",bae.getResponse());

		} finally {
			try {
				if(apiResponse != null) apiResponse.disconnect();							
			} catch(BoxAPIException bae) {
				// TODO just consume this.. submitted a case.  Whenever disconnect is called for this method, it seems to throw an exception. 
				// case: https://community.box.com/t5/custom/page/page-id/BoxViewTicketDetail?ticket_id=1183248
				
			}
		}
		
		return tempFolder;
	}
	
	/**
	 * Creates a folder with the given folderName under the given folder.  If the folder with the same name already exists, return the existing folder.
     *  - works with  AppUserConnection
     *  - Doesn't work with AppEnterpriseConnection (403)
	 * 
	 * @param folder
	 * @param folderName
	 * @return
	 */
	public static BoxFolder createFolderAt(BoxFolder folder, String folderName) {
		
		BoxFolder newFolder = null;
		
		try{
	        BoxFolder.Info info = folder.createFolder(folderName);
	        logger.debug("- createFolderAt: {}({})",info.getName(),info.getID());
	        newFolder = info.getResource();
	        
		} catch(BoxAPIException e) {

			// capture 409 item_name_in_use here and return the existing BoxFolder.
			if(e.getResponseCode()==409 && e.getResponse().contains("item_name_in_use")) {
				// parse response JSON and get the folder ID that has this name and return it..
				Scanner scanner = null;
				try {
					scanner = new Scanner(e.getResponse()).useDelimiter("\\A");
				    if(scanner.hasNext()) {
				    	String jsonRes = scanner.next();
						logger.debug(jsonRes);
				    	//parse JSON to get folder ID.  Make sure there was only one result...
				    	JsonParser jsonParser = new JsonParser();
						JsonObject jsonObj = (JsonObject) jsonParser.parse(jsonRes);
						
						JsonObject obj2 = (JsonObject) jsonObj.get("context_info");
						JsonArray arr = obj2.getAsJsonArray("conflicts");
						Iterator<JsonElement> iter = arr.iterator();
						String folderId = "";
						if(iter.hasNext()) {
							folderId = ((JsonObject)iter.next()).get("id").toString().replace("\"", "");							
							logger.debug("folderId={}",folderId);
							newFolder = new BoxFolder(folder.getAPI(), folderId);
						}
				    	
				    }
					
				} finally {
					if(scanner != null) scanner.close();
				}
				
			} else logger.info("- createFolderAt Error: {}, {}",e.getMessage(), e.getResponse());
		}
		
		return newFolder;
	}

	/**
	 * To get BoxGroup by name, need to be the co-admin or use the AppEnterpriseConnection.  AppUserConnection will not get any groups.
	 * 
	 * @param api
	 * @param groupName
	 * @return
	 */
	public static BoxGroup getBoxGroupByName(BoxAPIConnection api, String groupName) {
		
		BoxGroup tempGroup = null;
		
		try{
			Iterable<BoxGroup.Info> allGroupInfo = BoxGroup.getAllGroups(api);
			logger.debug("groups.hasNext={}",allGroupInfo.iterator().hasNext());
			for(BoxGroup.Info groupInfo : allGroupInfo) {
				logger.debug(groupInfo.getID()+": name="+groupInfo.getName());
				if(groupInfo.getName().equalsIgnoreCase(groupName)) {
					tempGroup = groupInfo.getResource();
					break;
				}
			}
		} catch(BoxAPIException e) {
			logger.debug("getBoxGroupByName: Error "+e.getMessage());
		}
		
		return tempGroup;
		
	}
	

	/**
	 * Creats a Box group with pre-set defaults.
	 * invitability_level and member_viewability_level are both set to all_managed_users
	 * 
	 * @param api
	 * @param name group name
	 * @param description group description
	 * @param externalSource (provenance in BoxGroup term) - if set, group can't be managed via UI.
	 * @param externalSourceIdentifier String to identify the external source (i.e., Grouper)
	 * 
	 * @see https://docs.box.com/reference#group-object  and https://community.box.com/t5/For-Admins/Inviting-Groups-To-Folders/ta-p/190 
	 * @see https://community.box.com/t5/Developer-Forum/Getting-404-trying-to-create-group-collaboration-on-a-folder-via/m-p/17259#M311
	 */
	public static boolean createGroupWithDefaults(BoxAPIConnection api, String name, String description, String externalSource, String externalSourceIdentifier) {
		
		boolean res = true;
		
		//TODO If provenance and/or? external_sync_identifier is/are set, group can't be managed via UI, even if they are blank/null.
		//See https://docs.box.com/reference#group-object  and https://community.box.com/t5/For-Admins/Inviting-Groups-To-Folders/ta-p/190 
	    //    for details on invitability_level and its relation to folder collaboration.. setting to "all_managed_users".
		// https://community.box.com/t5/Developer-Forum/Getting-404-trying-to-create-group-collaboration-on-a-folder-via/m-p/17259#M311
		
		//"invitability_level" and "member_viewability_level" options:
		// admins_only: Master Admin, Coadmins, group's Group Admin.
		// admins_and_members: Admins listed above and group members.
		// all_managed_users: All managed users in the enterprise.
		
		String requestBody = "{"
				+ "\"name\": \""+name+"\", ";
		
		if(!("".equals(externalSource)))
			requestBody += "\"provenance\": \""+externalSource+"\", ";
		if(!("".equals(externalSourceIdentifier)))
			requestBody += "\"external_sync_identifier\": \""+externalSourceIdentifier+"\", ";

		requestBody += "\"description\": \""+description+"\", "
				+ "\"invitability_level\": \"all_managed_users\", "
				+ "\"member_viewability_level\": \"all_managed_users\""
				+ "}";
		
		
		URL url = null;
		String urlString = "https://api.box.com/2.0/groups";
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			logger.info("Bad URL: {}",urlString);
			return false;
		}
		
		String method = "POST";
		BoxAPIRequest apiRequest = new BoxAPIRequest(api, url, method);
		apiRequest.addHeader("Box-Notifications", "off");
		apiRequest.setBody(requestBody);
		
		BoxAPIResponse apiResponse = null;
		try{
			
			apiResponse = apiRequest.send();
			res = (apiResponse.getResponseCode()==201);
			if(!res){
				logger.info("Failed to create group "+name+" with defaults set.  ResponseCode="+apiResponse.getResponseCode()+": "+apiResponse.toString());				
			}
			
		} finally {

			try {
				apiResponse.disconnect();
			} catch(BoxAPIException bae) {
				logger.info("- createGroupWithDefaults, apiResponse.disconnect(): {}", bae.getMessage());
			}
			
		}
	
		return res;
	}
	
	/**
	 * Get BoxUserLoginName (EPPN) for all enterprise-managed users.
	 * 
	 * @param api BoxAPIConnection for a coAdmin user
	 * @return ArrayList<String> of boxUserLoginNames.
	 */
	public static ArrayList<String> getAllEnterpriseUsers(BoxAPIConnection api) {
		
		ArrayList<String> boxUsers = new ArrayList<String>();
		
		try{
			Iterable<BoxUser.Info> allUserInfo = BoxUser.getAllEnterpriseUsers(api);
			for(BoxUser.Info userInfo : allUserInfo) {
				logger.debug(userInfo.getID()+": name="+userInfo.getName()+", status="+userInfo.getStatus().name()+", created: "+userInfo.getCreatedAt());
				boxUsers.add(userInfo.getLogin());
			}
		} catch(BoxAPIException bae) {
			logger.info("!! BoxAPIException@BoxUser.getAllEnterpriseUsers"+bae.getResponse());
		}
		
		return boxUsers;
	}
	
	/**
	 * Creates a Box user (enterprise managed user) with defaults. (ACTIVE, unlimited space, sync-enabled)
	 * 
	 * @param api CoAdmin user's connection
	 * @param login Box login (eppn)
	 * @param name
	 * @return BoxUser object for the new user
	 */
	public static BoxUser createUser(BoxAPIConnection api, String login, String name) throws BoxAPIException {
		
		CreateUserParams params = new CreateUserParams();
		params.setStatus(BoxUser.Status.ACTIVE);
		params.setCanSeeManagedUsers(true);
		params.setSpaceAmount(-1); // unlimited space
		params.setIsSyncEnabled(true);
		BoxUser.Info newUserInfo = BoxUser.createEnterpriseUser(api, login, name, params);

		return newUserInfo.getResource();	
	}

	/**
	 * Iterate a user's root folder to get a count of BoxItems (BoxFiles and BoxFolders) where this user is the owner.
	 * 
	 * @param api coAdminConnection
	 * @param boxUser
	 * @return
	 */
	public static int getBoxItemCount(BoxAPIConnection api, BoxUser boxUser) {

		int count = 0;
		
		URL url = null;
		String urlString = "https://api.box.com/2.0/folders/0";
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			logger.info("Bad URL: {}",urlString);
			return -1;
		}
		
		String method = "GET";
		BoxAPIRequest apiRequest = new BoxAPIRequest(api, url, method);
		apiRequest.addHeader("As-User", boxUser.getID());
		BoxAPIResponse apiResponse = null;
		try{
			apiResponse = apiRequest.send();
			Scanner scanner = null;
			try {
				scanner = new Scanner(apiResponse.getBody()).useDelimiter("\\A");
			    if(scanner.hasNext()) {
			    	String jsonRes = scanner.next();
//					logger.debug(jsonRes);
			    	JsonParser jsonParser = new JsonParser();
					JsonObject jsonObj = (JsonObject) jsonParser.parse(jsonRes);
					String itemCollection = jsonObj.get("item_collection").toString();
					JsonObject jsonObj1 = (JsonObject) jsonParser.parse(itemCollection);
					// NOTE: There is a total_count under item_collection, but this includes collaborated folders, which are not owned by this user.
					//       We're only interested in the folder that this user owns.
					JsonArray jsonArr = jsonObj1.getAsJsonArray("entries");
					Iterator<JsonElement> iter = jsonArr.iterator();
					String folderId = "";
					while(iter.hasNext()) {
						JsonObject obj = (JsonObject) iter.next();
						folderId = obj.get("id").toString().replace("\"", "");	
						if(userIsFolderOwner(api, boxUser, folderId)) count++;
//						logger.debug("Got folderId: {}",folderId);
					}

			    }
				
			} finally {
				if(scanner != null) scanner.close();
			}
			
		} catch (BoxAPIException bae) {

			logger.debug("Response: {}",bae.getResponse());
		} finally {
			try {
				if(apiResponse != null) apiResponse.disconnect();							
			} catch(BoxAPIException bae) {
				// TODO just consume this.. submitted a case.  Whenever disconnect is called for this method, it seems to throw an exception. 
				// case: https://community.box.com/t5/custom/page/page-id/BoxViewTicketDetail?ticket_id=1183248
				
			}
		}
		
		return count;
		
	}

	/**
	 * Determines if the boxUser is the owner of the folder identified by folderId
	 * 
	 * @param api
	 * @param boxUser
	 * @param folderId
	 * @return true/false
	 */
	private static boolean userIsFolderOwner(BoxAPIConnection api, BoxUser boxUser, String folderId) {

		boolean owner = false;
		
		URL url = null;
		String urlString = "https://api.box.com/2.0/folders/"+folderId;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			logger.info("Bad URL: {}",urlString);
			return owner;
		}
		
		String method = "GET";
		BoxAPIRequest apiRequest = new BoxAPIRequest(api, url, method);
		apiRequest.addHeader("As-User", boxUser.getID());
		BoxAPIResponse apiResponse = null;
		try{
			apiResponse = apiRequest.send();
			Scanner scanner = null;
			try {
				scanner = new Scanner(apiResponse.getBody()).useDelimiter("\\A");
			    if(scanner.hasNext()) {
			    	String jsonRes = scanner.next();
//					logger.debug(jsonRes);
			    	JsonParser jsonParser = new JsonParser();
					JsonObject jsonObj = (JsonObject) jsonParser.parse(jsonRes);
					String ownedBy = jsonObj.get("owned_by").toString();
					JsonObject jsonObj1 = (JsonObject) jsonParser.parse(ownedBy);
					String ownerId = jsonObj1.get("id").toString().replace("\"", "");
//					logger.debug("ownerId={}",ownerId);
					owner = ownerId.equals(boxUser.getID());
			    }
				
			} finally {
				if(scanner != null) scanner.close();
			}
			
		} catch (BoxAPIException bae) {

			logger.debug("Response: {}",bae.getResponse());
		} finally {
			try {
				if(apiResponse != null) apiResponse.disconnect();							
			} catch(BoxAPIException bae) {
				// TODO just consume this.. submitted a case.  Whenever disconnect is called for this method, it seems to throw an exception. 
				// case: https://community.box.com/t5/custom/page/page-id/BoxViewTicketDetail?ticket_id=1183248
				
			}
		}

		return owner;
	}
	
}
