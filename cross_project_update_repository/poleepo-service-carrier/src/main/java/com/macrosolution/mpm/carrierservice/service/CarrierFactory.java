package com.macrosolution.mpm.carrierservice.service;

import com.macrosolution.mpm.carrierservice.service.brt.BrtCarrierService;
import com.macrosolution.mpm.carrierservice.service.dhl.DhlCarrierService;
import com.macrosolution.mpm.carrierservice.service.fedex.FedexCarrierService;
import com.macrosolution.mpm.carrierservice.service.gls.GlsCarrierService;
import com.macrosolution.mpm.carrierservice.service.mit.MitCarrierService;
import com.macrosolution.mpm.carrierservice.service.qapla.QaplaCarrierService;
import com.macrosolution.mpm.carrierservice.service.spediscionline.SpedisciOnlineCarrierService;
import com.macrosolution.mpm.carrierservice.service.poste.PosteCarrierService;
import com.macrosolution.mpm.carrierservice.service.poste.delivery.PosteDeliveryCarrierService;
import com.macrosolution.mpm.carrierservice.service.tnt.TntCarrierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class CarrierFactory {

	public final static int CARRIER_TYPE_TNT	=1;
	public final static int CARRIER_TYPE_GLS	=2;
	public final static int CARRIER_TYPE_GENERIC	=3;
	public final static int CARRIER_TYPE_POSTE	=4;
	public final static int CARRIER_TYPE_SDA	=5;
	public final static int CARRIER_TYPE_CRONO	=6;
	public final static int CARRIER_TYPE_BRT	=7;
	public final static int CARRIER_TYPE_DHL	=8;
	public final static int CARRIER_TYPE_POSTE_DELIVERY	=9;
	public final static int CARRIER_TYPE_PAYPERSHIP	= 10;
	public final static int CARRIER_TYPE_MIT	= 21;
	public final static int CARRIER_TYPE_SPEDISCI_ONLINE = 22;
	public final static int CARRIER_TYPE_QAPLA = 23;
	public final static int CARRIER_TYPE_FEDEX = 30;

	@Autowired
	private ApplicationContext context;

	public CarrierService getService(int type) {
		switch (type / 1000) {
			case CARRIER_TYPE_TNT:
				return context.getBean(TntCarrierService.class);

			case CARRIER_TYPE_GLS:
				return context.getBean(GlsCarrierService.class);

			case CARRIER_TYPE_POSTE:
			case CARRIER_TYPE_SDA:
			case CARRIER_TYPE_CRONO:
				return context.getBean(PosteCarrierService.class);

			case CARRIER_TYPE_POSTE_DELIVERY:
				return context.getBean(PosteDeliveryCarrierService.class);

			case CARRIER_TYPE_BRT:
				return context.getBean(BrtCarrierService.class);

			case CARRIER_TYPE_DHL:
				return context.getBean(DhlCarrierService.class);

			case CARRIER_TYPE_SPEDISCI_ONLINE:
				return context.getBean(SpedisciOnlineCarrierService.class);

			case CARRIER_TYPE_MIT:
				return context.getBean(MitCarrierService.class);

			case CARRIER_TYPE_QAPLA:
				return context.getBean(QaplaCarrierService.class);

			case CARRIER_TYPE_FEDEX:
				return context.getBean(FedexCarrierService.class);
		}

		return null;
	}
}
