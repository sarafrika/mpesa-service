package com.sarafrika.apps.mpesaservice.services.products;

import com.sarafrika.apps.mpesaservice.dtos.admin.products.ConnectProductRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ConnectProductResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.CreateWebhookSubscriptionRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ProductDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.ProductGrantDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.UpsertProductGrantRequest;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookHealthDto;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookSecretResponse;
import com.sarafrika.apps.mpesaservice.dtos.admin.products.WebhookSubscriptionDto;
import com.sarafrika.apps.mpesaservice.exceptions.ConflictException;
import com.sarafrika.apps.mpesaservice.exceptions.InvalidRequestException;
import com.sarafrika.apps.mpesaservice.exceptions.NotFoundException;
import com.sarafrika.apps.mpesaservice.models.ClientShortcodeGrant;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.models.WebhookSubscription;
import com.sarafrika.apps.mpesaservice.repositories.ClientShortcodeGrantRepository;
import com.sarafrika.apps.mpesaservice.repositories.WebhookSubscriptionRepository;
import com.sarafrika.apps.mpesaservice.services.audit.AuditService;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookAdminService;
import com.sarafrika.apps.mpesaservice.services.webhooks.WebhookUrls;
import com.sarafrika.apps.mpesaservice.utils.enums.GrantOperation;
import com.sarafrika.apps.mpesaservice.utils.enums.IncomingPaymentStatus;
import com.sarafrika.apps.mpesaservice.utils.enums.OutgoingPaymentStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Products: Keycloak clients with grants, a webhook or machine API traffic. Calls come from client_api_usage
 * (counted per request); KES out from their own payouts, KES in from successful payments on shortcodes they
 * hold STK_PUSH or C2B_FEED on (a shared shortcode counts for each product). */
@Service
@RequiredArgsConstructor
public class ProductAdminService {

    static final String ENTITY_GRANT = "PRODUCT_GRANT";
    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final int STATS_DAYS = 7;

    private final ClientShortcodeGrantRepository grants;
    private final WebhookSubscriptionRepository subscriptions;
    private final WebhookAdminService webhooks;
    private final AuditService auditService;
    private final EntityManager entityManager;

    /** Sorted in memory: there are few products, and they are not rows of one table. */
    @Transactional(readOnly = true)
    public Page<ProductDto> listProducts(String search, Pageable pageable) {
        List<ProductDto> all = build(null);
        if (StringUtils.hasText(search)) {
            String needle = search.trim().toLowerCase();
            all = all.stream().filter(p -> p.clientId().toLowerCase().contains(needle)
                    || p.displayName().toLowerCase().contains(needle)).toList();
        }
        List<ProductDto> sorted = new ArrayList<>(all);
        sorted.sort(comparator(pageable.getSort()));
        int from = (int) Math.min(pageable.getOffset(), sorted.size());
        int to = Math.min(from + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(from, to), pageable, sorted.size());
    }

    @Transactional(readOnly = true)
    public ProductDto getProduct(String clientId) {
        return build(clientId).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("Product not found: " + clientId));
    }

    @Transactional
    public ConnectProductResponse connect(ConnectProductRequest request) {
        String clientId = request.clientId().trim();
        if (!grants.findByClientId(clientId).isEmpty()) {
            throw new ConflictException("Product '" + clientId + "' is already connected; edit its grants instead");
        }
        if (StringUtils.hasText(request.webhookUrl())) {
            WebhookUrls.validated("webhook_url", request.webhookUrl());
        }
        Set<UUID> seen = new HashSet<>();
        List<ClientShortcodeGrant> created = new ArrayList<>();
        for (int i = 0; i < request.grants().size(); i++) {
            ConnectProductRequest.GrantInput input = request.grants().get(i);
            if (!seen.add(input.shortcodeUuid())) {
                throw new InvalidRequestException("grants[" + i + "].shortcode_uuid", "is listed twice");
            }
            if (shortcodes(Set.of(input.shortcodeUuid())).isEmpty()) {
                throw new InvalidRequestException("grants[" + i + "].shortcode_uuid", "no such paybill or till");
            }
            ClientShortcodeGrant grant = new ClientShortcodeGrant();
            grant.setClientId(clientId);
            grant.setDisplayName(request.displayName().trim());
            grant.setShortcodeUuid(input.shortcodeUuid());
            grant.setOperations(EnumSet.copyOf(input.operations()));
            grant.setCreatedBy(CallerIdentity.actor());
            created.add(grants.save(grant));
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("client_id", clientId);
        after.put("display_name", request.displayName().trim());
        after.put("grants", created.stream().map(g -> Map.of("shortcode_uuid", g.getShortcodeUuid(),
                "operations", sortedOperations(g.getOperations()))).toList());
        auditService.record("PRODUCT_CONNECTED", ENTITY_GRANT, clientId, null, after);

        WebhookSubscriptionDto webhook = null;
        String secret = null;
        if (StringUtils.hasText(request.webhookUrl())) {
            WebhookSecretResponse registered = webhooks.create(new CreateWebhookSubscriptionRequest(clientId,
                    request.webhookUrl(), request.webhookEventTypes()));
            webhook = registered.subscription();
            secret = registered.secret();
        }
        entityManager.flush();
        return new ConnectProductResponse(getProduct(clientId), webhook, secret);
    }

    @Transactional
    public ProductGrantDto upsertGrant(String clientId, UUID shortcodeUuid, UpsertProductGrantRequest request) {
        if (shortcodes(Set.of(shortcodeUuid)).isEmpty()) {
            throw new NotFoundException("Shortcode not found");
        }
        List<ClientShortcodeGrant> existing = grants.findByClientId(clientId);
        ClientShortcodeGrant grant = existing.stream().filter(g -> g.getShortcodeUuid().equals(shortcodeUuid))
                .findFirst().orElse(null);
        ProductGrantDto before = grant == null ? null : toDto(grant, Map.of(), null);
        if (grant == null) {
            grant = new ClientShortcodeGrant();
            grant.setClientId(clientId);
            grant.setShortcodeUuid(shortcodeUuid);
            grant.setCreatedBy(CallerIdentity.actor());
            grant.setDisplayName(existing.stream().map(ClientShortcodeGrant::getDisplayName)
                    .filter(StringUtils::hasText).findFirst().orElse(clientId));
        }
        grant.setOperations(EnumSet.copyOf(request.operations()));
        grant.setActive(request.active() == null || request.active());
        grant.setUpdatedBy(CallerIdentity.actor());
        if (StringUtils.hasText(request.displayName())) {
            String name = request.displayName().trim();
            existing.forEach(other -> other.setDisplayName(name));
            grant.setDisplayName(name);
        }
        ClientShortcodeGrant saved = grants.save(grant);
        ProductGrantDto after = toDto(saved, shortcodes(Set.of(shortcodeUuid)), primaryHealth(clientId));
        auditService.record(before == null ? "PRODUCT_GRANT_CREATED" : "PRODUCT_GRANT_UPDATED", ENTITY_GRANT,
                saved.getUuid().toString(), before, after);
        return after;
    }

    @Transactional
    public void deleteGrant(String clientId, UUID shortcodeUuid) {
        ClientShortcodeGrant grant = grants.findByClientIdAndShortcodeUuid(clientId, shortcodeUuid)
                .orElseThrow(() -> new NotFoundException("This product has no grant on that shortcode"));
        ProductGrantDto before = toDto(grant, shortcodes(Set.of(shortcodeUuid)), null);
        grants.delete(grant);
        auditService.record("PRODUCT_GRANT_DELETED", ENTITY_GRANT, grant.getUuid().toString(), before, null);
    }

    @Transactional(readOnly = true)
    public Page<ProductGrantDto> listGrants(UUID shortcodeUuid, String clientId, Pageable pageable) {
        Specification<ClientShortcodeGrant> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (shortcodeUuid != null) {
                predicates.add(cb.equal(root.get("shortcodeUuid"), shortcodeUuid));
            }
            if (StringUtils.hasText(clientId)) {
                predicates.add(cb.equal(root.get("clientId"), clientId.trim()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<ClientShortcodeGrant> page = grants.findAll(spec, pageable);
        Map<UUID, MpesaShortCode> codes = shortcodes(page.getContent().stream()
                .map(ClientShortcodeGrant::getShortcodeUuid).collect(Collectors.toSet()));
        Map<String, WebhookHealthDto> health = new HashMap<>();
        page.getContent().stream().map(ClientShortcodeGrant::getClientId).distinct()
                .forEach(client -> health.put(client, primaryHealth(client)));
        return page.map(grant -> toDto(grant, codes, health.get(grant.getClientId())));
    }

    private List<ProductDto> build(String onlyClient) {
        List<ClientShortcodeGrant> allGrants = onlyClient == null ? grants.findAll() : grants.findByClientId(onlyClient);
        List<WebhookSubscription> allSubs = onlyClient == null ? subscriptions.findAll()
                : subscriptions.findByClientId(onlyClient);
        Map<String, BigDecimal> out = payoutsOut();
        Map<UUID, BigDecimal> in = paymentsInByShortcode();
        Map<String, Object[]> usage = usage();

        Set<String> clients = new TreeSet<>();
        allGrants.forEach(g -> clients.add(g.getClientId()));
        allSubs.forEach(s -> clients.add(s.getClientId()));
        if (onlyClient == null) {
            clients.addAll(trafficClients());
        } else if (allGrants.isEmpty() && allSubs.isEmpty() && trafficClients().contains(onlyClient)) {
            clients.add(onlyClient);
        }

        Map<String, List<ClientShortcodeGrant>> grantsByClient = allGrants.stream()
                .collect(Collectors.groupingBy(ClientShortcodeGrant::getClientId));
        Map<String, List<WebhookSubscription>> subsByClient = allSubs.stream()
                .collect(Collectors.groupingBy(WebhookSubscription::getClientId));
        Map<UUID, MpesaShortCode> codes = shortcodes(allGrants.stream().map(ClientShortcodeGrant::getShortcodeUuid)
                .collect(Collectors.toSet()));
        Map<UUID, WebhookHealthDto> health = webhooks.health(allSubs);

        List<ProductDto> products = new ArrayList<>();
        for (String client : clients) {
            List<ClientShortcodeGrant> own = grantsByClient.getOrDefault(client, List.of());
            List<WebhookSubscription> subs = subsByClient.getOrDefault(client, List.of()).stream()
                    .sorted(Comparator.comparing(WebhookSubscription::getId)).toList();
            List<WebhookSubscriptionDto> hooks = subs.stream().map(s -> webhooks.toDto(s, health)).toList();
            WebhookHealthDto primary = hooks.stream().filter(WebhookSubscriptionDto::active).findFirst()
                    .or(() -> hooks.stream().findFirst()).map(WebhookSubscriptionDto::health).orElse(null);

            BigDecimal kesIn = own.stream()
                    .filter(g -> g.allows(GrantOperation.STK_PUSH) || g.allows(GrantOperation.C2B_FEED))
                    .map(g -> in.getOrDefault(g.getShortcodeUuid(), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal kesOut = out.getOrDefault(client, BigDecimal.ZERO);
            Object[] calls = usage.get(client);
            long apiCalls = calls == null ? 0 : number(calls[1]);
            long apiErrors = calls == null ? 0 : number(calls[2]);
            double errorRate = apiCalls == 0 ? 0 : BigDecimal.valueOf(apiErrors)
                    .divide(BigDecimal.valueOf(apiCalls), 4, RoundingMode.HALF_UP).doubleValue();
            ProductDto.Stats stats = new ProductDto.Stats(kesIn.add(kesOut), kesIn, kesOut, apiCalls, apiErrors,
                    errorRate, calls == null ? null : (Instant) calls[3]);

            Set<GrantOperation> ops = EnumSet.noneOf(GrantOperation.class);
            own.stream().filter(ClientShortcodeGrant::isActive).forEach(g -> ops.addAll(g.getOperations()));
            String name = own.stream().map(ClientShortcodeGrant::getDisplayName).filter(StringUtils::hasText)
                    .findFirst().orElse(client);
            List<ProductGrantDto> grantDtos = own.stream()
                    .sorted(Comparator.comparing(g -> codes.containsKey(g.getShortcodeUuid())
                            ? codes.get(g.getShortcodeUuid()).getShortcode() : "")).map(g -> toDto(g, codes, primary))
                    .toList();
            products.add(new ProductDto(client, name, state(own, stats), stats, grantDtos, sortedOperations(ops),
                    hooks));
        }
        return products;
    }

    static ProductDto.State state(List<ClientShortcodeGrant> own, ProductDto.Stats stats) {
        if (own.isEmpty()) {
            return ProductDto.State.UNGRANTED;
        }
        if (own.stream().noneMatch(ClientShortcodeGrant::isActive)) {
            return ProductDto.State.PAUSED;
        }
        boolean traffic = stats.apiCalls7d() > 0 || stats.kesMoved7d().signum() > 0;
        return traffic ? ProductDto.State.LIVE : ProductDto.State.IDLE;
    }

    private WebhookHealthDto primaryHealth(String clientId) {
        List<WebhookSubscription> subs = subscriptions.findByClientId(clientId).stream()
                .sorted(Comparator.comparing(WebhookSubscription::isActive).reversed()
                        .thenComparing(WebhookSubscription::getId)).toList();
        if (subs.isEmpty()) {
            return null;
        }
        return webhooks.health(List.of(subs.get(0))).get(subs.get(0).getUuid());
    }

    private ProductGrantDto toDto(ClientShortcodeGrant grant, Map<UUID, MpesaShortCode> codes,
                                  WebhookHealthDto health) {
        MpesaShortCode code = codes.get(grant.getShortcodeUuid());
        return new ProductGrantDto(grant.getUuid(), grant.getClientId(),
                StringUtils.hasText(grant.getDisplayName()) ? grant.getDisplayName() : grant.getClientId(),
                grant.getShortcodeUuid(), code == null ? null : code.getShortcode(),
                code == null ? null : code.getBusinessName(), sortedOperations(grant.getOperations()), grant.isActive(),
                health, grant.getCreatedBy(), grant.getCreatedAt(), grant.getUpdatedAt());
    }

    private Map<UUID, MpesaShortCode> shortcodes(Collection<UUID> uuids) {
        if (uuids.isEmpty()) {
            return Map.of();
        }
        return entityManager.createQuery("SELECT s FROM MpesaShortCode s WHERE s.uuid IN :uuids", MpesaShortCode.class)
                .setParameter("uuids", uuids).getResultList().stream()
                .collect(Collectors.toMap(MpesaShortCode::getUuid, s -> s, (a, b) -> a));
    }

    private Map<String, BigDecimal> payoutsOut() {
        List<Object[]> rows = entityManager.createQuery("""
                        SELECT p.clientId, COALESCE(SUM(p.amount), 0) FROM MpesaOutgoingPayment p
                        WHERE p.status = :status AND p.createdAt >= :since AND p.clientId IS NOT NULL
                        GROUP BY p.clientId""", Object[].class)
                .setParameter("status", OutgoingPaymentStatus.SUCCESS)
                .setParameter("since", LocalDateTime.now().minusDays(STATS_DAYS)).getResultList();
        return rows.stream().collect(Collectors.toMap(r -> (String) r[0], r -> (BigDecimal) r[1]));
    }

    private Map<UUID, BigDecimal> paymentsInByShortcode() {
        List<Object[]> rows = entityManager.createQuery("""
                        SELECT p.shortcodeUuid, COALESCE(SUM(p.amount), 0) FROM MpesaIncomingPayment p
                        WHERE p.status = :status AND p.createdAt >= :since GROUP BY p.shortcodeUuid""", Object[].class)
                .setParameter("status", IncomingPaymentStatus.SUCCESS)
                .setParameter("since", LocalDateTime.now().minusDays(STATS_DAYS)).getResultList();
        return rows.stream().filter(r -> r[0] != null)
                .collect(Collectors.toMap(r -> (UUID) r[0], r -> (BigDecimal) r[1]));
    }

    private Map<String, Object[]> usage() {
        List<Object[]> rows = entityManager.createQuery("""
                        SELECT u.key.clientId, SUM(u.calls), SUM(u.errors), MAX(u.lastCallAt) FROM ClientApiUsage u
                        WHERE u.key.day >= :since GROUP BY u.key.clientId""", Object[].class)
                .setParameter("since", LocalDate.now(NAIROBI).minusDays(STATS_DAYS - 1)).getResultList();
        return rows.stream().collect(Collectors.toMap(r -> (String) r[0], r -> r));
    }

    private Set<String> trafficClients() {
        Set<String> clients = new HashSet<>(entityManager.createQuery(
                "SELECT DISTINCT p.clientId FROM MpesaOutgoingPayment p WHERE p.clientId IS NOT NULL", String.class)
                .getResultList());
        clients.addAll(entityManager.createQuery("SELECT DISTINCT u.key.clientId FROM ClientApiUsage u", String.class)
                .getResultList());
        clients.removeIf(Objects::isNull);
        return clients;
    }

    private static List<GrantOperation> sortedOperations(Collection<GrantOperation> operations) {
        return operations == null || operations.isEmpty() ? List.of() : List.copyOf(EnumSet.copyOf(operations));
    }

    private static Comparator<ProductDto> comparator(Sort sort) {
        Comparator<ProductDto> result = null;
        for (Sort.Order order : sort) {
            Comparator<ProductDto> next = switch (order.getProperty()) {
                case "displayName" -> Comparator.comparing(p -> p.displayName().toLowerCase());
                case "clientId" -> Comparator.comparing(ProductDto::clientId);
                case "kesMoved7d" -> Comparator.comparing(p -> p.stats().kesMoved7d());
                case "apiCalls7d" -> Comparator.comparingLong(p -> p.stats().apiCalls7d());
                default -> null;
            };
            if (next == null) {
                continue;
            }
            next = order.isDescending() ? next.reversed() : next;
            result = result == null ? next : result.thenComparing(next);
        }
        Comparator<ProductDto> tiebreak = Comparator.comparing(ProductDto::clientId);
        return result == null ? tiebreak : result.thenComparing(tiebreak);
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : 0;
    }
}
