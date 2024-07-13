package com.macrosolution.mpm.carrierservice.service.configuration;


import com.macrosolution.mpm.carrierservice.model.CarrierConfiguration;
import com.macrosolution.mpm.carrierservice.service.CarrierFactory;
import com.macrosolution.mpm.carrierservice.service.CarrierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class ConfigurationService {

    @Autowired
    private CarrierFactory factory;

    public CarrierConfiguration saveConfiguration(CarrierConfiguration request){
        CarrierService carrierService = factory.getService(request.getVirtualShipperType());
        return carrierService.saveConfiguration(request);
    }


    public Boolean updateConfiguration(CarrierConfiguration request){
        CarrierService carrierService = factory.getService(request.getVirtualShipperType());
        return carrierService.updateConfiguration(request);
    }

    public Collection<? extends CarrierConfiguration> getConfigurations(Long storeId, Optional<Integer> type) {
        List<CarrierConfiguration> response = new ArrayList<>();
        if(type.isPresent()){
            response.addAll(factory.getService(type.get()).getConfigurations(storeId, type).orElse(new ArrayList<>()));
        }else{
            response.addAll(factory.getService(CarrierFactory.CARRIER_TYPE_TNT * 1000).getConfigurations(storeId, type).orElse(new  ArrayList<>()));
            response.addAll(factory.getService(CarrierFactory.CARRIER_TYPE_GLS * 1000).getConfigurations(storeId, type).orElse(new ArrayList<>()));
            response.addAll(factory.getService(CarrierFactory.CARRIER_TYPE_POSTE * 1000).getConfigurations(storeId, type).orElse(new ArrayList<>()));
            response.addAll(factory.getService(CarrierFactory.CARRIER_TYPE_BRT * 1000).getConfigurations(storeId, type).orElse(new ArrayList<>()));
            response.addAll(factory.getService(CarrierFactory.CARRIER_TYPE_DHL * 1000).getConfigurations(storeId, type).orElse(new ArrayList<>()));
            response.addAll(factory.getService(CarrierFactory.CARRIER_TYPE_POSTE_DELIVERY * 1000).getConfigurations(storeId, type).orElse(new ArrayList<>()));
            response.addAll(factory.getService(CarrierFactory.CARRIER_TYPE_SPEDISCI_ONLINE * 1000).getConfigurations(storeId, type).orElse(new ArrayList<>()));
            response.addAll(factory.getService(CarrierFactory.CARRIER_TYPE_MIT * 1000).getConfigurations(storeId, type).orElse(new ArrayList<>()));
            response.addAll(factory.getService(CarrierFactory.CARRIER_TYPE_QAPLA * 1000).getConfigurations(storeId, type).orElse(new ArrayList<>()));
            response.addAll(factory.getService(CarrierFactory.CARRIER_TYPE_FEDEX * 1000).getConfigurations(storeId, type).orElse(new ArrayList<>()));

        }

        return response;
    }

    /**
     * Metodo per l'eliminazione delle configurazioni di uno specifico corriere di uno store.
     * @param storeId                       lo store di cui bisogna eliminare il corriere
     * @param virtualShipperType                          un numero che identifica il corriere da eliminare
     * @return void
     */
    public void deleteConfiguration(Long storeId, Integer virtualShipperType) {
        /* ottengo il servizo in base al tipo di configurazione da eliminare */
        CarrierService carrierService = factory.getService(virtualShipperType);

        /* ottengo la lista delle configurazioni da eliminare */
        List<CarrierConfiguration> configurations = carrierService.getConfigurations(storeId, Optional.of(virtualShipperType))
                .orElse(new ArrayList<>());

        /* elimino le configurazioni */
        for(CarrierConfiguration configuration : configurations)
            carrierService.deleteConfigurationById(configuration.getId());
    }

    public Boolean verifyConfiguration(CarrierConfiguration request){
        CarrierService carrierService = factory.getService(request.getVirtualShipperType());
        return carrierService.verifyConfiguration(request);
    }


}
