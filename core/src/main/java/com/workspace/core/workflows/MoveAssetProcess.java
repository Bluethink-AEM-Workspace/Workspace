package com.workspace.core.workflows;

import com.day.cq.commons.jcr.JcrUtil;
import com.day.cq.workflow.WorkflowException;
import com.day.cq.workflow.WorkflowSession;
import com.day.cq.workflow.exec.WorkItem;
import com.day.cq.workflow.exec.WorkflowProcess;
import com.day.cq.workflow.metadata.MetaDataMap;
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
        "process.label=Custom Category Move Asset Process"
    }
)
public class MoveAssetProcess implements WorkflowProcess {

    private static final Logger LOG = LoggerFactory.getLogger(MoveAssetProcess.class);

    // Schema Dropdown me mapped Property Name
    private static final String CATEGORY_PROPERTY = "deviceType"; 

    @Reference
    private ResourceResolverFactory resolverFactory;

    @Override
    public void execute(WorkItem workItem, WorkflowSession workflowSession, MetaDataMap args) throws WorkflowException {
        String rawPayload = workItem.getWorkflowData().getPayload().toString();
        
        // Remove /jcr:content suffix if payload points to content node
        String assetPath = rawPayload.endsWith("/jcr:content") 
                ? rawPayload.substring(0, rawPayload.indexOf("/jcr:content")) 
                : rawPayload;

        try {
            Session session = workflowSession.getSession();
            Map<String, Object> authInfo = new HashMap<>();
            authInfo.put(JcrResourceConstants.AUTHENTICATION_INFO_SESSION, session);

            try (ResourceResolver resolver = resolverFactory.getResourceResolver(authInfo)) {

                // Read destination root path passed from Workflow Process Dialog Arguments
                String customDestPath = args.get("destPath", String.class);
                String baseFolderPath = (customDestPath != null && !customDestPath.trim().isEmpty()) 
                        ? customDestPath.trim() 
                        : "/content/dam/workflow";

                // Safety guard: execute only if inside incoming directory
                if (!assetPath.startsWith(baseFolderPath + "/incoming")) {
                    LOG.info("Asset path {} is not in incoming directory. Skipping execution.", assetPath);
                    return;
                }

                // 1. Read Schema Dropdown Metadata Property
                Resource metadataResource = resolver.getResource(assetPath + "/jcr:content/metadata");
                String selectedCategory = "";

                if (metadataResource != null) {
                    ValueMap metadata = metadataResource.getValueMap();
                    selectedCategory = metadata.get(CATEGORY_PROPERTY, String.class);
                }

                if (selectedCategory == null || selectedCategory.trim().isEmpty()) {
                    LOG.warn("No category selected in metadata property '{}' for asset: {}. Skipping move.", CATEGORY_PROPERTY, assetPath);
                    return;
                }

                selectedCategory = selectedCategory.trim().toLowerCase();

                // 2. Define Dynamic Destination Folder (/content/dam/workflow/laptop etc.)
                String targetFolder = baseFolderPath + "/" + selectedCategory;

                // 3. Create Target Directory if missing (Fixed: using sling:Folder)
                if (!session.nodeExists(targetFolder)) {
                    JcrUtil.createPath(targetFolder, "sling:Folder", "sling:Folder", session, false);
                }

                // 4. Relocate Asset Node
                String assetName = assetPath.substring(assetPath.lastIndexOf("/"));
                String destinationPath = targetFolder + assetName;

                session.move(assetPath, destinationPath);
                session.save();

                LOG.info("Asset successfully moved based on category '{}' from {} to {}", selectedCategory, assetPath, destinationPath);
            }
        } catch (Exception e) {
            LOG.error("Error executing MoveAssetProcess for payload: {}", assetPath, e);
            throw new WorkflowException("Failed to route category asset", e);
        }
    }
}