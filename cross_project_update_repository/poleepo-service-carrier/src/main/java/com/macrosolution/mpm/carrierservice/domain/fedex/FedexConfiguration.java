package com.macrosolution.mpm.carrierservice.domain.fedex;

import com.macrosolution.mpm.carrierservice.converters.StringListConverter;
import com.macrosolution.mpm.carrierservice.domain.Configuration;
import lombok.*;

import javax.persistence.*;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
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

    private Boolean checkBookingForm;
    private Date priopntime;
    private Date priclotime;
    private Date secopntime;
    private Date secclotime;
    private Date availabilitytime;
    private Date pickuptime;

}
