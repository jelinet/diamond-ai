package com.showengine.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummary {
    private String id;
    private String title;
    private String masterPlayer;
    private long createdAt;
    private String lastQuestion;
}
