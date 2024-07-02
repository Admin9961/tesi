package com.macrosolution.mpm.carrierservice.repository.configuration;

import com.macrosolution.mpm.carrierservice.domain.fedex.FedexConfiguration;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FedexConfigurationRepository extends CrudRepository<FedexConfiguration, Integer> {


}
