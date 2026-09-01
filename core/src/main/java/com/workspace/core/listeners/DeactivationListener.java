// package com.workspace.core.listeners; // Apne package ka naam check kar lena, VS code ke hisaab se com.workspace.core hai

// import com.day.cq.replication.ReplicationAction;
// import org.apache.sling.event.jobs.JobManager;
// import org.osgi.service.component.annotations.Component;
// import org.osgi.service.component.annotations.Reference;
// import org.osgi.service.event.Event;
// import org.osgi.service.event.EventConstants;
// import org.osgi.service.event.EventHandler;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// import java.util.HashMap;
// import java.util.Map;

// @Component(service = EventHandler.class, immediate = true, property = {
//     EventConstants.EVENT_TOPIC + "=" + ReplicationAction.EVENT_TOPIC
// })
// public class DeactivationListener implements EventHandler {

//     private static final Logger log = LoggerFactory.getLogger(DeactivationListener.class);

//     @Reference
//     private JobManager jobManager;

//     @Override
//     public void handleEvent(Event event) {
//         try {
//             ReplicationAction action = ReplicationAction.fromEvent(event);
            
//             // Check: Action DEACTIVATE hona chahiye aur DAM ke andar hona chahiye
//             if (action != null && ReplicationAction.Type.DEACTIVATE.equals(action.getType()) 
//                 && action.getPath().startsWith("/content/dam/")) {
                
//                 log.info("🎯 AEM Logger: Deactivation event detected for -> {}", action.getPath());
                
//                 Map<String, Object> jobProps = new HashMap<>();
//                 jobProps.put("assetPath", action.getPath());
//                 jobProps.put("userId", action.getUserId());
                
//                 // Job start kar rahe hain
//                 jobManager.addJob("workspace/dam/asset/deactivate", jobProps);
//             }
//         } catch (Exception e) {
//             log.error("❌ AEM Logger: Error handling deactivation event", e);
//         }
//     }
// }