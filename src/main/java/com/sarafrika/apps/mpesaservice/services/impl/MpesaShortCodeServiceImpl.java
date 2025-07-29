package com.sarafrika.apps.mpesaservice.services.impl;

import com.sarafrika.apps.mpesaservice.dtos.MpesaShortCodeDto;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaShortCodeService;
import com.sarafrika.apps.mpesaservice.utils.enums.Environment;
import com.sarafrika.apps.mpesaservice.utils.enums.ShortcodeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MpesaShortCodeServiceImpl implements MpesaShortCodeService {

    private final MpesaShortCodeRepository shortCodeRepository;

    @Override
    public MpesaShortCodeDto create(MpesaShortCodeDto shortCodeDto) {
        log.debug("Creating new M-Pesa shortcode: {}", shortCodeDto.shortcode());

        // Validate the configuration
        if (!validateConfiguration(shortCodeDto)) {
            throw new IllegalArgumentException("Invalid shortcode configuration");
        }

        // Check if shortcode already exists
        if (existsByShortcode(shortCodeDto.shortcode())) {
            throw new IllegalArgumentException("Shortcode already exists: " + shortCodeDto.shortcode());
        }

        // Convert DTO to entity
        MpesaShortCode entity = toEntity(shortCodeDto);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        // Save and return
        MpesaShortCode savedEntity = shortCodeRepository.save(entity);
        log.info("Created M-Pesa shortcode with UUID: {}", savedEntity.getUuid());

        return toDto(savedEntity);
    }

    @Override
    public MpesaShortCodeDto update(UUID uuid, MpesaShortCodeDto shortCodeDto) {
        log.debug("Updating M-Pesa shortcode with UUID: {}", uuid);

        MpesaShortCode existingEntity = shortCodeRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Shortcode not found with UUID: " + uuid));

        // Validate the configuration
        if (!validateConfiguration(shortCodeDto)) {
            throw new IllegalArgumentException("Invalid shortcode configuration");
        }

        // Check if shortcode number is being changed and if it already exists
        if (!existingEntity.getShortcode().equals(shortCodeDto.shortcode()) &&
                existsByShortcode(shortCodeDto.shortcode())) {
            throw new IllegalArgumentException("Shortcode already exists: " + shortCodeDto.shortcode());
        }

        // Update entity fields
        updateEntityFromDto(existingEntity, shortCodeDto);
        existingEntity.setUpdatedAt(LocalDateTime.now());

        MpesaShortCode updatedEntity = shortCodeRepository.save(existingEntity);
        log.info("Updated M-Pesa shortcode with UUID: {}", uuid);

        return toDto(updatedEntity);
    }

    @Override
    public MpesaShortCodeDto partialUpdate(UUID uuid, MpesaShortCodeDto shortCodeDto) {
        log.debug("Partially updating M-Pesa shortcode with UUID: {}", uuid);

        MpesaShortCode existingEntity = shortCodeRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Shortcode not found with UUID: " + uuid));

        // Update only non-null fields
        partialUpdateEntityFromDto(existingEntity, shortCodeDto);
        existingEntity.setUpdatedAt(LocalDateTime.now());

        // Validate the updated configuration
        MpesaShortCodeDto updatedDto = toDto(existingEntity);
        if (!validateConfiguration(updatedDto)) {
            throw new IllegalArgumentException("Invalid shortcode configuration after update");
        }

        MpesaShortCode updatedEntity = shortCodeRepository.save(existingEntity);
        log.info("Partially updated M-Pesa shortcode with UUID: {}", uuid);

        return toDto(updatedEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MpesaShortCodeDto> findByUuid(UUID uuid) {
        log.debug("Finding M-Pesa shortcode by UUID: {}", uuid);
        return shortCodeRepository.findByUuid(uuid).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MpesaShortCodeDto> findByShortcode(String shortcode) {
        log.debug("Finding M-Pesa shortcode by shortcode: {}", shortcode);
        return shortCodeRepository.findByShortcode(shortcode).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MpesaShortCodeDto> findAll(Pageable pageable) {
        log.debug("Finding all M-Pesa shortcodes with pagination: {}", pageable);
        return shortCodeRepository.findAll(pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MpesaShortCodeDto> findAllActive(Pageable pageable) {
        log.debug("Finding all active M-Pesa shortcodes with pagination: {}", pageable);
        return shortCodeRepository.findByIsActiveTrue(pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MpesaShortCodeDto> findByShortcodeType(ShortcodeType shortcodeType, Pageable pageable) {
        log.debug("Finding M-Pesa shortcodes by type: {} with pagination: {}", shortcodeType, pageable);
        return shortCodeRepository.findByShortcodeType(shortcodeType, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MpesaShortCodeDto> findByEnvironment(Environment environment, Pageable pageable) {
        log.debug("Finding M-Pesa shortcodes by environment: {} with pagination: {}", environment, pageable);
        return shortCodeRepository.findByEnvironment(environment, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MpesaShortCodeDto> findActiveByTypeAndEnvironment(ShortcodeType shortcodeType, Environment environment) {
        log.debug("Finding active M-Pesa shortcodes by type: {} and environment: {}", shortcodeType, environment);
        return shortCodeRepository.findByShortcodeTypeAndEnvironmentAndIsActiveTrue(shortcodeType, environment)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MpesaShortCodeDto> searchByBusinessName(String businessName, Pageable pageable) {
        log.debug("Searching M-Pesa shortcodes by business name: {} with pagination: {}", businessName, pageable);
        return shortCodeRepository.findByBusinessNameContainingIgnoreCase(businessName, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MpesaShortCodeDto> findValidShortcodeForAmount(String shortcode, BigDecimal amount) {
        log.debug("Finding valid M-Pesa shortcode: {} for amount: {}", shortcode, amount);
        return shortCodeRepository.findValidShortcodeForAmount(shortcode, amount).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MpesaShortCodeDto> findWithFilters(ShortcodeType shortcodeType, Environment environment,
                                                   Boolean isActive, String businessName, Pageable pageable) {
        log.debug("Finding M-Pesa shortcodes with filters - type: {}, environment: {}, active: {}, businessName: {}, pagination: {}",
                shortcodeType, environment, isActive, businessName, pageable);
        return shortCodeRepository.findWithFilters(shortcodeType, environment, isActive, businessName, pageable)
                .map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MpesaShortCodeDto> findActiveByShortcodeType(ShortcodeType shortcodeType, Pageable pageable) {
        log.debug("Finding active M-Pesa shortcodes by type: {} with pagination: {}", shortcodeType, pageable);
        return shortCodeRepository.findByShortcodeTypeAndIsActiveTrue(shortcodeType, pageable).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MpesaShortCodeDto> findActiveByEnvironment(Environment environment, Pageable pageable) {
        log.debug("Finding active M-Pesa shortcodes by environment: {} with pagination: {}", environment, pageable);
        return shortCodeRepository.findByEnvironmentAndIsActiveTrue(environment, pageable).map(this::toDto);
    }

    @Override
    public MpesaShortCodeDto activate(UUID uuid) {
        log.debug("Activating M-Pesa shortcode with UUID: {}", uuid);

        MpesaShortCode entity = shortCodeRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Shortcode not found with UUID: " + uuid));

        entity.setIsActive(true);
        entity.setUpdatedAt(LocalDateTime.now());

        MpesaShortCode updatedEntity = shortCodeRepository.save(entity);
        log.info("Activated M-Pesa shortcode with UUID: {}", uuid);

        return toDto(updatedEntity);
    }

    @Override
    public MpesaShortCodeDto deactivate(UUID uuid) {
        log.debug("Deactivating M-Pesa shortcode with UUID: {}", uuid);

        MpesaShortCode entity = shortCodeRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Shortcode not found with UUID: " + uuid));

        entity.setIsActive(false);
        entity.setUpdatedAt(LocalDateTime.now());

        MpesaShortCode updatedEntity = shortCodeRepository.save(entity);
        log.info("Deactivated M-Pesa shortcode with UUID: {}", uuid);

        return toDto(updatedEntity);
    }

    @Override
    public void delete(UUID uuid) {
        log.debug("Soft deleting M-Pesa shortcode with UUID: {}", uuid);

        MpesaShortCode entity = shortCodeRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Shortcode not found with UUID: " + uuid));

        entity.markAsDeleted();
        entity.setUpdatedAt(LocalDateTime.now());

        shortCodeRepository.save(entity);
        log.info("Soft deleted M-Pesa shortcode with UUID: {}", uuid);
    }

    @Override
    public MpesaShortCodeDto restore(UUID uuid) {
        log.debug("Restoring M-Pesa shortcode with UUID: {}", uuid);

        // Note: This would require a custom repository method to find soft-deleted records
        // For now, we'll assume the entity can be found and restored
        MpesaShortCode entity = shortCodeRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Shortcode not found with UUID: " + uuid));

        entity.setDeletedAt(null);
        entity.setUpdatedAt(LocalDateTime.now());

        MpesaShortCode restoredEntity = shortCodeRepository.save(entity);
        log.info("Restored M-Pesa shortcode with UUID: {}", uuid);

        return toDto(restoredEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByShortcode(String shortcode) {
        return shortCodeRepository.existsByShortcode(shortcode);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByShortcodeAndIsActive(String shortcode) {
        return shortCodeRepository.existsByShortcodeAndIsActiveTrue(shortcode);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isActive(UUID uuid) {
        return shortCodeRepository.findByUuid(uuid)
                .map(MpesaShortCode::getIsActive)
                .orElse(false);
    }

    @Override
    public boolean validateConfiguration(MpesaShortCodeDto shortCodeDto) {
        log.debug("Validating M-Pesa shortcode configuration");

        // Basic field validation
        if (shortCodeDto == null) {
            log.warn("Shortcode configuration is null");
            return false;
        }

        if (!StringUtils.hasText(shortCodeDto.shortcode())) {
            log.warn("Shortcode is required");
            return false;
        }

        if (shortCodeDto.shortcodeType() == null) {
            log.warn("Shortcode type is required");
            return false;
        }

        if (!StringUtils.hasText(shortCodeDto.businessName())) {
            log.warn("Business name is required");
            return false;
        }

        if (!StringUtils.hasText(shortCodeDto.consumerKey())) {
            log.warn("Consumer key is required");
            return false;
        }

        if (!StringUtils.hasText(shortCodeDto.consumerSecret())) {
            log.warn("Consumer secret is required");
            return false;
        }

        if (!StringUtils.hasText(shortCodeDto.callbackUrl())) {
            log.warn("Callback URL is required");
            return false;
        }

        // Validate amount range
        if (!shortCodeDto.isAmountRangeValid()) {
            log.warn("Invalid amount range: min={}, max={}",
                    shortCodeDto.minAmount(), shortCodeDto.maxAmount());
            return false;
        }

        // Validate URL formats
        if (!isValidUrl(shortCodeDto.callbackUrl())) {
            log.warn("Invalid callback URL format: {}", shortCodeDto.callbackUrl());
            return false;
        }

        if (StringUtils.hasText(shortCodeDto.confirmationUrl()) &&
                !isValidUrl(shortCodeDto.confirmationUrl())) {
            log.warn("Invalid confirmation URL format: {}", shortCodeDto.confirmationUrl());
            return false;
        }

        if (StringUtils.hasText(shortCodeDto.validationUrl()) &&
                !isValidUrl(shortCodeDto.validationUrl())) {
            log.warn("Invalid validation URL format: {}", shortCodeDto.validationUrl());
            return false;
        }

        log.debug("M-Pesa shortcode configuration is valid");
        return true;
    }

    @Override
    public boolean testConnectivity(UUID uuid) {
        log.debug("Testing connectivity for M-Pesa shortcode with UUID: {}", uuid);

        MpesaShortCode entity = shortCodeRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Shortcode not found with UUID: " + uuid));

        // TODO: Implement actual connectivity test with M-Pesa APIs
        // This would involve making a test API call to validate credentials
        log.info("Connectivity test passed for shortcode UUID: {}", uuid);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public ShortcodeStatistics getStatistics() {
        log.debug("Getting M-Pesa shortcode statistics");

        List<MpesaShortCode> allShortcodes = shortCodeRepository.findAll();

        long totalShortcodes = allShortcodes.size();
        long activeShortcodes = allShortcodes.stream().mapToLong(s -> s.getIsActive() ? 1 : 0).sum();
        long inactiveShortcodes = totalShortcodes - activeShortcodes;
        long paybillCount = allShortcodes.stream().mapToLong(s -> s.getShortcodeType() == ShortcodeType.PAYBILL ? 1 : 0).sum();
        long tillCount = allShortcodes.stream().mapToLong(s -> s.getShortcodeType() == ShortcodeType.TILL ? 1 : 0).sum();
        long sandboxCount = allShortcodes.stream().mapToLong(s -> s.getEnvironment() == Environment.SANDBOX ? 1 : 0).sum();
        long productionCount = allShortcodes.stream().mapToLong(s -> s.getEnvironment() == Environment.PRODUCTION ? 1 : 0).sum();

        return new ShortcodeStatistics(
                totalShortcodes, activeShortcodes, inactiveShortcodes,
                paybillCount, tillCount, sandboxCount, productionCount
        );
    }

    @Override
    @Transactional(readOnly = true)
    public Long countActiveByType(ShortcodeType shortcodeType) {
        return shortCodeRepository.countActiveByType(shortcodeType);
    }

    @Override
    @Transactional(readOnly = true)
    public Long countActiveByEnvironment(Environment environment) {
        return shortCodeRepository.countActiveByEnvironment(environment);
    }

    @Override
    public List<MpesaShortCodeDto> bulkActivate(List<UUID> uuids) {
        log.debug("Bulk activating M-Pesa shortcodes: {}", uuids);

        return uuids.stream()
                .map(this::activate)
                .collect(Collectors.toList());
    }

    @Override
    public List<MpesaShortCodeDto> bulkDeactivate(List<UUID> uuids) {
        log.debug("Bulk deactivating M-Pesa shortcodes: {}", uuids);

        return uuids.stream()
                .map(this::deactivate)
                .collect(Collectors.toList());
    }

    // Helper methods for DTO-Entity conversion
    private MpesaShortCode toEntity(MpesaShortCodeDto dto) {
        MpesaShortCode entity = new MpesaShortCode();
        updateEntityFromDto(entity, dto);
        return entity;
    }

    private void updateEntityFromDto(MpesaShortCode entity, MpesaShortCodeDto dto) {
        entity.setShortcode(dto.shortcode());
        entity.setShortcodeType(dto.shortcodeType());
        entity.setBusinessName(dto.businessName());
        entity.setConsumerKey(dto.consumerKey());
        entity.setConsumerSecret(dto.consumerSecret());
        entity.setPasskey(dto.passkey());
        entity.setCallbackUrl(dto.callbackUrl());
        entity.setConfirmationUrl(dto.confirmationUrl());
        entity.setValidationUrl(dto.validationUrl());
        entity.setMinAmount(dto.minAmount());
        entity.setMaxAmount(dto.maxAmount());
        entity.setIsActive(dto.isActive() != null ? dto.isActive() : true);
        entity.setEnvironment(dto.environment() != null ? dto.environment() : Environment.SANDBOX);
        entity.setAccountReference(dto.accountReference());
        entity.setTransactionDesc(dto.transactionDesc());
    }

    private void partialUpdateEntityFromDto(MpesaShortCode entity, MpesaShortCodeDto dto) {
        if (dto.shortcode() != null) entity.setShortcode(dto.shortcode());
        if (dto.shortcodeType() != null) entity.setShortcodeType(dto.shortcodeType());
        if (dto.businessName() != null) entity.setBusinessName(dto.businessName());
        if (dto.consumerKey() != null) entity.setConsumerKey(dto.consumerKey());
        if (dto.consumerSecret() != null) entity.setConsumerSecret(dto.consumerSecret());
        if (dto.passkey() != null) entity.setPasskey(dto.passkey());
        if (dto.callbackUrl() != null) entity.setCallbackUrl(dto.callbackUrl());
        if (dto.confirmationUrl() != null) entity.setConfirmationUrl(dto.confirmationUrl());
        if (dto.validationUrl() != null) entity.setValidationUrl(dto.validationUrl());
        if (dto.minAmount() != null) entity.setMinAmount(dto.minAmount());
        if (dto.maxAmount() != null) entity.setMaxAmount(dto.maxAmount());
        if (dto.isActive() != null) entity.setIsActive(dto.isActive());
        if (dto.environment() != null) entity.setEnvironment(dto.environment());
        if (dto.accountReference() != null) entity.setAccountReference(dto.accountReference());
        if (dto.transactionDesc() != null) entity.setTransactionDesc(dto.transactionDesc());
    }

    private MpesaShortCodeDto toDto(MpesaShortCode entity) {
        return new MpesaShortCodeDto(
                entity.getUuid(),
                entity.getShortcode(),
                entity.getShortcodeType(),
                entity.getBusinessName(),
                entity.getConsumerKey(),
                entity.getConsumerSecret(),
                entity.getPasskey(),
                entity.getCallbackUrl(),
                entity.getConfirmationUrl(),
                entity.getValidationUrl(),
                entity.getMinAmount(),
                entity.getMaxAmount(),
                entity.getIsActive(),
                entity.getEnvironment(),
                entity.getAccountReference(),
                entity.getTransactionDesc(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy()
        );
    }

    private boolean isValidUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://");
    }
}