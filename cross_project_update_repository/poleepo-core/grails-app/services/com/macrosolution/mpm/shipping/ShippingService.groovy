package com.macrosolution.mpm.shipping

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.macrosolution.mpm.carrier.MpmCarrierService
import com.macrosolution.mpm.carrier.configuration.GlsCarrierConfiguration
import com.macrosolution.mpm.digest.DigestStatus
import com.macrosolution.mpm.digest.massive.MassiveDigestEntityStatus
import com.macrosolution.mpm.digest.massive.MassiveDigestType
import com.macrosolution.mpm.events.EventMessage
import com.macrosolution.mpm.events.WebHookEventType
import com.macrosolution.mpm.fulfillment.FulfillmentService
import com.macrosolution.mpm.marketplace.SourceMP
import com.macrosolution.mpm.order.FulfillmentStorage
import com.macrosolution.mpm.orderstate.OrderState
import com.macrosolution.mpm.dealer.DealerService
import com.macrosolution.mpm.order.Fulfillment
import com.macrosolution.mpm.product.DefaultCarrier
import com.macrosolution.mpm.product.Product
import com.macrosolution.mpm.product.Supply
import com.macrosolution.mpm.shipper.DealerShipper
import com.macrosolution.mpm.shipper.GlsShipper
import com.macrosolution.mpm.shipper.Shipper
import com.macrosolution.mpm.shipper.ShipperType
import com.macrosolution.mpm.shipping.brt.BrtBoxType
import com.macrosolution.mpm.shipping.brt.BrtServiceType
import com.macrosolution.mpm.shipping.dhl.DhlBooking
import com.macrosolution.mpm.booking.GlsBooking
import com.macrosolution.mpm.shipping.dhl.DhlBoxType
import com.macrosolution.mpm.shipping.gls.GlsBoxType
import com.macrosolution.mpm.shipping.pps.PayPerShipSiteData
import com.macrosolution.mpm.shipping.sda.SdaBooking
import com.macrosolution.mpm.shipping.sda.SdaBoxType
import com.macrosolution.mpm.shipping.sda.SdaServiceType
import com.macrosolution.mpm.shipping.tnt.TntBoxType
import com.macrosolution.mpm.utility.ExecutorUtility
import com.macrosolution.mpm.utility.FileUtility
import com.macrosolution.tntcarriermanager.TntProductType
import grails.transaction.Transactional

import com.macrosolution.mpm.address.Address
import com.macrosolution.mpm.store.Store
import com.macrosolution.mpm.order.Order
import com.macrosolution.mpm.order.OrderRow
import com.macrosolution.tntcarriermanager.TntLabelConsignment

import com.macrosolution.mpm.carrier.data.CarrierShipping
import com.macrosolution.mpm.carrier.data.CarrierAddress
import com.macrosolution.mpm.carrier.data.CarrierParcel
import com.macrosolution.mpm.utility.log.*
import com.macrosolution.mpm.utility.StringUtility

import com.macrosolution.mpm.integration.request.IntegrationRequest
import com.macrosolution.mpm.integration.response.IntegrationResponse
import groovy.json.JsonBuilder
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils
import org.hibernate.Query
import org.hibernate.Session
import org.hibernate.sql.JoinType
import org.hibernate.type.LongType
import org.springframework.transaction.TransactionStatus
import websalesmanager.MultiplePatternDateParser
import websalesmanager.WSMUtil

import javax.servlet.http.HttpServletRequest

import com.macrosolution.mpm.supplier.Supplier
import grails.gorm.DetachedCriteria

import java.math.RoundingMode
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

@Transactional
class ShippingService {
	private static Log MPMlog = Log.getLogger() as Log

    def orderService   
    def storeService
    def glsService
    def integrationService
    def grailsApplication
    def addressService
    def shippingService
    def comunicationService
    def orderStateService
    DealerService dealerService
    def sessionFactory
    def shipperService
    def variationService
    def productService
    def massiveDigestService
    def springSecurityService
    def grailsLinkGenerator
    def MPCarrierService
    MpmCarrierService mpmCarrierService
    FulfillmentService fulfillmentService

    def get(Long id){
        return Shipping.findById(id)
    }

    def getReceiverAddressList(Long id_shipping){
    	def shipping = Shipping.load(id_shipping)
	   	def addresses = Address.withCriteria {
    		'in' ('customer', shipping?.orders?.customer)
			eq 'deleted', false
    	}
    	return addresses
    }

    def getShippingItem(Long item_id){
        return ShippingItem.get(item_id)
    }

    def saveShippingItem(ShippingItem item){
        item.save(flush:true)
        return item
    }

    def getPickupAddressList(Long store_id){
    	def store = storeService.get(store_id)
    	def addresses = Address.findAllByStoreAndDeleted(store, false)
        addresses.addAll(Supplier.findAllByStoreAndDropShipping(store,true)?.address)
    	return addresses.findAll {it != null}.unique{it.id}
    }

    def isPickupAddressUsed (Long address_id) {
        def address = addressService.get(address_id)
        def shippings = Shipping.findAllByPickupAddress(address)
        if (shippings.isEmpty()) return false
        else return true
    }

    def getDropShipperAddress(Long supplier_id){
        return SupplierService.get(supplier_id).address
    }

    def updateOldPickUpShippings (Address oldPickUpAddress, Address newPickUpAddress){
        def oldShippings = new DetachedCriteria(Shipping).build{
            eq 'pickupAddress', oldPickUpAddress
            eq 'printed', true
        }.updateAll(pickupAddress:newPickUpAddress)
    }


    def list(Map params){
    	def sorting = [:]
    	if(params.sort && params.sort != "") sorting.sort = params.sort
    	if(params.order && params.order != "") sorting.order = params.order

    	def pagination = [:]
        if(params.max != null) pagination.max = params.getLong("max")
        if(params.offset != null) pagination.offset = params.getLong("offset")

    	def startDateCreated
    	def endDateCreated
        try{
            startDateCreated = params.getDate("startDateCreated", "dd/MM/yyyy")
            startDateCreated.set(hourOfDay: 00, minute: 00, second: 01)
            endDateCreated = params.getDate("endDateCreated", "dd/MM/yyyy")
    		endDateCreated.set(hourOfDay: 23, minute: 59, second: 59)
    	}catch(Exception e){
    		startDateCreated = null
    		endDateCreated   = null
                MPMlog.warn("IN METHOD LIST ON SHIPPING SERVICE CANNOT PARSE CREATE DATE: EXCEPTION IS ${e}:${e.message}")

    	}

	    def startDatePickup
	    def endDatePickup
        try{
            startDatePickup = params.getDate("startDatePickup", "dd/MM/yyyy")
            startDatePickup.set(hourOfDay: 00, minute: 00, second: 01)
            endDatePickup = params.getDate("endDatePickup", "dd/MM/yyyy")
		    endDatePickup.set(hourOfDay: 23, minute: 59, second: 59)
	    }catch(Exception e){
		    startDatePickup = null
		    endDatePickup = null
		    MPMlog.warn("IN METHOD LIST ON SHIPPING SERVICE CANNOT PARSE PICKUP DATE: EXCEPTION IS ${e}:${e.message}")

	    }

    	def startDateUpdated
    	def endDateUpdated
    	try{
            startDateUpdated = params.getDate("startDateUpdated", "dd/MM/yyyy")
            startDateUpdated.set(hourOfDay: 00, minute: 00, second: 01)
            endDateUpdated = params.getDate("endDateUpdated", "dd/MM/yyyy")
    		endDateUpdated.set(hourOfDay: 23, minute: 59, second: 59)
    	}catch(Exception e){
    		startDateUpdated = null
    		endDateUpdated   = null
    		MPMlog.warn("IN METHOD LIST ON SHIPPING SERVICE CANNOT PARSE UPDATED DATE: EXCEPTION IS ${e}:${e.message}")
    	}

    	def startDateShipped
    	def endDateShipped
    	try{
            startDateShipped = params.getDate("startDateShipped", "dd/MM/yyyy")
            startDateShipped.set(hourOfDay: 00, minute: 00, second: 00)
            endDateShipped = params.getDate("endDateShipped", "dd/MM/yyyy")
    		endDateShipped.set(hourOfDay: 23, minute: 59, second: 59)
    	}catch(Exception e){
            if(params.startDate && params.endDate ) {
                startDateShipped = params.startDate;
                endDateShipped = params.endDate;
            }else {
                startDateShipped = null
                endDateShipped = null
                MPMlog.warn("IN METHOD LIST ON SHIPPING SERVICE CANNOT PARSE SHIPPED DATE: EXCEPTION IS ${e}:${e.message}")
            }
    	}
        def supplierList = Supplier.getAll(params.dropShippers)
        def idAddressList=supplierList?.oldAddress?.id?.flatten()

	    /* leggo il peso minimo della spedizione */
	    BigDecimal minWeight = null
	    if(params.get("minWeight") != null)
		    minWeight = WSMUtil.toBigDecimal(params.get("minWeight"))

	    /* leggo il peso massimo della spedizione */
	    BigDecimal maxWeight = null
	    if(params.get("maxWeight") != null)
		    maxWeight = WSMUtil.toBigDecimal(params.get("maxWeight"))

	    MPMlog.debug("minWeight: ${minWeight}/${params.minWeight} - maxWeight: ${maxWeight}/${params.maxWeight}")

        // se c'è il parametro smartSearch eseguo la query in sql per cercare per la concatenazione di nome e cognome o il segnacollo
        List<Long> fullNameIds = null
        if(params.smartSearch){
            String q = "select distinct shipping.id as id " +
                "from shipping " +
                "left join address on shipping.receiver_address_id = address.id " +
                "left join orders_shippings on shipping.id = orders_shippings.shipping_id " +
                "left join orders on orders_shippings.order_id = orders.id " +
                "where orders.store_id = :store " +
                "and (" +
                    "concat(address.name, ' ', address.surname) like concat('%', :search, '%') " +
                    "or shipping.parcel_number_from like concat('%', :search, '%') " +
                ")"

            /* inizializzo la query */
            Session session = sessionFactory.getCurrentSession()
            Query sqlQuery = session.createSQLQuery(q)
		            .setParameter("search", params.smartSearch.trim())
		            .setParameter("store", params.store_id)

            /* ottengo i risultati della query */
            MPMlog.debug("Query ricerca ordini: ${sqlQuery.getQueryString()}")
            fullNameIds = sqlQuery.addScalar("id", LongType.INSTANCE).list()
        }

        def shippingCriteria = new grails.gorm.DetachedCriteria(Shipping).build{
        	if(sorting.sort){
        		order(sorting.sort,sorting.order?:"asc")
        	}else{
        		order("dateCreated","desc")
        	}
            if(params.store_id) {
                orders {
                    store {
                        delegate.eq('id', params.store_id)
                    }
                    if(params.get('orderS')){
                        def listState=params.list('orderS').collect{it.toInteger()}
                        currentState {
                            'in' ('state',listState)
                        }

                    }
                }
            }
            if(params.source)
            {
                List<Long> values= new ArrayList<Long>()
                params.getList('source').each{source->
                    values.add(Long.parseLong(source))
                }
                orders{
                    'in' ('source',values)
                }
            }

	        /* se mi è stato passato il barcode */
	        if(params.barcode){
//		        orders{
//			        rows{
//				        product{
//					        eq("barcode", params.barcode)
//				        }
//			        }
//		        }
		        orders{
			        rows{
				        productOrder{
					        eq("ean", params.barcode)
				        }
			        }
		        }
	        }

            if (params.checkShip) {
                def checkedList = []
                params.list('checkShip').each{shipId->
                    checkedList.add(shipId.toLong())
                }
                inList('id',checkedList)
            }
            

            if(params.complete != null && params.complete!=""){
            	or{
            		eq("complete", params.getBoolean("complete"))
            		if(!params.getBoolean("complete")) isNull ("complete")
            	}
            }

            if(params.confirmed != null && params.confirmed!=""){
            	or{
            		eq("confirmed", params.getBoolean("confirmed"))
            		if(!params.getBoolean("confirmed")) isNull ("confirmed")
            	}
            }

            if(params.fileLabel != null && params.fileLabel!=""){
                if(params.getBoolean("fileLabel")) {
                    isNotNull("fileLabel")
                }
                else {
                    isNull("fileLabel")
                }
            }

            if(params.toDownload != null && params.toDownload!=""){
                eq("toDownload", params.getBoolean("toDownload"))
            }

            if(params.sended != null && params.sended!=""){
            	or{
            		eq("sended", params.getBoolean("sended"))
            		if(!params.getBoolean("sended")) isNull ("sended")
            	}
            }

            if(params.printed != null && params.printed!=""){
            	if(params.getBoolean("printed")==true)
                    eq 'printed', true
            	if(params.getBoolean("printed")==false)
                    eq 'printed', false
            }

            if(params.delivered != null && params.delivered!=""){
                if(params.getBoolean("delivered")==true) 
                    isNotNull ("customerDeliveryDate")
                if(params.getBoolean("delivered")==false)
                    isNull ("customerDeliveryDate")
            }

            if(params.pickupAddress && params.pickupAddress != "" )
            	pickupAddress{
            		delegate.eq("id",params.getLong("pickupAddress"))
            	}
            if(params.shipper)
                shipper{
                    delegate."in"('id', params.list("shipper").collect{it.toLong()})
                }
            if(startDateCreated && endDateCreated)
            	between ("dateCreated", startDateCreated, endDateCreated)
	        if(startDatePickup && endDatePickup)
		        between("pickupDate", startDatePickup, endDatePickup)
            if(startDateUpdated && endDateUpdated)
            	between ("lastUpdated", startDateUpdated, endDateUpdated)
            if(startDateShipped && endDateShipped)
            	between ("deliveryDate", startDateShipped, endDateShipped)

            if (params.dropShippers) {

                // oldAddress{
                //     delegate.eq('id',supplierList.address.id)
                // }
                //delegate.eq('pickupAddress',supplierList.address)

                pickupAddress{
                    or{

                        if(idAddressList)
                            delegate."in"('id',idAddressList )
                        if(supplierList?.address?.id)
                            delegate."in"('id', supplierList?.address?.id)
                    }
                }

                delegate.eq 'printed', true
            }

            if(params.smartSearch){
            	or{
	            	ilike("shippingCode", "%${params.smartSearch}%")
	            	ilike("shippingInternalCode", "%${params.smartSearch}%")
	                orders{
	                	delegate.ilike("reference", "%${params.smartSearch}%")
	                }
	                receiverAddress{
	                	or{
	                		delegate.ilike("company","%${params.smartSearch}%")
							delegate.ilike("name","%${params.smartSearch}%")
							delegate.ilike("surname","%${params.smartSearch}%")
							delegate.ilike("vatNumber","%${params.smartSearch}%")
							delegate.ilike("cf","%${params.smartSearch}%")
							delegate.ilike("province","%${params.smartSearch}%")
							delegate.ilike("city","%${params.smartSearch}%")
							delegate.ilike("zipCode","%${params.smartSearch}%")
							delegate.ilike("address","%${params.smartSearch}%")
	                	}
	                }
	                pickupAddress{
	                	or{
	                		delegate.ilike("company","%${params.smartSearch}%")
							delegate.ilike("name","%${params.smartSearch}%")
							delegate.ilike("surname","%${params.smartSearch}%")
							delegate.ilike("vatNumber","%${params.smartSearch}%")
							delegate.ilike("cf","%${params.smartSearch}%")
							delegate.ilike("province","%${params.smartSearch}%")
							delegate.ilike("city","%${params.smartSearch}%")
							delegate.ilike("zipCode","%${params.smartSearch}%")
							delegate.ilike("address","%${params.smartSearch}%")
	                	}

	                }
                    if(fullNameIds) "in" "id", fullNameIds
            	}
            }

	        if(minWeight != null)
		        ge("weight", minWeight)
	        if(maxWeight != null)
		        le("weight", maxWeight)
        }
        return [shippings:shippingCriteria.list(pagination), totalCount:shippingCriteria.count()]
    }


    def listStoreShippings (store_id){
        def shippingList = Shipping.withCriteria{
            orders{
                store{
                    delegate.eq ('id', store_id)
                }
            }
        }
        return shippingList
    }

    List<Long> listStoreShippingsToUpdate(Long store_id, List<Long> ids, int max, Date startDate = null) {
        return Shipping.withCriteria{
            projections {
                distinct("id")
            }

            //TODO verificare che va bene confirmed = true al posto di printed
            eq('confirmed', true)
            isNull('customerDeliveryDate')
            isNull('dateRejected')

            // se la spedizione ha dropShipperConfirmed = true allora deve essere sended = true per lavorarla
            // altrimenti se la spedizione non è sended = true allora deve essere dropShipperConfirmed = true
            or {
                eq("dropShipperConfirmed", false)
                eq("sended", true)
            }

            if(startDate) {
                ge ('dateCreated', startDate)
            }

            /* non considero le spedizioni di amazon in questa query */
            ne("class", "com.macrosolution.mpm.shipping.AmazonShipping")

            // non considero le spedizioni generiche in questa qeury
            ne("class", "com.macrosolution.mpm.shipping.GenericShipping")


            orders{
                store{
                    delegate.eq ('id', store_id)
                }
            }

            if(ids.size() > 0) {
                not {
                    'in'("id", ids)
                }
            }

            maxResults(max)
        } as List<Long>
    }

    def listStoreBrtShippingsToUpdate(Long store_id, Date startDate = null) {
        def shippingList = BrtShipping.withCriteria{
            eq('printed', true)
            eq('confirmed', true)
            isNull('shippingCode')

            if(startDate) {
                ge ('dateCreated', startDate)
            }

            orders{
                store{
                    delegate.eq ('id', store_id)
                }
            }
        }
        return shippingList
    }

    def getShippedQuantity(OrderRow orderRow){
        def shippingQuantity = ShippingItem.createCriteria().get{
            projections{
                sum('quantity')
            }
            eq 'orderRow', orderRow
        }?:0
        return shippingQuantity
//        return 0
    }

    def getBeConfirmed(Long store_id){
        def shipping = Shipping.withCriteria{
            eq ('confirmed', false)
            isNotNull ('shippingCode' )
            orders{
                store{
                    eq ('id', store_id)
                }
            }
        }
        return shipping
    }

	/**
	 *  Metodo per l'eliminazione di una spedizione.
	 *  Il metodo effettua l'eliminazione sia da MPM che dal corriere.
	 *  @param shipping_id                  l'id della spedizione da eliminare
	 *  @param store_id                     l'id dello store della spedizione
	 *  @param deleteRemote                 true se bisogna cancellare anche sul corriere, false se non bisogna cancellare
	 * */
	def delete(Long shipping_id, Long store_id, Boolean deleteRemote) {
		/* ottengo la spedizione */
		Shipping shipping = this.get(shipping_id)

		/* chiamo la funzione padre */
		return delete(shipping, store_id, deleteRemote)
	}

	/**
	 *  Metodo per l'eliminazione di una spedizione.
	 *  Il metodo effettua l'eliminazione sia da MPM che dal corriere.
	 *  @param shipping                     la spedizione da eliminare
	 *  @param store_id                     l'id dello store della spedizione
	 *  @param deleteRemote                 true se bisogna cancellare anche sul corriere, false se non bisogna cancellare
	 * */
	def delete(Shipping shipping, Long store_id, Boolean deleteRemote){
		/* preparo la richiesta per l'eliminazione della spedizione di TNT */
        if(shipping instanceof TntShipping){
	        /* se ho l'etichetta */
            if(shipping.tntLabelConsignment){
	            /* cancello la spedizione sul corriere */
	            if(deleteRemote) {
                    IntegrationResponse integrationResponse = this.deleteRemote(store_id, shipping)

                    def data = integrationResponse.data

                    if(!data.isSuccess){
                        throw new Exception(data.errorMessage)
                    }
	            }
                /* elimino l'etichetta */
                def tntLabelConsignment = shipping.tntLabelConsignment
                shipping.tntLabelConsignment = null
                tntLabelConsignment.delete(flush: true)
            }
        } else if(shipping instanceof GlsShipping && deleteRemote){ /* faccio la richiesta per l'eliminazione della spedizione su GLS */
            if(shipping.printed && shipping.shippingCode != null) {
                //per le spedizioni all'estero non è presente il servizio AFMI
                glsService.deleteShipping((shipping.shipper as GlsShipper)?.userConfiguration,shipping.shippingCode, store_id, shipping.sedeFMI)
            }

        } else if(shipping instanceof BrtShipping && deleteRemote && shipping.printed){
            IntegrationResponse integrationResponse = this.deleteRemote(store_id, shipping)
            def data = integrationResponse.data

            if(!data.isSuccess){
                throw new Exception(data.errorMessage)
            }

        } else if(shipping instanceof DhlShipping && deleteRemote && shipping.confirmed){ // cancella la prenotazione del ritiro
            IntegrationResponse integrationResponse = this.deleteRemote(store_id, shipping)
            def data = integrationResponse.data

            if(!data.isSuccess){
                throw new Exception(data.errorMessage)
            }
        }
        else if(shipping instanceof SdaShipping && deleteRemote && shipping.confirmed){ // cancella la prenotazione del ritiro
            /* se la spedizione è di poste delivery, cancello il ritiro creato */
            if(shipping.shipper.type.type == ShipperType.POSTE_DELIVERY) {
                IntegrationResponse integrationResponse = this.deleteRemote(store_id, shipping)
                def data = integrationResponse.data

                if (!data.isSuccess) {
                    throw new Exception(data.errorMessage)
                }
            }
        }
        else if(shipping instanceof SpedisciOnlineShipping && deleteRemote && shipping.printed) {
            IntegrationResponse integrationResponse = this.deleteRemote(store_id, shipping)
            def data = integrationResponse.data

            if(!data.isSuccess){
                throw new Exception(data.errorMessage)
            }
        }
        else if(shipping instanceof QaplaShipping && deleteRemote && shipping.printed) {
            IntegrationResponse integrationResponse = this.deleteRemote(store_id, shipping)
            def data = integrationResponse.data

            if(!data.isSuccess){
                throw new Exception(data.errorMessage)
            }
        }
        else if(shipping instanceof AmazonShipping) {
            Map response = MPCarrierService.deleteShipping(store_id, shipping)

            if(!response.success){
                throw new Exception(response.message ?: "Errore durante la comunicazione con il canale di vendita")
            }
        }

        /* rimuovo la spedizione dagli ordini correlati */
        shipping.orders?.collect()?.each{ order ->
            removeOrder(shipping, order)
        }

		/* elimino la spedizione */
        shipping.delete(flush:true)
    }

    def save(Shipping shipping){
        shipping.save(flush:true)
        return shipping
    }

    def create(params,storeId) {
        def result=[:]
        //Shipper shipper = shipperService.get(params.getLong('shipper'))
        if(params.shipper.equals("Generic")) {
            params.shipper = "MPM"
        }
//        Shipper shipper = shipperService.getByCodeAndStoreId(params.shipper, storeId)
        Shipper shipper = params.shipper //shipperService.getByVirtualShipperTypeAndStoreId(params.shipper as Integer, storeId)
        params.shipper = shipper.id

        if(shipper.store.id !=storeId){
            //response.status = 401
            //render "Non puoi creare una spedizione per questo corriere. Prova ad effettuare di nuovo il login."
            result.status=401
            result.message="Non puoi creare una spedizione per questo corriere. Prova ad effettuare di nuovo il login."
            result.isSuccess=Boolean.FALSE
            result.rollback=Boolean.FALSE
            return result
        }
        def shipping
        if(shipper.type.type == ShipperType.TNT){

            def tntShipper = shipperService.getConfiguration(storeId, shipper.virtualShipperType)

            if(params.productType != null) {
                TntProductType productType = TntProductType.findByCode(params.productType)
                params.productType = productType
                params.tntApplication = productType?.tntApplication
            }

            shipping = new TntShipping(params)
            shipping.numberPackage = Long.parseLong(params.numberPackage)

            if(tntShipper.checkBookingForm == false){
                shipping.booking = new TntBooking()
                shipping.booking.pickupdate = new Date()
                if(tntShipper.priopntime) shipping.booking.priopntime = Date.parse('HH:mm', tntShipper.priopntime)
                if(tntShipper.priclotime) shipping.booking.priclotime = Date.parse('HH:mm', tntShipper.priclotime)
                if(tntShipper.secopntime) shipping.booking.secopntime = Date.parse('HH:mm', tntShipper.secopntime)
                if(tntShipper.secclotime) shipping.booking.secclotime = Date.parse('HH:mm', tntShipper.secclotime)
                if(tntShipper.availabilitytime) shipping.booking.availabilitytime = Date.parse('HH:mm', tntShipper.availabilitytime)
                if(tntShipper.pickuptime) shipping.booking.pickuptime = Date.parse('HH:mm', tntShipper.pickuptime)
            }

        }else if(shipper.type.type == ShipperType.GLS || shipper.type.type == ShipperType.GLS_2){
            //params.notificationPhone = params.getBoolean('notificationPhone')
            //params.afmiService = params.getBoolean('afmiService')
            shipping = new GlsShipping(params)
        }else if(shipper.type.type == ShipperType.GEN){
            shipping = new GenericShipping(params)
            shipping.confirmed=Boolean.TRUE
	        shipping.confirmedDate = new Date()
        }else if(shipper.type.type == ShipperType.SDA || shipper.type.type == ShipperType.POSTE || shipper.type.type == ShipperType.POSTE_DELIVERY) {
            //Anche per poste usiamo la classe SdaShipping e SdaBooking
            shipping = new SdaShipping(params)
            //shipping.bulky = params.getBoolean('bulky')
            shipping.booking = new SdaBooking()
            shipping.booking.pickupdate = new Date() +1
            shipping.notes = []
            if (params.priopntime) shipping.booking.priopntime = Date.parse('HH:mm', params.priopntime)
            if (params.priclotime) shipping.booking.priclotime = Date.parse('HH:mm', params.priclotime)
            if (params.secopntime) shipping.booking.secopntime = Date.parse('HH:mm', params.secopntime)
            if (params.secclotime) shipping.booking.secclotime = Date.parse('HH:mm', params.secclotime)


        }else if(shipper.type.type == ShipperType.BRT){
            if (params.deliveryDateRequired) params.deliveryDateRequired = Date.parse('dd/MM/yyyy', params.deliveryDateRequired)
            shipping = new BrtShipping(params)
            if (!params.pricingConditionCode) shipping.pricingConditionCode = ""
            shipping.volume = WSMUtil.toBigDecimal(params.volume)
        }else if(shipper.type.type == ShipperType.DHL){
            shipping = new DhlShipping(params)
            shipping.dhlBooking = new DhlBooking()
            shipping.dhlBooking.pickupdate = new Date()
            if (params.packageLocation) shipping.dhlBooking.packageLocation = params.packageLocation
            if (params.availabilitytime) shipping.dhlBooking.availabilitytime = Date.parse('HH:mm', params.availabilitytime)
            if (params.secclotime) shipping.dhlBooking.secclotime = Date.parse('HH:mm', params.secclotime)
        }
        else if(shipper.type.type == ShipperType.QAPLA) {
            shipping = new QaplaShipping(params)
        }
        else if(shipper.type.type == ShipperType.SPEDISCI_ONLINE) {
            shipping = new SpedisciOnlineShipping(params)
        }

        def storeLocation = storeService.get(storeId).pickupAddress
        shipping.pickupAddress = storeLocation
        shipping.senderAddress = storeLocation

        /* creo la mappa delle righe dell'ordine */
        Map orderRows = [:]
        params.orderRowId.collect{it -> Long.valueOf(it)}.each{ Long id ->
            Boolean select = params["orderRowSelected${id}"].toString().toBoolean()
            String total = params."quantityTotal${id}" ?: "0"
            String qnt = params."quantity${id}" ?: "0"
            orderRows.put(id, [id: id, select: select, total: new BigDecimal(total.replace(",", ".")), quantity: new BigDecimal(qnt.replace(",", "."))])
        }


        /* inserisco l'ordine nella spedizione */



        /* inserisco l'ordine nella spedizione */
        def order
        if(params.order){
            order = orderService.get(params.order)
            if(order.store.id != storeId){
                result.status=401
                result.message="Non puoi creare una spedizione per questo ordine. Prova ad effettuare di nuovo il login."
                result.isSuccess=Boolean.FALSE
                result.rollback=Boolean.TRUE
                return result
                //transactionStatus.setRollbackOnly()
                //response.status = 401
                //render "
                //return
            }
            addOrderToShipping(shipping, order, orderRows)
        }

        shipping.codValue=WSMUtil.toBigDecimal(params.codValue)
        shipping.insuredValue=WSMUtil.toBigDecimal(params.insuredValue)

        for (i in params.index){
            shipping.addToParcels(  weight: WSMUtil.toBigDecimal(params.get("parcel.weight"+i)),
                    boxType:BoxType.get(params.get("parcel.boxType"+i)),
                    width: WSMUtil.toBigDecimal(params.get("parcel.width"+i)),
                    height: WSMUtil.toBigDecimal(params.get("parcel.height"+i)),
                    depth: WSMUtil.toBigDecimal(params.get("parcel.depth"+i)))
        }

        /* setto il peso della spedizione */
        shipping.weight = shipping.parcels*.weight?.flatten()?.sum() as BigDecimal
        /* setto il tipo di stampa */
        shipping.outputType=params.outputType

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

            /* se il fornitore è altro, ma la spedizione può essere gestita solo da dropshipper MPM */
            MPMlog.debug("supplierComplete: ${supplierComplete} ${supplierComplete.findAll {it != null}.size()}")
            if(shipping.shipper.type.type == ShipperType.GEN && supplierComplete.findAll {it != null && it.dealerType != null}.size() > 0 && supplierComplete.findAll {it == null}.size() == 0) {
                //response.setStatus(500)
                //render "Non è possibile gestire le spedizioni dei fornitori dropshipping con il corriere generico"
                result.status=500
                result.message="Non è possibile gestire le spedizioni dei fornitori dropshipping con il corriere generico"
                result.isSuccess=Boolean.FALSE
                result.rollback=Boolean.TRUE
                return result
                //transactionStatus.setRollbackOnly()
                //return
            }

            // se si vuole forzare la creazione della spedizione allora si devono scalare le quantità
            /*if(params.forceShipping == "Y") {
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
                    result.status=500
                    result.message="<p>Impossibile creare la spedizione. Si \u00E8 verificato un errore durante lo scalo delle quantità"
                    result.isSuccess=Boolean.FALSE
                    result.rollback=Boolean.TRUE
                    return result
                    //response.status = 500
                    //render "<p>Impossibile creare la spedizione. Si \u00E8 verificato un errore durante lo scalo delle quantità"
                    //transactionStatus.setRollbackOnly()
                    //return
                }
            }*/

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
        save(shipping)
        MPMlog.debug("RETURN SHIPPING:" + shipping)
        result.status=200
        result.message=""
        result.isSuccess=Boolean.TRUE
        result.rollback=Boolean.FALSE
        result.shipping=shipping
        return result
        //return shipping

    }

    def deleteRemote(Long store_id, Shipping shipping){
        /* preparo la richiesta a carrier-service */
        IntegrationRequest request = IntegrationRequest.newInstance()
        request.url = grailsApplication.config.getProperty('services.carrier.url', "http://localhost:8080/carrier-service/") + "/shipping/" + store_id + "/" + shipping.shippingCode
        request.method = IntegrationRequest.METHOD_DELETE
        request.responseType = IntegrationRequest.RESPONSE_TYPE_JSON
        request.headers = ["X-STORE-BAN": store_id]

        def carrierConf = null
        if(ShipperType.isCarrierService(shipping.shipper.type.type)) {
            carrierConf = shipperService.getConfiguration(store_id, shipping.shipper.virtualShipperType)
        }

        def bodyAttributes = [:]
        if(shipping instanceof TntShipping){
            bodyAttributes.customer = carrierConf?.customer
            bodyAttributes.password = carrierConf?.password
            bodyAttributes.user = carrierConf?.username
            bodyAttributes.senderAccId = carrierConf?.account_id
            bodyAttributes.consignmentno = shipping.tntLabelConsignment.consignmentNo
            bodyAttributes.shipperType = 1
            bodyAttributes.virtualShipperType = carrierConf?.virtual_shipper_type
        } else if(shipping instanceof BrtShipping) {
            bodyAttributes.customerReference = shipping.id
            bodyAttributes.alphanumericSenderReference = shipping.alphanumericSenderReference
            bodyAttributes.shipperType = 7
            bodyAttributes.virtualShipperType = carrierConf.virtual_shipper_type
        } else if(shipping instanceof DhlShipping) {
            bodyAttributes.pickupCode = shipping.pickupCode
            bodyAttributes.originSvcArea = shipping.originSvcArea
            bodyAttributes.requestorName = shipping.senderAddress.nomeCompleto?:shipping.senderAddress.ragioneSociale?:shipping.senderAddress.store?.name
            bodyAttributes.pickupDate = shipping.dhlBooking?.pickupdate?.format('yyyy-MM-dd')
            bodyAttributes.cancelTime = shipping.dhlBooking?.availabilitytime?.format('HH:mm')
            bodyAttributes.shipperType = 8
            bodyAttributes.virtualShipperType = carrierConf.virtual_shipper_type
        }
        else if(shipping instanceof SdaShipping) {
            if(shipping.shipper.type.type == ShipperType.POSTE_DELIVERY) {
                bodyAttributes.pickupCode = shipping.pickupCode
                bodyAttributes.consignmentno = shipping.shippingCode
                bodyAttributes.shipperType = 9
                bodyAttributes.virtualShipperType = carrierConf.virtual_shipper_type
            }
        }else if(shipping instanceof SpedisciOnlineShipping) {
            bodyAttributes.emotionreference=shipping.ppsEmotionReference
            bodyAttributes.shipperType=ShipperType.CARRIER_ID_SPEDISCI_ONLINE
            bodyAttributes.virtualShipperType = carrierConf.virtual_shipper_type
        }
        else if(shipping instanceof QaplaShipping) {
            bodyAttributes.emotionreference = shipping.qaplaShippingId
            bodyAttributes.shipperType=ShipperType.CARRIER_ID_QAPLA
            bodyAttributes.virtualShipperType = carrierConf.virtual_shipper_type
        }

        def attributeBuilder = new JsonBuilder(bodyAttributes)
        def attributeBody = attributeBuilder.toString()
        request.body = attributeBody

        IntegrationResponse integrationResponse = integrationService.call(request)

        return integrationResponse
    }

    def getImportableOrders(Shipping shipping){
    	def orderList = Order.executeQuery("select distinct o from Order o where :myShipping not member of o.shippings and o.customer in(:custList) and (o.fullShipped is null or o.fullShipped = false)", [myShipping:shipping, custList: shipping.orders.customer])
        return orderList
    }

    def addOrderToShipping(Shipping shipping, Order order, Map rows = null){
        /* nel momento un cui creo la spedizione, l'ordine è fullshipped */
        Boolean fullShipped = true;

        order.addToShippings(shipping)
        order.rows.each{ orderRow ->
            def shippedQuantity = this.getShippedQuantity(orderRow)

            if(rows == null) {
                //controllo se questa riga compare già in altre spedizioni e nel caso quanti pezzi sono già stati spediti
                shipping.addToItems(
                        orderRow: orderRow,
                        quantity: orderRow.quantity - shippedQuantity
                )
            }
            else {
                /* ottengo i dettagli della riga dell'ordine */
                Map row = rows.get(orderRow.id)

                /* inserisco la quantità selezionata */
                shipping.addToItems(
                        orderRow: orderRow,
                        quantity: row.select ? row.quantity : 0
                )

                /* verifico se ho inserito tutte le quantità */
                if((!row.select && shippedQuantity < orderRow.quantity) || row.quantity + shippedQuantity < orderRow.quantity)
                    fullShipped = false
            }
        }
        order.fullShipped = fullShipped
        shipping.receiverAddress = shipping.receiverAddress?:order.deliveryAddress
        shipping.pickupAddress = shipping.pickupAddress?:order.store.location
        shipping.senderAddress = shipping.senderAddress?:order.store.location
        orderService.save(order)
        this.save(shipping)
        MPMlog.debug "SHIPPING ADDED TO ORDER"
    }

    def removeOrder(Shipping shipping, Order order){
        order.removeFromShippings(shipping)
        shipping.items.collect().each{
            if(order.rows.contains(it.orderRow))
            shipping.removeFromItems(it)
        }
        this.save(shipping)
        order.fullShipped = false
        orderService.save(order)
        return true
    }

    def getTntShippingsReadyToGo(Store store){
        def shippingList = TntShipping.withCriteria {
            eq('complete', true)
            or{
                eq('sended', false)
                isNull ('sended')
            }
            tntLabelConsignment{
                not{ isNull('date')}
            }
            pickupAddress{
                eq ('id', store.location.id)
            }
        }
        shippingList
    }

    def getGlsShippingsReadyToGo(Store store){
        def shippingList = GlsShipping.withCriteria {
            eq('complete', true)
            eq('confirmed', true)
            or{
                eq('sended', false)
                isNull ('sended')
            }
            not{ isNull('shippingCode')}
            pickupAddress{
                eq ('id', store.location.id)
            }
        }
    }

    def checkShipping(List<Long> shippingList, Long store_id){

        shippingList.each{shippingId ->
            Shipping.withNewTransaction { TransactionStatus transactionStatus2 ->
            Shipping shipping = Shipping.findById(shippingId)

            if(!shipping?.shipper?.enable) {
                MPMlog.debug("Non controllo la spedizione ${shipping} perchè il corriere di riferimento non è abilitato")
                return
            }

            IntegrationResponse response

            if(shipping instanceof GlsShipping){ // PER SPEDIZIONI GLS
                try {
                    response = glsService.getTracking((shipping.shipper as GlsShipper)?.userConfiguration,shipping, store_id, shipping.sedeFMI)
                } catch(Exception ex) {
                    MPMlog.error "EXCEPTION THROWN GETTING TRACKING INFO FROM GLS - SHIPPING ID ${shipping.id}", ex
                }

            }

            // Per le spedizioni gestite dai fornitori integrati
            else if(shipping instanceof DealerShipping) {

                try {
                    // Controllo la spedizione sul dealer-service
                    response = dealerService.checkShipping(store_id, shipping)
                }
                catch (Exception ex) {
                    MPMlog.error "EXCEPTION THROWN GETTING TRACKING INFO - SHIPPING ID ${shipping.id}: " + ex + " MESSAGE: " + ex.getMessage()
                }

            }
            else if(!(shipping instanceof GenericShipping)){ // PER SPEDIZIONI SDA, POSTE E TNT

                IntegrationRequest request =IntegrationRequest.newInstance()

                String shippingCode = shipping.shippingCode

                if(shipping instanceof QaplaShipping) {
                    shippingCode = shipping.shippingCode ?: shipping.parcelId
                }

                request.url=grailsApplication.config.getProperty('services.carrier.url', "http://localhost:8080/carrier-service/")+"/shipping/tracking/"+shippingCode

                request.method=IntegrationRequest.METHOD_GET
                request.responseType=IntegrationRequest.RESPONSE_TYPE_JSON

                request.queryParams.put("storeId", store_id)
                request.queryParams.put("reference", shipping.id)
                request.queryParams.put("carrierType", shipping.shipper.virtualShipperType)
                request.headers = ["X-STORE-BAN": store_id]

                try {
                    response = integrationService.call(request)
                } catch (Exception ex) {
                    MPMlog.error "EXCEPTION THROWN GETTING TRACKING INFO - SHIPPING ID ${shipping.id}: "+ex+" MESSAGE: "+ex.getMessage()
                }
            }

            if (response) {

                try {
                    def df = new MultiplePatternDateParser("dd/MM/yyyy HH:mm", "dd/MM/yyyy", "yyyy-MM-dd HH:mm:ss")

                    /* parametri per i webhook */
                    boolean isChangedStatus = false

                    MPMlog.debug("RESPONSE carrier-service :"+response.data+" for shipping "+shipping.id)
                    if(response.data.trackingInfo){
                        MPMlog.debug("RESPONSE carrier-service :"+response.data+" for shipping "+shipping.id)
                        //per BRT se la spedizione è partita allora possiamo salvare il numero di tracking
                        if(!shipping.shippingCode && response.data.trackingInfo.code){
                            shipping.shippingCode = response.data.trackingInfo.code
                        }
                        if(!shipping.trackingUrl && response.data.trackingInfo.trackingUrl){
                            shipping.trackingUrl = response.data.trackingInfo.trackingUrl
                        }
                        if(response.data.trackingInfo.price){
                            MPMlog.debug("Price carrier-service :"+response.data.trackingInfo.price)
                            shipping.price = new BigDecimal(response.data.trackingInfo.price.total)
                            MPMlog.debug("Price shipping :"+shipping.price)
                        }

                        // Se è una dealerShipping
                        if(shipping instanceof DealerShipping) {

                            // Setto il nome del corriere se mi viene inviato
                            if(StringUtils.isBlank(shipping.carrierName) && StringUtils.isNotBlank(response.data.trackingInfo?.carrierName)) {
                                shipping.carrierName = response.data.trackingInfo?.carrierName
                            }
                        }


                        // sul carrier service shippingDate indica la data di partenza della spedizione mentre su mpm usiamo il campo deliveryDate
                        if(response.data.trackingInfo.shippingDate){
                            MPMlog.debug("shippingDate carrier-service :"+response.data.trackingInfo.shippingDate)
                            shipping.deliveryDate = df.parse(response.data.trackingInfo.shippingDate)
                            shipping.sended=Boolean.TRUE

                            /* se è cambiato lo stato sended -> WEBHOOK */
                            if(shipping.isDirty("sended")) {
                                isChangedStatus = true
                            }

                            /* aggiorno lo stato degli ordini della spedizione */
                            shipping.orders.each {Order order ->
                                Boolean fullShipped = false
                                if (order.shippings.findAll { !it.sended }.size() == 0 && order.fullShipped)
                                    fullShipped = true

                                /* di base setto spedito parzialmente */
                                Integer state = OrderState.PARTIALLY_SHIPPED
                                if(!fullShipped && shipping.codValue != null)
                                    state = OrderState.PARTIALLY_SHIPPED_WAITING_PAYMENT
                                else if(fullShipped && shipping.codValue != null)
                                    state = OrderState.FULL_SHIPPED_WAITING_PAYMENT
                                else if(fullShipped)
                                    state = OrderState.FULL_SHIPPED

                                if(!fulfillmentService.updateOrderStateByShipping(order, shipping, OrderState.findByState(state))) {
                                    /* aggiorno lo stato dell'ordine */
                                    Boolean changed = orderService.updateOrderStateToState(order, state)

                                    /* setto lo stato dell'ordine */
                                    MPMlog.debug("SETTO STATO COMPLETO PER ORDINE ${order.id}: ${changed}")
                                }

                            }

                            MPMlog.debug("shippingDate shipping :"+shipping.deliveryDate)
                        }
                        // sul carrier service deliveryDate indica la data di consegna al cliente della spedizione mentre su mpm usiamo il campo customerDeliveryDate
                        if(response.data.trackingInfo.deliveryDate){
                            MPMlog.debug("deliveryDate carrier-service :"+response.data.trackingInfo.deliveryDate)
                            // format dd/MM/yyyy HH:mm sia per sda, tnt e gls
                            shipping.customerDeliveryDate = df.parse(response.data.trackingInfo.deliveryDate)
                            MPMlog.debug("shipping.customerDeliveryDate :"+shipping.customerDeliveryDate)

                            /* se è cambiato lo stato sended -> WEBHOOK */
                            if(shipping.isDirty("customerDeliveryDate")) {
                                isChangedStatus = true
                            }

                            /* aggiorno lo stato degli ordini della spedizione */
                            shipping.orders.each {Order order ->
                                /* se tutte le spedizioni sono completate ho completato l'ordine */
                                if (order.shippings.findAll { it.customerDeliveryDate == null }.size() == 0 && order.fullShipped) {

                                    /* di base setto spedito parzialmente */
                                    Integer state = OrderState.COMPLETED
                                    if(!fulfillmentService.updateOrderStateByShipping(order, shipping, OrderState.findByState(state))) {
                                        /* aggiorno lo stato dell'ordine */
                                        Boolean changed = orderService.updateOrderStateToState(order, state)

                                        /* setto lo stato dell'ordine */
                                        MPMlog.debug("SETTO STATO COMPLETO PER ORDINE ${order.id}: ${changed}")
                                    }
                                }
                            }
                        }
                        // data di annullamento della spedizione //solo per TNT per il momento
                        if(response.data.trackingInfo.canceledDate){
                            MPMlog.debug("canceledDate carrier-service :"+response.data.trackingInfo.canceledDate)
                            shipping.dateRejected = df.parse(response.data.trackingInfo.canceledDate)
                            MPMlog.debug("shipping.dateRejected :"+shipping.dateRejected)

                            /* se è cambiato lo stato sended -> WEBHOOK */
                            if(shipping.isDirty("dateRejected")) {
                                isChangedStatus = true
                            }

                        }

                        if(response.data.trackingInfo.shippingStates) {
                            response.data.trackingInfo.shippingStates.each{state->

                                def stateDate = df.parse(state.date)
                                // verifico se lo stato della spedizione è già presente nel db
                                ShippingState shippingState = shipping.states.find {it.code == state.code &&
                                                                                it.date == stateDate &&
                                                                                it.depotId == state.depotId}

                                //se lo stato era già presente non faccio niente, altrimento lo aggiungo alla spedizione
                                if (!shippingState) {
                                    shippingState = new ShippingState(state.code, state.description, stateDate, state.depotId, state.depotDescription)
                                    shipping.addToStates(shippingState)
                                    isChangedStatus |= parsingPoleepoShippingState(shipping, shippingState, stateDate);
                                }

                            }
                        } else { //il codice della spedizione non è valido perché è una spedizione troppo vecchia
                            MPMlog.debug("NON È PIÙ POSSIBILE VERIFICARE GLI STATI DELLA SPEDIZIONE CON ID"+shipping.id+" PERCHÈ È TROPPO VECCHIA");
                        }

                        //invia la notifica al cliente che la spedizione è partita
                        /** @Deprecated
                        if(shipping.isDirty("sended")) {
                            MPMlog.debug("START: SEND NOTIFICATION TO CUSTOMER")

                            comunicationService.sendShippingNotification(shipping, store_id)
                            MPMlog.debug("END: SEND NOTIFICATION TO CUSTOMER")
                        }
                         **/

                        shipping.save(flush: true, failOnError: true)

                        /* invio dei WebHook */
                        if(isChangedStatus) {
                            shipping.orders.each { Order order ->
                                def message = new EventMessage()
                                message.entityID = order.id
                                message.storeID = order.store.id
                                message.data.put(EventMessage.ORDER_SOURCEID_KEY, order.sourceId)
                                message.data.put(EventMessage.ORDER_REFERENCE, order.reference)
                                message.data.put(EventMessage.SHIPPING_ID_KEY, shipping.id)

                                // API-8 - Webhooks spedizioni - STATUS_SHIPMENT
                                List<Map> shippingItems = []
                                shipping.items.each { ShippingItem it ->
                                    FulfillmentStorage fs = FulfillmentStorage.findByOrderRowIdAndShippingId(it.orderRow.id, shipping.id)
                                    Map shippingItemFields = [:]
                                    shippingItemFields['order_row_id'] = it.orderRow.id
                                    shippingItemFields['supplier_id'] = fs?.supplierId
                                    shippingItemFields['fulfillment_id'] = fs?.fulfillmentId

                                    shippingItems.add(shippingItemFields)
                                }
                                message.data.put(EventMessage.SHIPPING_ITEMS_KEY, shippingItems)

                                PoleepoShippingState poleepoState = ShippingStatus.getPoleepoState(shipping);
                                if(poleepoState){
                                    //NUOVO CAMPI PER IL WEBHOOK
                                    //"status": "IN_TRANSIT",
                                    message.data.put(EventMessage.SHIPPING_STATUS_KEY, poleepoState.statusMnemonic)
                                    //"status_id" :260424,
                                    message.data.put(EventMessage.SHIPPING_STATUS_ID_KEY, poleepoState.statusId)
                                    //"sub_status": "NEXT_DAY_DELIVERY",
                                    message.data.put(EventMessage.SHIPPING_SUB_STATUS_KEY, poleepoState.subStatusMnemonic)
                                    //"sub_status_id" :150870,
                                    message.data.put(EventMessage.SHIPPING_SUB_STATUS_ID_KEY, poleepoState.subStatusId)
                                    //"name":"In Consegna",
                                    message.data.put(EventMessage.SHIPPING_STATUS_NAME_KEY, poleepoState.name)
                                    //"description":"La spedizione verrà consegnata il giorno successivo.",
                                    message.data.put(EventMessage.SHIPPING_STATUS_DESCRIPTION_KEY, poleepoState.description)
                                    //"courier_waiting_for_instructions": false,
                                    message.data.put(EventMessage.SHIPPING_COURRIER_WAIT_INSTRUCTION_KEY, poleepoState.courierWaitingInstructions)
                                    //"merchant_suggested_action":["action1", "action2"],
                                    message.data.put(EventMessage.SHIPPING_MERCHANT_SUGGESTED_ACTION_KEY, poleepoState.merchantSuggestedAction)
                                    //"shopper_suggested_action":[]
                                    message.data.put(EventMessage.SHIPPING_SHOPPER_SUGGESTED_ACTION_KEY, poleepoState.shopperSuggestedAction)
                                }else{
                                    //COME OGGI
                                    message.data.put(EventMessage.SHIPPING_STATUS_KEY, ShippingStatus.getStateName(shipping))
                                }

                                message.data.put(EventMessage.SHIPPING_TRAK_CODE_KEY, shipping.shippingCode)
                                message.data.put(EventMessage.SHIPPING_TRAK_URL_KEY, shipping.poleepoTrackingUrl?:shipping.trackingUrl)
                                message.userID = springSecurityService?.currentUser?.id
                                notify WebHookEventType.STATUS_SHIPMENT.name(), message
                            }
                        }
                    }
                } catch (Exception ex) {
                    MPMlog.error("EXCEPTION THROWN UPDATING SHIPPING WITH ID ${shipping.id}", ex)
                }
            }

            transactionStatus2.flush()
            MPMlog.info("Flushata la transazione per la spedizione ${shippingId}")
            }
        }
    }

    /**
     * Metodo per parsare il generico stato del tracking ottenuto dal corriere
     * @param shipping la spedizione per cui parsare lo stato del corriere
     * @param state lo stato del tracking ottenuto dal corriere
     * @param startDate la data in cui si è verificato lo stato del tracking
     * @return true se è variato lo stato della spedizione, false altrimenti
     * */
    Boolean parsingPoleepoShippingState(Shipping shipping, ShippingState state, Date stateDate) {
        /* inizializzo la variazione dello stato della spedizione */
        Boolean isChangedStatus = false

        /* recupero il tipo del corriere */
        String type = null
        switch (shipping){
            /* se la spedizione è di GLS, ritorno il tipo GLS */
            case GlsShipping:
                type = ShipperType.GLS
                break

           // TODO: ADD OTHER CASES WHEN WE HAVE MAPPED CARRIER CODE WITH POLEEPO STATUS
            default:
                MPMlog.info("Arrived shipping with id [${shipping.id}] to check status but is not managed, going to tell is changedStatus=true")
                isChangedStatus = true
                break
        }

        /* se ho lo shipping type, gestisco il mapping per il corriere */
        if(type != null) {
            /* cerco il mapping dello stato del corriere tra quelli di Poleepo */
            CarrierShippingStateMapping mapping = CarrierShippingStateMapping.findByCarrierTypeAndCarrierCode(type, state.code)
            if (mapping) {
                /* se la spedizione ha già degli stati, devo verificare se effettivamente è cambiato lo stato */
                if(shipping.states) {
                    /* recupero l'ultimo stato della spedizione */
                    ShippingState lastState = shipping.states.sort { it.id }.last()

                    /* se l'ultimo stato valido ha lo stato di Poleepo diverso da quello che ho trovato adesso, la spedizione ha cambiato stato */
                    if(mapping.poleepoShippingState.id != lastState.poleepoState?.id) {
                        isChangedStatus = true
                    }
                }
                else {
                    isChangedStatus = true
                }

                /* setto lo stato di Poleepo sullo stato del corriere */
                state.poleepoState = mapping.poleepoShippingState
                state.save(flush: true, failOnError: true)
            }
            else {
                MPMlog.warn("SHIPPING-STATUS-MAPPING - ${type} - Manca il mapping per il corriere ${type} e il codice dello stato: '${state.code}'")
            }
        }

        /* ritorno la variazione di stato della spedizione */
        return isChangedStatus
    }

    def printLabel(Shipping shipping, Long store_id, Boolean isBulk = false) {
        MPMlog.debug("START PRINTING LABEL")
        def result = [:]
        result.isSuccess = Boolean.TRUE

        IntegrationRequest request = IntegrationRequest.newInstance()
        request.url = grailsApplication.config.getProperty('services.carrier.url', "http://localhost:8080/carrier-service/")+"/shipping/${store_id}"
        request.method = IntegrationRequest.METHOD_POST
        request.responseType = IntegrationRequest.RESPONSE_TYPE_JSON
        request.headers = ["X-STORE-BAN": store_id]

        if(shipping instanceof TntShipping){

            def phoneS = shipping.senderAddress.phoneNumber
            phoneS = (phoneS?.startsWith("+39"))?phoneS?.drop(3):phoneS

            def phoneC = shipping.pickupAddress.phoneNumber
            phoneC = (phoneC?.startsWith("+39"))?phoneC?.drop(3):phoneC

            def phoneR = (shipping.receiverAddress.phoneNumber?:shipping.receiverAddress.customer?.phones[0])
            phoneR = (phoneR?.startsWith("+39"))?phoneR?.drop(3):phoneR

            def tntConf = shipperService.getConfiguration(store_id, shipping.shipper.virtualShipperType as Long)

            Map tntXmlParams = [
                    //userConf:shipping.shipper.tntUserConfiguration,

                    customer: tntConf?.customer,
                    password: tntConf?.password,
                    user: tntConf?.username,
                    senderAccId: tntConf?.account_id,

                actualWeight:(shipping.parcels.weight.sum()*1000 as Long).toString().padLeft(8, "0"),
                totalpackages:shipping.numberPackage,
                specialInstructions: shipping.notes?.join()?:"",
                productType:shipping.productType?.code?:"",
                outputType:shipping.outputType,
                tntApplication:shipping.tntApplication,
                errorAddress:tntConf?.errorAddress,
                addresses:[
                        [addressType:"S", addrline1:shipping.senderAddress.address, postcode:shipping.senderAddress.zipCode, phone1:phoneS?.take(3),phone2:phoneS?.drop(3),
                          town: shipping.senderAddress.city, province:shipping.senderAddress.province, name:shipping.senderAddress.ragioneSociale?:shipping.senderAddress.store?.name,
                          contactname: shipping.senderAddress.nomeCompleto?:shipping.senderAddress.ragioneSociale?:shipping.senderAddress.store?.name,
                          country: shipping.senderAddress.country],

                        [addressType:"C", addrline1:StringUtility.removeNonAscii(shipping.pickupAddress.address), postcode:shipping.pickupAddress.zipCode, phone1:phoneC?.take(3),phone2:phoneC?.drop(3),
                          town: StringUtility.removeNonAscii(shipping.pickupAddress.city), province:shipping.pickupAddress.province, name:StringUtility.removeNonAscii(shipping.pickupAddress.ragioneSociale?:shipping.pickupAddress.store?.name),
                          contactname: StringUtility.removeNonAscii(shipping.pickupAddress.nomeCompleto?:shipping.pickupAddress.ragioneSociale?:shipping.pickupAddress.store?.name),
                          country: shipping.pickupAddress.country],

                        [addressType:"R", addrline1:StringUtility.removeNonAscii(shipping.receiverAddress.address), postcode:shipping.receiverAddress.zipCode, phone1:phoneR?.take(3), phone2:phoneR?.drop(3),
                          town: StringUtility.removeNonAscii(shipping.receiverAddress.city), province:shipping.receiverAddress.province, name:StringUtility.removeNonAscii(shipping.receiverAddress.ragioneSociale?:shipping.receiverAddress.customer?.toString()),
                          contactname: StringUtility.removeNonAscii(shipping.receiverAddress.nomeCompleto?:shipping.receiverAddress.ragioneSociale?:shipping.receiverAddress.store?.name),
                          country: shipping.receiverAddress.country]
                    ],
                dimensions:shipping.parcels.collect{[
                        weight:(it.weight*1000 as Long).toString().padLeft(8, "0"),
                        width:it.width?((it.width*1000 as Long).toString().padLeft(6, "0")):null,
                        height:it.height?((it.height*1000 as Long).toString().padLeft(6, "0")):null,
                        depth:it.depth?((it.depth*1000 as Long).toString().padLeft(6, "0")):null,
                        itemtype:it.boxType.value
                ]},
                customerReference: shipping.orders?.reference?.join("; "),
                insuredValue: shipping.insuredValue,
                insuranceCommission:shipping.insuranceCommission,
                codValue:shipping.codValue,
                codCommission:shipping.codCommission
            ]

            if( (!shipping.senderAddress.equals(shipping.pickupAddress)) || (shipping.shipper.type.type == ShipperType.TNT && tntConf?.checkBookingForm == false) ){
                tntXmlParams.collectiontrg = [
                    priopntime: shipping.booking?.priopntime?.format('HHmm'),
                    priclotime: shipping.booking?.priclotime?.format('HHmm'),
                    secopntime: shipping.booking?.secopntime?.format('HHmm'),
                    secclotime: shipping.booking?.secclotime?.format('HHmm'),
                    availabilitytime: shipping.booking?.availabilitytime?.format('HHmm'),
                    pickupdate: shipping.booking?.pickupdate?.format('dd.MM.yyyy'),
                    pickuptime:shipping.booking?.pickuptime?.format('HHmm'),
                    pickupinstr: shipping.booking?.pickupinstr,
                ]
            }

            if(shipping.tntLabelConsignment?.consignmentNo){
                tntXmlParams.action = 'M'
                tntXmlParams.consignmentno = shipping.tntLabelConsignment.consignmentNo
            }else{
                tntXmlParams.action = 'I'
            }
            tntXmlParams.carrierType =1;

            tntXmlParams.virtualShipperType = shipping.shipper?.virtualShipperType

            // /** NEW CONNECTION
            /*
            IntegrationRequest request =IntegrationRequest.newInstance()

            request.url=grailsApplication.config.getProperty('services.carrier.url',
                    "http://localhost:8080/carrier-service/")+"/shipping/"+store_id+""

            request.method=IntegrationRequest.METHOD_POST
            request.responseType=IntegrationRequest.RESPONSE_TYPE_JSON
*/
            def attributeBuilder = new JsonBuilder(tntXmlParams)
            def attributeBody=attributeBuilder.toString()
            request.body=attributeBody


            IntegrationResponse     integrationResponse 	= integrationService.call(request)


            def data = integrationResponse.data

            if(data.isSuccess){
                TntLabelConsignment labelConsignment = TntLabelConsignment.findByConsignmentNo(data.labelConsignment.consignmentNo.toString())?:new TntLabelConsignment()
                labelConsignment.originDepotID=data.labelConsignment.originDepotID
                labelConsignment.destinationDepotID=data.labelConsignment.destinationDepotID
                labelConsignment.itemNo=data.labelConsignment.itemNo.toLong()
                labelConsignment.weight=data.labelConsignment.weight.toLong()
                labelConsignment.consignmentNo=data.labelConsignment.consignmentNo.toString()
                try{
                    labelConsignment.date=new Date().parse('yyyyMMdd',data.labelConsignment.date)
                }catch(Exception e){
                    labelConsignment.date=new Date()
                }
                labelConsignment.tntConNo=data.labelConsignment.conNo.toString()
                labelConsignment.save(flush:true)

                shipping.tntLabelConsignment = labelConsignment


                shipping.shippingCode = shipping.tntLabelConsignment?.consignmentNo?:shipping.shippingCode
                if(shipping.shippingCode){
                    shipping.trackingUrl = "http://www.tnt.it/tracking/getTrack?WT=1&ConsigNos="+shipping.shippingCode
                }
                shipping.confirmed = true
	            shipping.confirmedDate = new Date()
                save(shipping)

                result.fileLabel = data.pdfLabel.toString().decodeBase64()
//                return result
            }
            else{
                if(data.consignmentno?.trim()?.length()>0){
                    shipping.tntLabelConsignment = shipping.tntLabelConsignment?:new TntLabelConsignment()
                    shipping.tntLabelConsignment.consignmentNo=data.consignmentno
                    save(shipping)
                }
                result.errorMessage = data.errorMessage
                result.isSuccess = Boolean.FALSE
                return result
            }
        }
        else if(shipping instanceof GlsShipping){
            try {
                GlsCarrierConfiguration glsConf = (shipping.shipper as GlsShipper)?.userConfiguration

            	if(shipping.confirmed){
                    if(shipping.outputType == "ZPL") {
                        result.fileLabel = glsService.getZpl(glsConf, shipping.parcels.id, store_id)
                        return result
                    }else {
                        result.fileLabel = glsService.getPdf(glsConf,shipping.parcels.id, store_id)
                        return result
                    }
            	}

            	if(shipping.shippingCode) {
            		glsService.deleteShipping(glsConf,shipping.shippingCode, store_id, shipping.sedeFMI)
            		shipping.shippingCode = null
                    shipping.confirmed = false
		            shipping.confirmedDate = null
                    save(shipping)
            	}

                /* se la nazione è italia e se l'utente voleva fare il check dell'indirizzo prima di stampare l'etichetta */
                if("IT".equalsIgnoreCase(shipping.recipientCountryCode) && glsConf?.checkAddress) {
                    /* effettuo  la verifica dell'indirizzo */
                    Map addressResult = mpmCarrierService.checkAddress(ShipperType.findByType("GLS").id, shipping.receiverAddress)

                    /* se la risposta non è success, ritorno l'errore all'utente */
                    if(!addressResult.isSuccess) {
                        result.errorMessage = addressResult.message
                        result.isSuccess = Boolean.FALSE
                        return result
                    }
                }

                def data = glsService.printLabel(parseShipping(shipping), glsConf , store_id)
                shipping.printed = true
                shipping.shippingCode = data.shippingCode
                shipping.trackingUrl = data.trackingUrl
                shipping.sedeFMI = data.sedeFMI
                save(shipping)

                result.fileLabel = data.pdfLabel

                // Se non è un operazione massiva e se in configurazione ha la conferma automatica attiva devo confermare la spedizione
                if(!isBulk && glsConf?.autoConfirm) {
                    MPMlog.debug("Confermo la spedizione perchè l'utente vuole la conferma automatica")
                    List<Long> shippingIdList = [shipping.id]
                    closeWorkDay(shippingIdList, store_id, true)
                }
//                return result
            }
            catch(Exception e) {
                MPMlog.error("Errore durante la stampa dell'etichetta di GLS ${e.getMessage()}", e)
                result.errorMessage = e.getMessage()
                result.isSuccess = Boolean.FALSE
                return result
            }
        }
        else if(shipping.useCarrierService()) { //comprende anche le spedizioni con poste

            if(shipping instanceof BrtShipping || shipping instanceof SpedisciOnlineShipping || shipping instanceof QaplaShipping || shipping instanceof FedexShipping) {
                //per BRT e PPS se l'etichetta era già stata stampata bisogna eliminarla prima di rifare la stampa
                if(shipping.printed) {
                    IntegrationResponse delIntegrationResponse = this.deleteRemote(store_id, shipping)
                    def delData = delIntegrationResponse.data
                    if(!delData.isSuccess) {
                        result.errorMessage = delData.errorMessage
                        result.isSuccess = Boolean.FALSE
                        return result
                    }
                }
            }

            Map xmlParams = getCarrierXmlParams(shipping)
            def attributeBuilder = new JsonBuilder(xmlParams)
            def attributeBody=attributeBuilder.toString()
            request.body=attributeBody

            IntegrationResponse     integrationResponse     = integrationService.call(request)

            def data = integrationResponse.data

            if(data.isSuccess){
                if (data.checkBookingForm) {
                    shipping.confirmed = true
	                shipping.confirmedDate = new Date()
                } else {
                    shipping.confirmed = false
	                shipping.confirmedDate = null
                }

                if(data.packages) {
                    data.packages.each{ def pack ->
                        shipping.parcels.find {it.id == pack.packageId}?.marker = pack.marker
                    }
                }

                // #7dTAV9Kj - Per brt nel campo consignmentno è contenuto il valore del primo segnacollo, viene salvato nel campo segnacollo di BrtShipping
                if(shipping instanceof BrtShipping) {

                    shipping.parcelNumberFrom = data.consignmentno
                    shipping.parcelId = data.parcelId
                }
                else if(shipping instanceof SpedisciOnlineShipping) {
                    shipping.ppsEmotionReference = data.parcelId
                    shipping.shippingCode = data.consignmentno
                    shipping.trackingUrl = data.web_url
                    shipping.printed = true
                }
                else if (shipping instanceof QaplaShipping) {
                    shipping.qaplaShippingId = Long.parseLong(data.parcelId)
                    shipping.outputType = data.labelConsignment.conNo
                    shipping.labelCreationDate = new Date()
                    shipping.shippingCode = data.consignmentno
                    shipping.parcelId = data.trackParcelId
                }
                else {
                    shipping.shippingCode = data.consignmentno
                }
                save(shipping)
                if(data.pdfLabel){
                    result.fileLabel = data.pdfLabel.toString().decodeBase64();
                }
//                return result
            }else{
                result.errorMessage = data.errorMessage
                result.isSuccess = Boolean.FALSE
                return result
            }
        }

        /* WebHook - check if the shippingCode is changed */
        if(result.isSuccess) {

	        shipping.printed = true
	        shipping.save(flush: true)

            shipping.orders.each { Order order ->
                def message = new EventMessage()
                message.entityID = order.id
                message.storeID = order.store.id
                message.data.put(EventMessage.ORDER_SOURCEID_KEY, order.sourceId)
                message.data.put(EventMessage.ORDER_REFERENCE, order.reference)
                message.data.put(EventMessage.SHIPPING_ID_KEY, shipping.id)

                // API-8 - Webhooks spedizioni - STATUS_SHIPMENT
                List<Map> shippingItems = []
                shipping.items.each { ShippingItem it ->
                    FulfillmentStorage fs = FulfillmentStorage.findByOrderRowIdAndShippingId(it.orderRow.id, shipping.id)
                    Map shippingItemFields = [:]
                    shippingItemFields['order_row_id'] = it.orderRow.id
                    shippingItemFields['supplier_id'] = fs?.supplierId
                    shippingItemFields['fulfillment_id'] = fs?.fulfillmentId

                    shippingItems.add(shippingItemFields)
                }
                message.data.put(EventMessage.SHIPPING_ITEMS_KEY, shippingItems)

                PoleepoShippingState poleepoState = ShippingStatus.getPoleepoState(shipping);
                if(poleepoState){
                    //NUOVO CAMPI PER IL WEBHOOK
                    //"status": "IN_TRANSIT",
                    message.data.put(EventMessage.SHIPPING_STATUS_KEY, poleepoState.statusMnemonic)
                    //"status_id" :260424,
                    message.data.put(EventMessage.SHIPPING_STATUS_ID_KEY, poleepoState.statusId)
                    //"sub_status": "NEXT_DAY_DELIVERY",
                    message.data.put(EventMessage.SHIPPING_SUB_STATUS_KEY, poleepoState.subStatusMnemonic)
                    //"sub_status_id" :150870,
                    message.data.put(EventMessage.SHIPPING_SUB_STATUS_ID_KEY, poleepoState.subStatusId)
                    //"name":"In Consegna",
                    message.data.put(EventMessage.SHIPPING_STATUS_NAME_KEY, poleepoState.name)
                    //"description":"La spedizione verrà consegnata il giorno successivo.",
                    message.data.put(EventMessage.SHIPPING_STATUS_DESCRIPTION_KEY, poleepoState.description)
                    //"courier_waiting_for_instructions": false,
                    message.data.put(EventMessage.SHIPPING_COURRIER_WAIT_INSTRUCTION_KEY, poleepoState.courierWaitingInstructions)
                    //"merchant_suggested_action":["action1", "action2"],
                    message.data.put(EventMessage.SHIPPING_MERCHANT_SUGGESTED_ACTION_KEY, poleepoState.merchantSuggestedAction)
                    //"shopper_suggested_action":[]
                    message.data.put(EventMessage.SHIPPING_SHOPPER_SUGGESTED_ACTION_KEY, poleepoState.shopperSuggestedAction)
                }else{
                    //COME OGGI
                    message.data.put(EventMessage.SHIPPING_STATUS_KEY, ShippingStatus.getStateName(shipping))
                }

                message.data.put(EventMessage.SHIPPING_TRAK_CODE_KEY, shipping.shippingCode)
                message.data.put(EventMessage.SHIPPING_TRAK_URL_KEY, shipping.poleepoTrackingUrl?:shipping.trackingUrl)
                message.userID = springSecurityService?.currentUser?.id

                notify WebHookEventType.STATUS_SHIPMENT.name(), message
                notify WebHookEventType.PRINTED_SHIPMENT.name(), message

	            orderService.supplierAutoConfirm(order, store_id, Boolean.FALSE, shipping)
            }
        }

        MPMlog.debug("PRINT LDV:"+result.isSuccess)
        return result
    }

    //NON USATO
    def closeWorkDayUnique(Long shipping_id,  Long store_id){
        def data = [:]
        data.isSuccess = true
        def errorMessages = ""
        CarrierShipping carrierShipping
        def shipping= this.get(shipping_id)

        if (shipping  && !shipping.confirmed) {

            if(!shipping.shipper?.enable) {
                MPMlog.debug("Non confermo la spedizione ${shipping} perchè il corriere di riferimento non è abilitato")
                data.isSuccess = false
                errorMessages=shipping.shippingInternalCode + ": " +"Corriere non abilitato."

            }

            /* se la spedizione non è stampata o non ha l'etichetta, non la confermo */
            if(!shipping.printed  || shipping.fileLabel == null) {
                MPMlog.debug("Non confermo la spedizione ${shipping} perchè l'etichetta non è stata stampata")
                data.isSuccess = false
                errorMessages+=shipping.shippingInternalCode + ": " +"Etichetta non stampata."
            }


            if(!data.isSuccess) {
                data.errorMessages = errorMessages
                return data
            }

            if(shipping instanceof GlsShipping) {
                carrierShipping=(parseShipping(shipping))
            } else if(shipping instanceof SdaShipping || shipping instanceof BrtShipping || shipping instanceof DhlShipping) { // poste, sda e brt
                try{
                    Map xmlParams = getCarrierXmlParams(shipping)
                    IntegrationRequest request =IntegrationRequest.newInstance()

                    request.url=grailsApplication.config.getProperty('services.carrier.url',
                            "http://localhost:8080/carrier-service/")+"/shipping/pickup/"+store_id+""

                    request.method=IntegrationRequest.METHOD_POST
                    request.responseType=IntegrationRequest.RESPONSE_TYPE_JSON
                    request.headers = ["X-STORE-BAN": store_id]

                    def attributeBuilder = new JsonBuilder(xmlParams)
                    def attributeBody=attributeBuilder.toString()
                    request.body=attributeBody

                    IntegrationResponse     integrationResponse     = integrationService.call(request)

                    def confirmdata = integrationResponse.data

                    if(confirmdata.isSuccess){
                        shipping.pickupCode = confirmdata.codPickup
                        shipping.confirmed = true
	                    shipping.confirmedDate = new Date()
                        if(shipping instanceof DhlShipping)
                            shipping.originSvcArea = confirmdata.originSvcArea

                        save(shipping)
                    }else{
                        data.isSuccess = false
                        errorMessages=(shipping.shippingInternalCode + ": " +confirmdata.errorMessage)
                    }
                }catch(Exception e){
                    data.isSuccess = false
                    errorMessages=(shipping.shippingCode + ": Errore durante la prenotazione del ritiro.")
                    MPMlog.error "EXCEPTION THROWN BOOKING A PICKUP - SHIPPING ID ${shipping.id}: "+e+" MESSAGE: "+e.getMessage()
                }
            }
        }
        if(carrierShipping) {
            //ne caso in cui devo confermare una spedizione di gls la inserisco nella risposta
            data.glsCarrierShipping=carrierShipping

        }
        data.errorMessages = errorMessages

        return data
    }

    /**
     * Metodo per confermare una spedizione.
     * La spedizione viene confermata solo se è stampata e non confermata
     * @param shippings_id la lista degli id delle spedizioni
     * @param store_id l'id dello store per cui eseguire l'azione
     * @param force se true la conferma della spedizione avviene sempre anche se non risulta spedita
     * */
    def closeWorkDay(List<Long> shippings_id,  Long store_id, Boolean force = false){

        //def data = true 
        def data = [:]
        data.isSuccess = true
        def errorMessages = []
        List<CarrierShipping> listCarrierShipping=[]
        Shipping glsShipping = null;

        shippings_id.each{ idshipping->
            def shipping= this.get(idshipping)
            //aggiunto il controllo che la spedizione deve essere stampata per essere confermata

            if ((shipping && (shipping.printed && shipping.fileLabel != null) && !shipping.confirmed) || force) {

                if(!shipping.shipper?.enable) {
                    MPMlog.debug("Non confermo la spedizione ${shipping} perchè il corriere di riferimento non è abilitato")
                    data.isSuccess = false
                    errorMessages.add(shipping.shippingInternalCode + ": " +"Corriere non abilitato.")
                    return
                }

                if(shipping instanceof GlsShipping) {
                    glsShipping = shipping;
                    listCarrierShipping.add(parseShipping(shipping))
                }
                else if(shipping.useCarrierService()) { // poste, sda e brt
                    try{
                        Map xmlParams = getCarrierXmlParams(shipping)
                        IntegrationRequest request =IntegrationRequest.newInstance()

                        request.url=grailsApplication.config.getProperty('services.carrier.url',
                            "http://localhost:8080/carrier-service/")+"/shipping/pickup/"+store_id+""

                        request.method=IntegrationRequest.METHOD_POST
                        request.responseType=IntegrationRequest.RESPONSE_TYPE_JSON
                        request.headers = ["X-STORE-BAN": store_id]

                        def attributeBuilder = new JsonBuilder(xmlParams)
                        def attributeBody=attributeBuilder.toString()
                        request.body=attributeBody

                        IntegrationResponse     integrationResponse     = integrationService.call(request)

                        def confirmdata = integrationResponse.data

                        if(confirmdata.isSuccess){
                            shipping.pickupCode = confirmdata.codPickup
                            shipping.confirmed = true
	                        shipping.confirmedDate = new Date()
                            if(shipping instanceof DhlShipping)
                                shipping.originSvcArea = confirmdata.originSvcArea

                            save(shipping)
                        }else{
                            data.isSuccess = false
                            errorMessages.add(shipping.shippingInternalCode + ": " +confirmdata.errorMessage)
                        }
                    }catch(Exception e){
                        errorMessages.add(shipping.shippingCode + ": Errore durante la prenotazione del ritiro.")
                        MPMlog.error "EXCEPTION THROWN BOOKING A PICKUP - SHIPPING ID ${shipping.id}: "+e+" MESSAGE: "+e.getMessage()
                    }
                }
            }
            else {
                MPMlog.debug("SHIPPING ${shipping} NOT CONFIRMED!!!!")
            }

        }
        
        if (listCarrierShipping) {
            def glsdata = glsService.closeWorkDay(listCarrierShipping, store_id, glsShipping)
            //TODO : VEDERE COSA RISP DATA

            if(glsdata){
                listCarrierShipping.each{ carrierShipping->
                    def shipping= this.get(carrierShipping.shipping_id)
                    shipping.confirmed = true
	                shipping.confirmedDate = new Date()
                    save(shipping)
                }  
            }
            if(!glsdata) {
                data.isSuccess = false
                errorMessages.add("GLS: C'\u00E8 stato un errore nella conferma di alcune spedizioni, contattare la sede dei corrieri di riferimento per risolvere il problema!")
            }
        }

        data.errorMessages = errorMessages
        
        return data
    }

    CarrierShipping parseShipping(Shipping shipping){
    	def carrierShipping = new CarrierShipping()
    	carrierShipping.shipping_id		= shipping.id
    	carrierShipping.shippingCode	= shipping.shippingCode
        carrierShipping.actualWeight    = shipping.parcels.weight.sum()
        carrierShipping.totalpackages   = shipping.numberPackage
        if(shipping.hasProperty("productType")) carrierShipping.productType = shipping.productType.code
	    if(shipping.hasProperty("orderIdInNotes") && shipping.orderIdInNotes)
		    carrierShipping.notes       = shipping.orders[0].reference + " - " + shipping.notes.join("; ")
	    else
		    carrierShipping.notes       = shipping.notes.join("; ")
        carrierShipping.senderAddress   = parseAddress(shipping.senderAddress)
        carrierShipping.pickupAddress   = parseAddress(shipping.pickupAddress)
        carrierShipping.receiverAddress = parseAddress(shipping.receiverAddress)
		if(shipping.hasProperty("codCollectionMethod"))  carrierShipping.codCollectionMethod = shipping.codCollectionMethod
		if(shipping.hasProperty("supplementaryInsurance"))  carrierShipping.supplementaryInsurance = shipping.supplementaryInsurance
        carrierShipping.parcels = shipping.parcels.collect{parseParcel(it)}
        carrierShipping.codValue = shipping.codValue
        carrierShipping.insuredValue = shipping.insuredValue
        carrierShipping.phoneNotification = shipping.notificationPhone
        carrierShipping.afmiService = shipping.afmiService
        def phoneR = (shipping.receiverAddress.phoneNumber?:shipping.receiverAddress.customer?.phones.findAll{it != null}[0]?:shipping.receiverAddress.customer?.addresses?.phoneNumber.findAll{it != null}[0])
        carrierShipping.customerPhone = (phoneR?.startsWith("+39"))?phoneR?.drop(3):phoneR
        carrierShipping.outputType = shipping.outputType
        carrierShipping.recipientCountryCode = shipping.recipientCountryCode?:"IT"

        if(shipping instanceof GlsShipping) {
            carrierShipping.incoterm = (shipping as GlsShipping).incoterm ?: "0"
        }
        
        carrierShipping
    }

    CarrierAddress parseAddress(Address address){
        String name = address.name
        String surname = address.surname

        if(StringUtils.isBlank(address.name)) {
            name = address?.customer?.name
            surname = address?.customer?.surname
        }

        CarrierAddress carrierAddress = new CarrierAddress()
        carrierAddress.company     = address.company
        carrierAddress.name        = name
        carrierAddress.surname     = surname
        carrierAddress.address     = address.address
        carrierAddress.zipCode     = address.zipCode
        carrierAddress.city        = address.city
        carrierAddress.province    = address.province
        carrierAddress.country     = address.country
        carrierAddress.phoneNumber = address.phoneNumber
        carrierAddress.email       = address.email ?: address?.customer?.email

        carrierAddress
    }

    CarrierParcel parseParcel(Parcel parcel){
    	CarrierParcel carrierParcel = new CarrierParcel()
    	carrierParcel.id_parcel = parcel.id
    	carrierParcel.boxType = parcel.boxType?.value
    	carrierParcel.weight  = parcel.weight

    	carrierParcel
    }

    def getCarrierXmlParams(Shipping shipping) {
        def phoneS = shipping.senderAddress.phoneNumber
        phoneS = (phoneS?.startsWith("+39"))?phoneS?.drop(3):phoneS

        def phoneP = shipping.pickupAddress.phoneNumber
        phoneP = (phoneP?.startsWith("+39"))?phoneP?.drop(3):phoneP

        def phoneR = (shipping.receiverAddress.phoneNumber?:shipping.receiverAddress.customer?.phones.findAll{it != null}[0]?:shipping.receiverAddress.customer?.addresses?.phoneNumber.findAll{it != null}[0])
        phoneR = (phoneR?.startsWith("+39"))?phoneR?.drop(3):phoneR

		// comprende anche le spedizioni con poste
        if (shipping instanceof SdaShipping) {
            Map sdaXmlParams = [
                specialInstructions: (shipping?.orderIdInNotes ? shipping.orders[0].reference : "") + " " + shipping?.notes?.join(),
                codService:shipping.serviceType.value,
                outputType:shipping.outputType,
                addresses:[
                    [addressType:"S", addrline1:shipping.pickupAddress.address, postcode:shipping.pickupAddress.zipCode, phone1:phoneS, country: shipping.pickupAddress.country,
                        town: shipping.pickupAddress.city, province:shipping.pickupAddress.province, name:shipping.pickupAddress.company?:shipping.pickupAddress.store?.name, 
                        email:shipping.pickupAddress.email, fiscalCode: shipping.pickupAddress.vatNumber?:shipping.pickupAddress.cf,
                        contactname: shipping.pickupAddress.nomeCompleto?:shipping.pickupAddress.ragioneSociale?:shipping.pickupAddress.store?.name],

                    [addressType:"R", addrline1:StringUtility.removeNonAscii(shipping.receiverAddress.address), postcode:shipping.receiverAddress.zipCode, phone1:phoneR, country: shipping.receiverAddress.country,
                        town: StringUtility.removeNonAscii(shipping.receiverAddress.city), province:shipping.receiverAddress.province, name:StringUtility.removeNonAscii(shipping.receiverAddress.ragioneSociale?:shipping.receiverAddress.customer?.toString()), 
                        email:shipping.receiverAddress.email, fiscalCode: shipping.receiverAddress.vatNumber?:shipping.receiverAddress.cf],
                ],
                dimensions:shipping.parcels.collect{[weight:(it.weight*1000 as Long).toString().padLeft(8, "0"), 
                                                        width:it.width?((it.width*1000 as Long).toString().padLeft(8, "0")):null, 
                                                        height:it.height?((it.height*1000 as Long).toString().padLeft(8, "0")):null, 
                                                        depth:it.depth?((it.depth*1000 as Long).toString().padLeft(8, "0")):null, 
                                                        itemtype:it.boxType.value]},
                insuredValue: shipping.insuredValue,
                insuranceCommission:shipping.insuranceType,
                codValue:shipping.codValue,
                codCommission:shipping.codType,
                content: shipping.content,
                bulky: shipping.bulky?:false
            ]

            sdaXmlParams.collectiontrg = [
                priopntime: shipping.booking?.priopntime?.format('HH:mm'),
                priclotime: shipping.booking?.priclotime?.format('HH:mm'),
                secopntime: shipping.booking?.secopntime?.format('HH:mm'),
                secclotime: shipping.booking?.secclotime?.format('HH:mm'),
                pickupdate: shipping.booking?.pickupdate?.format('dd-MM-yyyy'),
                pickupinstr: shipping.booking?.pickupinstr,
            ]

            sdaXmlParams.collectionDate = shipping.booking?.pickupdate?.format('dd-MM-yyyy')

            sdaXmlParams.ldv = shipping.shippingCode

            // carrierType 5 va bene anche per poste perché sul carrier service il type 4 (poste) e 5 (sda) sono rimappati sullo stesso servizio
            if(shipping?.shipper?.type?.type == ShipperType.SDA) {
                sdaXmlParams.carrierType = 5
            }
            else if(shipping.shipper.type.type == ShipperType.POSTE_DELIVERY) {
                sdaXmlParams.carrierType = 9
            }
            else {
                sdaXmlParams.carrierType = 4
            }

            sdaXmlParams.virtualShipperType = shipping.shipper?.virtualShipperType

            return sdaXmlParams

        }
        else if(shipping instanceof BrtShipping) {
            Map brtXmlParams = [
                    customerReference: shipping.id,
                    alphanumericSenderReference: shipping.alphanumericSenderReference,
                    specialInstructions: shipping.notes.join(),
		            orderId: shipping.orders[0].reference,
                    codService:shipping.brtServiceType.value,
                    pricingConditionCode:shipping.pricingConditionCode,
                    outputType:shipping.outputType,
                    totalpackages:shipping.numberPackage,
                    totalVolume:shipping.volume?((shipping.volume*1000 as Long).toString().padLeft(8, "0")):null,
                    addresses:[
                            [addressType:"R", addrline1:shipping.receiverAddress.address, postcode:shipping.receiverAddress.zipCode, phone1:phoneR,
                             town: StringUtility.replaceAccentedLetters(shipping.receiverAddress.city), province:shipping.receiverAddress.province, 
                             name:shipping.receiverAddress.ragioneSociale?:shipping.receiverAddress.customer?.toString(),
                             email:shipping.receiverAddress.email, fiscalCode: shipping.receiverAddress.vatNumber?:shipping.receiverAddress.cf,
                             contactname:shipping.receiverAddress.nomeCompleto, country: shipping.recipientCountryCode],
                    ],
                    dimensions:shipping.parcels.collect{[weight:(it.weight*1000 as Long).toString().padLeft(8, "0"),
                                                         width:it.width?((it.width*1000 as Long).toString().padLeft(8, "0")):null,
                                                         height:it.height?((it.height*1000 as Long).toString().padLeft(8, "0")):null,
                                                         depth:it.depth?((it.depth*1000 as Long).toString().padLeft(8, "0")):null,
                                                         itemtype:it.boxType.value,
                                                         id: it?.id]},
                    insuredValue: shipping.insuredValue,
                    codValue:shipping.codValue,
                    codCommission:shipping.codType,
                    deliveryDateRequired:shipping.deliveryDateRequired?.format('yyyy-MM-dd'),
                    deliveryType:shipping.deliveryType,
                    departureDepot:shipping.departureDepot,
                    consigneeEMail: shipping?.consigneeEmail,
                    consigneeMobilePhoneNumber: shipping?.consigneeMobilePhoneNumber
            ]

            brtXmlParams.ldv = shipping.shippingCode

            brtXmlParams.carrierType = 7;

            brtXmlParams.virtualShipperType = shipping.shipper?.virtualShipperType

            return brtXmlParams

        }
        else if(shipping instanceof DhlShipping) {
            Map dhlXmlParams = [
                    customerReference: shipping.id,
                    //specialInstructions: shipping.notes.join(),
                    outputType:shipping.outputType,
                    totalpackages:shipping.numberPackage,
                    addresses:[
                            [addressType:"P", addrline1:shipping.pickupAddress.address, postcode:shipping.pickupAddress.zipCode, phone1:phoneP, country:shipping.pickupAddress.country,
                            town:shipping.pickupAddress.city, province:shipping.pickupAddress.province,
                            name:shipping.pickupAddress.nomeCompleto?:shipping.pickupAddress.ragioneSociale?:shipping.pickupAddress.store?.name,
                            email:shipping.pickupAddress.email, fiscalCode:shipping.pickupAddress.vatNumber?:shipping.pickupAddress.cf,
                            contactname:shipping.pickupAddress.nomeCompleto?:shipping.pickupAddress.ragioneSociale?:shipping.pickupAddress.store?.name],

                            [addressType:"R", addrline1:shipping.receiverAddress.address, postcode:shipping.receiverAddress.zipCode, phone1:phoneR, country:shipping.receiverAddress.country,
                            town:shipping.receiverAddress.city, province:shipping.receiverAddress.province, name:shipping.receiverAddress.ragioneSociale?:shipping.receiverAddress.customer?.toString(),
                            email:shipping.receiverAddress.email, fiscalCode:shipping.receiverAddress.vatNumber?:shipping.receiverAddress.cf, contactname:shipping.receiverAddress.customer?.toString()],

                            [addressType:"S", addrline1:shipping.senderAddress.address, postcode:shipping.senderAddress.zipCode, phone1:phoneS, country:shipping.senderAddress.country,
                            town:shipping.senderAddress.city, province:shipping.senderAddress.province, name:shipping.senderAddress.ragioneSociale?:shipping.pickupAddress.store?.name,
                            email:shipping.senderAddress.email, fiscalCode: shipping.senderAddress.vatNumber?:shipping.senderAddress.cf,
                            contactname:shipping.senderAddress.nomeCompleto?:shipping.senderAddress.ragioneSociale?:shipping.senderAddress.store?.name]
                    ],
                    dimensions:shipping.parcels.collect{[weight:(it.weight*1000 as Long).toString().padLeft(8, "0"),
                                                         width:it.width?((it.width*1000 as Long).toString().padLeft(8, "0")):null,
                                                         height:it.height?((it.height*1000 as Long).toString().padLeft(8, "0")):null,
                                                         depth:it.depth?((it.depth*1000 as Long).toString().padLeft(8, "0")):null,
                                                         itemtype:it.boxType.value]},
                    insuredValue: shipping.insuredValue,
                    codValue:shipping.codValue,
                    codCommission:shipping.codType,
                    content: shipping.content
            ]

            dhlXmlParams.collectiontrg = [
                    availabilitytime: shipping.dhlBooking?.availabilitytime?.format('HH:mm'),
                    secclotime: shipping.dhlBooking?.secclotime?.format('HH:mm'),
                    pickupdate: shipping.dhlBooking?.pickupdate?.format('yyyy-MM-dd'),
                    pickupinstr: shipping.dhlBooking?.pickupinstr,
                    packageLocation: shipping.dhlBooking?.packageLocation
            ]

            dhlXmlParams.ldv = shipping.shippingCode

            dhlXmlParams.carrierType = 8;

            dhlXmlParams.virtualShipperType = shipping.shipper?.virtualShipperType

            return dhlXmlParams
        }
        else if(shipping instanceof SpedisciOnlineShipping){
            def token = shipping.shipper.token
            def customerXml=Store.get(shipping.shipper.storeId)?.name?: " "
            def webhook = grailsLinkGenerator.link(absolute:true , uri:'/shipping/callback/' + token)
            //def webhook = "https://app.poleepo.cloud/paypership/webhook/test"

            Map ppsParams=[
                    customer:customerXml,
                    carrierType:ShipperType.CARRIER_ID_SPEDISCI_ONLINE,
                    referenceid: shipping.id,
                    shippingserviceid:shipping.ppsShippingServiceId,
                    id_external_service: shipping.ppsEmotionReference,
                    productType: shipping.ppsShipmentService,
                    carrierName:shipping.ppsCarrierName,
                    virtualShipperType: shipping.shipper?.virtualShipperType,
                    content: shipping.content,
                    addresses:[
                            [addressType:"shipFrom", name: shipping.pickupAddress.nomeCompleto, company: shipping.pickupAddress.company, addrline1: shipping.pickupAddress.address,
                            town: shipping.pickupAddress.city, province: shipping.pickupAddress.province, postcode: shipping.pickupAddress.zipCode, country: shipping.pickupAddress.country,
                            phone1: phoneP, email: shipping.pickupAddress.email],
                            [addressType:"shipTo", name: shipping.receiverAddress.nomeCompleto, company: shipping.receiverAddress.company, addrline1: shipping.receiverAddress.address,
                             town: shipping.receiverAddress.city, province: shipping.receiverAddress.province, postcode: shipping.receiverAddress.zipCode, country: shipping.receiverAddress.country,
                             phone1: phoneR, email: shipping.receiverAddress.email]
                    ],
                    dimensions:shipping.parcels.collect{[weight:(it.weight * 1000 as Long).toString().padLeft(8, "0"),
                                                         width:it.width ? (it.width * 1000 as Long).toString().padLeft(8, "0") : "0.01",
                                                         height:it.height ? (it.height * 1000 as Long).toString().padLeft(8, "0") : "0.01",
                                                         depth:it.depth ? (it.depth * 1000 as Long).toString().padLeft(8, "0") : "0.01"]},
                    insuredValue: shipping.insuredValue,
                    codValue:shipping.codValue,
                    outputType:shipping.outputType,
            ]

            return ppsParams
        }
        else if (shipping instanceof QaplaShipping) {
            Map qaplaMarams = [
                    carrierType: ShipperType.CARRIER_ID_QAPLA,
                    virtualShipperType: shipping.shipper?.virtualShipperType,
                    carrierName:shipping.carrierName,
                    shippingserviceid: shipping.carrierServiceName,
                    content: shipping.content,
                    addresses:[
                            [addressType:"R", addrline1:shipping.receiverAddress.address, postcode:shipping.receiverAddress.zipCode, phone1:phoneR, country:shipping.receiverAddress.country,
                             town:shipping.receiverAddress.city, province:shipping.receiverAddress.province, name:shipping.receiverAddress.ragioneSociale?:shipping.receiverAddress.customer?.toString(),
                             email:shipping.receiverAddress.email, fiscalCode:shipping.receiverAddress.vatNumber?:shipping.receiverAddress.cf, contactname:shipping.receiverAddress.customer?.toString()],
                    ],
                    dimensions:shipping.parcels.collect{[weight:(it.weight * 1000 as Long).toString().padLeft(8, "0"),
                                                         width:it.width ? (it.width * 1000 as Long).toString().padLeft(8, "0") : "0.01",
                                                         height:it.height ? (it.height * 1000 as Long).toString().padLeft(8, "0") : "0.01",
                                                         depth:it.depth ? (it.depth * 1000 as Long).toString().padLeft(8, "0") : "0.01"]},
                    items: shipping.items?.collect { item ->
                        return [
                                sku: item.orderRow.productOrder?.sku,
                                title: item.orderRow.description,
                                quantity: item.quantity?.toLong() ?: 0,
                                unitCost: item.orderRow.unitPriceTaxIncl?.setScale(2, RoundingMode.HALF_UP),
                                originCountry: "IT",
                                weight: item.orderRow.productOrder?.weight,
                                totalCost: item.orderRow.totalPrice?.setScale(2, RoundingMode.HALF_UP),
                                hsCode: null,
                        ]
                    },
                    insuredValue: shipping.insuredValue,
                    insuranceType: shipping.insuranceType,
                    codService: shipping.codType,
                    codValue:shipping.codValue,
                    orderReference: shipping.orders[0].reference,
                    referenceid: shipping.qaplaShippingId,
                    collectionDate: shipping.labelCreationDate,
                    deliveryType: shipping.deliveryType,
                    shippingCost: shipping.qaplaShippingCost,
            ]
            return qaplaMarams
        }
        else if (shipping instanceof FedexShipping) {
            // Costruisco la richiesta xml da inviare al backend

            MPMlog.debug("shipping service instanceof fedex")
            //TODO: verifica che tutti i parametri siano corretti
            Map fedexXmlParams = [
                    customerReference: shipping.id,
                    //specialInstructions: shipping.notes.join(),

                    outputType:shipping.outputType,
                    totalpackages:shipping.numberPackage,
                    actualWeight:(shipping.parcels.weight.sum()*1000 as Long).toString().padLeft(8, "0"),
                    totalpackages:shipping.numberPackage,

                    //specialInstructions: shipping.notes?.join()?:"",

                    productType:shipping.fedexProductType?.code?:"",
                    tntApplication:shipping.tntApplication,
                    addresses:[
                            [addressType:"S", addrline1:shipping.senderAddress.address, postcode:shipping.senderAddress.zipCode, phone1:phoneS?.take(3),phone2:phoneS?.drop(3),
                             town: shipping.senderAddress.city, province:shipping.senderAddress.province, name:shipping.senderAddress.ragioneSociale?:shipping.senderAddress.store?.name,
                             contactname: shipping.senderAddress.nomeCompleto?:shipping.senderAddress.ragioneSociale?:shipping.senderAddress.store?.name,
                             country: shipping.senderAddress.country],

                            [addressType:"C", addrline1:StringUtility.removeNonAscii(shipping.pickupAddress.address), postcode:shipping.pickupAddress.zipCode, phone1:phoneC?.take(3),phone2:phoneC?.drop(3),
                             town: StringUtility.removeNonAscii(shipping.pickupAddress.city), province:shipping.pickupAddress.province, name:StringUtility.removeNonAscii(shipping.pickupAddress.ragioneSociale?:shipping.pickupAddress.store?.name),
                             contactname: StringUtility.removeNonAscii(shipping.pickupAddress.nomeCompleto?:shipping.pickupAddress.ragioneSociale?:shipping.pickupAddress.store?.name),
                             country: shipping.pickupAddress.country],

                            [addressType:"R", addrline1:StringUtility.removeNonAscii(shipping.receiverAddress.address), postcode:shipping.receiverAddress.zipCode, phone1:phoneR?.take(3), phone2:phoneR?.drop(3),
                             town: StringUtility.removeNonAscii(shipping.receiverAddress.city), province:shipping.receiverAddress.province, name:StringUtility.removeNonAscii(shipping.receiverAddress.ragioneSociale?:shipping.receiverAddress.customer?.toString()),
                             contactname: StringUtility.removeNonAscii(shipping.receiverAddress.nomeCompleto?:shipping.receiverAddress.ragioneSociale?:shipping.receiverAddress.store?.name),
                             country: shipping.receiverAddress.country]
                    ],
                    dimensions:shipping.parcels.collect{[
                            weight:(it.weight*1000 as Long).toString().padLeft(8, "0"),
                            width:it.width?((it.width*1000 as Long).toString().padLeft(6, "0")):null,
                            height:it.height?((it.height*1000 as Long).toString().padLeft(6, "0")):null,
                            depth:it.depth?((it.depth*1000 as Long).toString().padLeft(6, "0")):null,
                            itemtype:it.boxType.value
                    ]},
                    insuredValue: shipping.insuredValue,
                    codValue:shipping.codValue,
                    customerReference: shipping.orders?.reference?.join("; "),
                    insuranceCommission:shipping.insuranceCommission,
                    codValue:shipping.codValue,
                    codCommission:shipping.codCommission

                    //TODO: verifica se devi includere anche questi parametri (non credo)
                    //codCommission:shipping.codType,
                    //content: shipping.content
            ]

            fedexXmlParams.collectiontrg = [
                    priopntime: shipping.fedexBooking?.priopntime?.format('HHmm'),
                    priclotime: shipping.fedexBooking?.priclotime?.format('HHmm'),
                    secopntime: shipping.fedexBooking?.secopntime?.format('HHmm'),
                    secclotime: shipping.fedexBooking?.secclotime?.format('HHmm'),
                    availabilitytime: shipping.fedexBooking?.availabilitytime?.format('HHmm'),
                    pickupdate: shipping.fedexBooking?.pickupdate?.format('dd.MM.yyyy'),
                    pickuptime:shipping.fedexBooking?.pickuptime?.format('HHmm'),
                    pickupinstr: shipping.fedexBooking?.pickupinstr

            ]

            fedexXmlParams.ldv = shipping.shippingCode

            fedexXmlParams.carrierType = 30;

            fedexXmlParams.virtualShipperType = shipping.shipper?.virtualShipperType

            return fedexXmlParams
        }
        else {
            return [
                    outputType: shipping.outputType,
                    carrierType: shipping.shipper.type.numType,
                    referenceid: shipping.id,
                    internalCode: shipping.shippingInternalCode,
                    orderReference: shipping.orders[0].reference,
                    virtualShipperType: shipping.shipper?.virtualShipperType,
                    orderDate: shipping.orders[0].creationDate.format("dd/MM/yyyy'T'HH:mm:ss"),
                    specialInstructions: shipping.notes.join(),
                    addresses: [
                            [
                                    addressType: "P",
                                    label: shipping.pickupAddress.label,
                                    addrline1: shipping.pickupAddress.address,
                                    postcode: shipping.pickupAddress.zipCode,
                                    phone1: phoneS,
                                    country: shipping.pickupAddress.country,
                                    town: shipping.pickupAddress.city,
                                    province: shipping.pickupAddress.province,
                                    name: shipping.pickupAddress.company ?: shipping.pickupAddress.store?.name,
                                    email: shipping.pickupAddress.email,
                                    fiscalCode: shipping.pickupAddress.vatNumber ?: shipping.pickupAddress.cf,
                                    contactname: shipping.pickupAddress.nomeCompleto ?: shipping.pickupAddress.ragioneSociale ?: shipping.pickupAddress.store?.name
                            ],
                            [
                                    addressType: "S",
                                    addrline1: shipping.shipper.store.legalAddress.address,
                                    postcode: shipping.shipper.store.legalAddress.zipCode,
                                    country: shipping.shipper.store.legalAddress.country,
                                    town: shipping.shipper.store.legalAddress.city,
                                    province: shipping.shipper.store.legalAddress.province,
                                    name: shipping.shipper.store.displayName ?: shipping.shipper.store.name,
                            ],
                            [
                                    addressType:"R",
                                    addrline1: shipping.receiverAddress.address,
                                    postcode: shipping.receiverAddress.zipCode,
                                    phone1:phoneR,
                                    country: shipping.receiverAddress.country,
                                    town: StringUtility.removeNonAscii(shipping.receiverAddress.city),
                                    province: shipping.receiverAddress.province,
                                    name: StringUtility.removeNonAscii(shipping.receiverAddress.ragioneSociale?:shipping.receiverAddress.customer?.toString()),
                                    email:shipping.receiverAddress.email,
                                    fiscalCode: shipping.receiverAddress.vatNumber?:shipping.receiverAddress.cf
                            ],
                    ],
                    dimensions:shipping.parcels.collect{[
                            id: it.id,
                            weight: (it.weight as Long).toString(),
                            width: it.width ? ((it.width as Long).toString()) : "0",
                            height: it.height ? ((it.height as Long).toString()) : "0",
                            depth: it.depth ? ((it.depth as Long).toString()) : "0",
                            itemtype: it.boxType.value,
                            marker: it.marker,
                    ]},
                    insuredValue: shipping.insuredValue,
                    codValue: shipping.codValue,
            ]
        }
    }

    def bookPickup(def booking, Long store_id) {
        def data = [:]
        if(booking instanceof GlsBooking) {
            data = glsService.addPickup(booking, store_id)
        }
        return data
    }

    def deletePickup(def booking, Long store_id) {
        def data = [:]
        if(booking instanceof GlsBooking) {
            data = glsService.deletePickup(booking, store_id)
        }
        return data
    }

    def getCountryCodeFromSource(Long source) {
        def countryCode = "IT"
        switch(source) {
            case SourceMP.SOURCE_MANOMANO_DE:
            case SourceMP.SOURCE_AMAZON_DE:
            case SourceMP.SOURCE_EBAY_DE:
                countryCode = "DE"
                break

            case SourceMP.SOURCE_MANOMANO_ES:
            case SourceMP.SOURCE_AMAZON_ES:
            case SourceMP.SOURCE_EBAY_ES:
                countryCode = "ES"
                break

            case SourceMP.SOURCE_MANOMANO_FR:
            case SourceMP.SOURCE_AMAZON_FR:
            case SourceMP.SOURCE_LEROY_MERLIN_FR:
            case SourceMP.SOURCE_EBAY_FR:
                countryCode = "FR"
                break

            case SourceMP.SOURCE_MANOMANO_GB:
            case SourceMP.SOURCE_AMAZON_GB:
                countryCode = "GB"
                break

            case SourceMP.SOURCE_MANOMANO_BE:
            case SourceMP.SOURCE_AMAZON_BE:
                countryCode = "BE"
                break

            case SourceMP.SOURCE_AMAZON_PL:
            case SourceMP.SOURCE_EBAY_PL:
                countryCode = "PL"
                break

            case SourceMP.SOURCE_AMAZON_NL:
            case SourceMP.SOURCE_EBAY_NL:
                countryCode = "NL"
                break

            case SourceMP.SOURCE_AMAZON_SE:
                countryCode = "SE"
                break

            case SourceMP.SOURCE_EBAY_CH:
                countryCode = "CH"
                break

            case SourceMP.SOURCE_EBAY_AT:
                countryCode = "AT"
                break
        }
        return countryCode
    }
    def printShippingLabel(shipping, storeId, isBulk = true)
    {
        def result=[:]
        result.isSuccess=Boolean.TRUE
        def message=""
        if(shipping.shipper.store.id != storeId){

            mesage= "Sembra che tu non abbia i permessi per questa operazione. Prova a rieseguire il login."
            result.isSuccess=Boolean.FALSE
            result.message= message
            return result
        }

        if(!shipping.shipper.enable) {

            message= "Corriere non abilitato."
            result.isSuccess=Boolean.FALSE
            result.message= message
            return result
        }

        /* verifico se la spedizione è modificabile */
        if(!shipping.editable) {

            message= "Non puoi stampare l'etichetta per questa spedizione. La spedizione non è più modificabile perchè è stata già inviata al fornitore."
            result.isSuccess=Boolean.FALSE
            result.message= message
            return result
        }

        def fileLabel
        try{
            result = printLabel(shipping, storeId, isBulk)

            MPMlog.debug("PRINT CARRIER LDV:"+result.isSuccess)

            if(result.isSuccess) {
                fileLabel = result.fileLabel
            }else {

                message= "Il corriere ha restituito il seguente messaggio: ${result.errorMessage}"
                result.message= message
                return result
            }
        }catch(Exception e){
            e.printStackTrace()

            message= "Si \u00E8 verificato un errore durante la comunicazione con il corriere. Contatta l'assistenza tecnica per maggiori informazioni."
            result.isSuccess=Boolean.FALSE
            result.message= message
            return result
        }

        if(fileLabel != null) {
            //salvo il file nella directory definita in application.groovy (shipping.filelabelpath)
            def datenow = new Date()
            def path = "/" + shipping.shipper.store.id + "/" + datenow[Calendar.MONTH] + "/" + datenow[Calendar.YEAR] + "/" + shipping.shipper.name + "/"
            def pathtoSave = grailsApplication.config.shipping.filelabelpath + path

            def dir = new File(pathtoSave)
            if (!dir.exists()) {

                dir.mkdirs()
            }
            if (shipping.fileLabel && shipping.fileLabel != '') {
                def filedelete = new File(dir, shipping.fileLabel)
                if (filedelete.exists()) {
                    filedelete.delete()
                }
            }
            def order = Order.createCriteria().get() {
                shippings {
                    eq("id", shipping.id)
                }
            }
            def fileName = order.reference ?: shipping.id
//shipping.shippingCode ?: shipping.id metto order.reference nel nome del file caso per ordini con stesso indirizzo (GLS)
            // altrimenti perdo etichetta stesso tracking ma numerata diveramente
            def fileExt = ".pdf"
            if (shipping.outputType == "ZPL") //per BRT si possono stampare etichette nel formato ZPL
                fileExt = ".zpl"
            if (shipping.outputType == "PNG") //per AMAZON si possono stampare etichette nel formato PNG
                fileExt = ".png"
            //per nome directory idstore+nomeshipper(tnt,gls,etc..)+anno-mese
            def dir2 = new File(grailsApplication.config.shipping.filelabelpath)
            def savefile = new File(dir2, path + "Label" + fileName + fileExt)
            savefile.withOutputStream {
                it.write(fileLabel)
            }
            shipping.fileLabel = path + "Label" + fileName + fileExt
        }

        shipping.printed = true
        save(shipping)
        MPMlog.debug("FINISH PRINT CARRIER LABEL:"+result.isSuccess)
        return result
    }

    public Map parseParamsForShipping(params,id)
    {

        def order=Order.get(id)
        def map=[:]
        map.order=id
        map.shipper = params.shipper
        map.notes=params.notes
        map.outputType=params.outputType
        map.index=params.get('index'+id)
        map.brtServiceType=params.brtServiceType //per brt
        map.serviceType=params.serviceType //per sda
        map.pricingConditionCode=params.pricingConditionCode
        map.deliveryType=params['deliveryType'+id]
        map.departureDepot=params.departureDepot
        map.notes=params.notes
        map.content=params['content'+id] // per DHL e spedisci online
        map.numberPackage=params['numberPackage'+id]
        map.recipientCountryCode=params['recipientCountryCode'+id]
        map.forceShipping="Y"
        map.productType=params.productType
        map.carrierName = params.get("carrierName")
        map.carrierServiceName = params.get("carrierServiceName")
        map.volume=params['volume'+id]
        map.alphanumericSenderReference=params['alphanumericSenderReference'+id]
        map.notificationPhone=params.getBoolean('notificationPhone')
        map.afmiService=params.getBoolean('afmiService')

        map.deliveryType = params.get("deliveryOption" + id)

        // SpedisciOnline
        map.ppsCarrierName = params.get("ppsCarrierName")
        map.ppsShippingServiceId = params.get("ppsShippingServiceId")
        map.ppsShipmentService = params.get("ppsShipmentService")
        map.pickupAddress = params.get("pickupAddress")

        for(String s:params.getList('index'+id))
        {

            map['parcel.weight'+s]=params.get('parcel.weight'+id+'_'+s)
            map['parcel.height'+s]=params.get('parcel.height'+id+'_'+s)
            map['parcel.width'+s]=params.get('parcel.width'+id+'_'+s)
            map['parcel.depth'+s]=params.get('parcel.depth'+id+'_'+s)
            map['parcel.boxType'+s]=params.get('boxType'+id+'_'+s)


        }
        /*
         creo la mappa delle righe dell'ordine
        Map orderRows = [:]
        params.list("orderRowId").collect{it -> Long.valueOf(it)}.each{ Long id ->
            Boolean select = params.getBoolean("orderRowSelected${id}")
            String total = params."quantityTotal${id}" ?: "0"
            String qnt = params."quantity${id}" ?: "0"
            orderRows.put(id, [id: id, select: select, total: new BigDecimal(total.replace(",", ".")), quantity: new BigDecimal(qnt.replace(",", "."))])
        }
         */
        map.orderRowId=new ArrayList<Long>()
        order.rows.each{row->
            map.orderRowId.add(row.id)
            map['orderRowSelected'+row.id.toString()]=true
            map['quantityTotal'+row.id.toString()]=row.getQuantity()?.toString()?:"0"
            map['quantity'+row.id.toString()]=row.getQuantity()?.toString()?:"0"


        }
        //per brt
        map.consigneeEmail = params.consigneeEmail
        map.consigneeMobilePhoneNumber = params.consigneeMobilePhoneNumber
	    map.orderIdInNotes = params.orderIdInNotes
        //per sda
        map.priopntime=params.priopntime
        map.priclotime=params.priclotime
        map.secopntime=params.secopntime
        map.secclotime=params.secclotime
        if(params.shipper.type.type == ShipperType.GLS || params.shipper.type.type == ShipperType.GLS_2) {
            //prelevo la configurazione di gls dello store per recuperare da userconfiguration se
            //il flag phoneInNotes è abilitato o meno per la stampa dell'etichetta
            map.phoneInNotes=(params.shipper as GlsShipper).userConfiguration.phoneInNotes?'true':'false'
        }
        //contrassegno
        def codValue = params['codValue'+id]
        if(codValue) {
//            println "OK"
            map.codValue = codValue
            def codType = ""
            //il codType lo valorizzo sempre come pagamento in contanti
//            switch(params.shipper){
            switch(params.shipper.type.type){
                case ShipperType.BRT:
                case ShipperType.DHL:
                    codType="0"
                    break;
                case ShipperType.GLS:
                case ShipperType.GLS_2:
                    codType="CONT"
                    break;
                case ShipperType.SDA:
                case ShipperType.POSTE:
                    codType="CON"
                    break;
                case ShipperType.TNT:
                    codType="S"
                    break;
            }
            map.codType = codType
        }

//        println map.codType
        return map
    }

    /**
     * Metodo per eseguire un operazione massiva sulle spedizioni di una lista di ordini
     * @param storeId l'id dello store
     * @param userId l'utente che esegue l'operazione
     * @param features i parametri di esecuzione
     * @return
     */
    def bulkShippingsOperations(Long storeId, Long userId, MassiveShippingActionParams features) {
        String tid = MPMlog.getTID()

        // recupero il virtual shipper type
        def virtualShipperType = features.params.shipper

        // recupero il codice del corriere
        String shipperCode = null
        Shipper shipper = null
        if(features.shippingAction?.createLdv) {
            shipper = Shipper.createCriteria().get {
                store {
                    eq('id', storeId)
                }
                eq('virtualShipperType', virtualShipperType as Integer)
            } as Shipper

            shipperCode = shipper?.code
        }

        // setto lo shipper nei params
        features.params.shipper = shipper

        // recupero la lista degli ordini
        def orders = features.orderIds

        /**
         * Stampo le lettere di vettura degli ordini creando le spedizioni
         * in maniera massiva
         */
        MPMlog.debug("START MASSIVE OPERATION SHIPPINGS..." + features)

        // calcolo tutte le tipologie LDV che ci sono per gli ordini selezionati
        List<String> labelOutputTypes
        if(features.shippingAction == MassiveShippingActionParams.Action.SHIPPING_BULK_REPRINT) {
            labelOutputTypes = Order.createCriteria().list {
                createAlias('shippings', 's', JoinType.LEFT_OUTER_JOIN.getJoinTypeValue())

                projections {
                    distinct "s.outputType"
                }

                "in"("id", orders)
            } as List<String>
        }
        else {
            labelOutputTypes = [features.params.outputType]
        }

        // semplico gli outputType
        labelOutputTypes = labelOutputTypes.collect {it == "A4" || it == "A6" ? "PDF" : it}.unique()

        // verifico se genererò un file zippato
        boolean isZipFile = false
        if(features.documentType == MassiveShippingActionParams.DocType.UNIQUE) {
            // se le tipologie di output delle etichette sono molteplici
            if(labelOutputTypes.size() > 1) {
                isZipFile = true
            }
            else if(!labelOutputTypes.contains("PDF") && (features.printListPrel || features.printOrder)) {
                isZipFile = true
            }
        }
        else {
            isZipFile = true
        }

        // calcolo l'estensione del file
        def extension
        def mime
        if(isZipFile) {
            extension = ".zip"
            mime="application/zip"
        }
        else {
            if(labelOutputTypes.contains("PDF")) {
                extension = ".pdf"
                mime="application/pdf"
            }
            else if (labelOutputTypes.contains("ZPL")) {
                extension = ".zpl"
                mime="application/zpl"
            }
            else {
                throw new Exception("Il formato di file ${labelOutputTypes} non è riconosciuto")
            }
        }

        /* creo il digest di esecuzione */
        Long digestId
        if(shipper != null) {
            digestId = massiveDigestService.create(storeId, userId, MassiveDigestType.CHANGE_ORDER_STATUS_AND_LDV, orders, null, shipperCode, shipper.virtualShipperType, shipper.title ?: null)
        }
        else {
            digestId = massiveDigestService.create(storeId, userId, MassiveDigestType.CHANGE_ORDER_STATUS_AND_LDV, orders, null, shipperCode, null, null)
        }
        def finalFile = massiveDigestService.createResultFile(digestId, mime, extension)

        // recupero il file unico PDF
        File uniquePdfFile = null
        if(features.documentType == MassiveShippingActionParams.DocType.UNIQUE) {
            if(mime == "application/pdf") {
                uniquePdfFile = finalFile
            }
            else {
                uniquePdfFile = File.createTempFile(tid + "-uniquePdf", ".pdf")
                uniquePdfFile.deleteOnExit()
            }
        }

        // recupero il file unico ZEBRA
        File uniqueZplFile = null
        if(features.documentType == MassiveShippingActionParams.DocType.UNIQUE) {
            if(mime == "application/zpl") {
                uniqueZplFile = finalFile
            }
            else {
                uniqueZplFile = File.createTempFile(tid + "-uniqueZebra", ".zpl")
                uniqueZplFile.deleteOnExit()
            }
        }

        /* inizializzo i parametri di esecuzione del thread-pool */
        Integer numThread = grailsApplication.config["bulkShippingsOperations.numberThread"] as Integer
        if(numThread < 0) {
            numThread = Runtime.getRuntime().availableProcessors()
        }
        Integer threadPoolTimeOut = grailsApplication.config["bulkShippingsOperations.threadPoolTimeOutInSeconds"] as Integer

        /* setto il digest in esecuzione */
        massiveDigestService.updateStatus(digestId, DigestStatus.WORKING)

        /* creo il thred-pool */
        ExecutorService threadPool = Executors.newFixedThreadPool(numThread)
        List<Future<MassiveShippingResult>> futures = orders.collect { Long id ->
            threadPool.submit({ ->
                MPMlog.setTID(tid + "-" + Thread.currentThread().getId())

                Boolean success = true
                massiveDigestService.updateEntity(digestId, id, MassiveDigestEntityStatus.WORKING)

                MassiveShippingResult shippingResults = []
                Long generatedShippingId = null
                Order.withNewTransaction { TransactionStatus status ->
                    def order = Order.get(id)

                    // verifico se ha senso lavorare l'ordine
                    Long state = order.currentState?.state
                    if(order == null || state == OrderState.CANCELLED_BY_CUSTOMER || state == OrderState.CANCELLED_BY_SELLER || state == OrderState.REFUND) {
                        massiveDigestService.updateEntity(digestId, id, MassiveDigestEntityStatus.SKIPPED, "L'ordine è stato saltato perchè si trova nello stato annullato o ritirato.")
                        success = false
                        return null
                    }

                    // se devo stampare le LDV
                    if (features.printLdv) {
                        List<Shipping> shippings = []

                        // se l'action mi impone di stampare le etichette
                        MPMlog.debug("Inizia la generazione delle spedizione per l'ordine ${id}")
                        if(features.shippingAction.createLdv) {
                            MPMlog.debug("Creo una nuova spedizione per l'ordine con id: ${id} con il corriere ${shipperCode}")

                            try {
                                // formo i parametri per creare la spedizione
                                def paramsShipping = parseParamsForShipping(features.params, id)

                                // verifico se la spedizione esiste già
                                Shipping shipping = order.shippings?.find {
                                    it.shipper.virtualShipperType == shipper.virtualShipperType
                                }

                                // se la spedizione non esiste, la creo
                                if (shipping == null) {
                                    // creo la spedizione e l'etichetta
                                    def result = create(paramsShipping, storeId)
                                    if (result.isSuccess == Boolean.TRUE) {
                                        shipping = result.shipping as Shipping
                                    }
                                    else if (result.isSuccess == Boolean.FALSE) {
                                        MPMlog.error("Errore durante la creazione della spedizione massiva: " + result.message)
                                        status.setRollbackOnly()
                                        massiveDigestService.updateEntity(digestId, id, MassiveDigestEntityStatus.ERROR, "Non è stato possibile creare la spedizione per il seguente errore:" + result.message)
                                        success = false
                                        return null
                                    }
                                }

                                // aggiungo la spedizione a quelle create
                                generatedShippingId = shipping.id
                                shippings.add(shipping)
                            }
                            catch (Exception ex) {
                                MPMlog.error("Errore durante la creazione della spedizione massiva", ex)
                                status.setRollbackOnly()
                                massiveDigestService.updateEntity(digestId, id, MassiveDigestEntityStatus.ERROR, "Non è stato possibile creare la spedizione per il seguente errore:" + ex.message)
                                success = false
                                return null
                            }
                        }
                        // se non devo stampare le etichette, torno tutte le spedizioni
                        else {
                            MPMlog.debug("Non creo una nuova spedizione per l'ordine con id: ${id}")
                            shippings.addAll(order.shippings)
                        }


                        MPMlog.debug("Inizia la stampa delle spedizioni per ottenere le lettere di vettura")
                        if(features.shippingAction.createLdv) {
                            try {
                                // stampo ogni spedizione non stampata
                                for(Shipping shipping : shippings) {
                                    // se la spedizione non è stampata, la stampo
                                    if(!shipping.printed) {
                                        // stampo l'etichetta
                                        def result = printShippingLabel(shipping, storeId)

                                        // se la stampa è andata male, esco con errore
                                        if (result.isSuccess == Boolean.FALSE) {
                                            MPMlog.info("La stampa dell'etichetta non è andata a buon fine: " + result.message)
                                            status.setRollbackOnly()
                                            massiveDigestService.updateEntity(digestId, id, MassiveDigestEntityStatus.ERROR, result.message as String)
                                            success = false
                                            return null
                                        }
                                    }
                                }
                            }
                            catch (Exception ex) {
                                MPMlog.info("Eccezione durante la stampa dell'etichetta:" + ex.message)
                                status.setRollbackOnly()
                                massiveDigestService.updateEntity(digestId, id, MassiveDigestEntityStatus.ERROR, ex.message)
                                success = false
                                return null
                            }
                        }
                        MPMlog.debug("Fine della stampa delle etichette")

                        // preparo il dato di uscita
                        shippingResults = new MassiveShippingResult([
                                orderId: id,
                                orderReference: order.reference,
                                shippings: shippings.collect {it -> new MassiveShippingResult.ShippingInfo([
                                        fileLabelPath: grailsApplication.config["shipping.filelabelpath"] + it.fileLabel,
                                        ldvOutputType: it.outputType == 'A4' || it.outputType == 'A6' ? "PDF" : it.outputType
                                ])}
                        ])

                        // se è stato richiesto un unico file
//                        if (features.documentType == MassiveShippingActionParams.DocType.UNIQUE) {
//                            for(Shipping shipping : shippings) {
//
//                            }
//
//                            if ((features.params.outputType == "PDF"  || features.params.outputType == "A4" || features.params.outputType == "A6" ||  fileIsZPL==false) && shipping.printed) {
//                                def pathtoLoad = grailsApplication.config.shipping.filelabelpath
//                                MPMlog.debug("PRINT UNIQUE DOCUMENT:" + shipping.fileLabel)
//                                File input = new File(pathtoLoad + shipping.fileLabel)
//                                if (input.exists()) {
//                                    MPMlog.debug("FILE EXIXTS PDF")
//                                    //ByteArrayOutputStream stream = new ByteArrayOutputStream()
//                                    //FileUtils.copyFile(input, stream)
//                                    //bos = mergeDocument(stream, bos)
//                                    ByteArrayOutputStream stream = new ByteArrayOutputStream()
//                                    ByteArrayOutputStream stream2 = new ByteArrayOutputStream()
//
//
//                                    if (features.printOrder == "yes") {
//
//                                        def map = [:]
//                                        map.orderIds = order.id.toString()
//                                        def orderStream = orderService.printOrder(map)
//                                        stream2 = orderStream
//                                    }
//                                    //FileUtils.copyFile(input, stream2)
//
//
//                                    def namefile =  order.reference + ".pdf"
//                                    byte[] bytes = FileUtils.readFileToByteArray(input);
//                                    ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);
//                                    baos.write(bytes, 0, bytes.length);
//                                    stream=baos
//                                    stream=mergeStream( stream,stream2)
//
//                                    resultArray.add([name: namefile, stream: stream,id:order.id.toString()])
//
//                                }
//                            } else if ((features.params.outputType == "ZPL" || fileIsZPL==true) && shipping.printed) {
//                                def pathtoLoad = grailsApplication.config.shipping.filelabelpath
//                                File input = new File(pathtoLoad + shipping.fileLabel)
//                                if (input.exists()) {
//                                    MPMlog.debug("FILE EXIXTS ZPL")
//                                    ByteArrayOutputStream stream = new ByteArrayOutputStream()
//                                    FileUtils.copyFile(input, stream)
//                                    def namefile =  order.reference + ".zpl"
//                                    resultArray.add([name: namefile, stream: stream,id:order.id.toString()])
//                                }
//
//                            }
//
//                        }
//                        else if (features.documentType == "zip") {
//                            def pathtoLoad = grailsApplication.config.shipping.filelabelpath
//                            File input = new File(pathtoLoad + shipping.fileLabel)
//                            def extention
//                            if(features.params.outputType == "PDF" || fileIsZPL==false)
//                                extention=".pdf"
//                            if(features.params.outputType == "ZPL" || fileIsZPL==true)
//                                extention=".zpl"
//                            def namefile =  order.reference + extention
//                            if (input.exists()) {
//                                if(features.params.outputType == "ZPL"|| fileIsZPL==true) {
//                                    ByteArrayOutputStream stream = new ByteArrayOutputStream()
//                                    FileUtils.copyFile(input, stream)
//                                    resultArray.add([name: namefile, stream: stream,id:order.id.toString()])
//
//                                }
//                                if(features.params.outputType == "PDF"|| features.params.outputType == "A4" || features.params.outputType == "A6" || fileIsZPL==false)
//                                {
//                                    ByteArrayOutputStream stream = new ByteArrayOutputStream()
//                                    ByteArrayOutputStream stream2 = new ByteArrayOutputStream()
//
//
//                                    if (features.printOrder == "yes") {
//                                        def map = [:]
//                                        map.orderIds = order.id.toString()
//                                        def orderStream = orderService.printOrder(map)
//                                        stream = orderStream
//                                    }
//                                    FileUtils.copyFile(input, stream2)
//                                    stream = mergeStream(stream2, stream)
//                                    def namefilezip =  order.reference + ".pdf"
//                                    resultArray.add ([name: namefilezip, stream: stream, id:order.id.toString()])
//
//                                }
//                            }
//                        }
                    }
                }

                MPMlog.debug("La stampa delle spedizione è finita correttamente, inizio la gestione dell'ordine")
                if (features.changeState && success) {
                    Order.withNewTransaction { TransactionStatus status ->
                        Order order = Order.get(id)

                        // recupero lo stato dell'ordine da settare
                        Long state_id = Long.parseLong(features.params.eventMPM as String)
                        OrderState selectedState = OrderState.get(state_id)

                        // recupero lo stato dell'ordine del marketplace
                        def mporders = selectedState?.mpOrderStates?.findAll { it?.source == order.source }?.sort {it?.priority}
                        def mpState_id = mporders[0]?.id

                        // cambio lo stato dell'ordine
                        MPMlog.debug("Cambio lo stato dell'ordine ${id} nel nuovo stato '${selectedState.name}'")
                        try{
                            orderService.updateOrderStateWithoutMachineRole(order, state_id, mpState_id)
                        }
                        catch(Exception ex) {
                            status.setRollbackOnly()
                            MPMlog.error("Errore durante l'aggiornamento dello stato dell'ordine sulla stampa massiva", ex)
                            massiveDigestService.updateEntity(digestId, id, MassiveDigestEntityStatus.ERROR, "Errore durante l'aggiornamento dello stato dell'ordine")
                        }
                        MPMlog.info("Finito di aggiornare lo stato dell'ordine per l'ordine ${id}")
                    }
                }

                // passo a confermare le spedizioni
                MPMlog.debug("Inizia la conferma dell'etichetta della spedizione")
                if(features.confirmShipping && success && (shipperCode!="TNT" && shipperCode!="BRT")) {
                    MPMlog.debug("Mi è stato chiesto di confermare l'etichetta e l'operazione è supportata")
                    Shipping.withNewTransaction { TransactionStatus status ->
                        // recupero ordine
                        Order order = Order.get(id)

                        // recupero la spedizione
                        def shipping = order.shippings?.find {it.id == generatedShippingId}
                        MPMlog.debug("Shipping: ${shipping} - ${shipping?.printed} - ${shipperCode}")

                        // se la spedizione è correttamente stampata, passo alla conferma
                        if(shipping && shipping?.printed) {
                            try{
                                // confermo la spedizione
                                def result = closeWorkDay([shipping.id], storeId)
                                if(result.isSuccess == Boolean.FALSE) {
                                    MPMlog.info("La conferma della spedizione non è andata a buon fine: ${result.errorMessages}")
                                    status.setRollbackOnly()
                                }
                                else {
                                    MPMlog.debug("Spedizione confermata correttamente.")
                                }
                            }
                            catch(Exception ex) {
                                MPMlog.error("Errore durante la conferma della spedizione", ex)
                                status.setRollbackOnly()
                            }
                        }
                    }
                }

                if(success) {
                    MPMlog.debug("Operazione conclusa con successo per l'ordine ${id}")
                    massiveDigestService.updateEntity(digestId, id, MassiveDigestEntityStatus.SUCCESS)
                    return shippingResults
                }
                return null
            } as Callable<MassiveShippingResult>)
        }

        // aspetto la fine del thread-pool
        ExecutorUtility.shutdownAndAwaitTermination(threadPool, threadPoolTimeOut)

        final zipFile = isZipFile

        // se l'utente ha richiesta la stampa della lista di prelievo degli ordini
        if(features.printListPrel) {
            ByteArrayOutputStream baos = orderService.printOrderPickupList(storeId, orders)

            if(zipFile && features.documentType != MassiveShippingActionParams.DocType.UNIQUE) {
                FileUtility.addFileToZip(baos.toByteArray(), "ListaPrelievo.pdf", finalFile)
            }
            else {
                FileUtility.appendPdfToFile(baos.toByteArray(), uniquePdfFile)
            }
        }

        def filesArray = []
        futures.each { Future<MassiveShippingResult> future ->
            try {
                // recupero il valore prodotto dal thread
                MassiveShippingResult result = future.get()
                if(result != null) {
                    MPMlog.debug("isZipFile: ${zipFile}")

                    // recupero il file PDF su cui scrivere
                    File pdfForOrder = uniquePdfFile
                    if (zipFile && features.documentType != MassiveShippingActionParams.DocType.UNIQUE) {
                        pdfForOrder = File.createTempFile(tid + "-orderDetail-${result.orderId}", ".pdf")
                        pdfForOrder.deleteOnExit()
                    }

                    // recupero il file PDF su cui scrivere
                    File zplForOrder = uniqueZplFile
                    if (zipFile && features.documentType != MassiveShippingActionParams.DocType.UNIQUE) {
                        zplForOrder = File.createTempFile(tid + "-orderDetail-${result.orderId}", ".zpl")
                        zplForOrder.deleteOnExit()
                    }

                    // aggiungo il dettaglio dell'ordine
                    if (features.printOrder) {
                        ByteArrayOutputStream baos = orderService.printOrderDetails(storeId, [result.orderId])
                        FileUtility.appendPdfToFile(baos.toByteArray(), pdfForOrder)
                    }

                    // ciclo le spedizioni ottenute
                    for (MassiveShippingResult.ShippingInfo shippingInfo : result.shippings) {
                        if (shippingInfo.ldvOutputType == "PDF") {
                            FileUtility.appendPdfToFile(new File(shippingInfo.fileLabelPath), pdfForOrder)
                        }
                        else if (shippingInfo.ldvOutputType == "ZPL") {
                            FileUtility.appendZplToFile(new File(shippingInfo.fileLabelPath), zplForOrder)
                        }
                    }

                    // aggiungo il file allo zip se serve
                    if (zipFile && features.documentType != MassiveShippingActionParams.DocType.UNIQUE && pdfForOrder != null && pdfForOrder.length() > 0) {
                        FileUtility.addFileToZip(pdfForOrder, result.orderReference + ".pdf", finalFile)
                    }

                    // aggiungo il file allo zip se serve
                    if (zipFile && features.documentType != MassiveShippingActionParams.DocType.UNIQUE && zplForOrder != null && zplForOrder.length() > 0) {
                        FileUtility.addFileToZip(zplForOrder, result.orderReference + ".zpl", finalFile)
                    }
                }

            } catch(Exception e) {
                MPMlog.error("Errore durante la stampa massiva", e)
            }
        }
        /**
         * Confermo le spedizioni di GLS DISABILITATO
         **/

        /*if(features.confirmShipping=="yes" && listCarrierShipping?.size()>0)
        {
            def store=Store.get(storeId)
            def shipper= GlsShipper.findByStoreAndCode(store,shipperCode)
            if(shipper ) {
                def glsdata = glsService.closeWorkDay(listCarrierShipping, storeId)


                if (glsdata) {
                    listCarrierShipping.each { carrierShipping ->
                        def shipping = this.get(carrierShipping.shipping_id)
                        shipping.confirmed = true
                        save(shipping)
                    }
                }
                if (!glsdata) {

                    def errorMessage = ("GLS: C'\u00E8 stato un errore nella conferma di alcune spedizioni, contattare la sede dei corrieri di riferimento per risolvere il problema!")
                }
            }
        }*/
        MPMlog.debug("RESULT OF PRINT:"+filesArray.collect{it.name})

//        if (features.printLdv == "yes") {
//
//            if(features.documentType == "unique" && changeExtention==true && features.params.reprinting=="true")
//                features.documentType="zip"
//            if (features.documentType == "unique" && filesArray?.size() > 0 && (features.params.outputType == "PDF" || features.params.outputType == "A4" || features.params.outputType == "A6" || ( containsZPL==false && features.params.reprinting=="true"))) {
//                MPMlog.debug("MERGE ALL PDF:" + filesArray.collect { it.name })
//
//                if (features.printListPrel == "yes") {
//                    def map = [:]
//                    map.orderIds = filesArray.collect { it.id }.join(",")//features.get('orderIds')
//                    def orderStream = orderService.printOrderList(map)
//
//                    mergeDocument(orderStream, finalFile)
//
//                }
//                mergeAllDocument(filesArray, finalFile)
//
//            }
//
//            if (features.printOrder == "yes" || features.printListPrel == "yes") {
//
//
//                if ((features.documentType == "unique" && features.params.outputType == "ZPL" && filesArray?.size() > 0) || (containsZPL==true && filesArray?.size()==numLabelZPL && features.params.reprinting=="true")) {
//                    //caso in cui il file è unco e stampo ZPL e si è scelto di stampare anche gli ordini allora restituisco un .zip
//                    // contentente tutte le etichette in zpl e tutti gli ordini in pdf
//                    MPMlog.debug("UNIQUE TYPE ZPL WITH ORDERS")
//
//                    bos = concatByteArrayZPLStream(filesArray)
//                    def listId = filesArray.collect { it.id }.findAll { it != null && it != "null" }.join(",")
//                    filesArray.clear()
//                    if (features.params.outputType == "ZPL" && features.printOrder == "yes") {
//                        def map = [:]
//                        map.orderIds = listId
//                        def orderStreamlocal = orderService.printOrder(map)
//                        filesArray.add([name: "ListaOrdini.pdf", stream: orderStreamlocal])
//                    }
//                    if (features.printListPrel == "yes") {
//                        def map = [:]
//                        map.orderIds = listId
//                        def orderStreamList = orderService.printOrderList(map)
//                        filesArray.add([name: "ListaPrelievo.pdf", stream: orderStreamList])
//                    }
//
//                    filesArray.add([name: "ListaEticehtte.zpl", stream: bos])
//
//                    ByteArrayOutputStream bos2 = new ByteArrayOutputStream()
//                    filesZip(finalFile, filesArray)
//
//                    //OutputStream fos = new FileOutputStream(filename)
//                    //try {
//                    //  bos.writeTo(fos);
//                    //} finally {
//                    //    MPMlog.debug("FILE IS WRITE TO DIGEST.")
//                    //    fos.close();
//                    //}
//                    massiveDigestService.updateStatus(digestId, DigestStatus.COMPLETED)
//                    massiveDigestService.notify(digestId)
//                    return
//
//
//                }
//
//
//            }
//            //se devo unire le etichette in formazo zpl
//            if ((features.params.outputType == "ZPL" && features.documentType == "unique" && filesArray?.size() > 0) ||
//                    ( features.documentType == "unique" && filesArray?.size() > 0 && filesArray?.size()==numLabelZPL && features.params.reprinting=="true") ) {
//                concatByteArrayZPL(filesArray, finalFile)
//
//            }
//            //se devo stampare in formato zpl o pdf e in un file zip
//            if (((features.params.outputType == "ZPL" || features.params.outputType == "PDF" || features.params.outputType == "A4" || features.params.outputType == "A6")
//                    && features.documentType == "zip" && filesArray?.size() > 0)||
//                    ( features.documentType == "zip" && filesArray?.size() > 0 && features.params.reprinting=="true"))
//            {
//                MPMlog.debug("ZIP ALL FILES")
//                if (features.printListPrel == "yes") {
//                    def map = [:]
//                    map.orderIds = filesArray.collect { it.id }.findAll { it != null && it != "null" }.join(",")
//                    def orderStreamList = orderService.printOrderList(map)
//                    filesArray.add([name: "ListaPrelievo.pdf", stream: orderStreamList])
//                }
//                if (features.printOrder == "yes" && (features.params.outputType == "ZPL"  ||( containsZPL && filesArray?.size()==numLabelZPL))) {
//                    //def map = [:]
//                    //map.orderIds = filesArray.collect{ it.id}.findAll{it!=null && it!="null"}.join(",")
//                    def listOrderId = filesArray.collect { it.id }.findAll { it != null && it != "null" }
//                    for (String id : listOrderId) {
//                        def map = [:]
//                        map.orderIds = id
//                        def orderStream = orderService.printOrder(map)
//                        filesArray.add([name: Order.get(Long.parseLong(id)).reference + ".pdf", stream: orderStream])
//                    }
//
//                    //def orderStream = orderService.printOrder(map)
//                    //filesArray.add([name: "ListaOrdini.pdf", stream: orderStream])
//
//                }
//                filesZip(finalFile, filesArray)
//            }
//        }
        /*extension = ".pdf"
    mime="application/pdf"
    if (features.params.outputType == "PDF" || features.params.outputType == "A4" || features.params.outputType == "A6") {
        extension = ".pdf"
        mime="application/pdf"
    } else if (features.params.outputType == "ZPL") {
        extension = ".zpl"
        mime="application/zpl"
    }
    if (features.documentType == "zip" || (features.documentType == "unique" && features.params.outputType == "ZPL" && features.printOrder == "yes")) {

        extension = ".zip"
        mime="application/zip"
    }

    if(bos.size()>0) {
        def filename = massiveDigestService.createResultFile(digestId, mime, extension)
        OutputStream fos = new FileOutputStream(filename)
        try {
            //bos.writeTo(fos)
        } finally {
            MPMlog.debug("FILE IS WRITE TO DIGEST" )
             fos.close()
        }
    }else
    {
        MPMlog.debug("FILE IS NOT WRITE STREAM IS EMPTY." )
    }*/

        //}


        // aggiungo il file allo zip se serve
        if(isZipFile && uniquePdfFile != null && uniquePdfFile.length() > 0) {
            FileUtility.addFileToZip(uniquePdfFile, "Etichette.pdf", finalFile)
        }

        // aggiungo il file allo zip se serve
        if(isZipFile && uniqueZplFile != null && uniqueZplFile.length() > 0) {
            FileUtility.addFileToZip(uniqueZplFile, "Etichette.zpl", finalFile)
        }


        massiveDigestService.updateStatus(digestId, DigestStatus.COMPLETED)
        massiveDigestService.notify(digestId)
        return
    }

    private Map createParamsForShipping(Long storeId, DefaultCarrier carrier, Order order){
        def map = [:]
        map.order = order.id
        map.shipperType = carrier.shipper.type.type
        map.shipper = carrier.shipper
        map.notes=[]
        Long Id = order.rows[0].id


        map.orderRowId=new ArrayList<Long>()

        map.orderRowId.add(Id)
        map['orderRowSelected'+Id]=true
        map['quantityTotal'+Id]=order.rows[0].quantity?.toString()?:"1.0"
        map['quantity'+Id]=order.rows[0].quantity?.toString()?:"1.0"

        def conf
        if(map.shipperType=="GLS")
        {
            Shipper shipper = shipperService.getByVirtualShipperTypeAndStoreId(carrier.shipper.virtualShipperType, storeId) // Shipper.findByStoreAndType(Store.get(storeId), carrier.shipper.type)
            conf = (shipper as GlsShipper)?.userConfiguration
        }
        else if( map.shipperType == "GLS_2"){

            Shipper shipper = shipperService.getByVirtualShipperTypeAndStoreId(carrier.shipper.virtualShipperType, storeId) // Shipper.findByStoreAndType(Store.get(storeId), carrier.shipper.type)
            conf = (shipper as GlsShipper)?.userConfiguration
        }
//        else if  (map.shipperType=="TNT")
//        {
//            conf=TntShipper.findByStore(Store.get(storeId))
//            TntProductType productType = TntProductType.findByCode(carrier.value)
//            map.productType = productType
//            map.tntApplication = productType?.tntApplication
//        }
        else{

            conf = shipperService.getConfiguration(storeId, carrier.shipper.virtualShipperType as Long)
            map.pricingConditionCode=conf?.pricingConditionCode?:""
            map.departureDepot=conf?.departureDepot?:""
            map.priopntime=conf?.priopntime?:""
            map.priclotime=conf?.priclotime?:""
            map.secopntime=conf?.secopntime?:""
            map.secclotime=conf?.secclotime?:""

            //per brt
            map.brtServiceType= BrtServiceType.findByValue((carrier.value!=""&& carrier?.value!=null) ? carrier.value:"")?:""

            if(map.shipperType == ShipperType.TNT) {
                // per TNT
                map.serviceType = TntProductType.findByCode((carrier.value!=""&& carrier?.value!=null) ? carrier.value:"")?:""
            }
            else {
                // per sda
                map.serviceType = SdaServiceType.findByValue((carrier.value != "" && carrier?.value != null) ? carrier.value : "") ?: ""
            }

        }

        map.outputType = conf.defaultLabelFormat?.toUpperCase()
        map.deliveryType="0"
        map.orderIdInNotes=conf?.orderIdInNotes
        map.content="Pacco"// per DHL
        map.numberPackage="1"
        map.recipientCountryCode="IT"
        map.forceShipping="Y"
        map.productType=""
        map.volume="1"
        map.alphanumericSenderReference=""
        map.notificationPhone=null
        map.afmiService=null

        def product=Product.get(order.rows[0].productOrder.productId)
        map.index = ["0"]
        map['parcel.weight0'] = "2"
        if(product.weight > 0.01) map['parcel.weight0'] = product.weight.toString()
        map['parcel.height0']=product.height?.setScale(2, BigDecimal.ROUND_HALF_UP)?.toString()?.replace(".", ",")?:"1"
        map['parcel.width0']=product.width?.setScale(2, BigDecimal.ROUND_HALF_UP)?.toString()?.replace(".", ",")?:"1"
        map['parcel.depth0']=product.depth?.setScale(2, BigDecimal.ROUND_HALF_UP)?.toString()?.replace(".", ",")?:"1"

        def boxtype
        if(map.shipperType==ShipperType.SDA)
        {
            boxtype=BoxType.findByValue(SdaBoxType.PACCHI_VALUE).id.toString()
        }
        else if(map.shipperType==ShipperType.GLS){
            boxtype=BoxType.findByValue(GlsBoxType.NORMAL_VALUE).id.toString()
        }
        else if(map.shipperType==ShipperType.BRT){
            boxtype=BoxType.findByValue(BrtBoxType.PACCHI_VALUE).id.toString()
        }
        else if(map.shipperType==ShipperType.POSTE_DELIVERY || map.shipperType==ShipperType.POSTE)
        {
            boxtype=BoxType.findByValue(SdaBoxType.PACCHI_VALUE).id.toString()
        }
        else if(map.shipperType==ShipperType.TNT)
        {
            boxtype=BoxType.findByValue(TntBoxType.COLLI_VALUE).id.toString()
        }
        else if(map.shipperType==ShipperType.DHL)
        {
            boxtype=BoxType.findByValue(DhlBoxType.PACCHI_VALUE).id.toString()
        }

        map['parcel.boxType0']=boxtype






        //per sda

        if(map.shipperType == ShipperType.GLS || map.shipperType == ShipperType.GLS_2)
        {
            //prelevo la configurazione di gls dello store per recuperare da userconfiguration se
            //il flag phoneInNotes è abilitato o meno per la stampa dell'etichetta
            def store=Store.get(order.storeId)
            def shipperGls=GlsShipper.findByStore(store)

            map.phoneInNotes=shipperGls.userConfiguration.phoneInNotes?'true':'false'

        }
        //contrassegno
        def codValue = order.paymentIsCod
        if(codValue) {
            map.codValue = (order.totalOrder + (order.codCost ?: 0)).toString()
            def codType = ""
            //il codType lo valorizzo sempre come pagamento in contanti
            switch(map.shipperType){
                case ShipperType.BRT:
                case ShipperType.DHL:
                    codType="0"
                    break;
                case ShipperType.GLS:
                case ShipperType.GLS_2:
                    codType="CONT"
                    break;
                case ShipperType.SDA:
                case ShipperType.POSTE:
                    codType="CON"
                    break;
                case ShipperType.TNT:
                    codType="S"
                    break;
            }
            map.codType = codType
        }

        return map
    }

    void saveFileLabelForShipping(Shipping shipping, byte[] fileLabel, String extension = null) {
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

        String fileExt = extension
        if(extension == null) {
            fileExt = ".pdf"
            if (shipping.outputType == "ZPL") //per BRT si possono stampare etichette nel formato ZPL
                fileExt = ".zpl"
            if (shipping.outputType == "PNG") //per AMAZON si possono stampare etichette nel formato PNG
                fileExt = ".png"
        }

        //per nome directory idstore+nomeshipper(tnt,gls,etc..)+anno-mese
        def dir2=new File(grailsApplication.config.shipping.filelabelpath)
        def savefile= new File(dir2,path + "Label" + fileName + fileExt)
        savefile.withOutputStream{
            it.write(fileLabel)
        }
        shipping.fileLabel = path + "Label" + fileName + fileExt
    }

    def handleWebhook(Shipper shipper, HttpServletRequest request) {
        Map result = [:]
        Long storeId = shipper.store.id
        switch(shipper.type) {
            case ShipperType.SPEDISCI_ONLINE:

                /* cerco il body della richiesta */
                String requestBodyString = IOUtils.toString(request.reader)
                MPMlog.info("Ricevuto da PPS webhook con body: ${requestBodyString}")

                if(requestBodyString.indexOf('{') > 0) {
                    int index = requestBodyString.indexOf('{')
                    requestBodyString = requestBodyString.substring(index)
                }

                /* se il body è vuoto, cerco un parametro */
                if(StringUtils.isBlank(requestBodyString)) {
                    requestBodyString = request.getParameter("params")
                }
                MPMlog.info("Trovato il body: ${requestBodyString}")

                if(StringUtils.isBlank(requestBodyString)) {
                    MPMlog.error("Errore durante la lettura de l webhook di PayPerShip, non c'è ilbody o il params con il webhook")
                    result.code = 401
                    result.message = "Unauthorized"
                    return result
                }

                /* ottengo i dati del webhook di PPS */
                PayPerShipSiteData ppsSiteData
                try {
                    ObjectMapper objectMapper = new ObjectMapper()
                    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                    ppsSiteData = objectMapper.readValue(requestBodyString, PayPerShipSiteData.class)
                }
                catch (Exception e) {
                    MPMlog.error("Errore durante il parsing del JSON del webhook di PayPerShip", e)
                    result.code = 401
                    result.message = "Unauthorized"
                    return result
                }

                //recupero della spedizione tramite referenceid
                Long shippingId
                MPMlog.debug("Recupero shipping tramite referenceid: ${ppsSiteData.referenceid}")
                try{
                    shippingId = Long.parseLong(ppsSiteData.referenceid)
                }catch(NumberFormatException e){
                    MPMlog.error("Errore durante il parsing del refenceid: ${ppsSiteData.referenceid}", e)
                    result.code = 401
                    result.message = "Unauthorized"
                    return result
                }

                SpedisciOnlineShipping shipping = SpedisciOnlineShipping.get(shippingId)

                if(!shipping) {
                    MPMlog.info("Shipping non trovata con il seguente referenceid: ${ppsSiteData.referenceid}")
                    result.code = 401
                    result.message = "Unauthorized"
                    return result
                }

                //validazione
                MPMlog.debug("Recupero della configurazione PayPerShip")
                def ppsConf = shipperService.getConfiguration(storeId, ShipperType.CARRIER_ID_PAYPERSHIP)
                if (!ppsConf) {
                    MPMlog.info("Configurazione PayPerShip non trovata per lo store: ${storeId}")
                    result.code = 500
                    result.message = "Errore interno"
                    return result
                }

                /* Eseguo la validazione */
                boolean skipValidation = (grailsApplication.config["paypership.skipValidation"] as String)?.equals("true") ?: false
                MPMlog.debug("Validazione tramite il campo valid - ${skipValidation}")
                if (!skipValidation && !ppsSiteData.isValid(ppsConf.customer, ppsConf.pswd, shipping.ppsEmotionReference)) {
                    MPMlog.info("Validazione fallita")
                    result.code = 401
                    result.message = "Unauthorized"
                    return result
                }

                //controllo se ho già scaricato il file
                //TODO capire quando ci chiamano se la chiamata è label o transactional
                if(!shipping.fileLabel) {
                    MPMlog.debug("Inizio il download dell'etichetta dal seguente url: ${ppsSiteData.url_label}")
                    shipping.shippingCode = ppsSiteData.shipping_number //se non c'è lo shipping_number?
                    //scarico il file
                    def datenow = new Date()
                    def path= "/" + shipper.store.id + "/" +datenow[Calendar.MONTH] + "/" + datenow[Calendar.YEAR] + "/" + shipper.name+ "/"
                    def pathtoSave = grailsApplication.config.shipping.filelabelpath + path

                    def dir = new File(pathtoSave)
                    if(!dir.exists()){
                        dir.mkdirs()
                    }
                    def fileExt = ppsSiteData.url_label.substring(ppsSiteData.url_label.lastIndexOf("."))
                    def fileName = "Label" + (shipping.shippingCode?:shipping.id) + fileExt


                    String localUrl = pathtoSave + fileName
                    String remoteUrl = ppsSiteData.url_label

                    MPMlog.debug("Il file verrà salvato in ${localUrl}")

                    try {
                        new File("$localUrl").withOutputStream { out ->
                            new URL(remoteUrl).withInputStream { from ->
                                out << from
                            }
                        }
                    }catch(Exception e) {
                        MPMlog.error("Errore durante il download dell'etichetta dal seguente url: ${ppsSiteData.url_label}")
                        result.code = 500
                        result.message = "Errore interno durante il download dell'etichetta"
                        return result
                    }
                    MPMlog.debug("Download del file eseguito con successo")

                    shipping.fileLabel = path + fileName
                    shipping.toDownload = true
                    shipping.confirmed = true
	                shipping.confirmedDate = new Date()
                    shipping.ppsCarrierName = ppsSiteData.carrier
                    //shipping.ppsShipmentService (cosa mettere?)

                    save(shipping)
                    MPMlog.info("Spedizione con id ${shipping.id} aggiornata con successo")
                }else {
                    MPMlog.info("Etichetta già scaricata per la spedizione con id: ${shipping.id}")
                }

                result.code = 200
                result.message = "Dati ricevuti con successo"

            default:
                break

        }
        return result
    }

	List<Long> listStoreShippingPickupAddressesToBook(Long shipperId) {
        Calendar calendar = Calendar.getInstance()
        calendar.setTime(new Date())
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.HOUR_OF_DAY, 0)

        // recupero la lista delle spedizioni associate allo shipper, confermate oggi e con il ritiro non prenotato
        def shippings = Shipping.createCriteria().list {
            projections {
                distinct("pickupAddress.id")
            }

            shipper {
                eq("id", shipperId)
            }

            isNull("pickupDate")
            ge("confirmedDate", calendar.getTime())
        }

        return shippings
    }

	List<Shipping> listStoreShippingsToBook(Long shipperId, Long pickupAddressId = null) {
		Calendar calendar = Calendar.getInstance()
		calendar.setTime(new Date())
		calendar.set(Calendar.MILLISECOND, 0)
		calendar.set(Calendar.SECOND, 0)
		calendar.set(Calendar.MINUTE, 0)
		calendar.set(Calendar.HOUR_OF_DAY, 0)

		// recupero la lista delle spedizioni associate allo shipper, confermate oggi e con il ritiro non prenotato
		def shippings = Shipping.createCriteria().list {
			shipper {
				eq("id", shipperId)
			}

            if(pickupAddressId != null) {
                pickupAddress {
                    eq("id", pickupAddressId)
                }
            }

			isNull("pickupDate")
			ge("confirmedDate", calendar.getTime())
		}

		return shippings
	}

    def autoPrintLdv(Long orderId,Long storeId) {
        //verifico se l'ordine rispetta i parametri per la stampa automatica della spedizione
        //verifico se l'ordine ha una sola riga
        def order=Order.get(orderId)
        if(!(order.rows && order.rows.size() == 1)) {
            order.autoLdvState = Order.AUTO_PRINT_LDV_SKIPPED
            order.save()
            return
        }
        OrderRow orderRow = order.rows[0]
        Product product = Product.get(orderRow.productOrder?.productId)
        //verifico se è il prodotto ordinato è gestito, se ha i corrieri di default settati e se è stata ordinata una sola quantit

        if(!(product && product.defaultCarriers?.size()>0 && orderRow.quantity == BigDecimal.ONE)) {
            order.autoLdvState = Order.AUTO_PRINT_LDV_SKIPPED
            order.save()
            return
        }

        // Se l'ordine ha già una spedizione settata allora setto il flag a skippato
        if(order.shippings.size() > 0){
            order.autoLdvState = Order.AUTO_PRINT_LDV_SKIPPED
            MPMlog.debug("Esiste già una configurazione di spedizione settata, quindi setto l'ordine : ${order.id} con stato : ${Order.AUTO_PRINT_LDV_SKIPPED}.")
            order.save()
            return
        }

        //provo a stampare la spedizione in base ai corrieri di default settati e alla priorità scelta
        List<DefaultCarrier> carriers = product.defaultCarriers.sort{it.priority}
        def printed=false
        MPMlog.debug("OK ${carriers}")

        carriers.each {carrier ->
            Order.withNewTransaction { TransactionStatus status ->
                MPMlog.debug("CONTROL IF ORDER ${order} IS VALID ${order.currentState}")
                def oldOrder=Order.get(orderId)
                def shipperCode=carrier.shipper?.type?.type
                if((oldOrder.currentState==OrderState.findByState(OrderState.CANCELLED_BY_CUSTOMER)) ||
                        (oldOrder.currentState==OrderState.findByState(OrderState.CANCELLED_BY_SELLER)) ||
                                (oldOrder.currentState==OrderState.findByState(OrderState.REFUND) ) ||
                        (carrier.shipper.enable==false) )
                {
                    //success = false

                    return
                }
                MPMlog.info("ORDER ${orderId} IS VALID")

                MPMlog.debug("CREATE SHIPPING FOR ORDER:" + oldOrder)
                MPMlog.debug("SHIPPER:" + shipperCode)
                def features=[:]
                features.shipper = shipperCode
                if(printed==false)
                {

                   def shipping

                   try {
                        //formo i parametri per creare la spedizione
                        def paramsShipping = createParamsForShipping(storeId, carrier, oldOrder)

                        shipping = order.shippings?.find {
                            it.shipper.name == shipperCode
                        }
                        if (shipping == null) {
                            def result = create(paramsShipping, storeId)
                            if (result.isSuccess == Boolean.TRUE) {
                                shipping = result.shipping
                            } else if (result.isSuccess == Boolean.FALSE) {
                                MPMlog.error("CREATE SHIPPING EXCEPTION:" + result.message)
                                status.setRollbackOnly()
                                //success = false
                                return
                            }
                        }
                   } catch (Exception ex) {
                        MPMlog.error("CREATE SHIPPING EXCEPTION:" + ex.message, ex)
                        status.setRollbackOnly()
                        //success = false
                        return
                   }


                    //shipping.outputType = features.params.outputType
                    MPMlog.debug("SHIPPING CREATED:" + shipping)


                    oldOrder.addToShippings(shipping)

                    MPMlog.debug("START PRINTING LABEL")
                    def result
                    try {
                        result = printShippingLabel(shipping, storeId)
                        if (result.isSuccess == Boolean.FALSE) {
                            MPMlog.warn("PRINT LABEL NOT SUCCESS: " + result.message)
                            status.setRollbackOnly()
                            def msg = result.message
                            //success = false
                            return
                        }
                        printed = result.isSuccess
                        if(printed==true) {
                            oldOrder.autoLdvState = Order.AUTO_PRINT_LDV_SUCCESS
                            oldOrder.save(flush:true)
                            MPMlog.debug("CHANGE STATE TO AUTO_PRINT_LDV_SUCCESS")
                        }
                    }
                    catch (Exception ex) {
                        MPMlog.error("CREATE SHIPPING LABEL EXCEPTION", ex)
                        status.setRollbackOnly()
                        return

                    }
                }


            }
        }
        Order.withNewTransaction { TransactionStatus status ->
            def oldOrder=Order.get(orderId)
            if (printed == false) {
                oldOrder.autoLdvState = Order.AUTO_PRINT_LDV_FAILED
                MPMlog.debug("CHANGE STATE TO AUTO_PRINT_LDV_FAILED")
                try{
                    oldOrder.save()
                    status.flush()
                }catch(Exception ex){
                    MPMlog.debug("STATUS NOT CHANGED:${ex.message}")
                }

            }
        }





        MPMlog.debug("PRINT LABEL IS ${printed} FOR ORDER ${order}")
    }

    /**
     * Metodo per stampare le etichette per le spedizioni
     * @param storeId l'id dello store
     * @param userId l'utente che esegue l'operazione
     * @param features i parametri di esecuzione
     * @return
     */
    def printShippingLabels(Long userId, Long storeId, List<Shipping> features) {
        String tid = MPMlog.getTID()

        List<String> labelOutputTypes

        println(features)

        labelOutputTypes = Order.createCriteria().list {
            createAlias('shippings', 's', JoinType.LEFT_OUTER_JOIN.getJoinTypeValue())

            projections {
                distinct "s.outputType"
            }

            store {
                eq 'id', storeId
            }

            "in"("s.id", features*.id)
        } as List<String>

        // semplico gli outputType
        labelOutputTypes = labelOutputTypes.collect {it == "A4" || it == "A6" ? "PDF" : it}.unique()

        println(labelOutputTypes)

        // verifico se genererò un file zippato
        boolean isZipFile = false
        if(labelOutputTypes.size() > 1) {
            isZipFile = true
        }

        // calcolo l'estensione del file
        def extension
        def mime
        if(isZipFile) {
            extension = ".zip"
            mime="application/zip"
        }
        else {
            if(labelOutputTypes.contains("PDF")) {
                extension = ".pdf"
                mime="application/pdf"
            }
            else if (labelOutputTypes.contains("ZPL")) {
                extension = ".zpl"
                mime="application/zpl"
            }
            else {
                throw new Exception("Il formato di file ${labelOutputTypes} non è riconosciuto")
            }
        }

        def finalFile

        if (isZipFile){
            finalFile = File.createTempFile(tid + "-uniquePdf", ".zip")
        } else if (labelOutputTypes.size() == 1 && labelOutputTypes.contains('PDF')){
            finalFile = File.createTempFile(tid + "-uniquePdf", ".pdf")
        } else {
            finalFile = File.createTempFile(tid + "-uniqueZebra", ".zpl")
        }

        // recupero il file unico PDF
        File uniquePdfFile = null
        if(mime == "application/pdf") {
            uniquePdfFile = finalFile
        }
        else {
            uniquePdfFile = File.createTempFile(tid + "-uniquePdf", ".pdf")
            uniquePdfFile.deleteOnExit()
        }

        // recupero il file unico ZEBRA
        File uniqueZplFile = null
        if(mime == "application/zpl") {
            uniqueZplFile = finalFile
        }
        else {
            uniqueZplFile = File.createTempFile(tid + "-uniqueZebra", ".zpl")
            uniqueZplFile.deleteOnExit()
        }

        final zipFile = isZipFile

        features.each { Shipping future ->
            try {
                MPMlog.debug("isZipFile: ${zipFile}")

                def fileLabelPath = grailsApplication.config["shipping.filelabelpath"] + future.fileLabel
                def ldvOutputType = future.outputType == 'A4' || future.outputType == 'A6' ? "PDF" : future.outputType


                // ciclo le spedizioni ottenute
                if (ldvOutputType == "PDF") {
                    FileUtility.appendPdfToFile(new File(fileLabelPath), uniquePdfFile)
                }
                else if (ldvOutputType == "ZPL") {
                    FileUtility.appendZplToFile(new File(fileLabelPath), uniqueZplFile)
                }

            } catch(Exception e) {
                MPMlog.error("Errore durante la stampa massiva", e)
            }
        }

        // aggiungo il file allo zip se serve
        if(isZipFile && uniquePdfFile != null && uniquePdfFile.length() > 0) {
            FileUtility.addFileToZip(uniquePdfFile, "Etichette.pdf", finalFile)
        }

        // aggiungo il file allo zip se serve
        if(isZipFile && uniqueZplFile != null && uniqueZplFile.length() > 0) {
            FileUtility.addFileToZip(uniqueZplFile, "Etichette.zpl", finalFile)
        }

        return finalFile
    }

}
