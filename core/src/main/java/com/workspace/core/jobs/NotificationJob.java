// package com.workspace.core.jobs; // Apne package ka naam check kar lena

// import com.adobe.granite.taskmanagement.Task;
// import com.adobe.granite.taskmanagement.TaskManager;
// import com.day.cq.mailer.MessageGateway;
// import com.day.cq.mailer.MessageGatewayService;
// import org.apache.commons.mail.HtmlEmail;
// import org.apache.sling.api.resource.LoginException;
// import org.apache.sling.api.resource.Resource;
// import org.apache.sling.api.resource.ResourceResolver;
// import org.apache.sling.api.resource.ResourceResolverFactory;
// import org.apache.sling.event.jobs.Job;
// import org.apache.sling.event.jobs.consumer.JobConsumer;
// import org.osgi.service.component.annotations.Component;
// import org.osgi.service.component.annotations.Reference;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// import java.util.Collections;
// import java.util.Date;
// import java.util.Map;

// @Component(service = JobConsumer.class, immediate = true, property = {
//     JobConsumer.PROPERTY_TOPICS + "=workspace/dam/asset/deactivate"
// })
// public class NotificationJob implements JobConsumer {

//     private static final Logger log = LoggerFactory.getLogger(NotificationJob.class);

//     @Reference
//     private TaskManager taskManager;

//     @Reference
//     private MessageGatewayService messageGatewayService;

//     @Reference
//     private ResourceResolverFactory resolverFactory;

//     @Override
//     public JobResult process(Job job) {
//         String assetPath = (String) job.getProperty("assetPath");
//         String userId = (String) job.getProperty("userId");

//         log.info("🚀 AEM Logger: Job Processing Started for asset -> {}", assetPath);

//         // Service User ke through backend me login karna. 
//         // Note: "dam-service-user" ko apne project ke actual service user name se replace karna pad sakta hai.
//         Map<String, Object> param = Collections.singletonMap(ResourceResolverFactory.SUBSERVICE, "dam-service-user");

//         try (ResourceResolver resolver = resolverFactory.getServiceResourceResolver(param)) {
            
//             // Check kar rahe hain ki asset sach me hai ya delete ho gaya
//             Resource assetResource = resolver.getResource(assetPath);
//             if (assetResource == null) {
//                 log.warn("⏭️ AEM Logger: Asset {} already removed before job. Graceful skip.", assetPath);
//                 return JobResult.OK; 
//             }

//             // Step 1: AEM Inbox Task Banayein
//             createInboxTask(assetPath, userId);

//             // Step 2: DAM Admins ko Email Bhejein
//             sendEmailNotification(assetPath, userId);

//             log.info("✅ AEM Logger: Job Successfully Completed for -> {}", assetPath);
//             return JobResult.OK;

//         } catch (LoginException e) {
//             log.error("❌ AEM Logger: Service user login failed. Please check user mapping. Error: ", e);
//             return JobResult.FAILED;
//         } catch (Exception e) {
//             log.error("❌ AEM Logger: Job Failed. System will retry. Error: ", e);
//             return JobResult.FAILED;
//         }
//     }

//     private void createInboxTask(String assetPath, String userId) throws Exception {
//         Task task = taskManager.getTaskManagerFactory().newTaskFactory("Task").newTask();
//         task.setName("Asset Deactivated: " + getAssetName(assetPath));
//         task.setDescription("User '" + userId + "' has deactivated the asset at path: " + assetPath + ". Please review.");
//         task.setCurrentAssignee("dam-administrators"); 
        
//         taskManager.createTask(task);
//         log.info("🔔 AEM Logger: Inbox Task created for dam-administrators");
//     }

//     private void sendEmailNotification(String assetPath, String userId) throws Exception {
//         MessageGateway<HtmlEmail> messageGateway = messageGatewayService.getGateway(HtmlEmail.class);
        
//         if (messageGateway == null) {
//             log.error("❌ AEM Logger: MessageGateway is null. Configure Day CQ Mail Service in OSGi.");
//             return;
//         }

//         HtmlEmail email = new HtmlEmail();
//         email.setCharset("UTF-8");
//         email.setSubject("[AEM DAM] Asset Deactivated Confirmation Required");
        
//         String emailBody = "<h3>Asset Deactivation Notification</h3>"
//                 + "<p><strong>Asset Name:</strong> " + getAssetName(assetPath) + "</p>"
//                 + "<p><strong>Asset Path:</strong> " + assetPath + "</p>"
//                 + "<p><strong>Initiated By:</strong> " + userId + "</p>"
//                 + "<p><strong>Deactivated At:</strong> " + new Date().toString() + "</p>"
//                 + "<br><p>Please confirm if this asset should remain deactivated or if cleanup is required.</p>";
        
//         email.setHtmlMsg(emailBody);
//         email.addTo("admin@yourcompany.com", "DAM Administrator");
        
//         messageGateway.send(email);
//         log.info("📧 AEM Logger: Email successfully sent to DAM Admin!");
//     }

//     private String getAssetName(String path) {
//         if (path == null || !path.contains("/")) return "Unknown Asset";
//         return path.substring(path.lastIndexOf('/') + 1);
//     }
// }