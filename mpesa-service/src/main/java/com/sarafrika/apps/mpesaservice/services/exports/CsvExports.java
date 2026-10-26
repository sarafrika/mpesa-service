package com.sarafrika.apps.mpesaservice.services.exports;

import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeHealthDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.shortcode.ShortcodeSummaryDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.transactions.TransactionDto;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.collections.TransactionFeedService;
import com.sarafrika.apps.mpesaservice.services.shortcode.ShortcodeAdminService;
import com.sarafrika.apps.mpesaservice.utils.Csv;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/** CSV downloads of the transactions feed (finance, audited) and the paybills list, capped at
 * mpesa.exports.max-rows. Filters are checked before the first byte, so a bad one still answers ApiError. */
@Service
public class CsvExports {

    static final List<String> TRANSACTION_COLUMNS = List.of("uuid", "created_at", "transaction_date",
            "processed_at", "direction", "type", "status", "shortcode", "business_name", "receipt",
            "checkout_request_id", "conversation_id", "originator_conversation_id", "account_reference", "phone",
            "customer_name", "amount", "result_code", "result_desc", "client_id", "c2b_channel");
    static final List<String> SHORTCODE_COLUMNS = List.of("shortcode", "business_name", "shortcode_type",
            "environment", "status", "products", "token_ok", "token_checked_at", "c2b_state", "last_payment_at",
            "collected_today", "paid_out_today", "float_utility", "float_working", "float_reported_at",
            "credentials_updated_at", "created_at");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final TransactionFeedService feed;
    private final ShortcodeAdminService shortcodes;
    private final AuditService audit;
    private final int maxRows;

    public CsvExports(TransactionFeedService feed, ShortcodeAdminService shortcodes, AuditService audit,
                      @Value("${mpesa.exports.max-rows:100000}") int maxRows) {
        this.feed = feed;
        this.shortcodes = shortcodes;
        this.audit = audit;
        this.maxRows = Math.max(1, maxRows);
    }

    public void transactions(TransactionFeedService.Filter filter, Sort sort, HttpServletResponse response)
            throws IOException {
        long total = feed.count(filter);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("filters", filter);
        after.put("sort", sort.toString());
        after.put("matching", total);
        after.put("rows", Math.min(total, maxRows));
        audit.record("TRANSACTIONS_EXPORTED", "TRANSACTION", null, null, after);
        Writer out = start(response, "transactions", total);
        Csv.row(out, TRANSACTION_COLUMNS);
        feed.export(filter, sort, maxRows, row -> Csv.row(out, cells(row)));
        out.flush();
    }

    public void shortcodes(ShortcodeAdminService.ShortcodeQuery query, Sort sort, HttpServletResponse response)
            throws IOException {
        Page<ShortcodeSummaryDto> page = shortcodes.list(query, PageRequest.of(0, maxRows, sort));
        Writer out = start(response, "paybills", page.getTotalElements());
        Csv.row(out, SHORTCODE_COLUMNS);
        for (ShortcodeSummaryDto code : page.getContent()) {
            Csv.row(out, cells(code));
        }
        out.flush();
    }

    private Writer start(HttpServletResponse response, String name, long total) throws IOException {
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + name + "-"
                + LocalDateTime.now().format(FILE_STAMP) + ".csv\"");
        response.setHeader("X-Export-Total", Long.toString(total));
        response.setHeader("X-Export-Truncated", Boolean.toString(total > maxRows));
        response.setHeader("Cache-Control", "no-store");
        return new BufferedWriter(new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8));
    }

    static List<Object> cells(TransactionDto t) {
        return Arrays.<Object>asList(t.uuid(), t.createdAt(), t.transactionDate(), t.processedAt(), t.direction(), t.type(),
                t.status(), t.shortcode(), t.businessName(), t.receipt(), t.checkoutRequestId(), t.conversationId(),
                t.originatorConversationId(), t.accountReference(), t.phone(), t.customerName(),
                t.amount() == null ? null : t.amount().toPlainString(), t.resultCode(), t.resultDesc(), t.clientId(),
                t.c2bChannel());
    }

    static List<Object> cells(ShortcodeSummaryDto s) {
        ShortcodeHealthDto h = s.health();
        List<Object> cells = new ArrayList<Object>(Arrays.<Object>asList(s.shortcode(), s.businessName(), s.shortcodeType(),
                s.environment(), s.status(), s.products() == null ? null
                        : s.products().stream().map(ShortcodeSummaryDto.ProductRef::clientId)
                        .collect(Collectors.joining("; "))));
        if (h == null) {
            cells.addAll(Arrays.asList(new Object[9]));
        } else {
            cells.addAll(Arrays.asList(h.tokenOk(), h.tokenCheckedAt(), h.c2bState(), h.lastPaymentAt(),
                    plain(h.collectedToday()), plain(h.paidOutToday()), plain(h.floatUtility()),
                    plain(h.floatWorking()), h.floatReportedAt()));
        }
        cells.add(s.credentialsUpdatedAt());
        cells.add(s.createdAt());
        return cells;
    }

    private static String plain(java.math.BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
