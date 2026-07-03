package com.showengine.model.sse;

import com.showengine.model.SubTask;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecompositionEvent {
    private String conversationId;
    private List<SubTask> subtasks;
}
