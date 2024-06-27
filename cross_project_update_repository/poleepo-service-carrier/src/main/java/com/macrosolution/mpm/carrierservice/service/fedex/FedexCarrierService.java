package com.macrosolution.mpm.carrierservice.service.fedex;

import com.macrosolution.mpm.carrierservice.dto.request.CancelRequest;
import com.macrosolution.mpm.carrierservice.dto.request.CustomShipperRequest;
import com.macrosolution.mpm.carrierservice.dto.request.GetCostRequest;
import com.macrosolution.mpm.carrierservice.dto.request.PdfLabelRequest;
import com.macrosolution.mpm.carrierservice.dto.response.Response;
import com.macrosolution.mpm.carrierservice.dto.response.address.AddressCheckResponse;
import com.macrosolution.mpm.carrierservice.dto.response.address.AddressResponse;
import com.macrosolution.mpm.carrierservice.dto.response.shipper.CustomShipperRequestResponse;
import com.macrosolution.mpm.carrierservice.dto.response.shipping.CreateShippingResponse;
import com.macrosolution.mpm.carrierservice.dto.response.shipping.GetCostResponse;
import com.macrosolution.mpm.carrierservice.dto.response.shipping.PickupResponse;
import com.macrosolution.mpm.carrierservice.dto.response.shipping.ShipmentServiceResponse;
import com.macrosolution.mpm.carrierservice.dto.response.tracking.TrackingInfo;
import com.macrosolution.mpm.carrierservice.model.CarrierConfiguration;
import com.macrosolution.mpm.carrierservice.service.CarrierService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class FedexCarrierService implements CarrierService {


    @Override
    public TrackingInfo getTracking(CarrierConfiguration configuration, String code, String reference) throws Exception {
        return null;
    }

    @Override
    public CarrierConfiguration saveConfiguration(CarrierConfiguration configuration) {
        return null;
    }

    @Override
    public Boolean updateConfiguration(CarrierConfiguration configuration) {
        return null;
    }

    @Override
    public void deleteConfigurationById(Integer configurationId) {

    }

    @Override
    public CreateShippingResponse pdfLabel(PdfLabelRequest request) throws Exception {
        return null;
    }

    @Override
    public Response delete(CancelRequest request) {
        return null;
    }

    @Override
    public Optional<List<CarrierConfiguration>> getConfigurations(Long storeId, Optional<Integer> type) {
        return Optional.empty();
    }

    @Override
    public PickupResponse bookPickup(PdfLabelRequest request) {
        return null;
    }

    @Override
    public Boolean verifyConfiguration(CarrierConfiguration request) {
        return null;
    }

    @Override
    public AddressCheckResponse checkAddress(String provincia, String cap, String comune, String indirizzo) throws Exception {
        return null;
    }

    @Override
    public List<AddressResponse> listPickupAddresses(Long storeId) throws Exception {
        return null;
    }

    @Override
    public List<ShipmentServiceResponse> listShipmentServices(Long storeId, PdfLabelRequest request) throws Exception {
        return null;
    }

    @Override
    public GetCostResponse getShippingCost(Long storeId, GetCostRequest request) throws Exception {
        return null;
    }

    @Override
    public CustomShipperRequestResponse customShipperRequest(CustomShipperRequest customRequest) {
        return null;
    }
}
