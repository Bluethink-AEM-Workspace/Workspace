package com.workspace.core.workflows;

import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;
import org.apache.commons.mail.HtmlEmail;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.jcr.resource.api.JcrResourceConstants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Session;
import java.util.HashMap;
import java.util.Map;

@Component(
    service = WorkflowProcess.class,
    property = {
        "process.label=Custom Move Asset Process"
    }
)
public class MoveAssetProcess implements WorkflowProcess {

    private static final Logger LOG = LoggerFactory.getLogger(MoveAssetProcess.class);
    private static final String DEFAULT_RECIPIENT_EMAIL = "harshitsa2002@gmail.com";

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Reference
    private MessageGatewayService messageGatewayService;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap args) throws WorkflowException {
        String payloadPath = workItem.getWorkflowData().getPayload().toString();

        try {
            Session session = workflowSession.getSession();
            Map<String, Object> authInfo = new HashMap<>();
            authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);

            ResourceResolver resolver = resolverFactory.getResourceResolver(authInfo);

            if (resolver != null) {
                Resource assetResource = resolver.getResource(payloadPath);

                if (assetResource != null) {
                    // 1. Asset Metadata Read Karein
                    Resource metadataResource = resolver.getResource(payloadPath + "/jcr:content/metadata");
                    String mimeType = "";

                    if (metadataResource != null) {
                        ValueMap metadata = metadataResource.getValueMap();
                        mimeType = metadata.get("dc:format", String.class); 
                    }

                    // 2. MIME Type ke Basis par Destination Folder Route Karein
                    String targetFolder = "/content/dam/workflow/approved/others";

                    if (mimeType != null) {
                        if (mimeType.startsWith("image/")) {
                            targetFolder = "/content/dam/workflow/approved/images";
                        } else if (mimeType.contains("pdf") || mimeType.contains("document") || mimeType.contains("word")) {
                            targetFolder = "/content/dam/workflow/approved/documents";
                        }
                    }

                    // 3. Asset Move Execute Karein
                    String assetName = payloadPath.substring(payloadPath.lastIndexOf("/"));
                    String destinationPath = targetFolder + assetName;

                    session.move(payloadPath, destinationPath);
                    session.save();
                    LOG.info("Asset successfully moved from {} to {} based on MIME Type: {}", payloadPath, destinationPath, mimeType);

                    // 4. Workflow dialog args se email fetch karein, ya default harshitsa2002@gmail.com apply karein
                    String recipientEmail = args.get("notificationEmail", DEFAULT_RECIPIENT_EMAIL);
                    sendSuccessEmail(payloadPath, destinationPath, recipientEmail);
                }
            }
        } catch (Exception e) {
            LOG.error("Error moving asset payload: {}", payloadPath, e);
            throw new WorkflowException("Failed to move asset", e);
        }
    }

    private void sendSuccessEmail(String sourcePath, String destinationPath, String recipientEmail) {
        try {
            HtmlEmail email = new HtmlEmail();
            email.addTo(recipientEmail);
            email.setSubject("AEM Notification: Asset Relocated Successfully");
            
            String htmlMessage = "<h3>Asset Relocation Notification</h3>"
                    + "<p>The asset has been successfully moved within the repository based on MIME metadata evaluation.</p>"
                    + "<ul>"
                    + "<li><b>Source Path:</b> " + sourcePath + "</li>"
                    + "<li><b>Destination Path:</b> " + destinationPath + "</li>"
                    + "</ul>";
            
            email.setHtmlMsg(htmlMessage);

            if (messageGatewayService != null) {
                MessageGateway<HtmlEmail> messageGateway = messageGatewayService.getGateway(HtmlEmail.class);
                if (messageGateway != null) {
                    messageGateway.send(email);
                    LOG.info("Notification email dispatched to {}", recipientEmail);
                } else {
                    LOG.warn("MessageGateway<HtmlEmail> instance unavailable. Verify Day CQ Mail Service OSGi configuration.");
                }
            } else {
                LOG.warn("MessageGatewayService is not bound.");
            }
        } catch (Exception e) {
            LOG.error("Failed to send relocation email to {}", recipientEmail, e);
        }
    }
}