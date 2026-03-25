package com.dodgeai.graph_system.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.util.*;

@Slf4j
@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    @Autowired
    private GraphService graphService;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Map<String, String>> conversationHistory = new ArrayList<>();


    public Map<String, Object> query(String userMessage) {
        Map<String, Object> result = new HashMap<>();

        if (!isRelevantQuery(userMessage)) {
            result.put("answer",
                    "This system is designed to answer questions about the " +
                            "SAP business dataset only — sales orders, deliveries, " +
                            "invoices, payments, customers and products.");
            result.put("sql", null);
            result.put("data", null);
            result.put("blocked", true);
            return result;
        }

        try {
            String sql = generateSQL(userMessage);
            log.info("Generated SQL: {}", sql);
            result.put("sql", sql);

            List<Map<String, Object>> data = executeSQL(sql);
            result.put("data", data);

            String answer = generateAnswer(userMessage, sql, data);
            result.put("answer", answer);
            result.put("blocked", false);

            conversationHistory.add(Map.of("role", "user", "content", userMessage));
            conversationHistory.add(Map.of("role", "assistant", "content", answer));
            if (conversationHistory.size() > 20)
                conversationHistory.subList(0, 2).clear();

        } catch (Exception e) {
            log.error("Query error: {}", e.getMessage(), e);
            result.put("answer",
                    "Sorry, I ran into an error processing that. " +
                            "Please try rephrasing your question.");
            result.put("sql", null);
            result.put("data", null);
            result.put("blocked", false);
        }

        return result;
    }

    private boolean isRelevantQuery(String query) {
        String q = query.toLowerCase();

        String[] blocked = {
                "poem", "story", "recipe", "weather", "joke",
                "capital of", "who invented", "bitcoin", "stock price",
                "movie", "song", "sports score", "translate", "write code",
                "how to cook", "president of", "history of"
        };
        for (String b : blocked) {
            if (q.contains(b)) return false;
        }

        String[] allowed = {
                "order", "delivery", "invoice", "payment", "customer",
                "product", "billing", "material", "sales", "plant",
                "journal", "amount", "status", "date", "flow", "trace",
                "incomplete", "broken", "pending", "cancelled", "paid",
                "unpaid", "quantity", "price", "total", "count", "list",
                "show", "find", "which", "how many", "what", "who", "when",
                "top", "revenue", "last", "days", "zero", "without", "never"
        };
        for (String a : allowed) {
            if (q.contains(a)) return true;
        }

        return false;
    }


    // STEP 1: NL → SQL
    private String generateSQL(String question) throws Exception {
        StringBuilder history = new StringBuilder();
        for (Map<String, String> msg : conversationHistory) {
            history.append(msg.get("role"))
                    .append(": ")
                    .append(msg.get("content"))
                    .append("\n");
        }

        // Extract 6-digit order number from question if present
        String orderNum = "740506";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\b(\\d{6})\\b").matcher(question);
        if (m.find()) orderNum = m.group(1);

        String prompt = String.format("""
            You are a senior PostgreSQL expert for a SAP Order-to-Cash system.
            Your ONLY job is to return a single valid PostgreSQL SQL query.

            %s

            ================================================================
            ABSOLUTE RULES — NEVER BREAK THESE:
            ================================================================
            1. Output ONLY raw SQL. No explanation. No markdown. No ```sql fences.
               The very first character of your response must be S (for SELECT).
            2. NEVER truncate any column name. Always write the FULL column name.
            3. Always alias every table and prefix every column with its alias.
            4. Add LIMIT 100 to all non-aggregate queries.
            5. For date arithmetic always use: NOW() - INTERVAL '30 days'
            6. For broken flow detection always use LEFT JOIN + IS NULL check.
            7. Always use bp.full_name (NEVER bp.full_ or bp.name).
            8. For date comparisons on TEXT columns use:
                            column_name >= TO_CHAR(NOW() - INTERVAL '30 days', 'YYYY-MM-DD')
                            NEVER use CAST() or ::DATE on text date columns.                      
            9. Always cast numeric and integer columns to text using ::text

            ================================================================
            FULL COLUMN NAMES — MEMORIZE THESE:
            ================================================================
            business_partners          → bp.full_name, bp.business_partner, bp.customer, bp.is_blocked
            sales_order_headers        → soh.sales_order, soh.sold_to_party, soh.total_net_amount,
                                         soh.transaction_currency, soh.creation_date,
                                         soh.overall_delivery_status, soh.overall_billing_status,
                                         soh.requested_delivery_date, soh.customer_payment_terms
            sales_order_items          → soi.sales_order, soi.sales_order_item, soi.material,
                                         soi.requested_quantity, soi.quantity_unit,
                                         soi.net_amount, soi.production_plant
            outbound_delivery_headers  → odh.delivery_document, odh.shipping_point,
                                         odh.creation_date, odh.goods_movement_status,
                                         odh.picking_status, odh.actual_goods_movement_date
            outbound_delivery_items    → odi.delivery_document, odi.delivery_document_item,
                                         odi.reference_sd_document, odi.plant,
                                         odi.actual_delivery_quantity
            billing_document_headers   → bdh.billing_document, bdh.sold_to_party,
                                         bdh.billing_document_date, bdh.total_net_amount,
                                         bdh.transaction_currency, bdh.accounting_document,
                                         bdh.is_cancelled, bdh.billing_document_type
            billing_document_items     → bdi.billing_document, bdi.billing_document_item,
                                         bdi.material, bdi.billing_quantity,
                                         bdi.net_amount, bdi.reference_sd_document
            payments                   → p.accounting_document, p.accounting_document_item,
                                         p.customer, p.clearing_date,
                                         p.clearing_accounting_document, p.amount,
                                         p.transaction_currency, p.posting_date
            journal_entries            → je.accounting_document, je.accounting_document_item,
                                         je.customer, je.reference_document,
                                         je.amount, je.posting_date, je.document_type,
                                         je.clearing_accounting_document
            products                   → pr.product, pr.product_old_id, pr.product_group
            product_descriptions       → pd.product, pd.language, pd.product_description
          

            ================================================================
            KEY JOIN CONDITIONS — USE EXACTLY:
            ================================================================
            business_partners  ↔ sales_order_headers  : bp.business_partner = soh.sold_to_party
            business_partners  ↔ billing_doc_headers  : bp.business_partner = bdh.sold_to_party
            sales_order_items  ↔ sales_order_headers  : soi.sales_order = soh.sales_order
            outbound_del_items ↔ sales_order_headers  : odi.reference_sd_document = soh.sales_order
            outbound_del_items ↔ outbound_del_headers : odi.delivery_document = odh.delivery_document
            billing_doc_items  ↔ outbound_del_items   : bdi.reference_sd_document = odi.delivery_document
            billing_doc_items  ↔ billing_doc_headers  : bdi.billing_document = bdh.billing_document
            journal_entries    ↔ billing_doc_headers  : je.reference_document = bdh.billing_document
            payments           ↔ billing_doc_headers  : p.clearing_accounting_document = bdh.accounting_document
            product_desc       ↔ products             : pd.product = pr.product AND pd.language = 'EN'
            product_desc       ↔ sales_order_items    : pd.product = soi.material AND pd.language = 'EN'
            product_desc       ↔ billing_doc_items    : pd.product = bdi.material AND pd.language = 'EN'

            ================================================================
            PROVEN SQL TEMPLATES — ADAPT THESE FOR SIMILAR QUESTIONS:
            ================================================================

            -- List all customers:
            SELECT bp.business_partner, bp.full_name, bp.customer
            FROM business_partners bp
            ORDER BY bp.full_name;

            -- Top customers by revenue:
            SELECT bp.full_name, SUM(soh.total_net_amount) AS total_revenue,
                   soh.transaction_currency
            FROM sales_order_headers soh
            JOIN business_partners bp ON bp.business_partner = soh.sold_to_party
            GROUP BY bp.full_name, soh.transaction_currency
            ORDER BY total_revenue DESC
            LIMIT 10;

            -- Unpaid invoices (billed but no payment received):
            SELECT bdh.billing_document, bdh.billing_document_date,
                   bdh.total_net_amount, bdh.transaction_currency, bp.full_name
            FROM billing_document_headers bdh
            JOIN business_partners bp ON bp.business_partner = bdh.sold_to_party
            LEFT JOIN payments p ON p.clearing_accounting_document = bdh.accounting_document
            WHERE p.accounting_document IS NULL
            AND bdh.is_cancelled = false
            ORDER BY bdh.total_net_amount DESC
            LIMIT 100;

            -- Delivered but never invoiced (broken flow):
            SELECT DISTINCT soh.sales_order, soh.total_net_amount,
                   soh.transaction_currency, bp.full_name,
                   odh.delivery_document
            FROM sales_order_headers soh
            JOIN business_partners bp ON bp.business_partner = soh.sold_to_party
            JOIN outbound_delivery_items odi ON odi.reference_sd_document = soh.sales_order
            JOIN outbound_delivery_headers odh ON odh.delivery_document = odi.delivery_document
            LEFT JOIN billing_document_items bdi ON bdi.reference_sd_document = odi.delivery_document
            WHERE bdi.billing_document IS NULL
            LIMIT 100;

            -- Orders without any delivery:
            SELECT soh.sales_order, soh.overall_delivery_status,
                   soh.total_net_amount, bp.full_name
            FROM sales_order_headers soh
            JOIN business_partners bp ON bp.business_partner = soh.sold_to_party
            LEFT JOIN outbound_delivery_items odi ON odi.reference_sd_document = soh.sales_order
            WHERE odi.delivery_document IS NULL
            LIMIT 100;

            -- Customers with zero orders:
            SELECT bp.business_partner, bp.full_name
            FROM business_partners bp
            LEFT JOIN sales_order_headers soh ON soh.sold_to_party = bp.business_partner
            WHERE soh.sales_order IS NULL;

            -- Orders created in last 30 days:
                        SELECT soh.sales_order, soh.creation_date,
                                                soh.total_net_amount, soh.transaction_currency, bp.full_name
                                         FROM sales_order_headers soh
                                         JOIN business_partners bp ON bp.business_partner = soh.sold_to_party
                                         WHERE soh.creation_date >= TO_CHAR(NOW() - INTERVAL '30 days', 'YYYY-MM-DD')
                                         ORDER BY soh.creation_date DESC
                                         LIMIT 100;

            -- Pending orders (not fully delivered OR not fully billed):
            SELECT soh.sales_order, soh.overall_delivery_status,
                   soh.overall_billing_status, soh.total_net_amount, bp.full_name
            FROM sales_order_headers soh
            JOIN business_partners bp ON bp.business_partner = soh.sold_to_party
            WHERE soh.overall_delivery_status <> 'C'
            OR soh.overall_billing_status <> 'C'
            LIMIT 100;

            -- Cancelled billing documents:
            SELECT bdh.billing_document, bdh.billing_document_date,
                   bdh.total_net_amount, bdh.transaction_currency, bp.full_name
            FROM billing_document_headers bdh
            JOIN business_partners bp ON bp.business_partner = bdh.sold_to_party
            WHERE bdh.is_cancelled = true
            ORDER BY bdh.billing_document_date DESC
            LIMIT 100;

            -- Top products by sales quantity:
            SELECT pd.product_description,
                   SUM(soi.requested_quantity) AS total_quantity
            FROM sales_order_items soi
            JOIN product_descriptions pd ON pd.product = soi.material AND pd.language = 'EN'
            GROUP BY pd.product_description
            ORDER BY total_quantity DESC
            LIMIT 10;

            -- Products with most billing documents:
            SELECT pd.product_description,
                   COUNT(DISTINCT bdi.billing_document) AS billing_count
            FROM billing_document_items bdi
            JOIN product_descriptions pd ON pd.product = bdi.material AND pd.language = 'EN'
            GROUP BY pd.product_description
            ORDER BY billing_count DESC
            LIMIT 10;

            -- Full flow trace for a specific sales order:
            SELECT soh.sales_order, bp.full_name,
                   odh.delivery_document, odh.goods_movement_status,
                   bdh.billing_document, bdh.total_net_amount,
                   bdh.is_cancelled,
                   p.accounting_document AS payment_document,
                   p.clearing_date AS payment_date
            FROM sales_order_headers soh
            JOIN business_partners bp ON bp.business_partner = soh.sold_to_party
            LEFT JOIN outbound_delivery_items odi ON odi.reference_sd_document = soh.sales_order
            LEFT JOIN outbound_delivery_headers odh ON odh.delivery_document = odi.delivery_document
            LEFT JOIN billing_document_items bdi ON bdi.reference_sd_document = odi.delivery_document
            LEFT JOIN billing_document_headers bdh ON bdh.billing_document = bdi.billing_document
            LEFT JOIN payments p ON p.clearing_accounting_document = bdh.accounting_document
            WHERE soh.sales_order = '%s'
            LIMIT 100;

            -- Journal entries linked to a billing document:
                        SELECT je.accounting_document::text,
                                                je.accounting_document_item::text,
                                                je.customer,
                                                je.reference_document,
                                                je.amount::text,
                                                je.transaction_currency,
                                                je.posting_date,
                                                je.document_type,
                                                je.clearing_accounting_document
                                         FROM journal_entries je
                                         WHERE je.reference_document = '%s'
                                         LIMIT 100;

            -- Revenue by month:
            SELECT DATE_TRUNC('month', soh.creation_date::timestamp) AS month,
                   SUM(soh.total_net_amount) AS monthly_revenue,
                   COUNT(soh.sales_order) AS order_count
            FROM sales_order_headers soh
            GROUP BY month
            ORDER BY month DESC
            LIMIT 12;

            -- Average order value per customer:
            SELECT bp.full_name,
                   ROUND(AVG(soh.total_net_amount)::numeric, 2) AS avg_order_value,
                   COUNT(soh.sales_order) AS total_orders
            FROM sales_order_headers soh
            JOIN business_partners bp ON bp.business_partner = soh.sold_to_party
            GROUP BY bp.full_name
            ORDER BY avg_order_value DESC;

            ================================================================
            CONVERSATION HISTORY (for follow-up questions):
            ================================================================
            %s

            ================================================================
            USER QUESTION:
            ================================================================
            %s

            SQL:
            """,
                graphService.getSchemaDescription(),
                orderNum,
                orderNum,
                history.toString(),
                question
        );

        String raw = callGemini(prompt);
        return cleanSQL(raw);
    }


    // STEP 2: Execute SQL
    private List<Map<String, Object>> executeSQL(String sql) {
        try {
            return graphService.getJdbcTemplate().queryForList(sql);
        } catch (Exception e) {
            log.error("SQL failed: {} | Error: {}", sql, e.getMessage());
            return List.of(Map.of("error", e.getMessage()));
        }
    }


    // STEP 3: Data → Natural Language Answer
    private String generateAnswer(String question,
                                  String sql,
                                  List<Map<String, Object>> data)
            throws Exception {
        String dataStr = data.size() > 20
                ? objectMapper.writeValueAsString(data.subList(0, 20))
                + "\n... and " + (data.size() - 20) + " more rows"
                : objectMapper.writeValueAsString(data);

        String prompt = String.format("""
            You are a senior SAP business data analyst assistant.
            Answer the user's question based STRICTLY on the query results below.
            Do NOT make up any data that is not in the results.

            USER QUESTION: %s

            SQL EXECUTED: %s

            QUERY RESULTS: %s

            RULES:
            1. Answer in clear, professional, friendly language.
            2. Always reference specific values, IDs, names, amounts from the data.
            3. If results are empty, clearly say no records were found and why.
            4. If the query returned an error, explain what went wrong simply.
            5. For lists, use bullet points with bold entity names.
            6. For broken flow questions, clearly state what step is missing.
            7. For counts/aggregates, lead with the number prominently.
            8. Keep answer between 2-6 sentences or a clean bullet list.
            9. Use INR currency symbol where amounts are in INR.
            10. Never mention SQL, tables, or technical terms in your answer.
            11. If results contain accounting documents, format them clearly as 'Journal Entry: [number]'.

            ANSWER:
            """,
                question, sql, dataStr
        );

        return callGemini(prompt);
    }


    // GEMINI API CALL
    private String callGemini(String prompt) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.1,
                        "maxOutputTokens", 1500
                )
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl + "?key=" + apiKey))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 429) {
            log.warn("Gemini rate limited. Retrying in 2 seconds...");
            Thread.sleep(2000);
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        if (response.statusCode() != 200) {
            log.error("Gemini error {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Gemini API error: " + response.statusCode());
        }

        JsonNode json = objectMapper.readTree(response.body());
        return json.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();
    }

    private String cleanSQL(String raw) {
        return raw.replaceAll("```sql", "")
                .replaceAll("```", "")
                .replaceAll("(?i)^sql\\s*", "")
                .trim();
    }

    public void clearHistory() {
        conversationHistory.clear();
    }
}