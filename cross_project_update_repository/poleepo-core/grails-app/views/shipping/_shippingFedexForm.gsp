<%@ page import = "com.macrosolution.mpm.shipping.Parcel" %>
<%@ page import = "com.macrosolution.mpm.shipping.fedex.FedexBoxType" %>
<%@ page import = "com.macrosolution.mpm.shipping.fedex.FedexServiceType" %>

<cdn:css href="css/datepicker/datepicker3.min.css" />
<cdn:js src="js/datepicker/bootstrap-datepicker.min.js" />

<g:set var="shipId" value="${shipping?.id ?: 0}"></g:set>

<g:if test="${service == null || service?.isEmpty()}">
    <!-- Scelta del tipo di spedizione -->
    <div class="row">
        <div class="col-md-12">
            <div class="form-group">
                <label for="BRTServiceType_${shipId}" class="col-sm-3 control-label">
                    <g:message code="shipping.fedex.service"/>
                </label>
                <div class="col-sm-9">
                    <div class="input-group">
                        <g:select class="form-control" name="fedexServiceType" id="FEDEXServiceType_${shipId}"
                                  from="${FedexServiceType.list()}" value="${shipping?.fedexServiceType?.id ?: FedexServiceType.findByLabel(FedexServiceType.SERVIZIO_STANDARD_VALUE)?.id}"
                                  optionValue="label" optionKey="id" required="required"/>
                    </div>
                </div>
            </div>
        </div>
    </div>
</g:if>
<g:else>
    <input type="hidden" name="fedexServiceType" value="${FedexServiceType.findByValue(service)?.id}"/>
</g:else>

<!-- Scelta del codice di filiale -->
<div class="row">
    <div class="col-md-12">
        <div class="form-group">
            <label for="BRTdepartureDepot" class="col-sm-3 control-label">
                <g:message code="shipper.brtUserConfiguration.seatCode"/>
            </label>
            <div class="col-sm-9">
                <div class="input-group">
                    <g:select class="form-control" name="departureDepot" id="BRTdepartureDepot"
                              from="${conf.departureDeposites}" value="${shipping?.departureDepot}"
                              optionValue="${{it.label+' - '+it.id}}" optionKey="id" required="required"/>
                </div>
            </div>
        </div>
    </div>
</div>

test value