package com.macrosolution.mpm.carrierservice.domain.fedex;

import com.macrosolution.mpm.carrierservice.converters.StringListConverter;
import com.macrosolution.mpm.carrierservice.domain.Configuration;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@Entity
@Table(name = "fedex_configuration",
        indexes = {@Index(name = "store_idx", columnList = "store_id")})
public class FedexConfiguration extends Configuration {

    @Column(name = "client_code")
    private String clientCode;

    @Column(name = "departure_depot")
    private String departureDepot;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

    @Column(name = "default_tariff")
    private String defaultTariff;

    @Convert(converter = StringListConverter.class)
    List<String> productTypes;

    public String getClientCode() {
        return clientCode;
    }

    public void setClientCode(String clientCode) {
        this.clientCode = clientCode;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) { this.username = username; }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDepartureDepot() {
        return departureDepot;
    }

    public void setDepartureDepot(String departureDepot) {
        this.departureDepot = departureDepot;
    }

    public String getDefaultTariff() {
        return defaultTariff;
    }

    public void setDefaultTariff(String defaultTariff) {
        this.defaultTariff = defaultTariff;
    }
}
