package com.macrosolution.mpm.carrierservice.service.fedex;

import com.macrosolution.mpm.carrierservice.domain.brt.BrtConfiguration;
import com.macrosolution.mpm.carrierservice.domain.fedex.FedexConfiguration;
import com.macrosolution.mpm.carrierservice.domain.paypership.SpedisciOnlineConfiguration;
import com.macrosolution.mpm.carrierservice.domain.qapla.QaplaConfiguration;
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
import com.macrosolution.mpm.carrierservice.model.DepartureDeposite;
import com.macrosolution.mpm.carrierservice.repository.configuration.FedexConfigurationRepository;
import com.macrosolution.mpm.carrierservice.service.CarrierFactory;
import com.macrosolution.mpm.carrierservice.service.CarrierService;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.ssl.TrustStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.SSLContext;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FedexCarrierService implements CarrierService {

    @Autowired
    private FedexConfigurationRepository fedexConfigurationRepository;

    // URL verso cui inviare le richieste
    public static String AUTH_API_URL = "https://apis-sandbox.fedex.com/oauth/token";

    @Override
    public TrackingInfo getTracking(CarrierConfiguration configuration, String code, String reference) throws Exception {
        return null;
    }

    // Salva la configurazione del corriere nella tabella 'fedex_configuration'
    @Override
    public CarrierConfiguration saveConfiguration(CarrierConfiguration configuration) {

        // La configurazione viene salvata nel db e restituita al ConfigurationController
        FedexConfiguration fedexConfiguration = new FedexConfiguration();

        // Prendiamo i parameri necessari dalla richiesta
        fedexConfiguration.setUsername(configuration.getUsername());
        fedexConfiguration.setPassword(configuration.getPassword());
        fedexConfiguration.setClientCode(configuration.getCustomerCode());
        fedexConfiguration.setStoreID(configuration.getStoreId());
        fedexConfiguration.setVirtualShipperType(configuration.getVirtualShipperType());
        fedexConfiguration.setShipperType(configuration.getCarrierType());
        fedexConfigurationRepository.save(fedexConfiguration);

        configuration.setId(fedexConfiguration.getId());
        return configuration;
    }

    // La funzione update viene richiamata ogni volta che provo ad aggiornare la configurazione di un corriere
    @Override
    public Boolean updateConfiguration(CarrierConfiguration request) {
        if(request.getDefault()!=null && request.getDefault()){
            fedexConfigurationRepository.resetDefault(request.getStoreId(), request.getVirtualShipperType());
        }

        FedexConfiguration fedexConfiguration = fedexConfigurationRepository.findByStoreIDAndVirtualShipperType(request.getStoreId(), request.getVirtualShipperType())
                .orElse(null);

        if(fedexConfiguration == null){
            return false;
        }

        // TODO: verifica che non devi settare il carryer type
        // penso di no, dato che siamo nella parte di update.
        // non devi settare nemmeno il virtualshippertype credo, dato che è statico

        if(request.getCustomerCode()!=null)
            //TODO: verifica che customercode corrisponda al clientcode
            fedexConfiguration.setClientCode(request.getCustomerCode());
        if(request.getUsername()!=null)
            fedexConfiguration.setUsername(request.getUsername());
        if(request.getPassword()!=null)
            fedexConfiguration.setPassword(request.getPassword());

        fedexConfigurationRepository.save(fedexConfiguration);

        return true;
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

    // Fa il get della conigurazione salvata corrente, viene chiamato ogni volta che si refresha la pagina di configurazione
    @Override
    public Optional<List<CarrierConfiguration>> getConfigurations(Long storeId, Optional<Integer> type) {
        Optional result;
        List<FedexConfiguration> conf = new ArrayList<>();

        if(type.isPresent()) {
            FedexConfiguration fedexConf = fedexConfigurationRepository.findByStoreIDAndVirtualShipperType(storeId, type.get())
                    .orElse(null);

            if(fedexConf != null) {
                conf.add(fedexConf);
            }
        }
        else {
            List<FedexConfiguration> fedexConf = fedexConfigurationRepository.findAllByStoreID(storeId)
                    .orElse(null);
            if(fedexConf != null && !fedexConf.isEmpty()){
                conf.addAll(fedexConf);
            }
        }

        List<CarrierConfiguration> response =null;
        if(!conf.isEmpty()) {
            response = conf.stream().map(c -> {
                CarrierConfiguration cc = null;
                FedexConfiguration fedexConfiguration = (FedexConfiguration) c;
                cc = new CarrierConfiguration();

                // Customer code == client code (forse nome di variabile usato è non standard, verifica...)
                cc.setCustomerCode(fedexConfiguration.getClientCode());
                cc.setUsername(fedexConfiguration.getUsername());
                cc.setPassword(fedexConfiguration.getPassword());
                cc.setCarrierType(CarrierFactory.CARRIER_TYPE_FEDEX);
                cc.setId(fedexConfiguration.getId());
                cc.setVirtualShipperType(fedexConfiguration.getVirtualShipperType());
                // TODO: aggiungi anche 'title'

                return cc;
            }).collect(Collectors.toList());
            result= Optional.of(response);
        }else{
            result = Optional.empty();
        }

        return result;
    }

    @Override
    public PickupResponse bookPickup(PdfLabelRequest request) {
        return null;
    }

    @Override
    public Boolean verifyConfiguration(CarrierConfiguration configuration) {
        // Verifico che la configurazione sia corretta inviando una richiesta di autenticazione
        // con il client id e il client-secret inserito

        //log.debug("username value {}", configuration.getUsername());
        //log.debug("password value {}", configuration.getPassword());

        HttpStatus status = null;
        boolean result = true;

        // Crea gli headers
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCEPT, "application/json");
        headers.add(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded");

        // Crea il payload
        Map<String, String> payload = new HashMap<>();
        payload.put("grant_type", "client_credentials");
        payload.put("client_id", configuration.getUsername());
        payload.put("client_secret", configuration.getPassword());

        // Converte il payload in formato URL-encoded
        StringBuilder requestBody = new StringBuilder();
        for (Map.Entry<String, String> entry : payload.entrySet()) {
            if (requestBody.length() > 0) {
                requestBody.append("&");
            }
            requestBody.append(entry.getKey()).append("=").append(entry.getValue());
        }

        // Crea la richiesta
        HttpEntity<String> httpRequest = new HttpEntity<>(requestBody.toString(), headers);

        try {
            RestTemplate rt = this.getFedexClient();
            ResponseEntity<String> authResponse = rt.exchange(AUTH_API_URL, HttpMethod.POST, httpRequest, String.class);
        }
        catch (HttpClientErrorException e) {
            status = e.getStatusCode();
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN || status == HttpStatus.METHOD_NOT_ALLOWED) {
                result = false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            result = false;
        }
        return result;
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

    private RestTemplate getFedexClient() throws Exception {
        TrustStrategy acceptingTrustStrategy = (cert, authType) -> true;
        SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, acceptingTrustStrategy).build();
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext, NoopHostnameVerifier.INSTANCE);

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("https", sslsf).register("http", new PlainConnectionSocketFactory()).build();

        BasicHttpClientConnectionManager connectionManager = new BasicHttpClientConnectionManager(
                socketFactoryRegistry);
        CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslsf)
                .setConnectionManager(connectionManager).build();

        HttpComponentsClientHttpRequestFactory clientHttpRequestFactory
                = new HttpComponentsClientHttpRequestFactory();

        clientHttpRequestFactory.setHttpClient(httpClient);

        RestTemplate rt = new RestTemplate(clientHttpRequestFactory);

        return rt;
    }
}
