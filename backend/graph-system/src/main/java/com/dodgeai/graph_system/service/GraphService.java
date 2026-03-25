package com.dodgeai.graph_system.service;

import com.dodgeai.graph_system.model.GraphEdge;
import com.dodgeai.graph_system.model.GraphNode;
import lombok.extern.slf4j.Slf4j;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@DependsOn("dataLoader")
public class GraphService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Graph<String, DefaultEdge> jGraph;
    private final Map<String, GraphNode> nodeMap = new ConcurrentHashMap<>();
    private final List<GraphEdge> edgeList = Collections.synchronizedList(new ArrayList<>());

    @PostConstruct
    public void buildGraph() {
        jGraph = new DefaultDirectedGraph<>(DefaultEdge.class);
        nodeMap.clear();
        edgeList.clear();

        log.info("Building SAP O2C graph...");

        loadCustomerNodes();
        loadProductNodes();
        loadSalesOrderNodes();
        loadSalesOrderItemNodes();
        loadDeliveryNodes();
        loadBillingNodes();
        loadJournalEntryNodes();
        loadPaymentNodes();

        log.info("Graph built: {} nodes, {} edges", nodeMap.size(), edgeList.size());
    }


    // NODE LOADERS

    private void loadCustomerNodes() {
        jdbcTemplate.query("""
            SELECT bp.*, a.city_name, a.country
            FROM business_partners bp
            LEFT JOIN business_partner_addresses a
            ON bp.business_partner = a.business_partner
        """, rs -> {
            String id = "BP-" + rs.getString("business_partner");
            GraphNode node = new GraphNode(id, "Customer",
                    rs.getString("full_name"));
            node.addProperty("businessPartner", rs.getString("business_partner"));
            node.addProperty("customer", rs.getString("customer"));
            node.addProperty("city", rs.getString("city_name"));
            node.addProperty("country", rs.getString("country"));
            node.addProperty("blocked", rs.getBoolean("is_blocked"));
            addNode(node);
        });
        log.debug("Loaded {} customer nodes", nodeMap.size());
    }

    private void loadProductNodes() {
        jdbcTemplate.query("""
            SELECT p.*, pd.product_description
            FROM products p
            LEFT JOIN product_descriptions pd
            ON p.product = pd.product AND pd.language = 'EN'
        """, rs -> {
            String id = "PROD-" + rs.getString("product");
            String desc = rs.getString("product_description");
            GraphNode node = new GraphNode(id, "Product",
                    (desc != null && !desc.isEmpty()) ? desc : rs.getString("product"));
            node.addProperty("product", rs.getString("product"));
            node.addProperty("productOldId", rs.getString("product_old_id"));
            node.addProperty("productGroup", rs.getString("product_group"));
            node.addProperty("baseUnit", rs.getString("base_unit"));
            addNode(node);
        });
    }

    private void loadSalesOrderNodes() {
        jdbcTemplate.query("SELECT * FROM sales_order_headers", rs -> {
            String id = "SO-" + rs.getString("sales_order");
            GraphNode node = new GraphNode(id, "SalesOrder",
                    "SO: " + rs.getString("sales_order"));
            node.addProperty("salesOrder", rs.getString("sales_order"));
            node.addProperty("creationDate", rs.getString("creation_date"));
            node.addProperty("totalNetAmount", rs.getDouble("total_net_amount"));
            node.addProperty("currency", rs.getString("transaction_currency"));
            node.addProperty("deliveryStatus", rs.getString("overall_delivery_status"));
            node.addProperty("billingStatus", rs.getString("overall_billing_status"));
            addNode(node);

            // Edge: Customer → SalesOrder
            String bpId = "BP-" + rs.getString("sold_to_party");
            addEdge(bpId, id, "PLACED_ORDER", "Placed");
        });
    }

    private void loadSalesOrderItemNodes() {
        jdbcTemplate.query("SELECT * FROM sales_order_items", rs -> {
            String id = "SOI-" + rs.getString("sales_order")
                    + "-" + rs.getString("sales_order_item");
            GraphNode node = new GraphNode(id, "SalesOrderItem",
                    "Item " + rs.getString("sales_order_item"));
            node.addProperty("salesOrder", rs.getString("sales_order"));
            node.addProperty("item", rs.getString("sales_order_item"));
            node.addProperty("material", rs.getString("material"));
            node.addProperty("quantity", rs.getDouble("requested_quantity"));
            node.addProperty("netAmount", rs.getDouble("net_amount"));
            node.addProperty("plant", rs.getString("production_plant"));
            addNode(node);

            // Edge: SalesOrder → SalesOrderItem
            addEdge("SO-" + rs.getString("sales_order"), id,
                    "HAS_ITEM", "Contains");

            // Edge: SalesOrderItem → Product
            addEdge(id, "PROD-" + rs.getString("material"),
                    "IS_MATERIAL", "Material");
        });
    }

    private void loadDeliveryNodes() {
        // Load delivery headers
        jdbcTemplate.query("SELECT * FROM outbound_delivery_headers", rs -> {
            String id = "DEL-" + rs.getString("delivery_document");
            GraphNode node = new GraphNode(id, "Delivery",
                    "DEL: " + rs.getString("delivery_document"));
            node.addProperty("deliveryDocument", rs.getString("delivery_document"));
            node.addProperty("shippingPoint", rs.getString("shipping_point"));
            node.addProperty("creationDate", rs.getString("creation_date"));
            node.addProperty("goodsMovementStatus",
                    rs.getString("goods_movement_status"));
            node.addProperty("pickingStatus", rs.getString("picking_status"));
            addNode(node);
        });

        // Edge: SalesOrder → Delivery (via delivery items)
        jdbcTemplate.query("""
            SELECT DISTINCT delivery_document, reference_sd_document
            FROM outbound_delivery_items
            WHERE reference_sd_document IS NOT NULL
            AND reference_sd_document != ''
        """, rs -> {
            String delId = "DEL-" + rs.getString("delivery_document");
            String soId  = "SO-"  + rs.getString("reference_sd_document");
            addEdge(soId, delId, "HAS_DELIVERY", "Delivered via");
        });

        jdbcTemplate.query("SELECT DISTINCT plant FROM outbound_delivery_items WHERE plant != ''", rs -> {
            String plantId = "PLANT-" + rs.getString("plant");
            if (!nodeMap.containsKey(plantId)) {
                GraphNode plantNode = new GraphNode(plantId, "Plant", "Plant: " + rs.getString("plant"));
                plantNode.addProperty("plant", rs.getString("plant"));
                addNode(plantNode);
            }
        });

// Link delivery items to plants
        jdbcTemplate.query("SELECT DISTINCT delivery_document, plant FROM outbound_delivery_items WHERE plant != ''", rs -> {
            String delId   = "DEL-"   + rs.getString("delivery_document");
            String plantId = "PLANT-" + rs.getString("plant");
            addEdge(delId, plantId, "SHIPS_FROM", "Plant");
        });
    }

    private void loadBillingNodes() {
        // Load billing headers
        jdbcTemplate.query("SELECT * FROM billing_document_headers", rs -> {
            String id = "BILL-" + rs.getString("billing_document");
            GraphNode node = new GraphNode(id, "Invoice",
                    "INV: " + rs.getString("billing_document"));
            node.addProperty("billingDocument", rs.getString("billing_document"));
            node.addProperty("billingDate", rs.getString("billing_document_date"));
            node.addProperty("totalNetAmount", rs.getDouble("total_net_amount"));
            node.addProperty("currency", rs.getString("transaction_currency"));
            node.addProperty("cancelled", rs.getBoolean("is_cancelled"));
            node.addProperty("accountingDocument", rs.getString("accounting_document"));
            addNode(node);
        });

        // Edge: Delivery → Invoice (via billing items referenceSdDocument)
        jdbcTemplate.query("""
            SELECT DISTINCT billing_document, reference_sd_document
            FROM billing_document_items
            WHERE reference_sd_document IS NOT NULL
            AND reference_sd_document != ''
        """, rs -> {
            String billId = "BILL-" + rs.getString("billing_document");
            String refDoc  = rs.getString("reference_sd_document");

            // referenceSdDocument can be a delivery OR a sales order
            String delId = "DEL-" + refDoc;
            String soId  = "SO-"  + refDoc;

            if (nodeMap.containsKey(delId)) {
                addEdge(delId, billId, "HAS_INVOICE", "Billed via");
            } else if (nodeMap.containsKey(soId)) {
                addEdge(soId, billId, "HAS_INVOICE", "Billed via");
            }
        });
    }

    private void loadJournalEntryNodes() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM journal_entries", Integer.class);
        log.info("Journal entries in DB: {}", count);

        jdbcTemplate.query("""
        SELECT DISTINCT ON (je.accounting_document)
               je.accounting_document,
               je.accounting_document_item,
               je.customer,
               je.reference_document,
               je.amount,
               je.transaction_currency,
               je.posting_date,
               je.document_type,
               je.clearing_accounting_document
        FROM journal_entries je
        ORDER BY je.accounting_document, je.accounting_document_item
        LIMIT 200
    """, rs -> {
            String acctDoc = rs.getString("accounting_document");
            String id = "JE-" + acctDoc;

            if (nodeMap.containsKey(id)) return;

            GraphNode node = new GraphNode(id, "JournalEntry",
                    "JE: " + acctDoc);
            node.addProperty("accountingDocument", acctDoc);
            node.addProperty("amount",
                    String.valueOf(rs.getDouble("amount")));
            node.addProperty("currency",
                    rs.getString("transaction_currency"));
            node.addProperty("postingDate",
                    rs.getString("posting_date"));
            node.addProperty("docType",
                    rs.getString("document_type"));
            node.addProperty("referenceDocument",
                    rs.getString("reference_document"));
            node.addProperty("customer",
                    rs.getString("customer"));
            addNode(node);

            // Link to billing doc via reference_document
            String refDoc = rs.getString("reference_document");
            if (refDoc != null && !refDoc.isEmpty()) {
                String billId = "BILL-" + refDoc;
                if (nodeMap.containsKey(billId)) {
                    addEdge(billId, id, "HAS_JOURNAL_ENTRY", "Posted to");
                    log.debug("✓ Edge: {} → {}", billId, id);
                } else {
                    log.debug("✗ No billing node for ref: {}", refDoc);
                }
            }
        });

        long jeCount = nodeMap.values().stream()
                .filter(n -> "JournalEntry".equals(n.getType()))
                .count();
        log.info("JournalEntry nodes created: {}", jeCount);
    }
    private void loadPaymentNodes() {
        jdbcTemplate.query("SELECT * FROM payments", rs -> {
            String acctDoc = rs.getString("accounting_document");
            String acctItem = rs.getString("accounting_document_item");
            String id = "PAY-" + acctDoc + "-" + acctItem;

            GraphNode node = new GraphNode(id, "Payment",
                    "PAY: " + acctDoc);
            node.addProperty("accountingDocument", acctDoc);
            node.addProperty("customer",
                    rs.getString("customer"));
            node.addProperty("amount",
                    rs.getString("amount"));       // getString not getDouble
            node.addProperty("currency",
                    rs.getString("transaction_currency"));
            node.addProperty("clearingDate",
                    rs.getString("clearing_date"));
            node.addProperty("postingDate",
                    rs.getString("posting_date"));
            addNode(node);

            // Edge: Invoice → Payment
            String clearDoc = rs.getString("clearing_accounting_document");
            if (clearDoc != null && !clearDoc.isEmpty()) {
                jdbcTemplate.query("""
                SELECT billing_document FROM billing_document_headers
                WHERE accounting_document = ? LIMIT 1
            """, new Object[]{clearDoc}, brs -> {
                    String billId = "BILL-" + brs.getString("billing_document");
                    addEdge(billId, id, "SETTLED_BY", "Paid");
                });
            }
        });

        log.info("Payment nodes loaded: {}",
                nodeMap.values().stream()
                        .filter(n -> "Payment".equals(n.getType()))
                        .count());
    }


    // HELPERS
    private void addNode(GraphNode node) {
        nodeMap.put(node.getId(), node);
        jGraph.addVertex(node.getId());
    }

    private void addEdge(String src, String tgt,
                         String rel, String label) {
        if (!nodeMap.containsKey(src)) return;
        if (!nodeMap.containsKey(tgt)) return;
        if (jGraph.containsEdge(src, tgt)) return;
        jGraph.addEdge(src, tgt);
        edgeList.add(new GraphEdge(
                src + "->" + tgt, src, tgt, rel, label));
    }


    // PUBLIC API
    public Collection<GraphNode> getAllNodes() {
        return nodeMap.values();
    }

    public List<GraphEdge> getAllEdges() {
        return edgeList;
    }

    public GraphNode getNode(String id) {
        return nodeMap.get(id);
    }

    public Map<String, Object> getNeighborhood(String nodeId) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();

        GraphNode center = nodeMap.get(nodeId);
        if (center == null) return Map.of("nodes", nodes, "edges", edges);
        nodes.add(center);

        for (GraphEdge e : edgeList) {
            if (e.getSource().equals(nodeId) || e.getTarget().equals(nodeId)) {
                edges.add(e);
                String otherId = e.getSource().equals(nodeId)
                        ? e.getTarget() : e.getSource();
                GraphNode other = nodeMap.get(otherId);
                if (other != null && !nodes.contains(other))
                    nodes.add(other);
            }
        }
        return Map.of("nodes", nodes, "edges", edges);
    }

    public String getSchemaDescription() {
        return """
            DATABASE SCHEMA (SAP Order-to-Cash, PostgreSQL):

            TABLE: sales_order_headers
              sales_order (PK), sales_order_type, sales_organization,
              sold_to_party (FK → business_partners.business_partner),
              creation_date, total_net_amount, transaction_currency,
              overall_delivery_status (A=Not started, B=Partial, C=Complete),
              overall_billing_status (A=Not billed, B=Partial, C=Fully billed),
              requested_delivery_date, customer_payment_terms

            TABLE: sales_order_items
              sales_order (FK), sales_order_item, material (FK → products.product),
              requested_quantity, quantity_unit, net_amount,
              transaction_currency, material_group, production_plant

            TABLE: outbound_delivery_headers
              delivery_document (PK), shipping_point, creation_date,
              actual_goods_movement_date, goods_movement_status, picking_status

            TABLE: outbound_delivery_items
              delivery_document (FK), delivery_document_item,
              reference_sd_document (FK → sales_order_headers.sales_order),
              plant, storage_location, actual_delivery_quantity, delivery_quantity_unit

            TABLE: billing_document_headers
              billing_document (PK), billing_document_type,
              sold_to_party (FK → business_partners),
              creation_date, billing_document_date, total_net_amount,
              transaction_currency, company_code, fiscal_year,
              accounting_document, is_cancelled (boolean)

            TABLE: billing_document_items
              billing_document (FK), billing_document_item, material,
              billing_quantity, net_amount, transaction_currency,
              reference_sd_document (FK → outbound_delivery_headers.delivery_document)

            TABLE: billing_document_cancellations
              billing_document (PK), sold_to_party, creation_date,
              total_net_amount, transaction_currency,
              accounting_document, cancelled_billing_document

            TABLE: payments
              accounting_document, accounting_document_item (composite PK),
              customer (FK → business_partners.customer),
              clearing_date, clearing_accounting_document,
              amount, transaction_currency, posting_date, gl_account

            TABLE: journal_entries
              accounting_document, accounting_document_item (composite PK),
              customer (FK), gl_account,
              reference_document (FK → billing_document_headers.billing_document),
              amount, transaction_currency, posting_date,
              document_type, clearing_date, clearing_accounting_document

            TABLE: business_partners
              business_partner (PK), customer, full_name,
              is_blocked (boolean), creation_date

            TABLE: business_partner_addresses
              business_partner (PK FK), city_name, country, region,
              street_name, postal_code

            TABLE: products
              product (PK), product_type, product_old_id,
              product_group, base_unit, gross_weight, net_weight, weight_unit

            TABLE: product_descriptions
              product (FK), language, product_description
              (always JOIN with language = 'EN' for English names)

            TABLE: plants
              plant (PK), plant_name, company_code, country, city_name

            KEY JOINS:
            - business_partners.business_partner = sales_order_headers.sold_to_party
            - sales_order_headers.sales_order = sales_order_items.sales_order
            - sales_order_items.material = products.product
            - outbound_delivery_items.reference_sd_document = sales_order_headers.sales_order
            - billing_document_items.reference_sd_document = outbound_delivery_headers.delivery_document
            - journal_entries.reference_document = billing_document_headers.billing_document
            - payments.clearing_accounting_document links to journal_entries

            BUSINESS FLOW:
            SalesOrder → Delivery → Invoice → JournalEntry → Payment

            BROKEN FLOW DETECTION:
            - overall_delivery_status != 'C' means not fully delivered
            - overall_billing_status != 'C' means not fully billed
            - is_cancelled = true means billing doc was cancelled
            - LEFT JOIN to find orders with no delivery or no invoice
            """;
    }

    public JdbcTemplate getJdbcTemplate() {
        return jdbcTemplate;
    }
}