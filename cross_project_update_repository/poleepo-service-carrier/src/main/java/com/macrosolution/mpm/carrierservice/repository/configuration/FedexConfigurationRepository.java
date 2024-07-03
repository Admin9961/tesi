package com.macrosolution.mpm.carrierservice.repository.configuration;

import com.macrosolution.mpm.carrierservice.domain.brt.BrtConfiguration;
import com.macrosolution.mpm.carrierservice.domain.fedex.FedexConfiguration;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FedexConfigurationRepository extends CrudRepository<FedexConfiguration, Integer> {

    @Modifying
    @Query("update FedexConfiguration set shipperDefault = false where storeID = :storeID and virtualShipperType = :virtualShipperType")
    void resetDefault(@Param("storeID") Long storeId, @Param("virtualShipperType") Integer virtualShipperType);

    @Query("select fc from FedexConfiguration fc where fc.storeID = :storeID")
    Optional<List<FedexConfiguration>> findAllByStoreID(Long storeID);

    @Query("select fc from FedexConfiguration fc where fc.storeID = :storeID and fc.virtualShipperType = :virtualShipperType")
    Optional<FedexConfiguration> findByStoreIDAndVirtualShipperType(@Param("storeID") Long storeID, @Param("virtualShipperType") Integer virtualShipperType);
}
