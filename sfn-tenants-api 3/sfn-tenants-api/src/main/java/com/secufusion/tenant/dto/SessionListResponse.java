package com.secufusion.tenant.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionListResponse {

    private List<SessionDTO> sessions;

    private int totalCount;

    private int activeUsers;

    // Pagination fields
    private int page;

    private int pageSize;

    private int totalPages;

    private boolean hasNext;

    private boolean hasPrevious;

    public static SessionListResponse of(List<SessionDTO> sessions) {
        long uniqueUsers = sessions.stream()
                .map(SessionDTO::getUserId)
                .distinct()
                .count();

        return SessionListResponse.builder()
                .sessions(sessions)
                .totalCount(sessions.size())
                .activeUsers((int) uniqueUsers)
                .page(0)
                .pageSize(sessions.size())
                .totalPages(1)
                .hasNext(false)
                .hasPrevious(false)
                .build();
    }

    public static SessionListResponse of(List<SessionDTO> sessions, int page, int pageSize, int totalCount) {
        long uniqueUsers = sessions.stream()
                .map(SessionDTO::getUserId)
                .distinct()
                .count();

        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        return SessionListResponse.builder()
                .sessions(sessions)
                .totalCount(totalCount)
                .activeUsers((int) uniqueUsers)
                .page(page)
                .pageSize(pageSize)
                .totalPages(totalPages)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }

    public static SessionListResponse empty() {
        return SessionListResponse.builder()
                .sessions(List.of())
                .totalCount(0)
                .activeUsers(0)
                .page(0)
                .pageSize(0)
                .totalPages(0)
                .hasNext(false)
                .hasPrevious(false)
                .build();
    }
}
