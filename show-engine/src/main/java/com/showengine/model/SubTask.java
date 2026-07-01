package com.showengine.model;

import com.showengine.enums.PlayerEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubTask {

    /** Which player executes this sub-task (PITCHER / CATCHER / FIELDER) */
    private PlayerEnum player;

    /** Short label shown in the UI right panel, such as "analyze market competition". */
    private String taskDescription;

    /** Expected output format or deliverable, such as "a market analysis report". */
    private String deliverable;
}
