package com.macrosolution.mpm.carrierservice.domain.fedex;

import com.macrosolution.mpm.carrierservice.domain.Configuration;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Index;
import javax.persistence.Table;
import java.util.Date;

@Entity
@Table(name = "fedex_configuration",
        indexes = {@Index(name = "store_idx", columnList = "store_id")})
public class FedexConfiguration extends Configuration {

    @Column(name = "client_code")
    private String clientCode;

    @Column(name = "username")
    private String username;

    @Column(name = "password")
    private String password;

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
}
