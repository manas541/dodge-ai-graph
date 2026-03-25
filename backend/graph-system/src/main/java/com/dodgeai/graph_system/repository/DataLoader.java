package com.dodgeai.graph_system.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.*;
import java.util.function.Consumer;

@Slf4j
@Component("dataLoader")
public class DataLoader {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final String DATA_DIR = "./data/sap-o2c-data";

    @PostConstruct
    public void init() {
        createTables();
        loadDataIfEmpty();
    }

    private void createTables() {

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS sales_order_headers (
                sales_order TEXT PRIMARY KEY,
                sales_order_type TEXT,
                sales_organization TEXT,
                sold_to_party TEXT,
                creation_date TEXT,
                total_net_amount NUMERIC,
                transaction_currency TEXT,
                overall_delivery_status TEXT,
                overall_billing_status TEXT,
                requested_delivery_date TEXT,
                customer_payment_terms TEXT
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS sales_order_items (
                sales_order TEXT,
                sales_order_item TEXT,
                material TEXT,
                requested_quantity NUMERIC,
                quantity_unit TEXT,
                net_amount NUMERIC,
                transaction_currency TEXT,
                material_group TEXT,
                production_plant TEXT,
                PRIMARY KEY (sales_order, sales_order_item)
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS outbound_delivery_headers (
                delivery_document TEXT PRIMARY KEY,
                shipping_point TEXT,
                creation_date TEXT,
                actual_goods_movement_date TEXT,
                goods_movement_status TEXT,
                picking_status TEXT
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS outbound_delivery_items (
                delivery_document TEXT,
                delivery_document_item TEXT,
                reference_sd_document TEXT,
                plant TEXT,
                storage_location TEXT,
                actual_delivery_quantity NUMERIC,
                delivery_quantity_unit TEXT,
                PRIMARY KEY (delivery_document, delivery_document_item)
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS billing_document_headers (
                billing_document TEXT PRIMARY KEY,
                billing_document_type TEXT,
                sold_to_party TEXT,
                creation_date TEXT,
                billing_document_date TEXT,
                total_net_amount NUMERIC,
                transaction_currency TEXT,
                company_code TEXT,
                fiscal_year TEXT,
                accounting_document TEXT,
                is_cancelled BOOLEAN
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS billing_document_items (
                billing_document TEXT,
                billing_document_item TEXT,
                material TEXT,
                billing_quantity NUMERIC,
                net_amount NUMERIC,
                transaction_currency TEXT,
                reference_sd_document TEXT,
                PRIMARY KEY (billing_document, billing_document_item)
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS billing_document_cancellations (
                billing_document TEXT PRIMARY KEY,
                sold_to_party TEXT,
                creation_date TEXT,
                total_net_amount NUMERIC,
                transaction_currency TEXT,
                accounting_document TEXT,
                cancelled_billing_document TEXT
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS payments (
                accounting_document TEXT,
                accounting_document_item TEXT,
                customer TEXT,
                clearing_date TEXT,
                clearing_accounting_document TEXT,
                amount NUMERIC,
                transaction_currency TEXT,
                posting_date TEXT,
                gl_account TEXT,
                PRIMARY KEY (accounting_document, accounting_document_item)
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS journal_entries (
                accounting_document TEXT,
                accounting_document_item TEXT,
                customer TEXT,
                gl_account TEXT,
                reference_document TEXT,
                amount NUMERIC,
                transaction_currency TEXT,
                posting_date TEXT,
                document_type TEXT,
                clearing_date TEXT,
                clearing_accounting_document TEXT,
                PRIMARY KEY (accounting_document, accounting_document_item)
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS business_partners (
                business_partner TEXT PRIMARY KEY,
                customer TEXT,
                full_name TEXT,
                is_blocked BOOLEAN,
                creation_date TEXT
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS business_partner_addresses (
                business_partner TEXT PRIMARY KEY,
                city_name TEXT,
                country TEXT,
                region TEXT,
                street_name TEXT,
                postal_code TEXT
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS products (
                product TEXT PRIMARY KEY,
                product_type TEXT,
                product_old_id TEXT,
                product_group TEXT,
                base_unit TEXT,
                gross_weight NUMERIC,
                net_weight NUMERIC,
                weight_unit TEXT
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS product_descriptions (
                product TEXT,
                language TEXT,
                product_description TEXT,
                PRIMARY KEY (product, language)
            )
        """);

        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS plants (
                plant TEXT PRIMARY KEY,
                plant_name TEXT,
                company_code TEXT,
                country TEXT,
                city_name TEXT
            )
        """);

        log.info("All tables created.");
    }

    private void loadDataIfEmpty() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sales_order_headers", Integer.class);

        if (count != null && count > 0) {
            log.info("Data already loaded ({} sales orders). Skipping.", count);
            return;
        }

        Path dir = Path.of(DATA_DIR);
        if (!Files.exists(dir)) {
            log.error("Data folder not found: {}", dir.toAbsolutePath());
            log.error("Please unzip sap-o2c-data into ./data/ folder");
            return;
        }

        log.info("Loading SAP data from: {}", dir.toAbsolutePath());

        load("sales_order_headers",                      this::insertSalesOrderHeader);
        load("sales_order_items",                        this::insertSalesOrderItem);
        load("outbound_delivery_headers",                this::insertDeliveryHeader);
        load("outbound_delivery_items",                  this::insertDeliveryItem);
        load("billing_document_headers",                 this::insertBillingHeader);
        load("billing_document_items",                   this::insertBillingItem);
        load("billing_document_cancellations",           this::insertBillingCancellation);
        load("payments_accounts_receivable",             this::insertPayment);
        load("journal_entry_items_accounts_receivable",  this::insertJournalEntry);
        load("business_partners",                        this::insertBusinessPartner);
        load("business_partner_addresses",               this::insertBusinessPartnerAddress);
        load("products",                                 this::insertProduct);
        load("product_descriptions",                     this::insertProductDescription);
        load("plants",                                   this::insertPlant);

        log.info("All data loaded successfully!");
        printCounts();
    }

    private void load(String folderName, Consumer<JsonNode> handler) {
        Path folder = Path.of(DATA_DIR, folderName);
        if (!Files.exists(folder)) {
            log.warn("⚠️ Folder not found, skipping: {}", folderName);
            return;
        }
        File[] files = folder.toFile().listFiles(
                f -> f.getName().endsWith(".jsonl"));
        if (files == null || files.length == 0) return;

        int count = 0;
        for (File file : files) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    try {
                        handler.accept(mapper.readTree(line));
                        count++;
                    } catch (Exception e) {
                        log.debug("Skipping line: {}", e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Error reading {}: {}", file.getName(), e.getMessage());
            }
        }
        log.info("  ✓ {} → {} records", folderName, count);
    }

    private void insertSalesOrderHeader(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO sales_order_headers VALUES
            (?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (sales_order) DO NOTHING
        """,
                s(n,"salesOrder"), s(n,"salesOrderType"),
                s(n,"salesOrganization"), s(n,"soldToParty"),
                date(n,"creationDate"), d(n,"totalNetAmount"),
                s(n,"transactionCurrency"), s(n,"overallDeliveryStatus"),
                s(n,"overallOrdReltdBillgStatus"),
                date(n,"requestedDeliveryDate"),
                s(n,"customerPaymentTerms")
        );
    }

    private void insertSalesOrderItem(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO sales_order_items VALUES
            (?,?,?,?,?,?,?,?,?)
            ON CONFLICT (sales_order, sales_order_item) DO NOTHING
        """,
                s(n,"salesOrder"), s(n,"salesOrderItem"),
                s(n,"material"), d(n,"requestedQuantity"),
                s(n,"requestedQuantityUnit"), d(n,"netAmount"),
                s(n,"transactionCurrency"), s(n,"materialGroup"),
                s(n,"productionPlant")
        );
    }

    private void insertDeliveryHeader(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO outbound_delivery_headers VALUES
            (?,?,?,?,?,?)
            ON CONFLICT (delivery_document) DO NOTHING
        """,
                s(n,"deliveryDocument"), s(n,"shippingPoint"),
                date(n,"creationDate"), date(n,"actualGoodsMovementDate"),
                s(n,"overallGoodsMovementStatus"), s(n,"overallPickingStatus")
        );
    }

    private void insertDeliveryItem(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO outbound_delivery_items VALUES
            (?,?,?,?,?,?,?)
            ON CONFLICT (delivery_document, delivery_document_item) DO NOTHING
        """,
                s(n,"deliveryDocument"), s(n,"deliveryDocumentItem"),
                s(n,"referenceSdDocument"), s(n,"plant"),
                s(n,"storageLocation"), d(n,"actualDeliveryQuantity"),
                s(n,"deliveryQuantityUnit")
        );
    }

    private void insertBillingHeader(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO billing_document_headers VALUES
            (?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (billing_document) DO NOTHING
        """,
                s(n,"billingDocument"), s(n,"billingDocumentType"),
                s(n,"soldToParty"), date(n,"creationDate"),
                date(n,"billingDocumentDate"), d(n,"totalNetAmount"),
                s(n,"transactionCurrency"), s(n,"companyCode"),
                s(n,"fiscalYear"), s(n,"accountingDocument"),
                b(n,"billingDocumentIsCancelled")
        );
    }

    private void insertBillingItem(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO billing_document_items VALUES
            (?,?,?,?,?,?,?)
            ON CONFLICT (billing_document, billing_document_item) DO NOTHING
        """,
                s(n,"billingDocument"), s(n,"billingDocumentItem"),
                s(n,"material"), d(n,"billingQuantity"),
                d(n,"netAmount"), s(n,"transactionCurrency"),
                s(n,"referenceSdDocument")
        );
    }

    private void insertBillingCancellation(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO billing_document_cancellations VALUES
            (?,?,?,?,?,?,?)
            ON CONFLICT (billing_document) DO NOTHING
        """,
                s(n,"billingDocument"), s(n,"soldToParty"),
                date(n,"creationDate"), d(n,"totalNetAmount"),
                s(n,"transactionCurrency"), s(n,"accountingDocument"),
                s(n,"cancelledBillingDocument")
        );
    }

    private void insertPayment(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO payments VALUES
            (?,?,?,?,?,?,?,?,?)
            ON CONFLICT (accounting_document, accounting_document_item) DO NOTHING
        """,
                s(n,"accountingDocument"), s(n,"accountingDocumentItem"),
                s(n,"customer"), date(n,"clearingDate"),
                s(n,"clearingAccountingDocument"),
                d(n,"amountInTransactionCurrency"),
                s(n,"transactionCurrency"), date(n,"postingDate"),
                s(n,"glAccount")
        );
    }

    private void insertJournalEntry(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO journal_entries VALUES
            (?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (accounting_document, accounting_document_item) DO NOTHING
        """,
                s(n,"accountingDocument"), s(n,"accountingDocumentItem"),
                s(n,"customer"), s(n,"glAccount"),
                s(n,"referenceDocument"), d(n,"amountInTransactionCurrency"),
                s(n,"transactionCurrency"), date(n,"postingDate"),
                s(n,"accountingDocumentType"), date(n,"clearingDate"),
                s(n,"clearingAccountingDocument")
        );
    }

    private void insertBusinessPartner(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO business_partners VALUES
            (?,?,?,?,?)
            ON CONFLICT (business_partner) DO NOTHING
        """,
                s(n,"businessPartner"), s(n,"customer"),
                s(n,"businessPartnerFullName"),
                b(n,"businessPartnerIsBlocked"),
                date(n,"creationDate")
        );
    }

    private void insertBusinessPartnerAddress(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO business_partner_addresses VALUES
            (?,?,?,?,?,?)
            ON CONFLICT (business_partner) DO NOTHING
        """,
                s(n,"businessPartner"), s(n,"cityName"),
                s(n,"country"), s(n,"region"),
                s(n,"streetName"), s(n,"postalCode")
        );
    }

    private void insertProduct(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO products VALUES
            (?,?,?,?,?,?,?,?)
            ON CONFLICT (product) DO NOTHING
        """,
                s(n,"product"), s(n,"productType"),
                s(n,"productOldId"), s(n,"productGroup"),
                s(n,"baseUnit"), d(n,"grossWeight"),
                d(n,"netWeight"), s(n,"weightUnit")
        );
    }

    private void insertProductDescription(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO product_descriptions VALUES
            (?,?,?)
            ON CONFLICT (product, language) DO NOTHING
        """,
                s(n,"product"), s(n,"language"),
                s(n,"productDescription")
        );
    }

    private void insertPlant(JsonNode n) {
        jdbcTemplate.update("""
            INSERT INTO plants VALUES
            (?,?,?,?,?)
            ON CONFLICT (plant) DO NOTHING
        """,
                s(n,"plant"), s(n,"plantName"),
                s(n,"companyCode"), s(n,"country"),
                s(n,"cityName")
        );
    }


    // Get string value safely
    private String s(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull() || v.isObject()) return "";
        return v.asText("").trim();
    }

    // Get date (strip time from ISO string)
    private String date(JsonNode n, String field) {
        String raw = s(n, field);
        if (raw.contains("T")) return raw.substring(0, 10);
        return raw;
    }

    // Get numeric value safely
    private double d(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return 0.0;
        try { return Double.parseDouble(v.asText("0")); }
        catch (Exception e) { return 0.0; }
    }

    // Get boolean value safely
    private boolean b(JsonNode n, String field) {
        JsonNode v = n.get(field);
        if (v == null || v.isNull()) return false;
        return v.asBoolean(false);
    }

    private void printCounts() {
        String[] tables = {
                "sales_order_headers", "sales_order_items",
                "outbound_delivery_headers", "outbound_delivery_items",
                "billing_document_headers", "billing_document_items",
                "payments", "journal_entries",
                "business_partners", "products"
        };
        log.info("📊 Record counts:");
        for (String t : tables) {
            Integer c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM " + t, Integer.class);
            log.info("   {} → {} rows", t, c);
        }
    }
}
