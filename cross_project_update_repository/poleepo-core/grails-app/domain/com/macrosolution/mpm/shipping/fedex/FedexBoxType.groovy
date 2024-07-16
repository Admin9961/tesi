package com.macrosolution.mpm.shipping.fedex

import com.macrosolution.mpm.shipping.BoxType

class FedexBoxType extends BoxType{

    public static final String COLLI_LABEL = 'Colli'
    public static final String COLLI_VALUE = "C"
    public static final String BUSTE_LABEL  = 'Buste'
    public static final String BUSTE_VALUE  = "S"
    public static final String BAULETTI_LABEL = 'Bauletti'
    public static final String BAULETTI_VALUE = "B"
    public static final String BAULETTI_GR_LABEL  = 'Bauletti Grandi'
    public static final String BAULETTI_GR_VALUE  = "D"

    static constraints = {
    }
}