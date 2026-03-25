package com.dodgeai.graph_system.model;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GraphEdge {

    private String id;
    private String source;
    private String target;
    private String relationship;
    private String label;
}