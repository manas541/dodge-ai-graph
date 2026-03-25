package com.dodgeai.graph_system.model;


import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.util.HashMap;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GraphNode {

    private String id;
    private String type;
    private String label;

    private Map<String, Object> properties = new HashMap<>();

    public GraphNode(String id, String type, String label) {
        this.id = id;
        this.type = type;
        this.label = label;
    }

    public void addProperty(String key, Object value) {
        this.properties.put(key, value);
    }
}