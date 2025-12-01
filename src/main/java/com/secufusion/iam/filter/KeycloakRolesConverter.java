////package com.secufusion.features.filter;
////
////import com.fasterxml.jackson.databind.ObjectMapper;
////import org.springframework.security.core.GrantedAuthority;
////import org.springframework.security.core.authority.SimpleGrantedAuthority;
////import org.springframework.security.oauth2.jwt.Jwt;
////import org.springframework.core.convert.converter.Converter;
////
////
////import java.util.Collection;
////import java.util.HashSet;
////import java.util.List;
////import java.util.Map;
////
////public class KeycloakRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
////
////    private static final String RESOURCE_ACCESS = "resource_access";
////
////    private static final String ROLES = "roles";
////
////    private static final String ROLE_PREFIX = "ROLE_";
////
////    private final ObjectMapper mapper = new ObjectMapper();
////
////    @Override
////
////    public Collection<GrantedAuthority> convert(Jwt jwt) {
////        Collection<GrantedAuthority> authorities = new HashSet<>();
////        Object resourceAccessObj = jwt.getClaim(RESOURCE_ACCESS);
////        if (resourceAccessObj == null) return authorities;
////        try {
////            Map<String, Object> resourceAccess = mapper.convertValue(resourceAccessObj, Map.class);
////            for (Map.Entry<String, Object> entry : resourceAccess.entrySet()) {
////                Object clientObj = entry.getValue();
////                if (!(clientObj instanceof Map)) continue;
////                Map<String, Object> clientMap = (Map<String, Object>) clientObj;
////                Object rolesObj = clientMap.get(ROLES);
////                if (rolesObj == null) continue;
////                List<String> roles = mapper.convertValue(rolesObj, List.class);
////                if (roles == null) continue;
////                for (String r : roles) {
////                    if (r == null || r.isBlank()) continue;
////                    authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + r));
////                }
////            }
////        } catch (Exception ex) {
////
////        }
////        return authorities;
////    }
////
////}
//
//package com.secufusion.iam.filter;
//
//import org.springframework.core.convert.converter.Converter;
//import org.springframework.security.core.GrantedAuthority;
//import org.springframework.security.core.authority.SimpleGrantedAuthority;
//import org.springframework.security.oauth2.jwt.Jwt;
//import java.util.*;
//
//public class KeycloakRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
//    @Override
//    public Collection<GrantedAuthority> convert(Jwt jwt) {
//        Collection<GrantedAuthority> authorities = new HashSet<>();
//
//        // Add client roles:
//        Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
//        if (resourceAccess != null) {
//            for (Map.Entry<String, Object> entry : resourceAccess.entrySet()) {
//                Object value = entry.getValue();
//                if (value instanceof Map) {
//                    Object rolesObj = ((Map<?, ?>) value).get("roles");
//                    if (rolesObj instanceof Collection) {
//                        for (Object role : (Collection<?>) rolesObj) {
//                            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
//                        }
//                    }
//                }
//            }
//        }
//        return authorities;
//    }
//}
