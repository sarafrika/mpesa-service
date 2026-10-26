package com.sarafrika.apps.mpesaservice.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

/** A Dynamic QR code Daraja generated for a shortcode: what was asked for and the PNG it returned. */
@Entity
@Table(name = "qr_codes")
@Getter
@Setter
@NoArgsConstructor
public class QrCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @UuidGenerator
    @Column(name = "uuid")
    private UUID uuid;

    @Column(name = "shortcode_uuid", nullable = false)
    private UUID shortcodeUuid;

    @Column(name = "merchant_name", nullable = false, length = 100)
    private String merchantName;

    @Column(name = "ref_no", nullable = false, length = 100)
    private String refNo;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "trx_code", nullable = false, length = 2)
    private String trxCode;

    @Column(name = "cpi", nullable = false, length = 20)
    private String cpi;

    @Column(name = "size", nullable = false)
    private int size;

    @Column(name = "request_id", length = 100)
    private String requestId;

    @Column(name = "image_base64", nullable = false, columnDefinition = "text")
    private String imageBase64;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
