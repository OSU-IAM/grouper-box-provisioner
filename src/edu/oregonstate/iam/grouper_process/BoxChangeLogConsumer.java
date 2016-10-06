package edu.oregonstate.iam.grouper_process;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.box.sdk.BoxAPIException;
import com.box.sdk.BoxUser;

import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.app.loader.GrouperLoaderConfig;
import edu.internet2.middleware.grouper.changeLog.ChangeLogConsumerBase;
import edu.internet2.middleware.grouper.changeLog.ChangeLogEntry;
import edu.internet2.middleware.grouper.changeLog.ChangeLogLabels;
import edu.internet2.middleware.grouper.changeLog.ChangeLogProcessorMetadata;
import edu.internet2.middleware.grouper.changeLog.ChangeLogTypeBuiltin;
import edu.internet2.middleware.subject.provider.LdapSubject;
import edu.oregonstate.iam.lib.box.BoxConfig;
import edu.oregonstate.iam.lib.box.BoxConnection;
import edu.oregonstate.iam.lib.box.BoxUtil;

/**
 * Box ChangeLog Consumer.
 *  - Creates/enables Box user account when users are added to grouper box-enabled users group.
 *  - When creating or enabling, also adds all osuMail (LDAP attr) as alias for the user's Box account.
 *  
 * @author hagimotk
 *
 */
public class BoxChangeLogConsumer extends ChangeLogConsumerBase {

	static Logger logger = LoggerFactory.getLogger(BoxChangeLogConsumer.class);
	
	@Override
	public long processChangeLogEntries(List<ChangeLogEntry> changeLogEntryList,  ChangeLogProcessorMetadata changeLogProcessorMetadata) {

		boolean enabled = false;
		boolean boxApiError = false;
		long currentId = -1;
		String groupName = "";
		String userName = "";
		String boxUserId = "";
		String result = "";
		BoxConfig boxConfig = null;
		BoxConnection boxCon = null;
		LdapSubject ldapSubject = null;
		String boxUserFullName = "";
		Set<String> osuMail = null;

		try {

			// get Box config - boxconfig.properties file path from the grouper-loader.properties
			String configPropertiesFilePath = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("changeLog.consumer.box.configPropertiesFilePath");
			boxConfig = new BoxConfig(configPropertiesFilePath);
					
			BoxUser boxUser = null;

			try {

				// get BoxConnection for Co-Admin user
				boxCon = new BoxConnection(boxConfig);

				// get the Grouper group ID Path for the Box-enabled Employees
				String grouperGroupIdPath = GrouperLoaderConfig.retrieveConfig().propertyValueStringRequired("changeLog.consumer.box.grouperGroupIdPath");

				for (ChangeLogEntry changeLogEntry : changeLogEntryList) {

					currentId = changeLogEntry.getSequenceNumber();

					//if this is a membership add action and category
					if (changeLogEntry.equalsCategoryAndAction(ChangeLogTypeBuiltin.MEMBERSHIP_ADD)) {

						enabled = false;
						
						groupName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.groupName);
						if(groupName.equals(grouperGroupIdPath)) {
							
							userName = changeLogEntry.retrieveValueForLabel(ChangeLogLabels.MEMBERSHIP_ADD.subjectId);
							boxUserId = userName+"@oregonstate.edu";

							// check if user exists
							try {
								boxUser = BoxUtil.getManagedUserByLogin(boxCon.getCoAdminConnection(), boxUserId);
								boxApiError = false;
							} catch(BoxAPIException bae) {
								// don't know if user exists or not at this point, so don't proceed
								boxApiError = true;
								logger.debug("BoxAPIException thrown at getManagedUserByLogin({}): {}", boxUserId,bae.getMessage());
							}

							if(boxApiError) {
								enabled = false;
								
							} else {

								ldapSubject = (LdapSubject) SubjectFinder.findByIdAndSource(userName, "ldap", false);
								if(ldapSubject==null) {
									// if not in ldap, don't try to proceed.  This will catch group membership records that are not actually "subjects"
									continue;
								}
								osuMail = ldapSubject.getAttributeValues("osuMail");

								if(boxUser == null) {
									
									// get user's full name from LDAP
									boxUserFullName = ldapSubject.getAttributeValue("givenName") + " " +  ldapSubject.getAttributeValue("sn");
									//trim name in case givenName was blank (If user is confidential, we'll get blank givenName and "Name Withheld" for sn.
									boxUserFullName = boxUserFullName.trim();
									
									// if user is null at this point, BoxUser didn't exist, so create with ACTIVE status
									try {
										boxUser = BoxUtil.createUser(boxCon.getCoAdminConnection(), boxUserId, boxUserFullName);										
										enabled = true;
									} catch(BoxAPIException bae) {
										logger.debug("BoxAPIException calling BoxUtil.createUser({}).  {}, {}",boxUserId,bae.getMessage(), bae.getResponse());
										enabled = false;
									}
																		
								} else {  
									
									//  if user exists but is not active, set status to active.
									enabled = setStatus(boxUser, BoxUser.Status.ACTIVE);
									
								}
								
								// if created/enabled successfully, add the osuMail values as aliases to this BoxUser, if it isn't already.
								if(enabled && osuMail != null) {
									String emailAlias = "";
									Iterator<String> iter = osuMail.iterator();
									while(iter.hasNext()) {
										emailAlias = iter.next();
										
										// if this is "@oregonstateuniversity.mail.onmicrosoft.com" address, ignore...
										if(emailAlias.contains("microsoft")) continue;
										
										// if this is the user's EPPN, don't try to add as alias..
										if(emailAlias.equals(boxUserId)) continue;
										
										if("".equals(BoxUtil.getEmailAliasId(boxUser, emailAlias))) {
											if(!BoxUtil.addEmailAliasNoNotification(boxCon.getCoAdminConnection(), boxUser.getID(), emailAlias)) {
												// log the error but continue...
												logger.debug("Failed to add alias '{}' to BoxUser {}.",emailAlias,boxUserId);  
											}
										}
									}
								}

								
							}
							
							result = enabled?"SUCCESS":"FAILURE";

							logger.debug("Box add/enable user, name: " + boxUserId + ", groupName: " + groupName + ", result: "+result);
						}
					}


					if(result.equals("FAILURE")) {
						changeLogProcessorMetadata.registerProblem(new Throwable("Failed to add, enable, or disable Box User"), "Failed to add, enable, or disable Box User", currentId);
						return currentId-1;
						
					}
				}

				
			} finally {
				
				boxCon.saveRefreshToken();

			}
		} catch (Exception e) {
			changeLogProcessorMetadata.registerProblem(e, "Error processing record", currentId);
			return currentId-1;
		}
		if (currentId == -1) {
			throw new RuntimeException("Couldn't process any records");
		}

		return currentId;
	}

	/**
	 * Sets BoxUser status
	 * 
	 * @param boxUser
	 * @param status
	 * @return boolean
	 */
	private boolean setStatus(BoxUser boxUser, BoxUser.Status status) {
		
		boolean success = false;
		BoxUser.Info userInfo = null;
		try{
			userInfo = boxUser.getInfo(BoxUser.ALL_FIELDS);
			userInfo.setStatus(status);
			boxUser.updateInfo(userInfo);
			success = true;
		} catch (BoxAPIException e) {
			String resp = e.getResponse();
			logger.debug("Exception while getting/setting user status to {}: {}",status.name(),resp);
			success = false;									
			// Capture "status":403,"code":"access_denied_insufficient_permissions" when trying to set status to INACTIVE for users with role co-admin or above.
			// - only relevant when removing, as we won't be provisioning a user as co-admin (just here in prep for when we have "deactivate" group...)
			if(status.name().equals("INACTIVE") && resp.contains("403") && resp.contains("access_denied_insufficient_permissions")) {
				throw e;
			}
		}
		return success;
	}
	
}
