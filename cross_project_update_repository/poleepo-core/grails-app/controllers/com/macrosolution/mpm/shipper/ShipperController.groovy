package com.macrosolution.mpm.shipper

import com.macrosolution.mpm.marketplace.SourceMP
import com.macrosolution.mpm.shipper.fedex.FedexProductType
import com.macrosolution.mpm.store.Store
import com.macrosolution.mpm.utility.log.Log
import com.macrosolution.tntcarriermanager.TntProductType
import grails.converters.JSON
import grails.web.servlet.mvc.GrailsParameterMap
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.digest.DigestUtils

import java.security.DigestInputStream
import java.security.MessageDigest

import static org.springframework.http.HttpStatus.*
import grails.plugin.springsecurity.annotation.Secured
import grails.transaction.Transactional
import websalesmanager.WSMUtil
import grails.core.GrailsApplication

@Secured(["ROLE_ADMIN","ROLE_SUPER_USER", "ROLE_ORDER_MANAGER","ROLE_ONBOARDING"])
@Transactional
class ShipperController {
    private static Log MPMlog = Log.getLogger() as Log

    def shipperService
    def storeService
    def springSecurityService
    GrailsApplication grailsApplication

    static allowedMethods = [save: "POST", update: "POST", delete: "DELETE", getForm:'POST']

    def index() {
       [shipperList: shipperService.getAll(store_id:session['store_id'])]
    }

    def show() {
        redirect action: 'edit' , id: params.id
    }

    def create() {
        def typeList = shipperService.listType()
        respond new Shipper(params), model:[typeList:typeList]
    }

    @Transactional
    def save() {
        Long storeId = session['store_id'] as Long
        Integer virtualShipperType = params.getInt("virtualShipperType")

        def shipper
        ShipperType type = shipperService.getType(params.getLong('type'))
        if(type.type == ShipperType.TNT) {
            if(!params.tntUserConfiguration.customer || !params.tntUserConfiguration.senderAccId || !params.tntUserConfiguration.user || !params.tntUserConfiguration.password) {
                transactionStatus.setRollbackOnly()

                response.status=405
                render 'La configurazione inserita non \u00E8 corretta!'
                return
            }

            params.tntUserConfiguration.checkBookingForm = params.tntUserConfiguration.checkBookingForm.toBoolean()
            if (params.tntUserConfiguration.priopntime) params.tntUserConfiguration.priopntime = Date.parse('HH:mm', params.tntUserConfiguration.priopntime)
            if (params.tntUserConfiguration.priclotime) params.tntUserConfiguration.priclotime = Date.parse('HH:mm', params.tntUserConfiguration.priclotime)
            if (params.tntUserConfiguration.secopntime) params.tntUserConfiguration.secopntime = Date.parse('HH:mm', params.tntUserConfiguration.secopntime)
            if (params.tntUserConfiguration.secclotime) params.tntUserConfiguration.secclotime = Date.parse('HH:mm', params.tntUserConfiguration.secclotime)
            if (params.tntUserConfiguration.availabilitytime) params.tntUserConfiguration.availabilitytime = Date.parse('HH:mm', params.tntUserConfiguration.availabilitytime)
            if (params.tntUserConfiguration.pickuptime) params.tntUserConfiguration.pickuptime = Date.parse('HH:mm', params.tntUserConfiguration.pickuptime)

            shipper = shipperService.getByVirtualShipperTypeAndStoreId(virtualShipperType, storeId) ?: new Shipper()
            shipper.properties = params

            shipper.name='TNT'
            shipper.code='TNT'

            if (params.getList("productTypes[]") != null) {

                List<String> codeList = params.getList("productTypes[]")
                Set<TntProductType> tntProductTypeList = []
                codeList?.forEach {
                    TntProductType tntProductType = TntProductType.findByCode(it.toString())
                    tntProductTypeList.add(tntProductType)
                }
                params.tntProductTypes = tntProductTypeList
            }
        }
        else if(type.type == ShipperType.GLS || type.type == ShipperType.GLS_2) {
            if(!params.userConfiguration.seatCode || !params.userConfiguration.username || !params.userConfiguration.contractCode || !params.userConfiguration.password) {
                transactionStatus.setRollbackOnly()
           
                response.status=405 
                render 'La configurazione inserita non \u00E8 corretta!'
                return
            }
            params.userConfiguration.checkBookingForm = params.userConfiguration.checkBookingForm?.toBoolean()
            params.userConfiguration.checkAddress = params.userConfiguration.checkAddress?.toBoolean()
            params.userConfiguration.autoConfirm = params.userConfiguration.autoConfirm?.toBoolean()
            params.userConfiguration.volumetricWeight = WSMUtil.toBigDecimal(params.userConfiguration?.volumetricWeight)

            shipper = shipperService.getByVirtualShipperTypeAndStoreId(virtualShipperType, storeId) ?: new GlsShipper()
            shipper.properties = params

            shipper.name = type.type == ShipperType.GLS ? 'GLS' : 'GLS_2'
            shipper.code = type.type == ShipperType.GLS ? 'GLS' : 'GLS_2'
            shipper.userConfiguration.storeID = session['store_id'] as Long
            shipper.userConfiguration.url = grailsApplication.config.gls.url

        }
        else if(type.type == ShipperType.SDA) {
            if(!params.sdaUserConfiguration.senderAccId || !params.sdaUserConfiguration.seatCode || !params.sdaUserConfiguration.user || !params.sdaUserConfiguration.password){
                transactionStatus.setRollbackOnly()
           
                response.status=405 
                render 'La configurazione inserita non \u00E8 corretta!'
                return
            }

            shipper = shipperService.getByVirtualShipperTypeAndStoreId(virtualShipperType, storeId) ?: new Shipper()
            shipper.properties = params

            shipper.name='SDA'
            shipper.code='SDA'

        }
        else if(type.type == ShipperType.POSTE) {
            if(!params.posteUserConfiguration.senderAccId || !params.posteUserConfiguration.seatCode || !params.posteUserConfiguration.user || !params.posteUserConfiguration.password){
                transactionStatus.setRollbackOnly()
           
                response.status=405 
                render 'La configurazione inserita non \u00E8 corretta!'
                return
            }

            shipper = shipperService.getByVirtualShipperTypeAndStoreId(virtualShipperType, storeId) ?: new Shipper()
            shipper.properties = params

            shipper.name='POSTE ITALIANE'
            shipper.code='POSTE'

        }
        else if(type.type == ShipperType.BRT) {
            if(!params.brtUserConfiguration.customer || !params.brtUserConfiguration.seatCode || !params.brtUserConfiguration.user || !params.brtUserConfiguration.password){
                transactionStatus.setRollbackOnly()
           
                response.status=405 
                render 'La configurazione inserita non \u00E8 corretta!'
                return
            }

            shipper = shipperService.getByVirtualShipperTypeAndStoreId(virtualShipperType, storeId) ?: new Shipper()
            shipper.properties = params

            shipper.name='BRT'
            shipper.code='BRT'
        }
        else if(type.type == ShipperType.DHL) {
            if(!params.dhlUserConfiguration.customer || !params.dhlUserConfiguration.password || !params.dhlUserConfiguration.senderAccId){
                transactionStatus.setRollbackOnly()
           
                response.status=405 
                render 'La configurazione inserita non \u00E8 corretta!'
                return
            }

            shipper = shipperService.getByVirtualShipperTypeAndStoreId(virtualShipperType, storeId) ?: new Shipper()
            shipper.properties = params

            shipper.name='DHL'
            shipper.code='DHL'
        }
        /* se ho Poste Delivery */
        else if(type.type == ShipperType.POSTE_DELIVERY) {

            shipper = shipperService.getByVirtualShipperTypeAndStoreId(virtualShipperType, storeId) ?: new Shipper()
            shipper.properties = params

            shipper.name = "POSTE ITALIANE"
            shipper.code = ShipperType.POSTE_DELIVERY
            params."poste_deliveryUserConfiguration.orderIdInNotes" =
                    (params."poste_deliveryUserConfiguration.orderIdInNotes" == "on") ? "true" : "false"
        }
        /* se ho Spedisci Online */
        else if(type.type == ShipperType.SPEDISCI_ONLINE) {

            shipper = shipperService.getByVirtualShipperTypeAndStoreId(virtualShipperType, storeId) ?: new Shipper()
            shipper.properties = params

            shipper.name = "SPEDISCI.ONLINE"
            shipper.code = ShipperType.SPEDISCI_ONLINE
        }
        else if(type.type == ShipperType.MIT) {
            if(!params.mitUserConfiguration.user || !params.mitUserConfiguration.password) {
                transactionStatus.setRollbackOnly()

                response.status=405
                render 'La configurazione inserita non \u00E8 corretta!'
                return
            }

            shipper = shipperService.getByVirtualShipperTypeAndStoreId(virtualShipperType, storeId) ?: new Shipper()
            shipper.properties = params

            shipper.name = "MIT"
            shipper.code = ShipperType.MIT
        }
        /* se ho qapla */
        else if (type.type == ShipperType.QAPLA) {
            shipper = shipperService.getByVirtualShipperTypeAndStoreId(virtualShipperType, storeId) ?: new Shipper()
            shipper.properties = params

            shipper.name = ShipperType.QAPLA
            shipper.code = ShipperType.QAPLA
        }

        /* se ho fedex */
        else if (type.type == ShipperType.FEDEX) {
            shipper = shipperService.getByVirtualShipperTypeAndStoreId(virtualShipperType, storeId) ?: new Shipper()
            shipper.properties = params

            shipper.name = ShipperType.FEDEX
            shipper.code = ShipperType.FEDEX
        }

        //verifica della configurazione
        params.carrier_type = type.id
        params.carrier_name = type.type.toLowerCase()
        params.store_id = storeId

        Boolean validConf
        try{
            log.debug("TEST PROVA")
            validConf = shipperService.verifyConfiguration(type, params as GrailsParameterMap)
        }
        catch(Exception e) {
            MPMlog.error("Errore durante la verifica del corriere ${shipper?.code} per lo store ${storeId}", e)
            transactionStatus.setRollbackOnly()
            response.status = 405 //errore inserimento
            render 'Errore durante la verifica della configurazione!'
            return
        }

        if(!validConf) {
            transactionStatus.setRollbackOnly()
            response.status=405
            render 'La configurazione inserita non \u00E8 corretta!'
            return
        }
        else if(ShipperType.isCarrierService(type.type)) {
            // per sda, poste, brt, dhl, poste delivery, paypership salvo i dati sul db del carrier service
            shipperService.save(params)
        }
        //fine verifica configurazione
        
        shipper.store = storeService.get(storeId)
        try{
            shipperService.save(shipper)
        }
        catch(Exception e){
            MPMlog.error("Errore durante il salvataggio del corriere ${shipper?.code} per lo store ${storeId}", e)
            transactionStatus.setRollbackOnly()
            //respond shipper.errors, view:'create'
            response.status=405 //errore inserimento
            render 'La configurazione inserita non \u00E8 corretta!'
            return
        }

        if (shipper.hasErrors()) {
            transactionStatus.setRollbackOnly()
            //respond shipper.errors, view:'create'
            response.status=405 //errore inserimento
            render shipper.errors
            return
        }
        else {
            def defaultShipper=shipperService.getDefault(shipper.store.id) 
            if(!defaultShipper){
                shipperService.setDefault( shipper, shipper.store.id)
            }

            render shipper.id
        }
    }

    @Transactional
    def update() {
        //prima verifico la configurazione
        params.store_id = session['store_id']

        /* recupero lo shipper type */
        ShipperType type = shipperService.getType(params.getLong('type'))
        MPMlog.debug("Type trovato per id ${params.getLong('type')} = ${type}")

        /* Verifico la configurazione */
        Boolean validConf
        try{
            validConf = shipperService.verifyConfiguration(type, params)
        }
        catch(Exception e){
            e.printStackTrace()
            transactionStatus.setRollbackOnly()
            response.status=405 //errore inserimento
            render 'Errore durante la verifica della configurazione!'
            return
        }

        if(!validConf) {
            transactionStatus.setRollbackOnly()
            response.status=405
            render 'La configurazione inserita non \u00E8 corretta!'
            return
        }
        //fine verifica configurazione

//        Shipper shipper = shipperService.get(params.getLong('id'))
        Shipper shipper = Shipper.findByStoreAndTypeAndVirtualShipperType(Store.get(session['store_id'] as Long), type, params.getInt('virtualShipperType'))
        MPMlog.debug("Trovato lo shipper: ${shipper?.id}")
        if(shipper != null && !ShipperType.isCarrierService(type.type) && !type.isMarketplace()) {
            if(shipper.name=="GLS"){
                //params.userConfiguration.notificationPhone = params.userConfiguration.notificationPhone.toBoolean()
                //params.userConfiguration.checkBookingForm = params.userConfiguration.checkBookingForm.toBoolean()
                params.userConfiguration.volumetricWeight = WSMUtil.toBigDecimal(params.userConfiguration?.volumetricWeight)
            }
            shipper.properties = params

            if (shipper.hasErrors()) {
                transactionStatus.setRollbackOnly()
                response.status=405
                render shipper.errors
                return
            }

            shipperService.save(shipper)
            if (shipper.hasErrors()) {
                transactionStatus.setRollbackOnly()
                response.status=405
                render shipper.errors
                return
            }
            else{
                render "Configurazione salvata con successo"
                return
            }
        }
        else {
            if(ShipperType.isCarrierService(type.type)) {
                MPMlog.debug("Sono anche qui")

                shipper.title = params.title
                shipper.virtualShipperType = params.getInt("virtualShipperType")

                if (shipper.hasErrors()) {
                    transactionStatus.setRollbackOnly()
                    response.status=405
                    render shipper.errors
                    return
                }

                shipperService.save(shipper)

                if(type.type == ShipperType.POSTE_DELIVERY) {
                    params."poste_deliveryUserConfiguration.orderIdInNotes" =
                            (params."poste_deliveryUserConfiguration.orderIdInNotes" == "on") ? "true" : "false"
                }

                if(type.type == ShipperType.TNT) {

                    MPMlog.debug("sono TNT")

                    params.tntUserConfiguration.checkBookingForm = params.tntUserConfiguration.checkBookingForm.toBoolean()
                    if (params.tntUserConfiguration.priopntime) params.tntUserConfiguration.priopntime = Date.parse('HH:mm', params.tntUserConfiguration.priopntime)
                    if (params.tntUserConfiguration.priclotime) params.tntUserConfiguration.priclotime = Date.parse('HH:mm', params.tntUserConfiguration.priclotime)
                    if (params.tntUserConfiguration.secopntime) params.tntUserConfiguration.secopntime = Date.parse('HH:mm', params.tntUserConfiguration.secopntime)
                    if (params.tntUserConfiguration.secclotime) params.tntUserConfiguration.secclotime = Date.parse('HH:mm', params.tntUserConfiguration.secclotime)
                    if (params.tntUserConfiguration.availabilitytime) params.tntUserConfiguration.availabilitytime = Date.parse('HH:mm', params.tntUserConfiguration.availabilitytime)
                    if (params.tntUserConfiguration.pickuptime) params.tntUserConfiguration.pickuptime = Date.parse('HH:mm', params.tntUserConfiguration.pickuptime)
                    if (params.tntUserConfiguration.errorAddress) params.tntUserConfiguration.errorAddress=params.tntUserConfiguration.errorAddress.toBoolean()

                    if (params.getList("productTypes[]") != null) {

                        List<String> codeList = params.getList("productTypes[]")
                        Set<TntProductType> tntProductTypeList = []
                        codeList?.forEach {
                            TntProductType tntProductType = TntProductType.findByCode(it.toString())
                            tntProductTypeList.add(tntProductType)
                        }
                        params.tntProductTypes = tntProductTypeList
                    }
                }

                if(type.type == ShipperType.FEDEX) {

                    MPMlog.debug("sono FEDEX")

                    if (params.getList("productTypes[]") != null) {

                        List<String> codeList = params.getList("productTypes[]")
                        Set<FedexProductType> fedexProductTypeList = []
                        codeList?.forEach {
                            FedexProductType fedexProductType = FedexProductType.findByCode(it.toString())
                            fedexProductTypeList.add(fedexProductType)
                        }
                        params.fedexProductTypes = fedexProductTypeList
                    }
                }
            }

            /* recupero da remoto e aggiorno */
            shipperService.update(type, params)
            render "Configurazione salvata con successo"
            return
        }
    }

    @Transactional
    def shipperDefault() {
        Long storeId = session["store_id"] as Long
        String shipperType = params.get("shipperType", null)

        if(shipperType == null) {
            response.setStatus(403)
            render "Non hai selezionato il corriere da impostare come defualt"
            return
        }

        /* ottengo il corriere */
        Shipper shipper = shipperService.getByCodeAndStoreId(shipperType, storeId)

        /* se non ho trovato il corriere */
        if (!shipper) {
            response.setStatus(404)
            render 'Non hai una configurazione attiva per questo corriere'
            return
        }

        /* imposto il corriere come default */
        def resp = shipperService.setDefault(shipper, storeId)

        /* se c'è stato un errore */
        if (!resp) {
            response.status = 405
            if(!shipper.enable)
            {
                render "Non puoi impostare questo corriere come default perchè non è abilitato!"
                return
            }
            render resp
        }
        else{
            render "Hai impostato   \""+shipper.name+"\" come corriere di default."
        }
    }

    protected void notFound() {
        request.withFormat {
            form multipartForm {
                flash.message = message(code: 'default.not.found.message', args: [message(code: 'shipper.label', default: 'Shipper'), params.id])
                redirect action: "index", method: "GET"
            }
            '*'{ render status: NOT_FOUND }
        }
    }

    def customShipperRequest() {

        Long storeId = session['store_id'] as Long
        Store store = Store.findById(storeId)
        Integer virtualShipperType = params.getInt("virtualShipperType")
        String action = params.get("shipperAction")

        MPMlog.debug("custom action -> ${action}")

        Map customParams = params

        customParams.remove("virtualShipperType")
        customParams.remove("shipperAction")

        render shipperService.customShipperRequest(store, virtualShipperType, action, params) as JSON
        return

    }
}
