package com.macrosolution.mpm.carrierservice.controller;

import com.macrosolution.mpm.carrierservice.domain.ShipperType;
import com.macrosolution.mpm.carrierservice.domain.tnt.TntProductType;
import com.macrosolution.mpm.carrierservice.dto.request.ConfigurationRequest;
import com.macrosolution.mpm.carrierservice.dto.request.ProductTypes;
import com.macrosolution.mpm.carrierservice.dto.response.configuration.ConfigurationResponse;
import com.macrosolution.mpm.carrierservice.dto.response.configuration.ConfigurationsResponse;
import com.macrosolution.mpm.carrierservice.dto.response.Response;
import com.macrosolution.mpm.carrierservice.model.CarrierConfiguration;
import com.macrosolution.mpm.carrierservice.model.DepartureDeposite;
import com.macrosolution.mpm.carrierservice.service.CarrierFactory;
import com.macrosolution.mpm.carrierservice.service.configuration.ConfigurationService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/configuration")
public class ConfigurationController {
	
	@Autowired
	ConfigurationService configurationService;
	
	private final Logger logger = LoggerFactory.getLogger("configuration-serivice");
	
	@RequestMapping(value = "/{storeId}", method=RequestMethod.PUT)
    public @ResponseBody Response save(@PathVariable Long storeId ,
									   @RequestBody ConfigurationRequest configurationRequest){
		ConfigurationResponse response = new ConfigurationResponse();		
		logger.info("START save configuration "+configurationRequest.getCarrier_type());
		CarrierConfiguration configuration = new CarrierConfiguration();
		configuration.setCarrierType(configurationRequest.getCarrier_type());
		configuration.setUsername(configurationRequest.getUsername());
		configuration.setPassword(configurationRequest.getPswd());
		configuration.setStoreId(storeId);
		configuration.setContractCode(configurationRequest.getAccount_id());
		configuration.setCustomerCode(configurationRequest.getCustomer());
		configuration.setDefault(configurationRequest.isDefault());
		configuration.setActive(configurationRequest.isDefault());
		configuration.setCheckBookingForm(configurationRequest.getCheckBookingForm());
		configuration.setSeatCode(configurationRequest.getSeatCode());
		configuration.setDefaultLabelFormat(configurationRequest.getDefaultLabelFormat());
		configuration.setOrderIdInNotes(configurationRequest.getOrderIdInNotes());

		configuration.setVirtualShipperType(configurationRequest.getVirtualShipperType());
		configuration.setTitle(configurationRequest.getTitle());

		// TNT
		configuration.setSenderAccId(configurationRequest.getAccount_id());
		if(configurationRequest.getProductTypes() != null) {
			configuration.setProductTypes(configurationRequest.getProductTypes());
		}
		if(configurationRequest.getErrorAddress() != null) {
			configuration.setErrorAddress(configurationRequest.getErrorAddress());
		}

		//SDA
		configuration.setServiceTypes(configurationRequest.getServiceTypes());
		// BRT
		configuration.setGiornoChiusura1(configurationRequest.getGiornoChiusura1());
		configuration.setPeriodoChiusura1(configurationRequest.getPeriodoChiusura1());
		configuration.setGiornoChiusura2(configurationRequest.getGiornoChiusura2());
		configuration.setPeriodoChiusura2(configurationRequest.getPeriodoChiusura2());
		configuration.setDefaultTariff(configurationRequest.getDefaultTariff());
		configuration.setConsigneeEmail(configurationRequest.getConsigneeEmail());
		configuration.setConsigneeMobilePhoneNumber(configurationRequest.getConsigneeMobilePhoneNumber());


		if(configurationRequest.getDepartureDeposites() != null){
			List<DepartureDeposite> ddList = new ArrayList<>();
			for (com.macrosolution.mpm.carrierservice.dto.request.DepartureDeposite dp : configurationRequest.getDepartureDeposites()) {
				if(dp.getId()!=null && !dp.getId().isEmpty()
						&& dp.getLabel()!=null && !dp.getLabel().isEmpty()) {
					DepartureDeposite departureDeposite = new DepartureDeposite();
					departureDeposite.setValue(dp.getId());
					departureDeposite.setLabel(dp.getLabel());
					departureDeposite.setSupplyID(dp.getSupplyID());
					ddList.add(departureDeposite);
				}
			}
			configuration.setDepartureDeposites(ddList);
		}

		//DHL
		configuration.setPackageLocation(configurationRequest.getPackageLocation());
		try {
			if(configurationRequest.getPriopntime()!= null && configurationRequest.getPriopntime()!="")
				configuration.setPriopntime(new SimpleDateFormat("HH:mm").parse(configurationRequest.getPriopntime()));
			if(configurationRequest.getPriclotime()!= null && configurationRequest.getPriclotime()!="")
				configuration.setPriclotime(new SimpleDateFormat("HH:mm").parse(configurationRequest.getPriclotime()));
			if(configurationRequest.getSecopntime()!= null && configurationRequest.getSecopntime()!="")
				configuration.setSecopntime(new SimpleDateFormat("HH:mm").parse(configurationRequest.getSecopntime()));
			if(configurationRequest.getSecclotime()!= null && configurationRequest.getSecclotime()!="")
				configuration.setSecclotime(new SimpleDateFormat("HH:mm").parse(configurationRequest.getSecclotime()));
			if(configurationRequest.getAvailabilitytime()!= null && configurationRequest.getAvailabilitytime()!="")
				configuration.setAvailabilitytime(new SimpleDateFormat("HH:mm").parse(configurationRequest.getAvailabilitytime()));
			if(configurationRequest.getPickuptime()!= null && configurationRequest.getPickuptime()!="")
				configuration.setPickuptime(new SimpleDateFormat("HH:mm").parse(configurationRequest.getPickuptime()));
		} catch (ParseException e) {
			e.printStackTrace();
		}

		configuration.setDefaultPickupAddress(configurationRequest.getDefaultPickupAddress());

		configuration = configurationService.saveConfiguration(configuration);
		if(configuration.getId()>0){
			response.setId(configuration.getId());
			response.setIsSuccess(true);
		}
		logger.info("END save configuration: "+response);
		return response;
	}

	@RequestMapping(value = {"/{storeId}","/{storeId}/{type}"}, method=RequestMethod.POST)
	public @ResponseBody ResponseEntity<Response> update(@PathVariable Long storeId ,@PathVariable Optional<Integer> type,
										 @RequestBody ConfigurationRequest configurationRequest){
		Response response = new Response();
		logger.info("START update configuration on STORE "+storeId+ " type "+type+ " and request: "+configurationRequest);
		CarrierConfiguration configuration = new CarrierConfiguration();
		if(type.isPresent())
			configuration.setCarrierType(type.get());
		configuration.setUsername(configurationRequest.getUsername());
		configuration.setPassword(configurationRequest.getPswd());
		configuration.setStoreId(storeId);
		configuration.setContractCode(configurationRequest.getAccount_id());
		configuration.setCustomerCode(configurationRequest.getCustomer());
		configuration.setDefault(configurationRequest.isDefault());
		configuration.setActive(configurationRequest.isActive());
		configuration.setCheckBookingForm(configurationRequest.getCheckBookingForm());
		configuration.setSeatCode(configurationRequest.getSeatCode());
		configuration.setDefaultLabelFormat(configurationRequest.getDefaultLabelFormat());
		configuration.setOrderIdInNotes(configurationRequest.getOrderIdInNotes());

		configuration.setTitle(configurationRequest.getTitle());
		configuration.setVirtualShipperType(configurationRequest.getVirtualShipperType());

		// TNT
		configuration.setSenderAccId(configurationRequest.getAccount_id());
		if(configurationRequest.getProductTypes() != null) {
			configuration.setProductTypes(configurationRequest.getProductTypes());
		}
		if(configurationRequest.getErrorAddress() != null) {
			configuration.setErrorAddress(configurationRequest.getErrorAddress());
		}

		//SDA
		configuration.setServiceTypes(configurationRequest.getServiceTypes());
		// BRT
		configuration.setGiornoChiusura1(configurationRequest.getGiornoChiusura1());
		configuration.setPeriodoChiusura1(configurationRequest.getPeriodoChiusura1());
		configuration.setGiornoChiusura2(configurationRequest.getGiornoChiusura2());
		configuration.setPeriodoChiusura2(configurationRequest.getPeriodoChiusura2());
		configuration.setDefaultTariff(configurationRequest.getDefaultTariff());
		configuration.setConsigneeEmail(configurationRequest.getConsigneeEmail());
		configuration.setConsigneeMobilePhoneNumber(configurationRequest.getConsigneeMobilePhoneNumber());

		if(configurationRequest.getDepartureDeposites() != null){
			List<DepartureDeposite> ddList = new ArrayList<>();
			for (com.macrosolution.mpm.carrierservice.dto.request.DepartureDeposite dp : configurationRequest.getDepartureDeposites()) {
				if(dp.getId()!=null && !dp.getId().isEmpty()
						&& dp.getLabel()!=null && !dp.getLabel().isEmpty()) {
					DepartureDeposite departureDeposite = new DepartureDeposite();
					departureDeposite.setValue(dp.getId());
					departureDeposite.setLabel(dp.getLabel());
					departureDeposite.setSupplyID(dp.getSupplyID());
					ddList.add(departureDeposite);
				}
			}
			configuration.setDepartureDeposites(ddList);
		}
		//DHL
		configuration.setPackageLocation(configurationRequest.getPackageLocation());
		try {
			if(configurationRequest.getPriopntime()!= null && configurationRequest.getPriopntime()!="")
				configuration.setPriopntime(new SimpleDateFormat("HH:mm").parse(configurationRequest.getPriopntime()));
			if(configurationRequest.getPriclotime()!= null && configurationRequest.getPriclotime()!="")
				configuration.setPriclotime(new SimpleDateFormat("HH:mm").parse(configurationRequest.getPriclotime()));
			if(configurationRequest.getSecopntime()!= null && configurationRequest.getSecopntime()!="")
				configuration.setSecopntime(new SimpleDateFormat("HH:mm").parse(configurationRequest.getSecopntime()));
			if(configurationRequest.getSecclotime()!= null && configurationRequest.getSecclotime()!="")
				configuration.setSecclotime(new SimpleDateFormat("HH:mm").parse(configurationRequest.getSecclotime()));
			if(configurationRequest.getAvailabilitytime()!= null && configurationRequest.getAvailabilitytime()!="")
				configuration.setAvailabilitytime(new SimpleDateFormat("HH:mm").parse(configurationRequest.getAvailabilitytime()));
			if(configurationRequest.getPickuptime()!= null && configurationRequest.getPickuptime()!="")
				configuration.setPickuptime(new SimpleDateFormat("HH:mm").parse(configurationRequest.getPickuptime()));
		} catch (Exception e) {
			e.printStackTrace();
		}

		// PPS
		configuration.setDefaultPickupAddress(configurationRequest.getDefaultPickupAddress());

		if(configurationRequest.getCouriers() != null && !configurationRequest.getCouriers().isEmpty()) {
			configuration.setCouriers(configurationRequest.getCouriers());
		}
		
		response.setIsSuccess(configurationService.updateConfiguration(configuration));
		logger.info("END update configuration: "+response);
		if(!response.getIsSuccess()){
			return new ResponseEntity<Response>(HttpStatus.NOT_FOUND);
		}

		return ResponseEntity.ok(response);
	}


	@RequestMapping(value = {"/{storeId}","/{storeId}/{type}"}, method=RequestMethod.GET)
	public @ResponseBody ConfigurationsResponse list(@PathVariable Long storeId ,@PathVariable Optional<Integer> type){
		logger.info("START get configuration on STORE "+storeId+ " type "+type);
		ConfigurationsResponse response = new ConfigurationsResponse();
		response.setIsSuccess(true);
		response.addAll(configurationService.getConfigurations(storeId , type).stream().map(
				conf -> {
					CarrierConfiguration configuration = (CarrierConfiguration) conf;
					ConfigurationResponse res = new ConfigurationResponse();
					//res.setAccount_id(configuration.getCustomerCode()); //TODO vedere se funziona così
					String carrierType ="";
					switch(configuration.getCarrierType()){
						case CarrierFactory.CARRIER_TYPE_TNT:
							carrierType= ShipperType.TNT;
							break;
						case CarrierFactory.CARRIER_TYPE_GLS:
							carrierType= ShipperType.GLS;
							break;
						case CarrierFactory.CARRIER_TYPE_POSTE:
							carrierType= ShipperType.POSTE;
							break;
						case CarrierFactory.CARRIER_TYPE_CRONO:
							carrierType= ShipperType.POSTE_CRONO;
							break;
						case CarrierFactory.CARRIER_TYPE_SDA:
							carrierType= ShipperType.POSTE_SDA;
							break;
						case CarrierFactory.CARRIER_TYPE_BRT:
							carrierType= ShipperType.BRT;
							List<com.macrosolution.mpm.carrierservice.dto.request.DepartureDeposite> departureDepositeList = new ArrayList<>();
							com.macrosolution.mpm.carrierservice.dto.request.DepartureDeposite ddDefaultResp = new com.macrosolution.mpm.carrierservice.dto.request.DepartureDeposite();
							ddDefaultResp.setId(configuration.getSeatCode());
							ddDefaultResp.setLabel("DEFAULT");
							departureDepositeList.add(ddDefaultResp);
							if(configuration.getDepartureDeposites()!=null){
								for (DepartureDeposite dd : configuration.getDepartureDeposites()){
									com.macrosolution.mpm.carrierservice.dto.request.DepartureDeposite ddResp = new com.macrosolution.mpm.carrierservice.dto.request.DepartureDeposite();
									ddResp.setId(dd.getValue());
									ddResp.setLabel(dd.getLabel());
									ddResp.setSupplyID(dd.getSupplyID());
									departureDepositeList.add(ddResp);
								}

							}
							res.setDepartureDeposites(departureDepositeList);
							break;
						case CarrierFactory.CARRIER_TYPE_DHL:
							carrierType= ShipperType.DHL;
							break;
						case CarrierFactory.CARRIER_TYPE_POSTE_DELIVERY:
							carrierType= ShipperType.POSTE_DELIVERY;
							break;
						case CarrierFactory.CARRIER_TYPE_SPEDISCI_ONLINE:
							carrierType= ShipperType.SPEDISCI_ONLINE;
							break;
						case CarrierFactory.CARRIER_TYPE_MIT:
							carrierType= ShipperType.MIT;
							break;
						case CarrierFactory.CARRIER_TYPE_FEDEX:
							carrierType= ShipperType.FEDEX;
							break;
						case CarrierFactory.CARRIER_TYPE_QAPLA:
							carrierType = ShipperType.QAPLA;
					}
					res.setCarrier_type(carrierType);
					res.setIs_default(configuration.getDefault());
					res.setIs_active(configuration.getActive());
					res.setUsername(configuration.getUsername());
					res.setPswd(configuration.getPassword());
					res.setAccount_id(configuration.getContractCode());
					res.setId(configuration.getId());
					res.setCheckBookingForm(configuration.getCheckBookingForm());
					res.setSeatCode(configuration.getSeatCode());
					res.setCustomer(configuration.getCustomerCode());
					res.setDefaultLabelFormat(configuration.getDefaultLabelFormat());
					res.setOrderIdInNotes(configuration.getOrderIdInNotes());
					res.setVirtualShipperType(configuration.getVirtualShipperType());
					res.setTitle(configuration.getTitle());

					// TNT
					if(StringUtils.isBlank(res.getAccount_id())) {
						res.setAccount_id(configuration.getSenderAccId());
					}
					res.setErrorAddress(configuration.getErrorAddress() == Boolean.TRUE);

					// SDA
					res.setServiceTypes(configuration.getServiceTypes());
					// BRT
					res.setGiornoChiusura1(configuration.getGiornoChiusura1());
					res.setPeriodoChiusura1(configuration.getPeriodoChiusura1());
					res.setGiornoChiusura2(configuration.getGiornoChiusura2());
					res.setPeriodoChiusura2(configuration.getPeriodoChiusura2());
					res.setDefaultTariff(configuration.getDefaultTariff());
					res.setConsigneeEmail(configuration.getConsigneeEmail());
					res.setConsigneeMobilePhoneNumber(configuration.getConsigneeMobilePhoneNumber());

					// DHL
					res.setPackageLocation((configuration.getPackageLocation()));
					try {
						res.setPriopntime(configuration.getPriopntime()!=null?new SimpleDateFormat("HH:mm").format(configuration.getPriopntime()):null);
						res.setPriclotime(configuration.getPriclotime()!=null?new SimpleDateFormat("HH:mm").format(configuration.getPriclotime()):null);
						res.setSecopntime(configuration.getSecopntime()!=null?new SimpleDateFormat("HH:mm").format(configuration.getSecopntime()):null);
						res.setSecclotime(configuration.getSecclotime()!=null?new SimpleDateFormat("HH:mm").format(configuration.getSecclotime()):null);
						res.setPickuptime(configuration.getPickuptime()!=null?new SimpleDateFormat("HH:mm").format(configuration.getPickuptime()):null);
						res.setAvailabilitytime(configuration.getAvailabilitytime()!=null?new SimpleDateFormat("HH:mm").format(configuration.getAvailabilitytime()):null);
					} catch (Exception e) {
						e.printStackTrace();
					}

					// PPS
					res.setDefaultPickupAddress(configuration.getDefaultPickupAddress());
					res.setPlatformBaseUrl(configuration.getPlatformBaseUrl());
					res.setSsoUrl(configuration.getSsoUrl());

					if(configuration.getCouriers() != null && !configuration.getCouriers().isEmpty()) {
						res.setCouriers(configuration.getCouriers());
					}

					return res;
				}
		).collect(Collectors.toList()));
		logger.info("END get configuration: "+response.toString());
		return response;

	}


	/**
	 * Metodo per l'eliminazione delle configurazioni di uno specifico corriere di uno store.
	 * @param storeId                       lo store di cui bisogna eliminare il corriere
	 * @param type                          un numero che identifica il corriere da eliminare
	 * @return Response
	 */
	@RequestMapping(value = "/{storeId}/{type}", method=RequestMethod.DELETE)
	public @ResponseBody Response delete(
			@PathVariable Long storeId,
			@PathVariable Integer type)
	{
		logger.info("START delete configuration on STORE " + storeId + " type " + type);
		Response response = new Response();

		try{
			configurationService.deleteConfiguration(storeId, type);
			response.setIsSuccess(true);
		}
		catch(Exception e) {
			logger.error("ERROR deleting configuration", e);
			response.setIsSuccess(false);
			response.setErrorLevel("e");
			response.setErrorMessage(e.getMessage());
		}

		logger.info("END delete configuration " + response.toString());
		return response;
	}

	@RequestMapping(value = "/verify/{type}", method=RequestMethod.GET)
    public @ResponseBody Response verify(@PathVariable(value="type", required=true) int type,
    								@RequestParam(value = "storeId", required = false) Long storeId,
    								@RequestParam(value = "username", required = false) String username,
    								@RequestParam(value = "password", required = false) String password,
    								@RequestParam(value = "accountId", required = false) String accountId,
    								@RequestParam(value = "seatCode", required = false) String seatCode,
    								@RequestParam(value = "customer", required = false) String customer,
    								@RequestParam(value = "virtualShipperType", required = false) Integer virtualShipperType
										 ){

		Response response = new Response();
		logger.info("START verify configuration on STORE "+storeId+ " type "+type);
		CarrierConfiguration configuration = new CarrierConfiguration();
		configuration.setCarrierType(type);
		configuration.setUsername(username);
		configuration.setPassword(password);
		configuration.setStoreId(storeId);
		configuration.setContractCode(accountId);
		configuration.setSeatCode(seatCode);
		configuration.setCustomerCode(customer);
		configuration.setVirtualShipperType(virtualShipperType);

		try {
			response.setIsSuccess(configurationService.verifyConfiguration(configuration));
		} catch (Exception e) {
			logger.error("Errore durante la verifica della configurazione", e);
			response.setIsSuccess(false);
		}
		logger.info("END verify configuration: "+response);

		return response;
	}
}
