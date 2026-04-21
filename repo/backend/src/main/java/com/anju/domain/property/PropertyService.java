package com.anju.domain.property;

import com.anju.common.BusinessException;
import com.anju.domain.property.dto.ComplianceReviewRequest;
import com.anju.domain.property.dto.CreatePropertyRequest;
import com.anju.domain.property.dto.UpdatePropertyRequest;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class PropertyService {

    private final PropertyRepository propertyRepository;

    public PropertyService(PropertyRepository propertyRepository) {
        this.propertyRepository = propertyRepository;
    }

    public Property create(CreatePropertyRequest request) {
        validateDateRange(request.getStartDate(), request.getEndDate());
        if (propertyRepository.existsByCode(request.getCode())) {
            throw new BusinessException(4003, "Property code already exists.");
        }

        Property property = new Property();
        property.setCode(normalize(request.getCode()));
        property.setStatus(normalizeOrDefault(request.getStatus(), "DELISTED"));
        property.setRent(request.getRent());
        property.setDeposit(request.getDeposit());
        property.setStartDate(request.getStartDate());
        property.setEndDate(request.getEndDate());
        property.setComplianceStatus(normalizeOrDefault(request.getComplianceStatus(), "PENDING"));
        property.setMediaRefs(serializeMediaRefs(request.getMediaRefs()));
        return propertyRepository.save(property);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Property getById(Long id) {
        return propertyRepository.findById(id)
                .orElseThrow(() -> new BusinessException(4040, "Property not found."));
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public Property getByCode(String code) {
        return propertyRepository.findByCode(normalize(code))
                .orElseThrow(() -> new BusinessException(4040, "Property not found."));
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public List<Property> list(String status, String complianceStatus, int page, int size) {
        int safeSize = Math.min(size, 100);
        Pageable pageable = PageRequest.of(page, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<Property> specification = Specification.where(null);
        if (StringUtils.hasText(status)) {
            String normalizedStatus = normalize(status);
            specification = specification.and((root, query, cb) -> cb.equal(root.get("status"), normalizedStatus));
        }
        if (StringUtils.hasText(complianceStatus)) {
            String normalizedComplianceStatus = normalize(complianceStatus);
            specification = specification.and((root, query, cb) ->
                    cb.equal(root.get("complianceStatus"), normalizedComplianceStatus));
        }
        return propertyRepository.findAll(specification, pageable).getContent();
    }

    public Property update(Long id, UpdatePropertyRequest request) {
        Property property = getById(id);

        if (StringUtils.hasText(request.getCode())) {
            String newCode = normalize(request.getCode());
            if (propertyRepository.existsByCodeAndIdNot(newCode, id)) {
                throw new BusinessException(4004, "Property code already exists.");
            }
            property.setCode(newCode);
        }
        if (request.getRent() != null) {
            property.setRent(request.getRent());
        }
        if (request.getDeposit() != null) {
            property.setDeposit(request.getDeposit());
        }
        if (StringUtils.hasText(request.getStatus())) {
            property.setStatus(normalize(request.getStatus()));
        }
        if (request.getStartDate() != null) {
            property.setStartDate(request.getStartDate());
        }
        if (request.getEndDate() != null) {
            property.setEndDate(request.getEndDate());
        }
        if (StringUtils.hasText(request.getComplianceStatus())) {
            property.setComplianceStatus(normalize(request.getComplianceStatus()));
        }
        if (request.getMediaRefs() != null) {
            property.setMediaRefs(serializeMediaRefs(request.getMediaRefs()));
        }

        validateDateRange(property.getStartDate(), property.getEndDate());
        return propertyRepository.save(property);
    }

    public void delete(Long id) {
        Property property = getById(id);
        propertyRepository.delete(property);
    }

    public Property markListed(Long id) {
        Property property = getById(id);
        property.setStatus("LISTED");
        return propertyRepository.save(property);
    }

    public Property markDelisted(Long id) {
        Property property = getById(id);
        property.setStatus("DELISTED");
        return propertyRepository.save(property);
    }

    public Property reviewCompliance(Long id, ComplianceReviewRequest request) {
        Property property = getById(id);
        property.setComplianceStatus(normalize(request.getComplianceStatus()));
        return propertyRepository.save(property);
    }

    private void validateDateRange(LocalDateTime startDate, LocalDateTime endDate) {
        if (startDate != null && endDate != null && !endDate.isAfter(startDate)) {
            throw new BusinessException(4005, "endDate must be after startDate.");
        }
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? normalize(value) : defaultValue;
    }

    private String normalize(String value) {
        return value.trim().toUpperCase();
    }

    private String serializeMediaRefs(List<String> mediaRefs) {
        if (mediaRefs == null || mediaRefs.isEmpty()) {
            return null;
        }
        return mediaRefs.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }
}
