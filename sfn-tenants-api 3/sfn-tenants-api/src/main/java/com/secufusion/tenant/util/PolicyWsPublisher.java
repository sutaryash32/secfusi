//package com.secufusion.tenant.util;
//
//import com.secufusion.tenant.dto.PolicyChangeEvent;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//import org.springframework.messaging.simp.user.SimpUserRegistry;
//import org.springframework.stereotype.Service;
//
//@Service
//@Slf4j
//public class PolicyWsPublisher {
//
//    private final SimpMessagingTemplate messagingTemplate;
//
//    public PolicyWsPublisher(SimpMessagingTemplate messagingTemplate) {
//        this.messagingTemplate = messagingTemplate;
//    }
//
////    public void publish(PolicyChangeEvent event) {
////
////        String tenantId = event.getTenantId();
////        String destination = "/topic/policy/" + tenantId;
////
////        log.info("Publishing policy event to WS destination: {}", destination);
////
////        messagingTemplate.convertAndSend(destination, event);
////    }
//    @Autowired
//    private SimpUserRegistry simpUserRegistry;
//
//    public void publish(PolicyChangeEvent event) {
//        log.info("WS users connected: {}", simpUserRegistry.getUserCount());
//        messagingTemplate.convertAndSend("/topic/policy/" + event.getTenantId(), event);
//    }
//}
//
