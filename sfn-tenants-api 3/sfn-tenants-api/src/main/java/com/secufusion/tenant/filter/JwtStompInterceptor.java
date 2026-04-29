package com.secufusion.tenant.filter;

import com.nimbusds.jwt.JWTClaimsSet;
import com.secufusion.tenant.entity.Tenant;
import com.secufusion.tenant.exception.AccessDeniedException;
import com.secufusion.tenant.util.JwtUtl;
import com.secufusion.tenant.util.StompPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class JwtStompInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtUtl jwtUtil;

//    @Override
//    public Message<?> preSend(Message<?> message, MessageChannel channel) {
//
//        StompHeaderAccessor accessor =
//                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
//
//        if (accessor == null) {
//            return message;
//        }
//
//        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
//
//            String auth = accessor.getFirstNativeHeader("Authorization");
//            if (auth == null || !auth.startsWith("Bearer ")) {
//                throw new AccessDeniedException("Missing Authorization");
//            }
//
//            // Decode + store tenantId in session
//            String token = auth.substring(7);
//            Tenant tenant = jwtUtil.getTenantFromToken(token);
//            String tenantId = tenant.getTenantID();
//
//            accessor.getSessionAttributes().put("tenantId", tenantId);
//        }
//
//        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
//
//            String destination = accessor.getDestination();
//            String tenantId = (String) accessor.getSessionAttributes().get("tenantId");
//
//            if (destination != null && destination.startsWith("/topic/policy/")) {
//
//                if (!destination.endsWith("/" + tenantId)) {
//                    throw new AccessDeniedException("Invalid tenant subscription");
//                }
//            }
//        }
//
//        return message;
//    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {

            String auth = accessor.getFirstNativeHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) {
                throw new AccessDeniedException("Missing Authorization");
            }

            String token = auth.substring(7);

            // Validate and decode token
            JWTClaimsSet claims = jwtUtil.decodeToken(token);
            if (claims == null) {
                throw new AccessDeniedException("Invalid token");
            }

            Tenant tenant = jwtUtil.getTenantFromToken(token);
            if (tenant == null) {
                throw new AccessDeniedException("Invalid tenant in token");
            }

            accessor.getSessionAttributes().put("tenantId", tenant.getTenantID());

            // Set user principal for user-targeted messaging
            try {
                String userId = claims.getSubject();
                if (userId != null) {
                    accessor.getSessionAttributes().put("userId", userId);
                    accessor.setUser(new StompPrincipal(userId));
                }
            } catch (Exception e) {
                // Non-fatal — user-targeted WS will fall back to tenant broadcast
            }
        }

        return message;
    }


}

