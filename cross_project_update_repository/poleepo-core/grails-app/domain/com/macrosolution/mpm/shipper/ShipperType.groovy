package com.macrosolution.mpm.shipper

import com.macrosolution.mpm.marketplace.SourceMP

class ShipperType {

    public static final String TNT = 'TNT'
    public static final String GLS = 'GLS'
    public static final String GLS_2 = 'GLS_2'
    public static final String GEN = 'Generic'
    public static final String POSTE = 'POSTE'
    public static final String POSTE_DELIVERY = 'POSTE_DELIVERY'
    public static final String SDA = 'SDA'
    public static final String CRONO = "CRONO";
    public static final String BRT = "BRT";
    public static final String DHL = "DHL";
    public static final String PAYPERSHIP = "PAYPERSHIP"
    public static final String MIT = "MIT";
    public static final String SPEDISCI_ONLINE = "SPEDISCI_ONLINE";
    public static final String QAPLA = "QAPLA";
    public static final String FEDEX = "FEDEX"

    /* AMAZON */
    public static final String AMAZON_IT = "AMZ-IT"
    public static final String AMAZON_DE = "AMZ-DE"
    public static final String AMAZON_ES = "AMZ-ES"
    public static final String AMAZON_FR = "AMZ-FR"
    public static final String AMAZON_GB = "AMZ-GB"
    public static final String AMAZON_BE = "AMZ-BE"
    public static final String AMAZON_PL = "AMZ-PL"
    public static final String AMAZON_NL = "AMZ-NL"
    public static final String AMAZON_SE = "AMZ-SE"

    // DEALER-SERVICE
    public static final String VIDAXL = "VIDAXL"
    public static final String BIGBUY = "BIGBUY"

    public static final Long CARRIER_ID_TNT = 1
    public static final Long CARRIER_ID_GLS = 2
    public static final Long CARRIER_ID_GENERIC = 3
    public static final Long CARRIER_ID_POSTE = 4
    public static final Long CARRIER_ID_SDA = 5
    public static final Long CARRIER_ID_CRONO = 6
    public static final Long CARRIER_ID_BRT = 7
    public static final Long CARRIER_ID_DHL = 8
    public static final Long CARRIER_ID_POSTE_DELIVERY = 9
    public static final Long CARRIER_ID_PAYPERSHIP = 10
    public static final Long CARRIER_ID_AMAZON_IT = 11
    public static final Long CARRIER_ID_AMAZON_DE = 12
    public static final Long CARRIER_ID_AMAZON_ES = 13
    public static final Long CARRIER_ID_AMAZON_FR = 14
    public static final Long CARRIER_ID_AMAZON_GB = 15
    public static final Long CARRIER_ID_AMAZON_BE = 16
    public static final Long CARRIER_ID_AMAZON_PL = 17
    public static final Long CARRIER_ID_AMAZON_NL = 18
    public static final Long CARRIER_ID_AMAZON_SE = 19
    public static final Long CARRIER_ID_MIT = 21
    public static final Long CARRIER_ID_SPEDISCI_ONLINE = 22
    public static final Long CARRIER_ID_QAPLA = 23
    public static final Long CARRIER_ID_FEDEX = 30


    String type

    static constraints = {
    }

    @Override
    public String toString(){
        return type
    }

    String getTypeName() {
        switch (type) {
            case POSTE_DELIVERY:
                return "POSTE DELIVERY"
            case POSTE:
                return "POSTE CRONO"
            case SPEDISCI_ONLINE:
                return "SPEDISCI.ONLINE"
            case AMAZON_IT:
                return "AMAZON ITALIA"
            case AMAZON_DE:
                return "AMAZON GERMANIA"
            case AMAZON_ES:
                return "AMAZON SPAGNA"
            case AMAZON_FR:
                return "AMAZON FRANCIA"
            case AMAZON_GB:
                return "AMAZON GRAN BRETAGNA"
            case AMAZON_BE:
                return "AMAZON BELGIO"
            case AMAZON_PL:
                return "AMAZON POLONIA"
            case AMAZON_NL:
                return "AMAZON OLANDA"
            case AMAZON_SE:
                return "AMAZON SVEZIA"
            default:
                return type
        }
    }

    Long getNumType(){
        switch (type) {
            case TNT:
                return CARRIER_ID_TNT
                break;
            case GLS:
            case GLS_2:
                return CARRIER_ID_GLS
                break;
            case SDA:
                return CARRIER_ID_SDA
                break;
            case BRT:
                return CARRIER_ID_BRT
                break;
            case DHL:
                return CARRIER_ID_DHL
                break;
            case GEN:
                return CARRIER_ID_GENERIC
                break;
            case POSTE_DELIVERY:
                return CARRIER_ID_POSTE_DELIVERY
                break;
            case CRONO:
                return CARRIER_ID_CRONO
                break;
            case POSTE:
                return CARRIER_ID_POSTE
                break;
            case MIT:
                return CARRIER_ID_MIT
            case FEDEX:
                return CARRIER_ID_FEDEX
            default:
                return id
        }

    }

    /**
     * Metodo per sapere se uno shipper type è di un marketplace
     * @return true se è di un canale di vendita, false altrimenti
     * */
    Boolean isMarketplace() {
        switch (type) {
            case AMAZON_IT:
            case AMAZON_DE:
            case AMAZON_ES:
            case AMAZON_FR:
            case AMAZON_GB:
            case AMAZON_BE:
            case AMAZON_PL:
            case AMAZON_NL:
            case AMAZON_SE:
                return true
            default:
                return false
        }
    }

    /**
     * Metodo per sapere se uno shipper type è di un marketplace
     * @return true se è di un canale di vendita, false altrimenti
     * */
    Boolean isDealer() {
        switch (type) {
            case VIDAXL:
            case BIGBUY:
                return true
            default:
                return false
        }
    }

    /**
     * Metodo per ottenere lo shipper type da una source
     * @param source la source del canale di vendita
     * @return lo shipper type per la source
     * */
    static String typeBySource(Long source) {
        switch (source) {
            case SourceMP.SOURCE_AMAZON_IT:
                return AMAZON_IT
            case SourceMP.SOURCE_AMAZON_DE:
                return AMAZON_DE
            case SourceMP.SOURCE_AMAZON_ES:
                return AMAZON_ES
            case SourceMP.SOURCE_AMAZON_FR:
                return AMAZON_FR
            case SourceMP.SOURCE_AMAZON_GB:
                return AMAZON_GB
            case SourceMP.SOURCE_AMAZON_BE:
                return AMAZON_BE
            case SourceMP.SOURCE_AMAZON_PL:
                return AMAZON_PL
            case SourceMP.SOURCE_AMAZON_NL:
                return AMAZON_NL
            case SourceMP.SOURCE_AMAZON_SE:
                return AMAZON_SE
            default:
                return null
        }
    }

    static String typeByDealer(String dealerType) {

        switch (dealerType) {
            case DealerShipper.DEALER_VIDAXL: return VIDAXL
            case DealerShipper.DEALER_BIGBUY: return BIGBUY

            default: return null
        }
    }

    /**
     * Metodo per ottenere il type dal nome delle API di Poleepo
     * */
    static String typeFromApi(String api) {
        switch (api) {
            case "TNT_IT":
                return TNT
            case "GLS_IT":
                return GLS
            case "GENERIC":
                return GEN
            case "POSTE_ITALIANE":
                return POSTE
            case "POSTE_DELIVERY":
                return POSTE_DELIVERY
            case "POSTE_CRONO":
                return CRONO
            case "SDA":
                return SDA
            case "BRT_IT":
                return BRT
            case "DHL_IT":
                return DHL
            case "PAY_PER_SHIP":
                return PAYPERSHIP
            case "AMAZON_IT":
                return AMAZON_IT
            case "AMAZON_DE":
                return AMAZON_DE
            case "AMAZON_ES":
                return AMAZON_ES
            case "AMAZON_FR":
                return AMAZON_FR
            case "AMAZON_GB":
                return AMAZON_GB
            case "AMAZON_BE":
                return AMAZON_BE
            case "AMAZON_PL":
                return AMAZON_PL
            case "AMAZON_NL":
                return AMAZON_NL
            case "AMAZON_SE":
                return AMAZON_SE
            case "GLS2_IT":
                return GLS_2
            case "MIT":
                return MIT
            case "FEDEX":
                return FEDEX
            default:
                return GEN
        }
    }

    static Long sourceFromApi(String api) {
        switch (api) {
            case "AMAZON_IT":
                return SourceMP.SOURCE_AMAZON_IT
            case "AMAZON_DE":
                return SourceMP.SOURCE_AMAZON_DE
            case "AMAZON_ES":
                return SourceMP.SOURCE_AMAZON_ES
            case "AMAZON_FR":
                return SourceMP.SOURCE_AMAZON_FR
            case "AMAZON_GB":
                return SourceMP.SOURCE_AMAZON_GB
            case "AMAZON_BE":
                return SourceMP.SOURCE_AMAZON_BE
            case "AMAZON_PL":
                return SourceMP.SOURCE_AMAZON_PL
            case "AMAZON_NL":
                return SourceMP.SOURCE_AMAZON_NL
            case "AMAZON_SE":
                return SourceMP.SOURCE_AMAZON_SE
            default:
                return null
        }
    }

    static Boolean isCarrierService(String type) {
        switch (type) {
            case TNT:
            case POSTE:
            case POSTE_DELIVERY:
            case SDA:
            case CRONO:
            case BRT:
            case DHL:
            case PAYPERSHIP:
            case SPEDISCI_ONLINE:
            case MIT:
            case QAPLA:
            case FEDEX:
                return true
            default:
                return false
        }
    }

    static Integer getVirtualShipperType(String type, Integer offset) {

        Integer res = 0;

        switch (type) {
            case TNT:
                res = 1000
                break
            case GLS:
            case GLS_2:
                res = 2000
                break
            case GEN:
                res = 3000
                break
            case POSTE:
                res = 4000
                break
            case SDA:
                res = 5000
                break
            case CRONO:
                res = 6000
                break
            case BRT:
                res = 7000
                break
            case DHL:
                res = 8000
                break
            case POSTE_DELIVERY:
                res = 9000
                break
            case PAYPERSHIP:
                res = 10000
                break
            case AMAZON_IT:
                res = 11000
                break
            case AMAZON_DE:
                res = 12000
                break
            case AMAZON_ES:
                res = 13000
                break
            case AMAZON_FR:
                res = 14000
                break
            case AMAZON_GB:
                res = 15000
                break
            case AMAZON_BE:
                res = 16000
                break
            case AMAZON_PL:
                res = 17000
                break
            case AMAZON_NL:
                res = 18000
                break
            case AMAZON_SE:
                res = 19000
                break
            case MIT:
                res = 21000
                break
            case SPEDISCI_ONLINE:
                res = 22000
                break
            case QAPLA:
                res = 23000
                break
            case VIDAXL:
                res = 24000
                break
            case BIGBUY:
                res = 25000
                break
            case FEDEX:
                res = 30000
                break
        }

        return res + offset

    }

    static Integer getTypeNumberByVirtualShipperType(Integer virtualShipperType) {
        int type = (int) (virtualShipperType / 1000)
        if(type == 2) {
            if(virtualShipperType % 1000 == 2) {
                return 20
            }
        }
        return type
    }

    static String getTypeByVirtualShipperType(Integer virtualShipperType) {
        int offset = virtualShipperType % 1000

        switch ((virtualShipperType / 1000) as Integer) {
            case 1: return TNT
            case 2: return offset == 1 ? GLS : GLS_2
            case 3: return GEN
            case 4: return POSTE
            case 5: return SDA
            case 6: return CRONO
            case 7: return BRT
            case 8: return DHL
            case 9: return POSTE_DELIVERY
            case 10: return PAYPERSHIP
            case 11: return AMAZON_IT
            case 12: return AMAZON_DE
            case 13: return AMAZON_ES
            case 14: return AMAZON_FR
            case 15: return AMAZON_GB
            case 16: return AMAZON_BE
            case 17: return AMAZON_PL
            case 18: return AMAZON_NL
            case 19: return AMAZON_SE
            case 21: return MIT
            case 22: return SPEDISCI_ONLINE
            case 23: return QAPLA
            case 24: return VIDAXL
            case 25: return BIGBUY
            case 30: return FEDEX
            default: return ""
        }
    }

    static Integer getOffsetByVirtualShipperType(Integer virtualShipperType) {
        return virtualShipperType % 1000;
    }
}
