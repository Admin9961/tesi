package com.macrosolution.mpm.carrierservice.domain;

import javax.persistence.*;

@Entity
@Table(name    = "shipper_type")
public class ShipperType {
    
    public static final String TNT = "TNT";
    public static final String GLS = "GLS";
    public static final String POSTE = "POSTE";
    public static final String POSTE_SDA = "SDA";
    public static final String POSTE_CRONO = "CRONO";
    public static final String POSTE_DELIVERY = "POSTE_DELIVERY";
    public static final String BRT = "BRT";
    public static final String DHL = "DHL";
    public static final String GEN = "Generico";
    public static final String PAYPERSHIP = "PAYPERSHIP";
    public static final String MIT = "MIT";
    public static final String SPEDISCI_ONLINE = "SPEDISCI_ONLINE";
    public static final String QAPLA = "QAPLA";
    public static final String FEDEX = "FEDEX";

    @Id
    @GeneratedValue(strategy= GenerationType.AUTO)
    private Integer id;
    private String type;


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "ShipperType{" +
                "type='" + type + '\'' +
                '}';
    }
}
