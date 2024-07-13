package com.macrosolution.mpm.shipper.fedex

class FedexProductType {

    /* VALORI SERVIZI NAZIONALI */
    public static final String ECONOMY_EXPRESS_CODE = 'N'
    public static final String ECONOMY_EXPRESS_DESC = 'Economy Express'
    public static final String EXPRESS_CODE = 'A'
    public static final String EXPRESS_DESC = 'Express'

    /* VALORI SERVIZI INTERNAZIONALI */
    public static final String INTERNATIONAL_NOVE_EXPRESS_MERCE_DESC = "09:00 Express Merce"
    public static final String INTERNATIONAL_NOVE_EXPRESS_MERCE_CODE = "09N"
    public static final String INTERNATIONAL_DIECI_EXPRESS_MERCE_DESC = "10:00 Express Merce"
    public static final String INTERNATIONAL_DIECI_EXPRESS_MERCE_CODE = "10N"

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
