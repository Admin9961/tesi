package com.macrosolution.mpm.shipping


import cloud.poleepo.libraries.security.token.Download
import cloud.poleepo.libraries.security.token.TokenUtility
import com.macrosolution.mpm.dealer.DealerService
import com.macrosolution.mpm.marketplace.MPException
import com.macrosolution.mpm.marketplace.SourceMP
import com.macrosolution.mpm.marketplace.amazon.AmazonUtility
import com.macrosolution.mpm.marketplace.carrier.MPCarrier
import com.macrosolution.mpm.marketplace.data.MPShippingFilter
import com.macrosolution.mpm.marketplace.data.MPShippingLabel
import com.macrosolution.mpm.marketplace.data.MPShippingService
import com.macrosolution.mpm.marketplace.configuration.MPConfiguration
import com.macrosolution.mpm.order.Fulfillment
import com.macrosolution.mpm.order.OrderRow
import com.macrosolution.mpm.shipper.AmazonShipper
import com.macrosolution.mpm.booking.GlsBooking
import com.macrosolution.mpm.store.FastActions
import com.macrosolution.mpm.store.Store
import com.macrosolution.mpm.utility.log.ILog
import com.macrosolution.mpm.utility.log.Log
import com.macrosolution.tntcarriermanager.TntProductType
import grails.converters.JSON
import org.apache.commons.codec.binary.Base64
import org.apache.commons.lang3.StringUtils

import javax.servlet.http.HttpServletResponse

import static org.springframework.http.HttpStatus.*
import grails.transaction.Transactional
import com.macrosolution.mpm.shipper.Shipper
import com.macrosolution.mpm.shipper.ShipperType
import com.macrosolution.mpm.order.Order
import com.macrosolution.mpm.order.OrderNote
import com.macrosolution.mpm.address.Address
import grails.plugin.springsecurity.annotation.Secured
import websalesmanager.WSMUtil
import com.opencsv.CSVWriter

import com.macrosolution.mpm.shipper.GlsShipper
import com.macrosolution.mpm.shipper.TntShipper
import com.macrosolution.mpm.supplier.Supplier
import com.macrosolution.mpm.product.Supply
import com.macrosolution.mpm.secure.User
import com.macrosolution.mpm.shipping.sda.SdaBooking
import com.macrosolution.mpm.shipping.dhl.DhlBooking


@Secured(["ROLE_ADMIN","ROLE_SUPER_USER", "ROLE_ORDER_MANAGER","ROLE_SHIPPING_MANAGER"])
@Transactional
class ShippingController {
    private static ILog MPMlog = Log.getLogger()

    def shippingService
    def shipperService
    def orderService
    def addressService
    def storeService
    def supplierService
    def productService
    def variationService
    DealerService dealerService
    def MPConfigurationService
    def fastActionsService
    def MPCarrierService

    static allowedMethods = [save: "POST", update: "POST"]

    def springSecurityService

    @Secured(["ROLE_ADMIN","ROLE_SUPER_USER", "ROLE_ORDER_MANAGER","ROLE_SHIPPING_MANAGER"])
    def index() {


        List<Shipper> shipperList = shipperService.getAll(store_id:session["store_id"]) as List<Shipper>
        def pickupAddressList = shippingService.getPickupAddressList(session["store_id"])


        /* ottengo le configurazioni dello store */
        List<MPConfiguration> conf = MPConfigurationService.list(store_id: session["store_id"], orderManager: true) as List<MPConfiguration>

        /* ottengo la lista delle source */
        List<Long> listSource = conf.collect{MPConfiguration it-> SourceMP.getSource(it)}

        def currentUser = springSecurityService.currentUser
        def userAuthority = currentUser.authorities.authority

        if(userAuthority.contains("ROLE_SHIPPING_MANAGER")){
            def suppliers = []
            def storeList = storeService.getUserStores(currentUser)

            suppliers = supplierService.list(store_id:session["store_id"],dropshipping:"dropshipping",shippingManager:currentUser.id)

            render view:'/shipping/shippingManager/index', model:[dropShippingSuppliers:suppliers, shipperStoreList:storeList, shipperList:shipperList]

            return
        }

        [shipperList:shipperList, pickupAddressList:pickupAddressList, maxWeight: new BigDecimal("10000"),listSource:listSource]
    }

    def getStores(){
        def suppliers = []
        User currentUser = springSecurityService.currentUser
        def storeList = storeService.getUserStores(currentUser)

        if (storeList.size()>0){
            render g.select(name:"store_name", from:storeList,value:"name",optionKey:"id", optionValue:"name", class:"select2", multiple:"true", required:"required",style:"width:100%")

        }
        else render "NO"
    }

    def getSuppliers(){
        def suppliers = []
        User currentUser = springSecurityService.currentUser
        def storeList = storeService.getUserStores(currentUser)
        storeList?.each {store->
            suppliers.addAll(supplierService.list(store_id:store.id,dropshipping:"dropshipping"))
        }

        if (suppliers.size()>0){
            render g.select(name:"supplier_name", from:suppliers,value:"name",optionKey:"id", optionValue:"name", class:"select2", multiple:"true", required:"required",style:"width:100%")
        }
        else render "NO"
    }


    def list() {
        params.offset=params.offset?:"0"
        params.max = params.max?:"10"
        params.put('store_id', session['store_id'])

        def currentUser = springSecurityService.currentUser
        def userAuthority = currentUser.authorities.authority
        def list = new ArrayList()
        if(userAuthority.contains("ROLE_SHIPPING_MANAGER")){
            list = shippingService.list(params)
            /*def suppliers = []
            def storeList = storeService.getUserStores(currentUser)
            suppliers = supplierService.list(store_id:session["store_id"],dropshipping:"dropshipping",shippingManager:currentUser.id)
            if (!suppliers){
                list.shippings = []
                list.totalCount = 0
            }*/
            render template:'/shipping/shippingManager/list', model:[shippingList:list.shippings, records:list.totalCount, barcode: params.barcode]
//        }else if(userAuthority.contains("ROLE_DEALER_LOGISTIC")){
//
//            Store store = Store.findById(params.getLong('client'))
//            if(store != null)
//                params.dropShippers = Supplier.findAllByStoreAndDealerType(store, currentUser.dealerType).id
//            else
//                params.dropShippers = Supplier.findAllByDealerType(currentUser.dealerType).id
//            params.store_id = null
//            list = shippingService.list(params)
//            render template: '/dealer/logistic/list', model: [shippingList: list.shippings, shippingRecords: list.totalCount]
        }else {
            list = shippingService.list(params)
            [shippingList: list.shippings, records: list.totalCount]
        }

    }

    def create() {
        respond new Shipping(params)
    }

    @Transactional
    def save() {

        Long storeId = session['store_id'] as Long
        def virtualShipperType = params.shipper

        //Shipper shipper = shipperService.get(params.getLong('shipper'))
        if(params.shipper.equals("Generic")) {
            params.shipper = "MPM"
        }

        Shipper shipper = shipperService.getByVirtualShipperTypeAndStoreId(params.shipper as Integer, storeId)
        params.shipper = shipper.id

        if(shipper.store.id != storeId){
            response.status = 401
            render "Non puoi creare una spedizione per questo corriere. Prova ad effettuare di nuovo il login."
            return
        }
        def shipping
        def storeLocation = storeService.get(storeId).pickupAddress

        if(shipper.type.type == ShipperType.TNT) {
            shipping = new TntShipping(params)
            shipping.numberPackage = params.getLong('numberPackage')
            shipping.tntApplication = params.get("tnt_application")

            def shipperConf = shipperService.getConfiguration(storeId, virtualShipperType as Long)

            if(shipperConf.checkBookingForm == false){
                shipping.booking = new TntBooking()
                shipping.booking.pickupdate = new Date()
                if(shipperConf.priopntime) shipping.booking.priopntime = Date.parse('HH:mm', shipperConf.priopntime)
                if(shipperConf.priclotime) shipping.booking.priclotime = Date.parse('HH:mm', shipperConf.priclotime)
                if(shipperConf.secopntime) shipping.booking.secopntime = Date.parse('HH:mm', shipperConf.secopntime)
                if(shipperConf.secclotime) shipping.booking.secclotime = Date.parse('HH:mm', shipperConf.secclotime)
                if(shipperConf.availabilitytime) shipping.booking.availabilitytime = Date.parse('HH:mm', shipperConf.availabilitytime)
                if(shipperConf.pickuptime) shipping.booking.pickuptime = Date.parse('HH:mm', shipperConf.pickuptime)
            }

            if (shipping.tntApplication == "MYRTL") {
                TntProductType productType = TntProductType.findByCode(params.get("nationalProductType") as String)
                shipping.productType = productType
            }
            else {
                TntProductType productType = TntProductType.findByCode(params.get("internationalProductType") as String)
                shipping.productType = productType
            }
        }
        else if(shipper.type.type == ShipperType.GLS || shipper.type.type == ShipperType.GLS_2 ){
            params.notificationPhone = params.getBoolean('notificationPhone')
            params.afmiService = params.getBoolean('afmiService')
            params.orderIdInNotes = params.orderIdInNotes == "on"
            shipping = new GlsShipping(params)
        }
        else if(shipper.type.type == ShipperType.GEN){
            shipping = new GenericShipping(params)
            shipping.confirmed=Boolean.TRUE
            shipping.confirmedDate = new Date()
            shipping.printed = Boolean.TRUE
            shipping.fileLabel = " "
        }
        else if(shipper.type.type == ShipperType.SDA || shipper.type.type == ShipperType.POSTE || shipper.type.type == ShipperType.POSTE_DELIVERY) {
            params.orderIdInNotes = params.orderIdInNotes == "on"

            //Anche per poste usiamo la classe SdaShipping e SdaBooking
            shipping = new SdaShipping(params)
            shipping.codType = (params.hasCod=="Y")?params.codType:null
            shipping.bulky = params.getBoolean('bulky')
            shipping.booking = new SdaBooking()
            shipping.booking.pickupdate = new Date() +1
            if (params.priopntime) shipping.booking.priopntime = Date.parse('HH:mm', params.priopntime)
            if (params.priclotime) shipping.booking.priclotime = Date.parse('HH:mm', params.priclotime)
            if (params.secopntime) shipping.booking.secopntime = Date.parse('HH:mm', params.secopntime)
            if (params.secclotime) shipping.booking.secclotime = Date.parse('HH:mm', params.secclotime)
        }
        else if(shipper.type.type == ShipperType.BRT){
            if (params.deliveryDateRequired) params.deliveryDateRequired = Date.parse('dd/MM/yyyy', params.deliveryDateRequired)
            params.consigneeEmail=params.consigneeEmail=="on"?true:false
            params.consigneeMobilePhoneNumber=params.consigneeMobilePhoneNumber=="on"?true:false
            params.orderIdInNotes = params.orderIdInNotes == "on"
            shipping = new BrtShipping(params)
            //il codice tariffa se non inserito bisogna salvarlo come stringa vuota, altrimenti nel form viene visualizzato il codice tariffa settato in configurazione
            if (!params.pricingConditionCode) shipping.pricingConditionCode = ""
            shipping.volume = WSMUtil.toBigDecimal(params.volume)
        }
        else if(shipper.type.type == ShipperType.DHL){
            shipping = new DhlShipping(params)
            shipping.dhlBooking = new DhlBooking()
            shipping.dhlBooking.pickupdate = new Date()
            if (params.packageLocation) shipping.dhlBooking.packageLocation = params.packageLocation
            if (params.availabilitytime) shipping.dhlBooking.availabilitytime = Date.parse('HH:mm', params.availabilitytime)
            if (params.secclotime) shipping.dhlBooking.secclotime = Date.parse('HH:mm', params.secclotime)
        }
        else if (shipper.type.type == ShipperType.MIT) {
            shipping = new MitShipping(params)
        }
        else if(shipper.type.type == ShipperType.SPEDISCI_ONLINE) {

            shipping = new SpedisciOnlineShipping(params)
            if(!shipping.codValue) shipping.codType = null

            storeLocation = Address.findById(params.ppsAddressId as Long)
        }
        else if (shipper.type.type == ShipperType.QAPLA) {
            shipping = new QaplaShipping(params)


            def deliveryType = params.get("deliveryType")

            if(deliveryType instanceof String) {
                shipping.deliveryType = deliveryType
            }
            else {
                shipping.deliveryType = (deliveryType as List<String>)?.join(",") ?: null
            }


        }

        shipping.pickupAddress = storeLocation
        shipping.senderAddress = storeLocation

        /* creo la mappa delle righe dell'ordine */
        Map orderRows = [:]
        params.list("orderRowId").collect{it -> Long.valueOf(it)}.each{ Long id ->
            Boolean select = params.getBoolean("orderRowSelected${id}")
            String total = params."quantityTotal${id}" ?: "0"
            String qnt = params."quantity${id}" ?: "0"
            orderRows.put(id, [id: id, select: select, total: new BigDecimal(total.replace(",", ".")), quantity: new BigDecimal(qnt.replace(",", "."))])
        }


        /* inserisco l'ordine nella spedizione */
        def order
        if(params.order){
            order = orderService.get(params.getLong('order'))
            if(order.store.id != session["store_id"]){
                transactionStatus.setRollbackOnly()
                response.status = 401
                render "Non puoi creare una spedizione per questo ordine. Prova ad effettuare di nuovo il login."
                return
            }
            //conttrollo se l'ordine ha giacenza insufficiente
            //
            if(params.forceShipping!="Y") {
                order.rows.each { row ->
                    if (!row.fulfillments && row.productOrder?.productId != null) {
                        //ritorna per chiedere all'utente se vuole forzare la creazione della spedizione
                        response.setStatus(501)
                        render ""
                        transactionStatus.setRollbackOnly()
                        return
                    }

                }
            }
            shippingService.addOrderToShipping(shipping,order, orderRows)
        }
        shipping.codValue=WSMUtil.toBigDecimal(params.codValue)
        shipping.insuredValue=WSMUtil.toBigDecimal(params.insuredValue)


        for (i in params.list("index")){
            BoxType boxType
            def boxTypeValue = params.get("parcel.boxType"+i)
            if(shipper.type.type == ShipperType.MIT) {
                boxType = BoxType.findByValue(boxTypeValue)
            }
            else {
                boxType = BoxType.get(boxTypeValue)
            }

            shipping.addToParcels(
                    weight: WSMUtil.toBigDecimal(params.get("parcel.weight"+i)),
                    boxType: boxType,
                    width: WSMUtil.toBigDecimal(params.get("parcel.width"+i)),
                    height: WSMUtil.toBigDecimal(params.get("parcel.height"+i)),
                    depth: WSMUtil.toBigDecimal(params.get("parcel.depth"+i))
            )
        }

        /* setto il peso della spedizione */
        shipping.weight = shipping.parcels*.weight?.flatten()?.sum() as BigDecimal

        /* ottengo la mappa item - fornitori */
        Boolean dropShipperAmbigous = false
        Map<Supplier, List<ShippingItem>> fulMap = [:]
        def shippingItems = shipping.items.findAll{it.quantity>0 && it.orderRow.productOrder?.productId!=null}
        //List<OrderRow> orderRowToScale = new ArrayList<OrderRow>()
        // controllo se sono prodotti gestiti da MPM
        if(shippingItems) {
            shippingItems.forEach{ShippingItem item ->
                MPMlog.debug("START ${item.id} - ${item.quantity}")
                BigDecimal otherCount = BigDecimal.ZERO

                // controllo se c'è un order row con giacenza insufficiente

                item.orderRow.fulfillments.forEach{Fulfillment fulfillment ->
                    Supply supply = Supply.get(fulfillment.supplyId)

                    MPMlog.debug("FULL: ${fulfillment.id} - ${fulfillment.dealerType} - ${supply?.supplier?.dropShipping} - ${fulfillment.quantity}")
                    if(fulfillment.dealerType != null && fulfillment.quantity >= item.quantity) {
                        List<ShippingItem> lst = fulMap.get(supply.supplier, [])
                        MPMlog.debug("LIST: ${lst}")
                        lst.add(item)
                        fulMap.put(supply.supplier, lst)
                    }
                    else if(fulfillment.dealerType == null && supply?.supplier?.dropShipping && fulfillment.quantity >= item.quantity) {
                        List<ShippingItem> lst = fulMap.get(supply.supplier, [])
                        MPMlog.debug("LIST: ${lst}")
                        lst.add(item)
                        fulMap.put(supply.supplier, lst)
                    }
                    else if(!supply?.supplier?.dropShipping && fulfillment.dealerType == null)
                        otherCount += fulfillment.quantity

                    MPMlog.debug("OTHER COUNT: ${otherCount}")
                }

                MPMlog.debug("OtherCount: ${otherCount} >= ${item.quantity} - ${otherCount >= item.quantity}")
                if(otherCount >= item.quantity){
                    List<ShippingItem> lst = fulMap.get(null, [])
                    lst.add(item)
                    fulMap.put(null, lst)
                }

            }
            MPMlog.debug("MAP: ${fulMap}")

            List<Supplier> suppliers = fulMap.keySet() as List

            /* ottengo tutti i fornitori che possono soddisfare la spedizione */
            List<Supplier> supplierComplete = []
            suppliers.forEach{ Supplier supplier ->
                if((fulMap.get(supplier)?.size() ?: 0) == shippingItems.size()) {
                    supplierComplete.add(supplier)
                }
            }
            MPMlog.debug("DEALERSCOMPLETE: ${supplierComplete.size()}")

            /* se nessuno completa la spedizione */
            if(supplierComplete.size() == 0 && params.forceShipping != "Y") {
                response.setStatus(500)
                render "Non è possibile completare la spedizione con le quantità impostate e la selezione dei fornitori specificata"
                transactionStatus.setRollbackOnly()
                return
            }

            /* se il fornitore è altro, ma la spedizione può essere gestita solo da dropshipper MPM */
            MPMlog.debug("supplierComplete: ${supplierComplete} ${supplierComplete.findAll {it != null}.size()}")
            if((shipping.shipper.type.type == ShipperType.GEN  ) && supplierComplete.findAll {it != null && it.dealerType != null}.size() > 0 && supplierComplete.findAll {it == null}.size() == 0) {
                response.setStatus(500)
                render "Non è possibile gestire le spedizioni dei fornitori dropshipping con il corriere selezionato"
                transactionStatus.setRollbackOnly()
                return
            }

            // se si vuole forzare la creazione della spedizione allora si devono scalare le quantità
            if(params.forceShipping == "Y") {
                List<Object> productFinished = new ArrayList<Object>();
                try{
                    order.rows?.each{it->
                        if(!it.fulfillments) {
                            if(it.productOrder?.productId){
                                if(it.productOrder.variationId){
                                    def variation=variationService.get(it.productOrder.variationId)
                                    productFinished=orderService.scaleQuantity(order, it,productFinished, variation, true)
                                }else{
                                    def product=productService.get(it.productOrder.productId)
                                    productFinished=orderService.scaleQuantity(order,it,productFinished, product, true)
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace()
                    response.status = 500
                    render "<p>Impossibile creare la spedizione. Si \u00E8 verificato un errore durante lo scalo delle quantità"
                    transactionStatus.setRollbackOnly()
                    return
                }
            }

            // ### DROPSHIPPING ####
            /* setto i dati iniziali del dropshipping,
            se suppliers, contiene null significa che lo stock può gestire la spedizione,
            quindi se trovo un dropShipper potrei sbagliare indirizzo*/
            Supplier dropShipper = null
            Boolean ambigous = suppliers.contains(null)

            /* ciclo i fonitori che soddisfano la spedizione per capise se posso mandarla ad un dropShipper */
            suppliers.forEach{Supplier supplier ->
                /* se il fornitore corrente soddisfa tutta la spedizione, ed è un dropShipper */
                if(fulMap.get(supplier)?.size() == shippingItems.size() && supplier?.dropShipping) {

                    /* se avevo trovato già un dropshipper, la mia scelta può essere sbagliata */
                    if(dropShipper != null)
                        ambigous = true

                    /* se non ho già selezionato un dropShipper di MPM, aggiorno con il dropShipper corrente */
                    if(dropShipper?.dealerType == null)
                        dropShipper = supplier
                }
            }
            MPMlog.debug("DROPSHIPPER: ${dropShipper} - AMBIGOUS: ${ambigous}")

            /* se ho trovato il dropShipper, cambio il pickup */
            if(dropShipper != null)
                shipping.pickupAddress = dropShipper.address

            /* se non sono sicuro della scelta che ho fatto, torno un errore */
            if(dropShipper != null && ambigous)
                dropShipperAmbigous = true
            // #### FINE DROPSHIPPING ####
        }

        /* calcolo il numero di telefono del ricevente */
        String phoneR = null
        /* se nell'indirizzo di ricezione c'è il numero di telefono */
        if(shipping?.receiverAddress?.phoneNumber != null && !shipping.receiverAddress.phoneNumber.isEmpty()) {
            phoneR = shipping.receiverAddress.phoneNumber
        }
        /* se sono definiti sul customer una lista di numeri di telefono */
        else if(shipping?.receiverAddress?.customer?.phones != null && !shipping.receiverAddress.customer.phones.findAll{ !StringUtils.isEmpty(it)}.isEmpty()) {
            phoneR = shipping.receiverAddress.customer.phones.findAll{ !StringUtils.isEmpty(it)}[0]
        }
        /* se sono definiti sul customer dei numero negli altri indirizzi */
        else if(shipping?.receiverAddress?.customer?.addresses?.phoneNumber != null && !shipping.receiverAddress.customer.addresses.phoneNumber.findAll{!StringUtils.isEmpty(it)}.isEmpty()) {
            phoneR = shipping.receiverAddress.customer.addresses.phoneNumber.findAll{ !StringUtils.isEmpty(it)}[0]
        }

        /* calcolo l'email del ricevente */
        String emailR = null
        /* se nell'indirizzo di ricezione c'è il numero di telefono */
        if(StringUtils.isNotBlank(shipping?.receiverAddress?.email)) {
            emailR = shipping.receiverAddress.email
        }
        /* se sono definiti sul customer una lista di numeri di telefono */
        else if(StringUtils.isNotBlank(shipping?.receiverAddress?.customer?.email)) {
            emailR = shipping?.receiverAddress?.customer?.email
        }

        // aggiungo una nota alla spedizione con il numero di telefono
        if(params.phoneInNotes == 'true') {
            /* se ho trovato il numero di telefono, lo setto nelle note */
            if(phoneR != null && !phoneR.isEmpty()) {
                shipping.addToNotes("tel " + (phoneR?.startsWith("+39") ? phoneR?.drop(3) : phoneR))
            }
        }
        /* setto numero di telefono ed email nell'indirizzo di ricezione */
        shipping.receiverAddress.phoneNumber = phoneR
        shipping.receiverAddress.email = emailR

        /* aggiorno la spedizione */
        shippingService.save(shipping)

//        MPMlog.debug("DROPSHIPPERAMIGOUS: ${dropShipperAmbigous}")
//        render template:"shippingForm", model:[shipping:shipping]
        render ([shippingId: shipping.id, dropShipperAmbigous: dropShipperAmbigous] as JSON)
    }

    @Transactional
    def edit() {
        Shipping shipping = shippingService.get(params.getLong('id'))
        if(shipping == null){
            flash.message="Non ho trovato la spedizione cercata"
            redirect action:'index'
            return
        }

        if(shipping.shipper.store.id != session["store_id"]){
            flash.error="Non puoi modificare questa spedizione. Prova ad effettuare di nuovo il login."
            redirect action:'index'
            return
        }

        /* ottengo le preferenze sulle azioni veloci */
        FastActions fastActions = fastActionsService.get(session["store_id"] as Long)

        [shipping:shipping,fastActions:fastActions]
    }

    @Transactional
    def update() {
        Shipping shipping = shippingService.get(params.getLong('id'))

        if(!shipping){
            response.status = 404
            render "Spedizione non trovata"
            return
        }
        if(shipping.shipper.store.id != session["store_id"]){
            response.status = 401
            render "Non puoi modificare questa spedizione. Prova ad effettuare di nuovo il login."
            return
        }

        /* verifico se la spedizione è modificabile */
        if(!shipping.editable) {
            response.status = 500
            render "Non puoi modificare questa spedizione. La spedizione non è più modificabile perchè è stata già inviata al fornitore."
            return
        }

        if(shipping instanceof GlsShipping && shipping.confirmed){
            response.status = 401
            render 'Questa spedizione non può essere modificata perchè i sistemi GLS non accettano le modifiche dopo la conferma. Contatta la tua sede di riferimento GLS per concordare la modifica della spedizione.'
            return
        }

        if (shipping instanceof BrtShipping) {
            if (shipping.printed && params.alphanumericSenderReference != shipping.alphanumericSenderReference) {
                response.status = 500
                render "Non puoi modificare il riferimento mittente alfanumerico di una spedizione già stampata. Per modificarlo puoi cancellare la spedizione e crearla di nuovo."
                return
            }
            if (params.deliveryDateRequired) params.deliveryDateRequired = Date.parse('dd/MM/yyyy', params.deliveryDateRequired)
        }

        if(shipping instanceof QaplaShipping) {
            if (!params.get("carrierServiceName"))
                params.carrierServiceName = null
            if (!params.get("deliveryType"))
                params.deliveryType = null
        }

        shipping.properties = params

        shipping.parcels.clear()
        for (i in params.list("index")){
            shipping.addToParcels(	weight: WSMUtil.toBigDecimal(params.get("parcel.weight"+i)),
                    boxType:BoxType.get(params.get("parcel.boxType"+i)),
                    width: WSMUtil.toBigDecimal(params.get("parcel.width"+i)),
                    height: WSMUtil.toBigDecimal(params.get("parcel.height"+i)),
                    depth: WSMUtil.toBigDecimal(params.get("parcel.depth"+i)))
        }

        /* setto il peso della spedizione */
        shipping.weight = shipping.parcels*.weight.flatten().sum() as BigDecimal

        if (shipping.hasErrors()) {
            response.status = 500
            render 'Errore'
            return
        }

        shipping.codValue=WSMUtil.toBigDecimal(params.codValue)
        shipping.insuredValue=WSMUtil.toBigDecimal(params.insuredValue)
        shipping.orderIdInNotes = params.orderIdInNotes == "on"

        // SdaShipping comprende anche le spedizioni con poste
        if(shipping instanceof SdaShipping) {
            shipping.codType = (params.hasCod=="Y")?params.codType:null
            shipping.insuranceType = params.insuranceType
            shipping.content = params.content
            shipping.bulky = params.getBoolean('bulky')
        }
        else if (shipping instanceof GlsShipping) {
            shipping.notificationPhone = params.getBoolean('notificationPhone')
            shipping.afmiService = params.getBoolean('afmiService')
        }
        else if (shipping instanceof BrtShipping) {
            if(!params.pricingConditionCode) {
                shipping.pricingConditionCode = ""
            }
            shipping.consigneeEmail=params.consigneeEmail=="on"?true:false
            shipping.consigneeMobilePhoneNumber=params.consigneeMobilePhoneNumber=="on"?true:false
            shipping.volume = WSMUtil.toBigDecimal(params.volume)
        }
        else if(shipping instanceof SpedisciOnlineShipping) {
            if(!shipping.codValue) shipping.codType = null
            shipping.content = params.content
            shipping.pickupAddress = Address.findById(params.ppsAddressId as Long)
        }
        else if(shipping instanceof TntShipping) {
            shipping.tntApplication = params.get("tnt_application")
            if (shipping.tntApplication == "MYRTL") {
                TntProductType productType = TntProductType.findByCode(params.get("nationalProductType") as String)
                shipping.productType = productType
            }
            else {
                TntProductType productType = TntProductType.findByCode(params.get("internationalProductType") as String)
                shipping.productType = productType
            }
        }

        shippingService.save(shipping)
        render 'ok'
    }

    @Transactional
    def saveBooking(){
        Shipping shipping = shippingService.get(params.getLong('id'))

        if(!shipping){
            response.status = 404
            render "Spedizione non trovata"
            return
        }

        if(shipping.shipper.store.id != session["store_id"]){
            response.status = 401
            render "Non puoi modificare questa spedizione. Prova ad effettuare di nuovo il login."
            return
        }

        try{
            if(shipping instanceof DhlShipping) {
                shipping.dhlBooking.secclotime = Date.parse('HH:mm', params.secclotime)
                shipping.dhlBooking.availabilitytime = Date.parse('HH:mm', params.availabilitytime)
                shipping.dhlBooking.pickupdate = Date.parse('dd/MM/yyyy', params.pickupdate)
                shipping.dhlBooking.packageLocation = params.packageLocation
                shipping.dhlBooking.pickupinstr = params.pickupinstr
            }
            else {
                shipping.booking = shipping.booking?:new TntBooking()
                if(params.priopntime) shipping.booking.priopntime = Date.parse('HH:mm', params.priopntime)
                if(params.priclotime) shipping.booking.priclotime = Date.parse('HH:mm', params.priclotime)
                if(params.secopntime) shipping.booking.secopntime = Date.parse('HH:mm', params.secopntime)
                if(params.secclotime) shipping.booking.secclotime = Date.parse('HH:mm', params.secclotime)
                if(params.availabilitytime) shipping.booking.availabilitytime = Date.parse('HH:mm', params.availabilitytime)
                if(params.pickuptime) shipping.booking.pickuptime = Date.parse('HH:mm', params.pickuptime)
                if(params.pickupdate) shipping.booking.pickupdate = Date.parse('dd/MM/yyyy', params.pickupdate)
                shipping.booking.pickupinstr = params.pickupinstr
            }
        }
        catch(Exception e) {
            response.status = 401
            render 'Si \u00E8 verificato un errore durante il salvataggio: controlla i dati inseriti e riprova'
        }
        render "ok"
    }


    @Transactional
    def setStatus(){
        List<Shipping> shipping = Shipping.findAllByIdInList(params.list('id'))
        for(it in shipping){
            if(it.shipper.store.id!=session["store_id"]){
                transactionStatus.setRollbackOnly()
                response.status = 401
                render "Non puoi modificare queste spedizioni. Prova ad effettuare di nuovo il login."
                return
            }

            /* verifico se la spedizione è modificabile */
            if(!it.editable) {
                response.status = 500
                render "Non puoi modificare lo stato di questa spedizione. La spedizione non è più modificabile perchè è stata già inviata al fornitore."
                return
            }

            it.properties = params
            if (it.hasErrors()) {
                response.status = 500
                render 'Errore'
                return
            }
            if(params.sended && it.sended){
                it.deliveryDate = new Date()
            }else{
                it.deliveryDate = null
            }
            shippingService.save(it)
        }
        render 'ok'
    }

    @Transactional
    def setShipped(){
        List<Shipping> shipping = Shipping.findAllByIdInList(params.list('checkShip'))
        Boolean allShipped = Boolean.TRUE
        def contaship=0
        for(it in shipping){
            if(it.shipper.store.id!=session["store_id"]){
                transactionStatus.setRollbackOnly()
                response.status = 401
                render "Non puoi modificare queste spedizioni. Prova ad effettuare di nuovo il login."
                return
            }
            if(it.printed && it.confirmed && !it.sended){
                it.sended = true
                it.deliveryDate = new Date()
                shippingService.save(it)
                contaship++
            }else{
                allShipped = Boolean.FALSE
            }
        }

        if (contaship<=0) {
            render 'Nessuna spedizione è stata impostata come spedita'
        }

        else if (contaship==1){
            render 'Una spedizione è stata impostata come spedita'
        }

        else if (contaship<shipping.size()) {
            render 'Sono stata impostate come spedite ' + contaship + ' spedizioni'
        }

        else if (contaship==shipping.size()){
            render 'Tutte le spedizioni selezionate sono state impostate come spedite'
        }
    }


    def setRejected(){
        Shipping shipping = Shipping.load(params.getLong('shipping_id'))

        if(shipping.shipper.store.id!=session["store_id"]){
            transactionStatus.setRollbackOnly()
            response.status = 401
            render "Non puoi modificare questa spedizione. Prova ad effettuare di nuovo il login."
            return
        }
        else {
            def reasons = RejectReason.getAll()
            render template:'/shipping/shippingManager/modal/reject' , model:[shipping:shipping,reasons:reasons]
        }

        return
    }


    @Transactional
    def saveRejected(){
        Shipping shipping = Shipping.load(params.getLong('shipping_id'))
        def reason = RejectReason.findByReason(params.get('reason'))
        def rejectReason = "SPEDIZIONE ANNULLATA - Motivo: " + reason.description + " Note: " + params.get('notes')

        def contaship=0

        if(!shipping.sended && !shipping.dateRejected){
            shipping.dateRejected = new Date()
            shipping.addToNotes(rejectReason)
            shippingService.save(shipping)
            contaship++

            //inserisco la nota negli ordini della spedizione
            if (shipping.orders){
                shipping?.orders.each{shipOrder->
                    OrderNote note = new OrderNote()
                    note.type = OrderNote.TYPE_REJECTED
                    note.text = rejectReason
                    shipOrder.hasWarning = true
                    shipOrder.addToNotes(note)
                    orderService.save(shipOrder)
                }
            }
        }else if (shipping.sended){
            render 'Non puoi rifiutare una spedizione già presa in carico dal corriere\n'
        }
        else if (shipping.dateRejected){
            render 'Questa spedizione risulta già rifiutata\n'
        }

        if (contaship>0)
            render 'La spedizione è stata rifiutata'
        if (contaship<=0) {
            render 'Non è stata apportata alcuna modifica'
        }

        return
    }


    @Transactional
    def delete() {
        def shipping = shippingService.get(params.getLong('id'))
        if(!shipping){
            response.status = 404
            render "Spedizione non trovata, aggiorna la pagina e riprova l'eliminazione"
            return
        }
        if(shipping.shipper.store.id!=session["store_id"]){
            response.status = 401
            render "Non puoi eliminare questa spedizione. Prova a rifare il login"
            return
        }

        /* verifico se la spedizione è modificabile */
        if(!shipping.editable) {
            response.status = 500
            render "Non puoi eliminare questa spedizione. La spedizione non è più modificabile perchè è stata già inviata al fornitore."
            return
        }

        try {
            shippingService.delete(shipping.id, (Long) session["store_id"], true)
        }
        catch (Exception e) {
            response.status = 500
            render e.getMessage()
            return
        }

        flash.message = message(code: 'default.deleted.message', args: [message(code: 'shipping.label', default: 'Shipping'),shipping.shippingCode?:shipping.shippingInternalCode])
        redirect action:"index", method:"GET"
    }

    protected void notFound(){
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'shipping.label', default: 'Shipping'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }

    def getForm() {
        def virtualShipperType = params.get("id")
        def typeInt = ShipperType.getTypeNumberByVirtualShipperType(virtualShipperType as Integer)
        Long storeId = session['store_id'] as Long

        Shipping shipping = shippingService.get(params.getLong('shipping_id'))

        Order order
        if(params.getLong("order_id") != null) {
            order = orderService.get(params.getLong('order_id'))
        }
        else {
            order = shipping.orders[0]
        }

        def pay_method

        if (order?.paymentIsCod) pay_method = 'COD'

        // calcolo peso totale dei prodotti ordinati
        BigDecimal totalWeight = WSMUtil.toBigDecimal(params.get("weight"))

        def s = shipperService.getConfiguration(storeId, virtualShipperType as Long)

        /* se l'ordine ha una sola riga con una sola quantità setto i dati delle misure */
        BigDecimal parcelWidth = null
        BigDecimal parcelHeight = null
        BigDecimal parcelDepth = null
        if((shipping?.parcels == null || shipping?.parcels?.isEmpty()) && order.rows.size() == 1) {
            OrderRow firstOrderRow = order.rows[0]
            if(firstOrderRow.quantity == BigDecimal.ONE) {
                parcelWidth = firstOrderRow.productOrder?.shippingWidth
                parcelHeight = firstOrderRow.productOrder?.shippingHeight
                parcelDepth = firstOrderRow.productOrder?.shippingDepth
            }
        }

        if (s.carrier_type && ShipperType.isCarrierService(s.carrier_type as String)) {

            def type = s.carrier_type

            if (type == ShipperType.TNT) {
                def tntConf = s
                def productTypes = TntProductType.findAllByIdInList(tntConf.serviceTypes as List<Long>)

                render template: 'shippingTntForm', model: [pay_method: pay_method, totalOrder: (order.totalOrder + (order.codCost ?: 0)), totalWeight: totalWeight, conf: tntConf, service: params.service, productTypes: productTypes, shipping:shipping, parcelWidth: parcelWidth, parcelHeight: parcelHeight, parcelDepth: parcelDepth]
            }
            else if (type == ShipperType.SDA || type == ShipperType.POSTE) {
                //def shipperConfs = shipperService.getConfigurations(session["store_id"]);
                def sdaconf = s
                // (type == ShipperType.SDA) ? shipperConfs.get(ShipperType.SDA) : shipperConfs.get(ShipperType.POSTE)
                if (params.shipping_id) {
//                    def shipping = shippingService.get(params.getLong('shipping_id'))
                    render template: 'shippingSdaForm', model: [shipping: shipping, conf: sdaconf]
                } else {
                    render template: 'shippingSdaForm', model: [pay_method: pay_method, totalOrder: (order.totalOrder + (order.codCost ?: 0)), totalWeight: totalWeight, conf: sdaconf, service: params.service, parcelWidth: parcelWidth, parcelHeight: parcelHeight, parcelDepth: parcelDepth]
                }
            }
            else if (type == ShipperType.BRT) {
                //def shipperConfs = shipperService.getConfigurations(session["store_id"]);
                def brtconf = s // shipperConfs.get(ShipperType.BRT)
                def countryCode = shippingService.getCountryCodeFromSource(order?.source)
                if (params.shipping_id) {
//                    def shipping = shippingService.get(params.getLong('shipping_id'))
                    render template: 'shippingBrtForm', model: [shipping: shipping, conf: brtconf, orderId: ""]
                } else {
                    // QUESTO CAMPO E' LIMITATO DA BRT A 15 CARATTERI, QUINDI SE L'ID DELL'ORDINE E' PIU' LUNGO LO TAGLIO
                    String orderId = null
                    if (brtconf?.orderIdInNotes && order.sourceId)
                        orderId = order.sourceId.length() > 15 ? order.sourceId.substring(order.sourceId?.length() - 15) : order.sourceId

                    render template: 'shippingBrtForm', model: [pay_method: pay_method, totalOrder: (order.totalOrder + (order.codCost ?: 0)), totalWeight: totalWeight, conf: brtconf, service: params.service, countryCode: countryCode, orderId: orderId]
                }
            }
            else if (type == ShipperType.FEDEX) {
                MPMlog.debug("ho scelto fedex")
                //def shipperConfs = shipperService.getConfigurations(session["store_id"]);
                def fedexconf = s // shipperConfs.get(ShipperType.BRT)
                //def countryCode = shippingService.getCountryCodeFromSource(order?.source)
                if (params.shipping_id) {
                    MPMlog.debug("entra nell'if")
//                    def shipping = shippingService.get(params.getLong('shipping_id'))
                    render template: 'shippingFedexForm', model: [shipping: shipping, conf: fedexconf, orderId: ""]
                } else {
                    MPMlog.debug("entra nell'else")
                    //TODO: aggiungere parmetri aggiuntivi
                    render template: 'shippingFedexForm', model: [shipping: shipping, conf: fedexconf, orderId: ""]
                }
            }
            else if (type == ShipperType.DHL) {
                //def shipperConfs = shipperService.getConfigurations(session["store_id"]);
                def dhlconf = s // shipperConfs.get(ShipperType.DHL)
                if (params.shipping_id) {
//                    def shipping = shippingService.get(params.getLong('shipping_id'))
                    render template: 'shippingDhlForm', model: [shipping: shipping, conf: dhlconf]
                } else {
                    render template: 'shippingDhlForm', model: [pay_method: pay_method, totalOrder: (order.totalOrder + (order.codCost ?: 0)), totalWeight: totalWeight, conf: dhlconf, parcelWidth: parcelWidth, parcelHeight: parcelHeight, parcelDepth: parcelDepth]
                }
            }
            else if (type == ShipperType.POSTE_DELIVERY) {
                def posteDeliveryConf = s // shipperConfs.get(ShipperType.POSTE_DELIVERY)
                if (params.shipping_id) {
//                    def shipping = shippingService.get(params.getLong('shipping_id'))
                    render template: 'shippingPosteDeliveryForm', model: [shipping: shipping, conf: posteDeliveryConf]
                } else {
                    render template: 'shippingPosteDeliveryForm', model: [pay_method : pay_method,
                                                                          totalOrder : (order.totalOrder + (order.codCost ?: 0)),
                                                                          totalWeight: totalWeight, conf: posteDeliveryConf,
                                                                          service    : params.service,
                                                                          parcelWidth: parcelWidth, parcelHeight: parcelHeight, parcelDepth: parcelDepth]
                }
            }
            else if( type == ShipperType.SPEDISCI_ONLINE ) {
                def spediscionlineconf = s
                def countryCode = shippingService.getCountryCodeFromSource(order?.source)
                Store store = storeService.get((Long)session['store_id'])
                Shipper shipper = Shipper.findByStoreAndType(store, ShipperType.findByType(ShipperType.SPEDISCI_ONLINE))
                def pickupAddresses = shippingService.getPickupAddressList(store.id)
                Address defaultAddress = Address.findById(spediscionlineconf?.defaultPickupAddress as Long)
                if(params.shipping_id) {
//                    def shipping = shippingService.get(params.getLong('shipping_id'))
                    render template: 'shippingSpedisciOnlineForm', model:[shipping       : shipping,
                                                                          conf           : spediscionlineconf,
                                                                          pickupAddresses: pickupAddresses, defaultAddress: defaultAddress]
                } else {
                    render template: 'shippingSpedisciOnlineForm', model: [pay_method     : pay_method,
                                                                           totalOrder     : (order.totalOrder + (order.codCost ?: 0)),
                                                                           totalWeight    : totalWeight,
                                                                           conf           : spediscionlineconf,
                                                                           service        : params.service,
                                                                           countryCode    : countryCode,
                                                                           pickupAddresses: pickupAddresses,
                                                                           order: order, defaultAddress: defaultAddress,
                                                                           parcelWidth: parcelWidth, parcelHeight: parcelHeight, parcelDepth: parcelDepth]
                }

            }
            else if (type == ShipperType.MIT) {
                def mitconf = s

//                Shipping shipping = null
//                if (params.shipping_id) {
//                    shipping = shippingService.get(params.getLong('shipping_id'))
//                }


                render template: 'shippingMitForm', model: [conf       : mitconf,
                                                            shipping   : shipping,
                                                            totalOrder : (order.totalOrder + (order.codCost ?: 0)),
                                                            totalWeight: totalWeight]
            }
            else if (type == ShipperType.QAPLA) {
                def conf = s
                shipping = null

                def courierCode = params.get("service") as String

                if (params.shipping_id) {
                    shipping = shippingService.get(params.getLong('shipping_id')) as QaplaShipping
                    courierCode = shipping.carrierName
                }

                def courierName = conf.couriers?.find {it -> it.code == courierCode }?.name

                render template: "shippingQaplaForm", model: [
                        conf: conf,
                        shipping: shipping,
                        totalWeight: totalWeight,
                        totalOrder: (order.totalOrder + (order.codCost ?: 0)),
                        pay_method: pay_method,
                        courierCode: courierCode,
                        courierName: courierName,
                        virtualShipperType: virtualShipperType
                ]
            }
        }
        // al 99% qui è GLS
        else {
            def type = s.carrier_type
            if (type == ShipperType.GLS || type == ShipperType.GLS_2) {
                def glsConf = null
                if(type == ShipperType.GLS) {
                    glsConf = shipperService.getConfiguration(storeId, ShipperType.getVirtualShipperType(ShipperType.GLS, 1))?.userConfiguration
                }
                else {
                    glsConf = shipperService.getConfiguration(storeId, ShipperType.getVirtualShipperType(ShipperType.GLS_2, 2))?.userConfiguration
                }
                def countryCode = shippingService.getCountryCodeFromSource(order?.source)
                render template: 'shippingGlsForm', model: [pay_method: pay_method, totalOrder: (order.totalOrder + (order.codCost ?: 0)), totalWeight: totalWeight, conf: glsConf, service: params.service, countryCode: countryCode, parcelWidth: parcelWidth, parcelHeight: parcelHeight, parcelDepth: parcelDepth]
            }

            // TODO da gestire GEN
            else if (type == ShipperType.GEN) {
                render template: 'shippingGenericForm', model: [totalWeight: totalWeight]
            } else {
                render ""
            }
        }
    }

    @Transactional
    def addNote(){
        Shipping shipping = shippingService.get(params.getLong('id'))
        if(shipping.shipper.store.id != session["store_id"]){
            response.status = 401
            render "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            return
        }
        shipping.addToNotes(params.text)
        shippingService.save(shipping)
        render template:'note', model:[text:params.text]
    }

    @Transactional
    def removeNote(){
        Shipping shipping = shippingService.get(params.getLong('id'))
        if(shipping.shipper.store.id != session["store_id"]){
            response.status = 401
            render "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            return
        }
        shipping.removeFromNotes(params.text)
        shippingService.save(shipping)
        render 'ok'
    }

    @Transactional
    def getImportableOrders(){
        Shipping shipping = shippingService.get(params.getLong('id'))
        if(shipping.shipper.store.id != session["store_id"]){
            response.status = 401
            render "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            return
        }
        def orderList =shippingService.getImportableOrders(shipping)
        render template:'modals/modalImportableOrders', model:[orderList:orderList, shipping:shipping]
    }

    @Transactional
    def importOrders(){
        Shipping shipping = shippingService.get(params.getLong('id'))
        if(shipping.shipper.store.id != session["store_id"]){
            response.status = 401
            render "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            return
        }

        /* verifico se la spedizione è modificabile */
        if(!shipping.editable) {
            response.status = 500
            render "Non puoi importare ordini in questa spedizione. La spedizione non è più modificabile perchè è stata già inviata al fornitore."
            return
        }

        String result =''
        params.list('orderToImport').collect({ it as Long }).each{ orderId ->
            def order = orderService.get(orderId)
            shippingService.addOrderToShipping(shipping, order)
            result = result+g.render( template:"/shipping/template/importedOrder", model:[order:order, shipping:shipping])
        }
        render result
    }

    @Transactional
    def setShippedQuantity(){
        ShippingItem item = shippingService.getShippingItem(params.getLong('id'))
        if(item.shipping.shipper.store.id != session["store_id"]){
            response.status = 401
            render "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            return
        }

        /* verifico se la spedizione è modificabile */
        if(!item.shipping.editable) {
            response.status = 500
            render "Non puoi modificare la quantità degli oggetti da spedire. La spedizione non è più modificabile perchè è stata già inviata al fornitore."
            return
        }

        item.quantity = WSMUtil.toBigDecimal(params.quantity)
        if(item.quantity == 0) {
            /* verifico che almeno un item abbia quantità maggiore di 0 */
            Long qnt = item.shipping.items*.quantity.sum()
            if(qnt == 0) {
                MPMlog.debug("ERRORE")
                response.status = 500
                render "Non si possono avere spedizioni vuote. Elimina la spedizione per continuare."
                transactionStatus.setRollbackOnly()
                return
            }
        }

        shippingService.saveShippingItem(item)

        def shippedQuantity = shippingService.getShippedQuantity(item.orderRow)
        Order order = item.orderRow.order
        if(shippedQuantity < item.orderRow.quantity){
            order.fullShipped=false
        }else if(shippedQuantity > item.orderRow.quantity){
            response.status = 500
            render "Il numero di oggetti da spedire supera il numero di oggetti ordinati."
            transactionStatus.setRollbackOnly()
            return
        }
        else{
            order.fullShipped = true
            for(orderRow in order.rows){
                def shippedQuantityRow = shippingService.getShippedQuantity(orderRow)
                if(shippedQuantityRow < orderRow.quantity){
                    order.fullShipped = false
                    break
                }
            }
        }

        orderService.save(order)
        render 'ok'
    }

    @Transactional
    def setAddress(){
        Shipping shipping = shippingService.get(params.getLong('id'))
        if(shipping.shipper.store.id != session["store_id"]){
            response.status = 401
            render "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            return
        }

        /* verifico se la spedizione è modificabile */
        if(!shipping.editable) {
            response.status = 500
            render "Non puoi modificare questa spedizione. La spedizione non è più modificabile perchè è stata già inviata al fornitore."
            return
        }

        Address address = addressService.get(params.getLong('id_address'))
        shipping.properties[params.fieldToSet] = address
        shippingService.save shipping
        render template:"/address/address", model:[address:address]
    }


    @Transactional
    def removeOrder(){
        Shipping shipping = shippingService.get(params.getLong('id'))
        if(shipping.shipper.store.id != session["store_id"]){
            response.status = 401
            render "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            return
        }

        /* verifico se la spedizione è modificabile */
        if(!shipping.editable) {
            response.status = 500
            render "Non puoi rimuovere l'ordine selezionato. La spedizione non è più modificabile perchè è stata già inviata al fornitore."
            return
        }

        if(shipping.orders.size()==1 && shipping.orders[0].id==params.getLong('id_order')){
            response.status=401
            render 'Non puoi rimuovere un ordine se è il solo della spedizione.'
            return
        }
        def order = orderService.get(params.getLong('id_order'))
        //se l'ordine è contenuto nella spedizione viene rimosso
        if(order.shippings.contains(shipping)){
            shippingService.removeOrder(shipping,order)
            render 'Ordine rimosso da questa spedizione.'
        }else{
            render 'Questo ordine non era incluso nella spedizione.'
        }
    }

    @Transactional
    def printLabel(){
        Shipping shipping = shippingService.get(params.getLong('id'))
        if(shipping.shipper.store.id != session["store_id"]){
            response.status = 401
            render "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            return
        }

        if(!shipping.shipper.enable) {
            response.status = 500
            render "Corriere non abilitato."
            return
        }

        /* verifico se la spedizione è modificabile */
        if(!shipping.editable) {
            response.status = 500
            render "Non puoi stampare l'etichetta per questa spedizione. La spedizione non è più modificabile perchè è stata già inviata al fornitore."
            return
        }

        def fileLabel
        try{
            def result = shippingService.printLabel(shipping, session["store_id"])
            if(result.isSuccess) {
                fileLabel = result.fileLabel
            }else {
                response.status = 500
                render "<p>Si \u00E8 verificato un errore durante la comunicazione con il corriere:</p><p><strong><i>${result.errorMessage}</i></strong></p>"
                return
            }
        }catch(Exception e){
            e.printStackTrace()
            response.status = 500
            render "<p>Si \u00E8 verificato un errore durante la comunicazione con il corriere.<br/>Contatta l'assistenza tecnica per maggiori informazioni.("+e.message+")"

            return
        }
        //salvo il file nella directory definita in application.groovy (shipping.filelabelpath)
        def datenow= new Date()
        def path= "/" + shipping.shipper.store.id + "/" +datenow[Calendar.MONTH] + "/" + datenow[Calendar.YEAR] + "/" + shipping.shipper.name+ "/"
        def pathtoSave = grailsApplication.config.shipping.filelabelpath + path

        def dir = new File(pathtoSave)
        if(!dir.exists())
        {

            dir.mkdirs()
        }
        if ( shipping.fileLabel &&  shipping.fileLabel!='')
        {
            def filedelete=new File(dir,shipping.fileLabel)
            if(filedelete.exists())
            {
                filedelete.delete()
            }
        }
        def fileName = shipping.shippingCode ?: shipping.id
        def fileExt = ".pdf"
        if (shipping.outputType == "ZPL") //per BRT si possono stampare etichette nel formato ZPL
            fileExt = ".zpl"
        if (shipping.outputType == "PNG") //per AMAZON si possono stampare etichette nel formato PNG
            fileExt = ".png"
        //per nome directory idstore+nomeshipper(tnt,gls,etc..)+anno-mese
        def dir2=new File(grailsApplication.config.shipping.filelabelpath)
        def savefile= new File(dir2,path + "Label" + fileName + fileExt)
        savefile.withOutputStream{
            it.write(fileLabel)
        }
        shipping.fileLabel = path + "Label" + fileName + fileExt
//        shippingService.saveFileLabelForShipping(shipping, fileLabel)
        shipping.printed = true
        shippingService.save(shipping)

        /* ritorno i dati da scaricare */
        render ([name: "Label${fileName}${fileExt}", mime: "application/octet-stream", data: new String(Base64.encodeBase64(savefile.bytes))] as JSON)
    }

    @Transactional
    @Secured(["ROLE_ADMIN","ROLE_SUPER_USER", "ROLE_ORDER_MANAGER","ROLE_SHIPPING_MANAGER"])
    def downloadLabel(){
        Shipping shipping = shippingService.get(params.getLong('id'))

        if (!shipping.fileLabel){
            response.status=400
            render "La bolla non è presente, contatta l'amministratore ..."
            return
        }

        def file= new File(grailsApplication.config.shipping.filelabelpath,shipping.fileLabel)

        if (file.exists()) {
            shipping.toDownload = false
            shipping.save(flush: true)

            /* ottengo i dati della LDV */
            def fileName = shipping.shippingCode ?: shipping.id
            def fileExt = ".pdf"
            if (shipping.outputType == "ZPL") //per BRT si possono stampare etichette nel formato ZPL
                fileExt = ".zpl"
            if (shipping.outputType == "PNG") //per AMAZON si possono stampare etichette nel formato PNG
                fileExt = ".png"

            /* ritorno i dati da scaricare */
            render ([name: "Label${fileName}${fileExt}", mime: "application/octet-stream", data: new String(Base64.encodeBase64(file.bytes))] as JSON)
        }
        else{
            response.status=400
            render "Bolla non trovata..."
        }
    }

    def resume() {
        Long storeId = session['store_id'] as Long
        Store store = storeService.get(storeId)

        /* setto i parametri per la ricerca delle spedizioni */
        params.put("store_id", storeId)
        params.max = null
        params.offset = null

        /* ottengo la lista delle spdizioni */
        List<Shipping> shippingList = shippingService.list(params)?.shippings as List<Shipping>

        /* ottengo la lista delle configurazioni su carrier-service */
        List<Integer> shipperTypes = shippingList.shipper.unique(false)*.virtualShipperType
        def shipperConfs = shipperService.getConfigurations(storeId)

        /* inserisco solo le configurazioni che mi servono */
        Map<Integer, List> shippers = [:]
        shipperTypes.each {Integer shipperType ->
            shippers.put(shipperType, shipperConfs[shipperType])
        }

        /* visualizzo la pagina di riepilogo */
        render view: 'resume', model: [shippingList: shippingList, shippers: shippers, store: store]
    }

    def getReceiverAddressList(){
        Shipping shipping = shippingService.get(params.getLong('shipping'))
        if(shipping.shipper.store.id != session["store_id"]){
            response.status = 401
            render "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            return
        }

        def list = shippingService.getReceiverAddressList(params.getLong('shipping'))
        render template:'/address/modals/modalAddressChoice', model:[addressList:list]
    }

    def getPickupAddressList(){
        def list = shippingService.getPickupAddressList(session['store_id'])
        render template:'/address/modals/modalAddressChoice', model:[addressList:list]
    }

    def getPickupAddressChoice(){
        Long storeId = (Long) session["store_id"]
        def deafultAddress = storeService.get(storeId).pickupAddress
        def list = shippingService.getPickupAddressList(storeId)
        list.remove(list.find {it.id == deafultAddress.id})
        list.add(0, deafultAddress)
        render template:'/address/addressChoice', model:[addressList:list, defaultAddress:deafultAddress]
    }

    def getBeConfirmed(){
        def beConfirmed = shippingService.getBeConfirmed(session['store_id'])
        render beConfirmed.size()
    }

    @Transactional
    def closeWorkDay(){
        List<Shipping> shippings = Shipping.findAllByIdInList(params.list('checkShip'))
        if(shippings.find{it.shipper.store.id!=session["store_id"]}){
            response.status = 401
            render "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            return
        }

        def result=shippingService.closeWorkDay(params.list('checkShip').collect({ it as Long }) ,session['store_id'])

        if(result.isSuccess){
            render "Spedizione confermate con successo!"
        }else{
            response.status=401
            //render "C'\u00E8 stato un errore nella conferma di alcune spedizioni, contattare la sede dei corrieri di riferimento per risolvere il problema!"
            render result.errorMessages
        }
    }

    def pickingList(){
        params.store_id = session['store_id']
        params.max = null
        params.offset = null
        def shippingList = shippingService.list(params)
        withFormat{
            "csv" {
                List<String[]> csv_content=new ArrayList<String[]>()
                csv_content.add(["Spedizione","Ordine","SKU","EAN","Nome Articolo","Fornitore","SKU fornitore","Quantità"] as String[])
                csv_content.addAll(shippingList.shippings.items.flatten().collect{[it.shipping.shippingCode, it.orderRow.order.reference, it.orderRow.product?.sku,it.orderRow.product?.barcode, it.orderRow.description,(it.orderRow?.productOrder?.supplies?.name?.join(",")?:""), it.orderRow?.productOrder?.supplies?.sku?.join(",")?:"", it.quantity.toLong()] as String[]})
                ByteArrayOutputStream out = new ByteArrayOutputStream()
                OutputStreamWriter outwriter=new OutputStreamWriter(out)
                CSVWriter csvWriter = new CSVWriter(outwriter,(char)"|",(char)'"','\n')
                csvWriter.writeAll(csv_content)
                csvWriter.close()
                outwriter.close()
                response.setHeader('Set-Cookie', "fileDownload=true; path=/")
                render file:out.toByteArray(), contentType:"application/download", fileName:"lista_ritiro_${new Date().format('dd-MM-yyyy')}.csv"
                out.close()
                return
            }
            '*' {
                def glsShipper = GlsShipper.findByStore(storeService.get(session['store_id']))
                def tntShipper = TntShipper.findByStore(storeService.get(session['store_id']))
                def total = 0
                shippingList.shippings.each{shipping ->
                    total += shipping.items?.size()?:0
                }
                respond shippingList: shippingList.shippings, glsShipper:glsShipper, tntShipper:tntShipper, total: total
            }
        }
    }

    @Transactional
    def checkShipping() {
        Shipping shipping = shippingService.get(params.getLong('id'))
        if(shipping.shipper.store.id != session["store_id"]){
            response.status = 401
            render "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            return
        }

        if(!shipping.shipper.enable) {
            response.status = 500
            render "Corriere non abilitato."
            return
        }

        List<Long> shippingList = [shipping.id]

        try{
            shippingService.checkShipping(shippingList, session["store_id"] as Long)
            render 'ok'
        }catch(Exception e){
            e.printStackTrace()
            response.status = 500
            render "<p>Si \u00E8 verificato un errore durante la comunicazione con il corriere:</p><p><strong><i>${e.message}</i></strong></p>"
            return
        }
    }

    /**
     * Metodo per ottenere il template di una spedizione
     * */
    def getShippingTemplate() {
        Long storeId = (Long) session["store_id"]
        Long orderId = params.getLong("orderId")

        /* ottengo la spedizione */
        Shipping shipping = Shipping.get(params.getLong("shipId"))


        /* verifico di avere i diritti per vedere la spedizione */
        if(shipping.shipper.store.id != storeId) {
            response.status = 401
            render "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            return
        }

        def productTypes = []
        if(shipping.shipper.type.type == ShipperType.TNT) {
            def shipperConf = shipperService.getConfiguration(storeId, shipping.shipper.virtualShipperType as Long)
            productTypes = TntProductType.findAllByIdInList(shipperConf.serviceTypes as List<Long>)
        }

        render template: "shippingForm", model: [shipping: shipping, orderId: orderId, productTypes: productTypes]
    }

    @Transactional
    def bookPickup() {
        Long storeId = session["store_id"] as Long
        ShipperType type = shipperService.getType(params.getLong('type'))
        if(type.type == ShipperType.GLS || type.type == ShipperType.GLS_2){
            params.pickupdate = Date.parse('dd/MM/yyyy', params.pickupdate)
            GlsBooking booking = new GlsBooking(params)
            booking.storeId = storeId
            booking.shipperType = type
            booking.applicantName = Store.get(storeId).name
            Long pickupAddressId = params.getLong('pickupAddress')
            if(pickupAddressId) {
                booking.pickupAddress = Address.get(pickupAddressId)
            }

            try {
                def result = shippingService.bookPickup(booking, storeId)
                if(!result.isSuccess) {
                    transactionStatus.setRollbackOnly()
                    response.status = 500
                    render result.errorMessage
                }
            }catch(Exception e) {
                e.printStackTrace()
                transactionStatus.setRollbackOnly()
                response.status = 401
                render 'Si è verificato un errore inaspettato'
            }
        }
        render ""
    }

    @Transactional
    def deletePickup() {
        Long storeId = session["store_id"] as Long
        ShipperType type = shipperService.getType(params.getLong('type'))
        if(type.type == ShipperType.GLS || type.type == ShipperType.GLS_2){
            GlsBooking booking = GlsBooking.get(params.getLong('id'))
            if (booking == null) {
                transactionStatus.setRollbackOnly()
                response.status = 403
                render "Prenotazione del ritiro non trovata!"
                return
            }
            def currentUser = springSecurityService.currentUser
            booking.email = currentUser.email
            if(currentUser.nome || currentUser.cognome) {
                booking.applicantName = currentUser.nome + " " + currentUser.cognome
            }else{
                booking.applicantName = Store.get(storeId).name
            }

            try {
                def result = shippingService.deletePickup(booking, storeId)
                if(!result.isSuccess) {
                    transactionStatus.setRollbackOnly()
                    response.status = 500
                    render result.errorMessage
                }
            }catch(Exception e) {
                MPMlog.error("Errore durante l'eliminazione del ritiro di GLS", e)
                transactionStatus.setRollbackOnly()
                response.status = 401
                render 'Si è verificato un errore inaspettato'
            }
        }
        render ""
    }

    def getPickupList(){
        Long storeId = session["store_id"] as Long
        ShipperType type = shipperService.getType(params.getLong('type'))
        def list
        if(type.type == ShipperType.GLS || type.type == ShipperType.GLS_2){
            //list = GlsBooking.findAllByStoreId(storeId, [sort: "pickupdate"])
            list = GlsBooking.createCriteria().list() {
                order("pickupdate", "asc")
                eq 'storeId', storeId
                ge 'pickupdate', new Date().clearTime()
            }
            render template:'/carriers/gls/deletePickup', model:[bookingList:list]
        }

    }

    /**
     * Metodo che restituisce tutti i servizi di spedizione di un determinato corriere
     */
    def getShipmentServices() {
        String type = params.get("type")
        Store store = storeService.get((Long)session['store_id'])
        Shipper shipper = Shipper.findByStoreAndType(store, ShipperType.findByType(type))
        Address address = Address.findById(params.get("departures_id") as Long)
        Order order = orderService.get(params.get("order_id") as Long)
        Shipping shipping = shippingService.get(params.get("shipId") as Long)

        def map = [:]
        switch(shipper.type) {
            case ShipperType.SPEDISCI_ONLINE:
                if (shipping) {
                    map = shippingService.getCarrierXmlParams(shipping)
                }
                else {
                    map = [addresses: [
                            [addressType: "shipFrom", name: address?.nomeCompleto, company: address?.company, addrline1: address?.address,
                             town       : address?.city, province: address?.province, postcode: address?.zipCode, country: address?.country,
                             phone1     : address?.phoneNumber, email: address?.email],
                            [addressType: "shipTo", name: order?.deliveryAddress?.nomeCompleto, company: order?.deliveryAddress?.company, addrline1: order?.deliveryAddress?.address,
                             town       : order?.deliveryAddress?.city, province: order?.deliveryAddress?.province, postcode: order?.deliveryAddress?.zipCode, country: order?.deliveryAddress?.country,
                             phone1     : order?.deliveryAddress?.phoneNumber, email: order?.deliveryAddress?.email]],
                           virtualShipperType: shipper?.virtualShipperType
                    ]
                }
                break

            case ShipperType.QAPLA:
                map = [virtualShipperType: shipper?.virtualShipperType]
        }

        def shipmentServices = shipperService.getShipmentServices(store, shipper, map as Map)

        render shipmentServices as JSON
    }

    /**
     * Metodo per ottenere la lista degli inidirizzi di ritiro di uno store per le select2
     * */
    def listSelect2Pickups() {
        Long storeId = session["store_id"] as Long

        /* ottengo gli indirizzi di ritiro */
        List<Address> addresses = shippingService.getPickupAddressList(storeId)

        /* ritorno i dati per la select2 */
        render([results: addresses.collect { [
                id: it.id,
                text: it.getSelect2String()
        ] }] as JSON)
    }

    /**
     * Metodo per ottenere i filtri del canale di vendita per la creazione della spedizione
     * */
    def mpFilters() {
        /* recupero i parametri dall richiesta */
        Long storeId = session["store_id"] as Long
        Long orderId = params.getLong("orderId")
        BigDecimal toShipWeight = WSMUtil.toBigDecimal(params.get("toShipWeight"))
        BigDecimal toShipWidth = WSMUtil.toBigDecimal(params.get("toShipWidth"))
        BigDecimal toShipHeight = WSMUtil.toBigDecimal(params.get("toShipHeight"))
        BigDecimal toShipDepth = WSMUtil.toBigDecimal(params.get("toShipDepth"))
        BigDecimal toShipValue = WSMUtil.toBigDecimal(params.get("toShipValue"))

        /* recupero l'ordine */
        Order order = orderService.get(orderId)
        if(order == null || order.storeId != storeId) {
            response.setStatus(403)
            render "Non sei autorizzato"
            return
        }

        /* ottengo lo shipper per il canale di vendita */
        MPCarrier mpCarrier = MPCarrierService.getMPCarrier(storeId, order.source)
        if(mpCarrier == null) {
            response.setStatus(403)
            render "Non sei autorizzato"
            return
        }

        /* ritorno i filtri del canale di vendita  */
        if(AmazonUtility.isAmazon(order.source)) {
            render template: "/shipping/mpShipping/filters/amazon", model: [
                    order: order, toShipWeight: toShipWeight, toShipWidth: toShipWidth,
                    toShipHeight: toShipHeight, toShipDepth: toShipDepth, toShipValue: toShipValue,
                    carrier: (mpCarrier as AmazonShipper)
            ]
        }
        else {
            response.setStatus(404)
            render "Corriere non supportato"
        }
    }

    /**
     * Metodo per ottenere la lista dei servizi di spedizione di un canale di vendita
     * */
    def mpServices() {
        Long storeId = session["store_id"] as Long
        Long orderId = params.getLong("orderId")
        Long source = params.getLong("source")

        /* ottengo la lista di servizi */
        try {
            List<MPShippingService> services = MPCarrierService.getShippingServices(storeId, source, Order.get(orderId), params)

            /* ritorno la lista di servizi */
            if(AmazonUtility.isAmazon(source)) {
                render template: "/shipping/mpShipping/services/amazon", model: [services: services]
            }
        }
        catch (MPException mpe) {
            MPMlog.debug("MPException - ${mpe?.message}")
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            render mpe.message
        }
        catch (Exception e) {
            MPMlog.error("Errore durante il recupero dei servizi del canale di vendita", e)
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            render "Errore"
        }
    }

    /**
     * Metodo per ottenere la lista dei filtri aggiuntivi di un servizio di spedizione del canale di vendita
     * */
    def mpServicesAdditionals() {
        Long storeId = session["store_id"] as Long
        Long orderId = params.getLong("orderId")
        Long source = params.getLong("source")

        /* ottengo la lista di servizi */
        try {
            List<MPShippingFilter> additionals = MPCarrierService.getShippingServiceAdditionals(storeId, source, Order.get(orderId), params)

            /* ritorno la lista di servizi */
            if(AmazonUtility.isAmazon(source)) {
                render template: "/shipping/mpShipping/filters/amazon_additionals", model: [additionals: additionals]
            }
        }
        catch (Exception e) {
            MPMlog.error("Errore durante il recupero dei filtri aggiuntivi del servizio selezionato", e)
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            render "Errore"
        }
    }

    /**
     * Metodo per creare un nuova spedizione per un canale di vendita
     * */
    @Transactional
    def mpShipping() {
        Long storeId = session["store_id"] as Long
        Long orderId = params.getLong("orderId")
        Long source = params.getLong("source")

        try {
            Order order = orderService.get(orderId)
            if(order.storeId != storeId) {
                response.status = 401
                render "Non puoi creare una spedizione per questo ordine. Prova ad effettuare di nuovo il login."
                return
            }

            /* creo la spedizione sul canale di vendita */
            MPShippingLabel shippingLabel = MPCarrierService.createNewShipping(storeId, source, order, params)

            /* ritorno un messaggio di success */
            Map response = [:]
            switch(shippingLabel.shipping.outputType) {
                case "ZPL":
                    response["mime"] = "application/zpl"
                    response["name"] = "${order.reference}.zpl"
                    break
                case "PDF":
                    response["mime"] = "application/pdf"
                    response["name"] = "${order.reference}.pdf"
                    break
                case "PNG":
                    response["mime"] = "image/png"
                    response["name"] = "${order.reference}.png"
                    break
                default:
                    response["mime"] = "application/octet-stream"
                    response["name"] = "${order.reference}.pdf"
                    break
            }
            response["data"] = new String(Base64.encodeBase64(shippingLabel.fileLabel))

            /* ritorno la LDV */
            render (response as JSON)
        }
        catch (Exception e) {
            MPMlog.error("Errore durante la stampa della spedizione per il canale di vendita", e)
            response.status = HttpServletResponse.SC_INTERNAL_SERVER_ERROR
            render "Errore"
        }
    }

    @Transactional
    def dealerShipping() {

    }

    /**
     * End point richiamato per ora solo da PayPerShip.it per mandarci informazioni riguardo le spedizioni
     */
    @Transactional
    @Secured(["permitAll"])
    def webhook() {
        Map result = [:]

        if (!params.token || params.token.isEmpty()) {
            response.sendError(404)
            return
        }

        Shipper shipper = Shipper.findByToken(params.token)
        if(shipper) {
            try {
                result = shippingService.handleWebhook(shipper, request)
                response.setStatus(result.code)
            }catch(Exception e) {
                MPMlog.error("Errore durante il parsing dei dati da ${shipper}", e)
                response.setStatus(500)
                result.code = 500
                result.message = "Errore interno"
            }
        } else{
            MPMlog.info("Shipper non trovato con il seguente token: ${params.token}")
            response.setStatus(401)
            result.code = 401
            result.message = "Unauthorized"
        }

        render result as JSON
    }


    /**
     * End point richiamato per ora solo da PayPerShip.it per mandarci informazioni riguardo le spedizioni
     */
    @Transactional
    @Secured(["permitAll"])
    def publicDownloadLdvFile() {
        try {
            /* parametri della richiesta */
            String token = (params.token as String)?.replace(" ", "+")

            /* dati dalle properties */
            String decryptKey = grailsApplication.config["poleepo.security.jwt_crypto"] as String
            String currentPlatform = grailsApplication.config["poleepo.platform"] as String
            String fileLabelPath = grailsApplication.config["shipping.filelabelpath"] as String

            /* decripto il token */
            Download downloadInfo = TokenUtility.retrieveDownloadFromToken(token, decryptKey)
            MPMlog.debug("Donwload info: ${downloadInfo.properties}")

            /* se il tipo di download non è valido, ritorno 404 */
            MPMlog.debug("downloadInfo.getDownloadType() != \"SHIPPING_LDV\" -> ${downloadInfo.getDownloadType() != "SHIPPING_LDV"}")
            MPMlog.debug("currentPlatform != downloadInfo.getPlatform().name() -> ${currentPlatform != downloadInfo.getPlatform().name()}")
            if(downloadInfo.getDownloadType() != "SHIPPING_LDV" || currentPlatform != downloadInfo.getPlatform().name()) {
                response.setStatus(404)
                return
            }

            /* recupero la spedizione */
            Shipping shipping = shippingService.get(downloadInfo.getIdentifier())

            /* se la LDV non è accessibile, ritorno 404 */
            MPMlog.debug("shipping == null -> ${shipping == null}")
            MPMlog.debug("shipping.shipper?.store?.id != downloadInfo.getStoreId() -> ${shipping.shipper?.store?.id != downloadInfo.getStoreId()}")
            MPMlog.debug("!shipping.fileLabel -> ${!shipping.fileLabel}")
            if(shipping == null || shipping.shipper?.store?.id != downloadInfo.getStoreId() || !shipping.fileLabel) {
                response.setStatus(404)
                return
            }

            /* recupero la LDV */
            File ldvFile = new File(fileLabelPath, shipping.fileLabel)

            /* se il file non esiste, torno 404 */
            MPMlog.debug("!ldvFile.exists() -> ${!ldvFile.exists()}")
            if(!ldvFile.exists()) {
                response.setStatus(404)
                return
            }

            /* ottengo i dati della LDV */
            def fileName = shipping.shippingCode ?: shipping.shippingInternalCode
            def fileExt = ".pdf"
            if (shipping.outputType == "ZPL") //per BRT si possono stampare etichette nel formato ZPL
                fileExt = ".zpl"
            if (shipping.outputType == "PNG") //per AMAZON si possono stampare etichette nel formato PNG
                fileExt = ".png"

            /* ritorno i dati da scaricare */
            response.contentType = "application/octet-stream"
            response.setHeader("Content-disposition", "attachment;filename=\"Label${fileName}${fileExt}\"")
            response.outputStream << ldvFile.bytes
            response.outputStream.flush()
            response.outputStream.close()
        }
        catch (Exception e) {
            MPMlog.error("Errore durante il download delle lettere di vettura tramite token", e)
            response.setStatus(404)
        }
    }



    /**
     * End point richiamato per la pagina pubblica di tracking /lp/tracking/{token}
     */
    @Transactional
    @Secured(["permitAll"])
    def tracking() {
        /* parametri della richiesta */
        String token = (params.token as String)?.replace(" ", "+")

        /* dati dalle properties */
        String decryptKey = grailsApplication.config["poleepo.security.jwt_crypto"] as String
        String currentPlatform = grailsApplication.config["poleepo.platform"] as String
        String fileLabelPath = grailsApplication.config["shipping.filelabelpath"] as String

        /* decripto il token */
        // Download downloadInfo = TokenUtility.retrieveDownloadFromToken(token, decryptKey)
        // MPMlog.debug("Tracking info: ${downloadInfo.properties}")

        /* recupero la spedizione */
        Shipping shipping = shippingService.get(81237L)
        if(shipping){
            render view: "/shipping/tracking/index.gsp" , model: [shipping:shipping, token:token]
        }else{
            render view: "/shipping/tracking/unauthorized.gsp"
        }

    }

    /**
     * End point richiamato per la il dettaglio del tracking
     */
    @Transactional
    @Secured(["permitAll"])
    def trackingDetails(){
        Shipping shipping;
        if(params.token){
            String token = (params.token as String)?.replace(" ", "+")

            /* dati dalle properties */
            String decryptKey = grailsApplication.config["poleepo.security.jwt_crypto"] as String
            String currentPlatform = grailsApplication.config["poleepo.platform"] as String
            String fileLabelPath = grailsApplication.config["shipping.filelabelpath"] as String

            /* decripto il token */
            // Download downloadInfo = TokenUtility.retrieveDownloadFromToken(token, decryptKey)
            // MPMlog.debug("Tracking info: ${downloadInfo.properties}")
            shipping = shippingService.get(81237L)
        }else{
            shipping = shippingService.get(params.getLong("id"));
        }

        if(shipping){
            def currentState = ShippingStatus.getStateName(shipping)
            List states =  ShippingStatus.getTrackingStates(shipping, true);
            render template: "/shipping/tracking/tracking" , model: [states:states, currentState:currentState, allStates:(params.allStates == 'true')]
        }else{
            response.status = 404
            render "Spedizione non trovata"
            return
        }
    }

    @Transactional
    def printShippingLabels(){
        List<Shipping> shipping = Shipping.findAllByIdInListAndPrinted(params.list('checkShip'), true)
        Long userId = ((User) springSecurityService.currentUser)?.id
        Long storeId = session['store_id'] as Long

        def finalFile
        //Controllo se la lista da lavorare è popolata
        if (shipping.size() > 0){
            //Se popolata eseguo l'operazione
            finalFile = shippingService.printShippingLabels(userId, storeId, shipping)

            println(finalFile)

            def fileExt

            if (finalFile.name.contains(".pdf")){
                fileExt = ".pdf"
            } else if (finalFile.name.contains(".zpl")){
                fileExt = ".zpl"
            } else {
                fileExt = ".zip"
            }

            render ([name: "Etichette${fileExt}", mime: "application/octet-stream", data: new String(Base64.encodeBase64(finalFile.bytes))] as JSON)
        }

        response.status = 404
        render g.message(code: 'controllers.shipping.printShippingLabels.error')
    }

}
