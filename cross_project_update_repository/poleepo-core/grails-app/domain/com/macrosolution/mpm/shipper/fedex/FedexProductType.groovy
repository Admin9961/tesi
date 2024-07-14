package com.macrosolution.mpm.shipper.fedex

class FedexProductType {

    /* VALORI SERVIZI NAZIONALI */
    public static final String ECONOMY_EXPRESS_CODE = 'N'
    public static final String ECONOMY_EXPRESS_DESC = 'Economy Express'
    public static final String EXPRESS_CODE = 'A'
    public static final String EXPRESS_DESC = 'Express'
    public static final String NATIONAL_DIECI_EXPRESS_DESC = "10:00 Express"
    public static final String NATIONAL_DIECI_EXPRESS_CODE = "D"
    public static final String NATIONAL_DODICI_EXPRESS_DESC = "12:00 Express"
    public static final String NATIONAL_DODICI_EXPRESS_CODE = "T"
    public static final String NATIONAL_DIRECT_EXPRESS_DESC = "Direct Express"
    public static final String NATIONAL_DIRECT_EXPRESS_CODE = "32"
    public static final String NATIONAL_CARGO_EXPRESS_DESC = "Cargo Express"
    public static final String NATIONAL_CARGO_EXPRESS_CODE = "13"

    /* VALORI SERVIZI INTERNAZIONALI */
    public static final String INTERNATIONAL_NOVE_EXPRESS_MERCE_DESC = "09:00 Express Merce"
    public static final String INTERNATIONAL_NOVE_EXPRESS_MERCE_CODE = "09N"
    public static final String INTERNATIONAL_DIECI_EXPRESS_MERCE_DESC = "10:00 Express Merce"
    public static final String INTERNATIONAL_DIECI_EXPRESS_MERCE_CODE = "10N"
    public static final String INTERNATIONAL_DODICI_EXPRESS_MERCE_DESC = "12:00 Express Merce"
    public static final String INTERNATIONAL_DODICI_EXPRESS_MERCE_CODE = "12N"
    public static final String INTERNATIONAL_EXPRESS_DESC = "Express"
    public static final String INTERNATIONAL_EXPRESS_CODE = "15N"
    public static final String INTERNATIONAL_IDE_MASTERCON_ROAD_DESC = "IDE Mastercon Road"
    public static final String INTERNATIONAL_IDE_MASTERCON_ROAD_CODE = "130"
    public static final String INTERNATIONAL_IDE_ECONOMY_DESC = "IDE Economy"
    public static final String INTERNATIONAL_IDE_ECONOMY_CODE = "30"
    public static final String INTERNATIONAL_DODICI_ECONOMY_EXPRESS_DESC = "12:00 Economy Express"
    public static final String INTERNATIONAL_DODICI_ECONOMY_EXPRESS_CODE = "412"
    public static final String INTERNATIONAL_ECONOMY_EXPRESS_DESC = "Economy Express"
    public static final String INTERNATIONAL_ECONOMY_EXPRESS_CODE = "48N"
    public static final String INTERNATIONAL_IDE_MASTER_SHIPMENT_EXPRESS_DESC = "IDE Master shipment for Express (AIR)"
    public static final String INTERNATIONAL_IDE_MASTER_SHIPMENT_EXPRESS_CODE = "99"
    public static final String INTERNATIONAL_IDE_CHILD_SHIPMENT_EXPRESS_DESC = "IDE Express"
    public static final String INTERNATIONAL_IDE_CHILD_SHIPMENT_EXPRESS_CODE = "29"

    public static final String NATIONAL = "NAZIONALE"
    public static final String INTERNATIONAL = "INTERNAZIONALE"

    String code
    String description
    String location

    static constraints = {
    }

    String getSelectName() {
        if(location == INTERNATIONAL) {
            return "${description} (Internazionale)"
        }
        return "${description} (Nazionale)"
    }

    String getTntApplication() {
        if(location == INTERNATIONAL) {
            return "MYRTLI"
        }
        return "MYRTL"
    }

    static List<FedexProductType> getNationalProductTypeList() {
        return findAllByLocation(FedexProductType.NATIONAL);
    }

    static List<FedexProductType> getInternationalProductTypeList() {
        return findAllByLocation(FedexProductType.INTERNATIONAL);
    }
}
