package com.ethioloadai.freight.repository;

import com.ethioloadai.freight.entity.Freight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FreightRepository extends JpaRepository<Freight, Long>, JpaSpecificationExecutor<Freight> {

    List<Freight> findByShipperId(Long shipperId);

    List<Freight> findByStatus(Freight.FreightStatus status);

    List<Freight> findByStatusAndMaterialType(Freight.FreightStatus status, String materialType);

    List<Freight> findByMatchedDriverId(Long driverId);

    Optional<Freight> findByIdAndShipperId(Long id, Long shipperId);

    boolean existsByShipperIdAndId(Long shipperId, Long id);
}
