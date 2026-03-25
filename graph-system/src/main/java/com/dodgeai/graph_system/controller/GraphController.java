package com.dodgeai.graph_system.controller;


import com.dodgeai.graph_system.service.GraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/graph")
@CrossOrigin(origins = "*")
public class GraphController {

    @Autowired
    private GraphService graphService;

    // Get ALL nodes and edges
    @GetMapping
    public ResponseEntity<Map<String, Object>> getFullGraph() {
        return ResponseEntity.ok(Map.of(
                "nodes", graphService.getAllNodes(),
                "edges", graphService.getAllEdges()
        ));
    }

    // Get neighbors of one node (for expand on click)
    @GetMapping("/node/{nodeId}")
    public ResponseEntity<Map<String, Object>> getNeighborhood(
            @PathVariable String nodeId) {
        return ResponseEntity.ok(
                graphService.getNeighborhood(nodeId));
    }

    // Health check + counts
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "nodeCount", graphService.getAllNodes().size(),
                "edgeCount", graphService.getAllEdges().size(),
                "status", "ok"
        ));
    }

    // Rebuild graph from DB (useful after data changes)
    @PostMapping("/rebuild")
    public ResponseEntity<Map<String, Object>> rebuild() {
        graphService.buildGraph();
        return ResponseEntity.ok(Map.of(
                "message", "Graph rebuilt successfully",
                "nodeCount", graphService.getAllNodes().size(),
                "edgeCount", graphService.getAllEdges().size()
        ));
    }
}
