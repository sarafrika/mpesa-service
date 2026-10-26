package com.sarafrika.apps.mpesaservice.services.impl;

import com.sarafrika.apps.mpesaservice.dtos.MpesaShortCodeDto;
import com.sarafrika.apps.mpesaservice.models.MpesaShortCode;
import com.sarafrika.apps.mpesaservice.repositories.MpesaShortCodeRepository;
import com.sarafrika.apps.mpesaservice.services.MpesaShortCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MpesaShortCodeServiceImpl implements MpesaShortCodeService {

    private final MpesaShortCodeRepository shortCodeRepository;

    @Override
    public MpesaShortCodeDto create(MpesaShortCodeDto dto) {
        log.info("Creating new M-Pesa shortcode: {}", dto.shortcode());

        // Check if shortcode already exists
        if (shortCodeRepository.existsByShortcode(dto.shortcode())) {
            throw new IllegalArgumentException("Shortcode already exists: " + dto.shortcode());
        }

        MpesaShortCode entity = convertToEntity(dto);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());

        MpesaShortCode saved = shortCodeRepository.save(entity);
        log.info("Successfully created M-Pesa shortcode with UUID: {}", saved.getUuid());

        return convertToDto(saved);
    }

    @Override
    public MpesaShortCodeDto update(UUID uuid, MpesaShortCodeDto dto) {
        log.info("Updating M-Pesa shortcode with UUID: {}", uuid);

        MpesaShortCode existing = shortCodeRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Shortcode not found with UUID: " + uuid));

        // Check if shortcode is being changed and if new shortcode already exists
        if (!existing.getShortcode().equals(dto.shortcode()) &&
                shortCodeRepository.existsByShortcode(dto.shortcode())) {
            throw new IllegalArgumentException("Shortcode already exists: " + dto.shortcode());
        }

        updateEntityFromDto(existing, dto);
        existing.setUpdatedAt(LocalDateTime.now());

        MpesaShortCode updated = shortCodeRepository.save(existing);
        log.info("Successfully updated M-Pesa shortcode with UUID: {}", uuid);

        return convertToDto(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MpesaShortCodeDto> findByUuid(UUID uuid) {
        log.debug("Finding M-Pesa shortcode by UUID: {}", uuid);
        return shortCodeRepository.findByUuid(uuid)
                .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MpesaShortCodeDto> findByShortcode(String shortcode) {
        log.debug("Finding M-Pesa shortcode by shortcode: {}", shortcode);
        return shortCodeRepository.findByShortcode(shortcode)
                .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MpesaShortCodeDto> findAll(Pageable pageable) {
        log.debug("Finding all M-Pesa shortcodes with pagination: {}", pageable);
        return shortCodeRepository.findAll(pageable)
                .map(this::convertToDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MpesaShortCodeDto> findAllActive(Pageable pageable) {
        log.debug("Finding all active M-Pesa shortcodes with pagination: {}", pageable);
        return shortCodeRepository.findByIsActiveTrue(pageable)
                .map(this::convertToDto);
    }

    @Override
    public void deleteByUuid(UUID uuid) {
        log.info("Soft deleting M-Pesa shortcode with UUID: {}", uuid);

        MpesaShortCode entity = shortCodeRepository.findByUuid(uuid)
                .orElseThrow(() -> new IllegalArgumentException("Shortcode not found with UUID: " + uuid));

        shortCodeRepository.delete(entity); // This triggers soft delete via @SQLDelete
        log.info("Successfully soft deleted M-Pesa shortcode with UUID: {}", uuid);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByShortcode(String shortcode) {
        return shortCodeRepository.existsByShortcode(shortcode);
    }

    /**
     * Convert DTO to Entity
     */
    private MpesaShortCode convertToEntity(MpesaShortCodeDto dto) {
        MpesaShortCode entity = new MpesaShortCode();

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
        entity.setEnvironment(dto.environment());
        entity.setAccountReference(dto.accountReference());
        entity.setTransactionDesc(dto.transactionDesc());
        entity.setCreatedBy(dto.createdBy());
        entity.setUpdatedBy(dto.updatedBy());

        return entity;
    }

    /**
     * Convert Entity to DTO
     */
    private MpesaShortCodeDto convertToDto(MpesaShortCode entity) {
        return new MpesaShortCodeDto(
                entity.getUuid(),
                entity.getShortcode(),
                entity.getShortcodeType(),
                entity.getBusinessName(),
                entity.getConsumerKey(),
                null, // Don't expose consumer secret in reads
                null, // Don't expose passkey in reads
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

    /**
     * Update entity fields from DTO
     */
    private void updateEntityFromDto(MpesaShortCode entity, MpesaShortCodeDto dto) {
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
        if (dto.updatedBy() != null) entity.setUpdatedBy(dto.updatedBy());
    }
}