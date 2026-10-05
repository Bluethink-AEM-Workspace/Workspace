package com.workspace.core.workflows;

import com.day.cq.mailer.MessageGateway;
import com.day.cq.mailer.MessageGatewayService;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;
import org.apache.commons.mail.Email;
import org.apache.commons.mail.SimpleEmail;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Session;

@Component(
    service = WorkflowProcess.class,
    property = {
        "process.label=Move Asset Based on Metadata Schema Category"
    }
)
public class MoveAssetByMetadataProcess implements WorkflowProcess {

    private static final Logger log = LoggerFactory.getLogger(MoveAssetByMetadataProcess.class);

    private static final String BASE_DAM_PATH = "/content/dam/workflow/";
    private static final String FAILURE_FOLDER = "/content/dam/workflow/failure";
    private static final String NOTIFICATION_EMAIL = "harshitsa2002@gmail.com";
    private static final String CATEGORY_PROPERTY = "category";

    @Reference
    private MessageGatewayService messageGatewayService;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap metaDataMap) throws WorkflowException {
        Session session = workflowSession.getSession();

        try {
            String rawPayload = workItem.getWorkflowData().getPayload().toString();
            log.info("Processing Workflow for Raw Payload: {}", rawPayload);

            // Clean payload path (launcher sends path with /jcr:content/metadata)
            String payloadPath = rawPayload;
            if (payloadPath.contains("/jcr:content")) {
                payloadPath = payloadPath.substring(0, payloadPath.indexOf("/jcr:content"));
            }

            if (!session.nodeExists(payloadPath)) {
                log.error("Asset node not found at path: {}", payloadPath);
                return;
            }

            Node assetNode = session.getNode(payloadPath);
            if (!assetNode.isNodeType("dam:Asset")) {
                log.error("Payload path is not a dam:Asset: {}", payloadPath);
                return;
            }

            // Fetch Category property directly from JCR metadata
            String metadataPath = payloadPath + "/jcr:content/metadata";
            String selectedCategory = "";

            if (session.nodeExists(metadataPath)) {
                Node metadataNode = session.getNode(metadataPath);
                if (metadataNode.hasProperty(CATEGORY_PROPERTY)) {
                    selectedCategory = metadataNode.getProperty(CATEGORY_PROPERTY).getString();
                }
            }

            String assetName = payloadPath.substring(payloadPath.lastIndexOf("/") + 1);

            if (selectedCategory == null || selectedCategory.trim().isEmpty()) {
                log.warn("No category selected in metadata for asset: {}", assetName);
                moveToFolder(session, payloadPath, FAILURE_FOLDER, assetName, "No category chosen in metadata schema dropdown.");
                return;
            }

            String targetCategory = selectedCategory.toLowerCase().trim();

            // Plural / Exact category folder mappings
            if ("laptop".equals(targetCategory) || "laptops".equals(targetCategory)) {
                targetCategory = "laptops";
            } else if ("phone".equals(targetCategory) || "phones".equals(targetCategory)) {
                targetCategory = "phones";
            } else if ("earphone".equals(targetCategory) || "earphones".equals(targetCategory)) {
                targetCategory = "earphones";
            } else if ("tablet".equals(targetCategory) || "tablets".equals(targetCategory)) {
                targetCategory = "tablets";
            } else if ("smartwatch".equals(targetCategory) || "smartwatches".equals(targetCategory)) {
                targetCategory = "smartwatches";
            }

            String targetFolderPath = BASE_DAM_PATH + targetCategory;
            String targetAssetPath = targetFolderPath + "/" + assetName;

            // Prevent overwrite if duplicate exists in target folder
            if (session.nodeExists(targetAssetPath)) {
                log.warn("Duplicate asset found in target folder: {}", targetAssetPath);
                moveToFolder(session, payloadPath, FAILURE_FOLDER, assetName, "Duplicate asset already exists in '" + targetCategory + "' folder.");
            } else {
                log.info("Moving asset from {} to category folder: {}", payloadPath, targetAssetPath);

                moveToFolder(session, payloadPath, targetFolderPath, assetName, null);

                sendEmailNotification(
                    "Asset Moved Successfully to Category: " + selectedCategory,
                    "Asset '" + assetName + "' moved successfully to: " + targetAssetPath
                );
            }

        } catch (Exception e) {
            log.error("Error executing MoveAssetByMetadataProcess", e);
            throw new WorkflowException("Process Failed: " + e.getMessage(), e);
        }
    }

    private void moveToFolder(Session session, String sourcePath, String targetFolderPath, String assetName, String failureReason) {
        try {
            // Ensure target folder exists before calling session.move
            ensureFolderExists(session, targetFolderPath);

            String finalTargetPath = targetFolderPath + "/" + assetName;

            if (!sourcePath.equals(finalTargetPath)) {
                session.move(sourcePath, finalTargetPath);
                session.save();
                log.info("Successfully moved node to: {}", finalTargetPath);
            }

            if (failureReason != null) {
                sendEmailNotification(
                    "Workflow Warning: Asset Moved to " + targetFolderPath,
                    "Asset '" + assetName + "' action message: " + failureReason + "\nMoved to: " + finalTargetPath
                );
            }
        } catch (Exception e) {
            log.error("Failed moving node from {} to {}", sourcePath, targetFolderPath, e);
        }
    }

    private void ensureFolderExists(Session session, String folderPath) throws Exception {
        if (!session.nodeExists(folderPath)) {
            String[] pathSegments = folderPath.split("/");
            String currentPath = "";
            for (String segment : pathSegments) {
                if (segment.isEmpty()) continue;
                currentPath += "/" + segment;
                if (!session.nodeExists(currentPath)) {
                    Node parentNode = session.getNode(currentPath.substring(0, currentPath.lastIndexOf("/")));
                    Node newFolder = parentNode.addNode(segment, "sling:Folder");
                    newFolder.addNode("jcr:content", "nt:unstructured");
                }
            }
            session.save();
        }
    }

    private void sendEmailNotification(String subject, String bodyContent) {
        try {
            if (messageGatewayService != null) {
                MessageGateway<Email> messageGateway = messageGatewayService.getGateway(SimpleEmail.class);
                Email email = new SimpleEmail();
                email.addTo(NOTIFICATION_EMAIL);
                email.setSubject(subject);
                email.setMsg(bodyContent);

                if (messageGateway != null) {
                    messageGateway.send(email);
                }
            }
        } catch (Exception e) {
            log.error("Failed sending email notification", e);
        }
    }
}