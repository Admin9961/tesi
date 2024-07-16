package com.macrosolution.mpm.shipping.fedex

class FedexBooking{

    Date priopntime
    Date priclotime
    Date secopntime
    Date secclotime
    Date availabilitytime
    Date pickupdate
    Date pickuptime
    String pickupinstr

    static constraints = {
        priopntime nullable:true
        priclotime nullable:true
        secopntime nullable:true
        secclotime nullable:true
        availabilitytime nullable:true
        pickupdate nullable:true
        pickuptime nullable:true
        pickupinstr nullable:true
    }
}
