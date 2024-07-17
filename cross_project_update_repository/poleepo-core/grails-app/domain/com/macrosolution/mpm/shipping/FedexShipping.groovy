package com.macrosolution.mpm.shipping

import com.macrosolution.mpm.shipper.fedex.FedexProductType
import com.macrosolution.mpm.shipping.fedex.FedexBooking

class FedexShipping extends Shipping {

    // devo riferirmi alle foreign key sempre inserendo il nome fedex davanti, altrimenti vanno in conflitto con le altre
    // (es: in tnt si chiama soltanto booking, non posso chiamare anche questa booking)
    FedexBooking fedexBooking
    FedexProductType fedexProductType

    String codCommission  		// Commissioni CashOnDelivery a carico di mittente (S) o destinatario (R)
    String insuranceCommission	// Commissioni assicurazione a carico di mittente (S) o destinatario (R)

    static constraints = {
        //TODO: manca fedexLabelConsignment, capire se necessario
        fedexBooking nullable:true
        fedexProductType nullable: true

        //capire se servono
        codCommission nullable:true
        insuranceCommission nullable:true
    }

    /**
     * Metodo per ottenere il metodo di spedizione da utilizzare nell'aggiornamento del tracking dell'ordine
     * @return il servizio della spedizione
     * */
    String getShippingMethod() {
        if(fedexProductType != null)
            return fedexProductType.description
        return "Express"
    }

    @Override
    boolean useCarrierService() {
        return true
    }
}
