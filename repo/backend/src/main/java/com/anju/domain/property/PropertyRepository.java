package com.anju.domain.property;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property, Long>, JpaSpecificationExecutor<Property> {

    boolean existsByCode(String code);

    Optional<Property> findByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
