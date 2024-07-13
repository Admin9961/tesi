package com.macrosolution.mpm.carrier

import com.macrosolution.mpm.marketplace.configuration.MPConfiguration
import com.macrosolution.mpm.marketplace.configuration.MPConfigurationService
import com.macrosolution.mpm.orderstate.OrderState
import com.macrosolution.mpm.shipper.Shipper
import com.macrosolution.mpm.shipper.ShipperType
import com.macrosolution.mpm.store.Store
import com.macrosolution.mpm.util.FeatureService
import com.macrosolution.mpm.utility.log.ILog
import com.macrosolution.mpm.utility.log.Log
import com.macrosolution.tntcarriermanager.TntProductType
import com.macrosolution.mpm.shipper.fedex.FedexProductType
import grails.converters.JSON
import grails.plugin.springsecurity.annotation.Secured

@Secured(["permitAll"])
class CarriersController {
    private static ILog MPMlog = Log.getLogger();

    def shippingService
    def shipperService
    FeatureService featureService
    MPConfigurationService MPConfigurationService

    def index() {
        def storeId = session['store_id']

        if(session['store_id']){
            def store_id    = session['store_id']
            Store store = Store.findById(storeId)

            def shipperConfs = shipperService.getConfigurations(store_id);
            def tntconf            = shipperConfs.get(ShipperType.TNT)
            def glsconf            = shipperConfs.get(ShipperType.GLS)
            def gls2conf           = shipperConfs.get(ShipperType.GLS_2)
            def sdaconf            = shipperConfs.get(ShipperType.SDA)
            def posteconf          = shipperConfs.get(ShipperType.POSTE)
            def posteDconf         = shipperConfs.get(ShipperType.POSTE_DELIVERY)
            def brtconf            = shipperConfs.get(ShipperType.BRT)
            def dhlconf            = shipperConfs.get(ShipperType.DHL)
            def mitconf            = shipperConfs.get(ShipperType.MIT)
            def spedisciOnlineconf = shipperConfs.get(ShipperType.SPEDISCI_ONLINE)
            def qaplaConf          = shipperConfs.get(ShipperType.QAPLA)
            def fedexConf          = shipperConfs.get(ShipperType.FEDEX)

            if(glsconf)
            {
                glsconf=glsconf[0]

            }
            if(gls2conf)
            {
                gls2conf=gls2conf[0]

            }
            if(tntconf)
            {
                tntconf=tntconf[0]
                try {
                    def shipper = shipperService.getByCodeAndStoreId(ShipperType.TNT, store_id)
                    tntconf.shipperDefault = shipper?.shipperDefault?:false
                    tntconf.enable = shipper?.enable?:false
                } catch (Exception e) {
                    MPMlog.error("DUPLICATE SHIPPER FOR SDA WITH STORE_ID = "+store_id)
                }
            }
            //setta sulla configurazione di sda se è di default o no
            if(sdaconf) {
                sdaconf=sdaconf[0]
                try {
                    def shipper = shipperService.getByCodeAndStoreId(ShipperType.SDA, store_id)
                    sdaconf.shipperDefault = shipper?.shipperDefault?:false
                    sdaconf.enable = shipper?.enable?:false
                } catch (Exception e) {
                    MPMlog.error("DUPLICATE SHIPPER FOR SDA WITH STORE_ID = "+store_id)
                }
            }
            //setta sulla configurazione di poste se è di default o no
            if(posteconf) {
                posteconf=posteconf[0]
                try {
                    def shipper = shipperService.getByCodeAndStoreId(ShipperType.POSTE, store_id)
                    posteconf.shipperDefault = shipper?.shipperDefault?:false
                    posteconf.enable = shipper?.enable?:false
                } catch (Exception e) {
                    MPMlog.error("DUPLICATE SHIPPER FOR POSTE WITH STORE_ID = "+store_id)
                }
            }
            //setta sulla configurazione di poste se è di default o no
            if(posteDconf) {
                posteDconf=posteDconf[0]
                try {
                    def shipper = shipperService.getByCodeAndStoreId(ShipperType.POSTE_DELIVERY, store_id)
                    posteDconf.shipperDefault = shipper?.shipperDefault?:false
                    posteDconf.enable = shipper?.enable?:false
                } catch (Exception e) {
                    MPMlog.error("DUPLICATE SHIPPER FOR POSTE DELIVERY WITH STORE_ID = "+store_id)
                }
            }
            //setta sulla configurazione di brt se è di default o no
            if(brtconf) {
                brtconf=brtconf[0]
                try {
                    def shipper = shipperService.getByCodeAndStoreId(ShipperType.BRT, store_id)
                    brtconf.shipperDefault = shipper?.shipperDefault?:false
                    brtconf.enable = shipper?.enable?:false
                } catch (Exception e) {
                    MPMlog.error("DUPLICATE SHIPPER FOR BRT WITH STORE_ID = "+store_id)
                }
            }
            //setta sulla configurazione di dhl se è di default o no
            if(dhlconf) {
                dhlconf=dhlconf[0]
                try {
                    def shipper = shipperService.getByCodeAndStoreId(ShipperType.DHL, store_id)
                    dhlconf.shipperDefault = shipper?.shipperDefault?:false
                    dhlconf.enable = shipper?.enable?:false
                } catch (Exception e) {
                    MPMlog.error("DUPLICATE SHIPPER FOR DHL WITH STORE_ID = "+store_id)
                }
            }
            //setta sulla configurazione di dhl se è di default o no
            if(mitconf) {
                mitconf=mitconf[0]
                try {
                    def shipper = shipperService.getByCodeAndStoreId(ShipperType.MIT, store_id)
                    mitconf.shipperDefault = shipper?.shipperDefault?:false
                    mitconf.enable = shipper?.enable?:false
                } catch (Exception e) {
                    MPMlog.error("DUPLICATE SHIPPER FOR MIT WITH STORE_ID = "+store_id)
                }
            }

            //setta sulla configurazione di spedisci online se è di default o no
            if(spedisciOnlineconf) {
                spedisciOnlineconf=spedisciOnlineconf[0]
                try {
                    def shipper = shipperService.getByCodeAndStoreId(ShipperType.SPEDISCI_ONLINE, store_id)
                    spedisciOnlineconf.shipperDefault = shipper?.shipperDefault ?: false
                    spedisciOnlineconf.enable = shipper?.enable ?: false
                } catch (Exception e) {
                    MPMlog.error("DUPLICATE SHIPPER FOR SPEDISCI ONLINE WITH STORE_ID = " + store_id)
                }
            }
            // setta sulla configurazione di qapla se è di default o no
            if (qaplaConf) {
                qaplaConf = qaplaConf[0]
                try {
                    def shipper = shipperService.getByCodeAndStoreId(ShipperType.QAPLA, store_id)
                    qaplaConf.shipperDefault = shipper?.shipperDefault ?: false
                    qaplaConf.enable = shipper?.enable ?: false
                } catch (Exception e) {
                    MPMlog.error("DUPLICATE SHIPPER FOR QAPLA WITH STORE_ID = " + store_id)
                }
            }

            if (fedexConf) {
                fedexConf = fedexConf[0]
                try {
                    def shipper = shipperService.getByCodeAndStoreId(ShipperType.FEDEX, store_id)
                    fedexConf.shipperDefault = shipper?.shipperDefault ?: false
                    fedexConf.enable = shipper?.enable ?: false
                } catch (Exception e) {
                    MPMlog.error("DUPLICATE SHIPPER FOR FEDEX WITH STORE_ID = " + store_id)
                }
            }

            [store:store, listShipper:shipperConfs,
             tntconf: tntconf,
             glsconf: glsconf,
             gls2conf: gls2conf,
             sdaconf: sdaconf,
             brtconf: brtconf,
             posteconf: posteconf,
             posteDeliveryconf: posteDconf,
             dhlconf: dhlconf,
             mitconf: mitconf,
             spedisciOnlineconf: spedisciOnlineconf,
             qaplaconf: qaplaConf,
             fedexconf: fedexConf]
        }
    }

    def showTemplate() {
        def category = params.get('category')
        def template = params.get('template')
        def storeId =session['store_id']
        def store=Store.get(storeId)
        def shipper
        def listShipper = shipperService.getConfigurations(storeId)

        if(category == 'carriers') {
            ShipperType shipperType = null

            MPMlog.debug("shippers -> ${shipper as JSON}")

            if (template == 'tnt') {
//                shipper = TntShipper.findByStore(store)


                shipper = listShipper.get(ShipperType.TNT)
//                try {
//                    if(shipper != null && shipper[0] != null) {
//                        def tnt = (shipperService.getAll(store_id: storeId, code: ShipperType.TNT) as List)[0]
//
//                        shipper[0].shipperDefault = tnt?.shipperDefault ?: false
//                        shipper[0].enable = tnt?.enable ?: false
//                    }
//                } catch (Exception e) {
//                    MPMlog.error("Errore durante il recupero dello shipper per BRT e store ${storeId}", e)
//                }

                /* recupero la lista di servizi nazionali e internazionali */
                def nationalProductTypes = TntProductType.getNationalProductTypeList()
                def internationalProductTypes = TntProductType.getInternationalProductTypeList()

                /* recupero la lista di servizi nazionali e internazionali presenti nella configurazione */
                shipper?.each { s ->
                    def nationalProductTypeStored = []
                    def internationalProductTypeStored = []

                    Shipper tntShipper = shipperService.getByCodeAndStoreIdAndVirtualShipperType(ShipperType.TNT, storeId, s.virtual_shipper_type)

                    MPMlog.debug("tntShipper -> ${tntShipper}")

                    s.shipperDefault = tntShipper?.shipperDefault ?: false
                    s.enable = tntShipper?.enable ?: false
                    s.virtualShipperType = s.virtual_shipper_type

                    if(s.serviceTypes != null && (s.serviceTypes as List).size() > 0) {
                        nationalProductTypeStored = TntProductType.findAllByLocationAndIdInList(TntProductType.NATIONAL, (s.serviceTypes ?: new ArrayList<>() as List<String>).collect {Long.parseLong(it)})
                        internationalProductTypeStored = TntProductType.findAllByLocationAndIdInList(TntProductType.INTERNATIONAL, (s.serviceTypes ?: new ArrayList<>() as List<String>).collect {Long.parseLong(it)})
                    }
                    s.nationalProductTypeStored = nationalProductTypeStored
                    s.internationalProductTypeStored = internationalProductTypeStored
                }?.sort {it?.virtualShipperType}


                render template: "/carriers/tnt/configuration", model: [shippers: shipper, shipperType: shipperType, nationalProductTypes: nationalProductTypes, internationalProductTypes: internationalProductTypes]
                return
            }
            else if (template == 'gls') {
                shipper = listShipper.get(ShipperType.GLS)
                render template: "/carriers/gls/configuration", model: [shippers: shipper, shipperType: shipperType]
                return
            }
            else if (template == 'sda') {

                shipper = listShipper.get(ShipperType.SDA)
                try {
                    if(shipper != null && shipper[0] != null) {
                        def sda = shipperService.getByCodeAndStoreId(ShipperType.SDA, storeId)
                        shipper[0].shipperDefault = sda?.shipperDefault ?: false
                        shipper[0].enable = sda?.enable ?: false
                        shipper[0].virtualShipperType = shipper[0]?.virtual_shipper_type
                    }
                } catch (Exception e) {
                    MPMlog.error("Errore durante il recupero dello shipper per SDA e store ${storeId}", e)
                }

                render template: "/carriers/sda/configuration", model: [shippers: shipper, shipperType: shipperType]
                return

            }
            else if (template == 'poste') {

                Shipper posteCrono = shipperService.getByCodeAndStoreId(ShipperType.POSTE, storeId) as Shipper
                Shipper posteDelivery = shipperService.getByCodeAndStoreId(ShipperType.POSTE_DELIVERY, storeId) as Shipper

                render template: "/carriers/poste/configuration", model: [crono: posteCrono, delivery: posteDelivery]
                return

            }
            else if (template == 'brt') {

                shipper = listShipper.get(ShipperType.BRT)
                try {
                    if(shipper != null) {

                        shipper.each {s ->
                            Shipper brt = shipperService.getByCodeAndStoreIdAndVirtualShipperType(ShipperType.BRT, storeId, s.virtual_shipper_type)

                            s.shipperDefault = brt?.shipperDefault ?: false
                            s.enable = brt?.enable ?: false
                            s.virtualShipperType = s.virtual_shipper_type

                        }

                    }
                } catch (Exception e) {
                    MPMlog.error("Errore durante il recupero dello shipper per BRT e store ${storeId}", e)
                }

                render template: "/carriers/brt/configuration", model: [shippers: shipper, shipperType: shipperType]
                return

            }
            else if (template == 'dhl') {

                shipper = listShipper.get(ShipperType.DHL)
                try {
                    if(shipper != null && shipper[0] != null) {
                        def dhl = shipperService.getByCodeAndStoreId(ShipperType.DHL, storeId)
                        shipper[0].shipperDefault = dhl?.shipperDefault?:false
                        shipper[0].enable= dhl?.enable?:false
                        shipper[0].virtualShipperType = shipper[0]?.virtual_shipper_type
                    }
                } catch (Exception e) {
                    MPMlog.error("Errore durante il recupero dello shipper per DHL e store ${storeId}", e)
                }
                render template: "/carriers/dhl/configuration", model: [shippers: shipper, shipperType: shipperType]
                return

            }
            else if (template == 'mit') {

                shipper = listShipper.get(ShipperType.MIT)
                try {
                    if(shipper != null && shipper[0] != null) {
                        Shipper mit = shipperService.getByCodeAndStoreId(ShipperType.MIT, storeId) as Shipper
                        shipper[0].shipperDefault = mit?.shipperDefault?:false
                        shipper[0].enable= mit?.enable?:false
                        shipper[0].virtualShipperType = shipper[0]?.virtual_shipper_type
                    }
                } catch (Exception e) {
                    MPMlog.error("Errore durante il recupero dello shipper per MIT e store ${storeId}", e)
                }

                render template: "/carriers/mit/configuration", model: [shippers: shipper]
                return
            }
            else if (template == 'spediscionline') {
                shipper = listShipper.get(ShipperType.SPEDISCI_ONLINE)
                try {
                    if(shipper != null && shipper[0] != null) {
                        def spedisciOnline = shipperService.getByCodeAndStoreId(ShipperType.SPEDISCI_ONLINE, storeId)
                        shipper[0].shipperDefault = spedisciOnline?.shipperDefault ?: false
                        shipper[0].enable = spedisciOnline?.enable ?: false
                        shipper[0].token = spedisciOnline?.token ?: null
                        shipper[0].virtualShipperType = shipper[0]?.virtual_shipper_type

                    }
                } catch (Exception e) {
                    MPMlog.error("Errore durante il recupero dello shipper per SPEDISCI ONLINE e store ${storeId}", e)
                }
            }
            else if (template == 'qapla') {
                shipper = listShipper.get(ShipperType.QAPLA)
                try {
                    if(shipper != null && shipper[0] != null) {
                        def qapla = shipperService.getByCodeAndStoreId(ShipperType.QAPLA, storeId)
                        shipper[0].shipperDefault = qapla?.shipperDefault ?: false
                        shipper[0].enable = qapla?.enable ?: false
                        shipper[0].virtualShipperType = shipper[0]?.virtual_shipper_type
                    }
                } catch (Exception e) {
                    MPMlog.error("Errore durante il recupero dello shipper per QAPLA e store ${storeId}", e)
                }
            }
            else if (template == 'fedex') {
                /**
                shipper = listShipper.get(ShipperType.FEDEX)
                try {
                    if(shipper != null && shipper[0] != null) {
                        def fedex = shipperService.getByCodeAndStoreId(ShipperType.FEDEX, storeId)
                        shipper[0].shipperDefault = fedex?.shipperDefault ?: false
                        shipper[0].enable = fedex?.enable ?: false
                        shipper[0].virtualShipperType = shipper[0]?.virtual_shipper_type
                    }
                } catch (Exception e) {
                    MPMlog.error("Errore durante il recupero dello shipper per FEDEX e store ${storeId}", e)
                }
                render template: "/carriers/fedex/configuration", model: [shippers: shipper, shipperType: shipperType]
                return
                 */

                /* recupero la lista di servizi nazionali e internazionali */
                def nationalProductTypes = FedexProductType.getNationalProductTypeList()
                def internationalProductTypes = FedexProductType.getInternationalProductTypeList()

                /* recupero la lista di servizi nazionali e internazionali presenti nella configurazione */
                shipper?.each { s ->
                    def nationalProductTypeStored = []
                    def internationalProductTypeStored = []

                    Shipper fedexShipper = shipperService.getByCodeAndStoreIdAndVirtualShipperType(ShipperType.FEDEX, storeId, s.virtual_shipper_type)

                    MPMlog.debug("fedexShipper -> ${fedexShipper}")

                    s.shipperDefault = fedexShipper?.shipperDefault ?: false
                    s.enable = fedexShipper?.enable ?: false
                    s.virtualShipperType = s.virtual_shipper_type

                    if(s.serviceTypes != null && (s.serviceTypes as List).size() > 0) {
                        nationalProductTypeStored = FedexProductType.findAllByLocationAndIdInList(FedexProductType.NATIONAL, (s.serviceTypes ?: new ArrayList<>() as List<String>).collect {Long.parseLong(it)})
                        internationalProductTypeStored = FedexProductType.findAllByLocationAndIdInList(FedexProductType.INTERNATIONAL, (s.serviceTypes ?: new ArrayList<>() as List<String>).collect {Long.parseLong(it)})
                    }
                    s.nationalProductTypeStored = nationalProductTypeStored
                    s.internationalProductTypeStored = internationalProductTypeStored
                }?.sort {it?.virtualShipperType}


                render template: "/carriers/fedex/configuration", model: [shippers: shipper, shipperType: shipperType, nationalProductTypes: nationalProductTypes, internationalProductTypes: internationalProductTypes]
                return
            }
            else if (template == 'automatic_op') {
                if(store)
                {

                    List<OrderState> orderStates = OrderState.list()

                    List<Long> sources = (MPConfigurationService.list(store_id: storeId) as List<MPConfiguration>)*.source

                    render template: '/' + category + '/' + template, model:[store:store, orderStates: orderStates, sources: sources]
                    return
                }
                else
                {
                    render false
                    return
                }

            }


            if(store)
                render template: '/' + category + '/' + template + '/configuration', model: [shippers: shipper, shipperType: shipperType]
            else
                render false

        }
        else if(category == "subShippers") {
            /* se il corriere principale è poste */
            if(params.get("shipper") == "poste") {
                /* se il corriere principale è poste ed il template è crono */
                if(params.get("template") == "crono") {
                    def cronoShipper = listShipper.get(ShipperType.POSTE)

                    try {
                        if (cronoShipper != null && cronoShipper[0] != null) {
                            def poste = shipperService.getByCodeAndStoreId(ShipperType.POSTE, storeId)
                            cronoShipper[0].shipperDefault = poste?.shipperDefault ?: false
                            cronoShipper[0].enable = poste?.enable ?: false
                        }
                    } catch (Exception e) {
                        MPMlog.error("Errore durante il recupero dello shipper per POSTE e store ${storeId}", e)
                    }

                    render template: "/carriers/poste/crono", model: [shippers: cronoShipper, shipperType: ShipperType.findByType(ShipperType.POSTE)]
                    return
                }
                /* se il corriere principale è poste ed il template è crono */
                else if(params.get("template") == "delivery") {
                    def deliveryShipper = listShipper.get(ShipperType.POSTE_DELIVERY)
                    MPMlog.debug("deliveryShipper - ${deliveryShipper}")

                    try {
                        if (deliveryShipper != null && deliveryShipper[0] != null) {
                            def poste = shipperService.getByCodeAndStoreId(ShipperType.POSTE_DELIVERY, storeId)
                            deliveryShipper[0].shipperDefault = poste?.shipperDefault ?: false
                            deliveryShipper[0].enable = poste?.enable ?: false
                        }
                    } catch (Exception e) {
                        MPMlog.error("Errore durante il recupero dello shipper per POSTE_DELIVERY e store ${storeId}", e)
                    }

                    render template: "/carriers/poste/delivery", model: [shippers: deliveryShipper, shipperType: ShipperType.findByType(ShipperType.POSTE_DELIVERY)]
                    return
                }
            }
        }

    }

    def setEnable() {
        /* ottengo i parametri dalla richiesta */
        Long storeId = session['store_id'] as Long
        def store = Store.get(storeId)
        String shipperType = params.get('shipperType')
        Boolean enable = params.getBoolean('enable')
        Integer virtualShipperType = params.getInt("virtualShipperType")
        if(params.getBoolean('isStore'))
        {
            store.enableAutomaticLdv=enable
            store.save(flush:true)
            render true
            return
        }
        /* ottengo il corriere */
        Shipper shipper = shipperService.getByCodeAndStoreIdAndVirtualShipperType(shipperType, storeId, virtualShipperType)

        MPMlog.debug("trovato shipper -> ${shipper}")

        if(shipper) {
            shipperService.setEnable(shipper, store, enable)
            def result=shipperService.getDefault(storeId)
            def map=[:]
            //se sto abilitando il corriere e non c'è nessun altro corriere di default (oltre a quello generico) lo metto come di default
            if(enable && (!result || result.type.type == ShipperType.GEN)) {
                shipperService.setDefault(shipper, storeId)
                map.id = shipper.id
                map.switchery = shipper.code
            }else if(result) {
                map.id = result.id
                map.switchery = result.code
            }

            render map as JSON
            return
        }

        response.status = 401
        render "C'è stato un errore nel disabilitare abilitare il corriere: riprova in seguito."

    }

    def getPickupAddresses() {
        /* ottengo i parametri dalla richiesta */
        Long storeId = session['store_id'] as Long
        def store = Store.get(storeId)
        ShipperType type = shipperService.getType(params.getLong('type'))

        /* ottengo il corriere */
        Shipper shipper = shipperService.getByCodeAndStoreId(type.type, storeId)

        // TODO: chiamata a carrier-service
        def result = shipperService.getPickUpAddresses(store, shipper)
        render result as JSON
        return

    }
    def setErrorAddress(){

        Long storeId = session['store_id'] as Long
        def store = Store.get(storeId)
        String shipperType = params.get('shipperType')
        Boolean enable = params.getBoolean('enable')

        /* ottengo il corriere */
        Shipper shipper = shipperService.getByCodeAndStoreId(shipperType, storeId)

        if(shipper) {

            shipper.errorAddress=enable
            shipper.save(flush:true)
            response.status=200
            return true

        }

        response.status = 401
        render "C'è stato un errore nel disabilitare abilitare la proprietà di TNT: riprova in seguito."
    }
}
